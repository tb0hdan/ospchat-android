package com.ospchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ospchat.android.MainActivity
import com.ospchat.android.R
import com.ospchat.shared.data.discovery.DiscoveryRepository
import com.ospchat.shared.data.groups.GroupSyncer
import com.ospchat.shared.data.identity.IdentityRepository
import com.ospchat.shared.data.discovery.Endpoint
import com.ospchat.shared.data.discovery.Peer
import com.ospchat.shared.data.peers.GossipedPeerStore
import com.ospchat.shared.data.peers.PeerAvatarSync
import com.ospchat.shared.data.peers.PeerDao
import com.ospchat.shared.data.peers.PeerRepository
import com.ospchat.shared.data.peers.PeerRouter
import com.ospchat.shared.data.peers.RelayBridgeRegistry
import com.ospchat.shared.util.Base64Util
import com.ospchat.shared.net.server.MessageServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that hosts the NSD registration + discovery and the
 * embedded Ktor server for as long as the user wants to be visible. Holds a
 * [WifiManager.MulticastLock] for the duration because many Wi-Fi drivers
 * filter multicast packets (mDNS) when the application is not actively
 * listening.
 *
 * The multicast lock is acquired on the main thread inside [onStartCommand]
 * so there's no race between an async acquire and [onDestroy].
 *
 * Returns [START_NOT_STICKY] — discovery and the server are only useful when
 * the UI starts them, so we don't want the system to relaunch this service
 * in isolation.
 */
@AndroidEntryPoint
class DiscoveryForegroundService : Service() {
    @Inject lateinit var identityRepository: IdentityRepository

    @Inject lateinit var discoveryRepository: DiscoveryRepository

    @Inject lateinit var peerRepository: PeerRepository

    @Inject lateinit var peerDao: PeerDao

    @Inject lateinit var gossipedPeerStore: GossipedPeerStore

    @Inject lateinit var relayBridgeRegistry: RelayBridgeRegistry

    @Inject lateinit var peerRouter: PeerRouter

    @Inject lateinit var peerAvatarSync: PeerAvatarSync

    @Inject lateinit var groupSyncer: GroupSyncer

    @Inject lateinit var messageServer: MessageServer

    @Inject lateinit var wifiManager: WifiManager

    @Inject lateinit var turnServer: com.ospchat.shared.turn.OspChatTurnServer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private var peerSyncJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * `true` once we've successfully started the server and NSD. A subsequent
     * `onStartCommand` (e.g. from `PeersScreen.LaunchedEffect` re-firing after
     * navigation back) is a no-op while this flag is set.
     */
    @Volatile private var fullyStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForegroundNotification()
        acquireMulticastLock()
        if (fullyStarted || startJob?.isActive == true) {
            // Already running (or about to be). Subsequent intents from the
            // UI re-binding are normal — just stay alive.
            return START_NOT_STICKY
        }
        startJob =
            scope.launch {
                val nickname = identityRepository.nicknameFlow.first()
                if (nickname.isNullOrBlank()) {
                    Log.w(TAG, "No nickname set; stopping service")
                    stopSelf()
                    return@launch
                }
                val uuid = identityRepository.ensureUuid()
                // Phase 2a multi-network bridging: per-install Ed25519 keypair
                // backs the pubkey we broadcast via mDNS TXT (pk=) and serve
                // from GET /v1/info; receivers TOFU-pin on first sight and
                // reject same-UUID resolutions from a different pubkey.
                val publicKeyB64 =
                    runCatching {
                        Base64Util.encode(
                            identityRepository.ensureSigningKeyPair().publicKeyBytes(),
                        )
                    }.getOrElse {
                        Log.w(TAG, "ensureSigningKeyPair failed; starting without pubkey", it)
                        null
                    }
                // Phase 4: read the user's relay opt-in. Bridge peers that
                // sit on multiple LANs (typically a desktop) toggle this to
                // forward messages between otherwise-unrouted networks.
                val relayEnabled =
                    runCatching { identityRepository.currentRelayEnabled() }
                        .getOrElse {
                            Log.w(TAG, "currentRelayEnabled failed; defaulting to false", it)
                            false
                        }
                try {
                    val preferredPort = identityRepository.lastServerPort() ?: 0
                    val port =
                        messageServer.start(
                            uuid = uuid,
                            nickname = nickname,
                            preferredPort = preferredPort,
                            publicKeyB64 = publicKeyB64,
                            relayEnabled = relayEnabled,
                        )
                    runCatching { identityRepository.setLastServerPort(port) }
                        .onFailure { Log.w(TAG, "setLastServerPort($port) failed", it) }
                    // Phase 2b multi-network bridging: warm the discovery
                    // service's TOFU pubkey pin map from the persistent
                    // peer table BEFORE start(). This is what makes the
                    // F9 hijack defence survive a process restart — an
                    // attacker that wins the post-restart mDNS race with
                    // a different pubkey is rejected because we recognise
                    // the legitimate peer's pubkey from disk.
                    val persistedPins =
                        runCatching {
                            peerDao.loadPinnedPubkeys().associate { it.uuid to it.pubKey }
                        }.getOrElse {
                            Log.w(TAG, "loadPinnedPubkeys failed; starting without persistent pins", it)
                            emptyMap()
                        }
                    discoveryRepository.preloadPinnedPubkeys(persistedPins)

                    // Phase 4 multi-network bridging cleanup: drop any
                    // self-row that an earlier gossip-collector bug may
                    // have left behind (before the self-filter below was
                    // in place). Safe no-op if no such row exists.
                    runCatching { peerDao.deleteByUuid(uuid) }
                        .onFailure { Log.w(TAG, "deleteByUuid(self=$uuid) cleanup failed", it) }
                    discoveryRepository.start(
                        nickname = nickname,
                        uuid = uuid,
                        port = port,
                        publicKeyB64 = publicKeyB64,
                    )
                    Log.d(
                        TAG,
                        "discovery started: uuid=$uuid nickname=$nickname port=$port " +
                            "pk=${publicKeyB64?.take(16) ?: "<none>"} persistedPins=${persistedPins.size} " +
                            "relayEnabled=$relayEnabled",
                    )
                    // Phase 3 multi-network bridging — start the embedded TURN
                    // server when the user has opted into relay. The server
                    // enumerates UP / non-loopback IPv4 interfaces itself.
                    // Tied to the foreground service lifecycle: stops in
                    // onDestroy, restarts on next service launch.
                    if (relayEnabled) {
                        runCatching { turnServer.start() }
                            .onFailure { Log.w(TAG, "TURN server start failed", it) }
                    }
                    fullyStarted = true
                    // Phase 4: auto-record gossiped peers as phantom
                    // PeerEntity rows (lastHost="" sentinel) so the UI
                    // surfaces them as sendable targets. The send path
                    // detects the phantom and routes via PeerRouter
                    // through a relay-enabled bridge.
                    //
                    // Gate on PeerRouter — gossip can reach us from a
                    // bridge whose `relayEnabled = false` (or that's
                    // offline), in which case there's no actual way to
                    // message the gossiped peer. Skip recording until a
                    // viable route appears.
                    scope.launch {
                        kotlinx.coroutines.flow.combine(
                            gossipedPeerStore.peers,
                            relayBridgeRegistry.bridges,
                            discoveryRepository.peerSnapshot,
                        ) { gossiped, _, _ -> gossiped }.collect { gossiped ->
                            gossiped.values.forEach { gp ->
                                // Defensive self-filter: never auto-record a
                                // phantom for the local user's own UUID.
                                if (gp.uuid == uuid) return@forEach
                                val hasRoute = peerRouter.routeTo(gp.uuid)?.toUuid != null
                                if (!hasRoute) return@forEach
                                val phantom =
                                    Peer(
                                        uuid = gp.uuid,
                                        nickname = gp.nickname,
                                        candidates = listOf(Endpoint(host = "", port = 0)),
                                        publicKey = gp.publicKey,
                                    )
                                runCatching { peerRepository.recordSeen(phantom) }
                                    .onFailure { Log.w(TAG, "recordSeen(phantom ${gp.uuid}) failed", it) }
                            }
                        }
                    }
                    // Phase 4 multi-network bridging — periodic liveness
                    // probe. Android NSD holds onto stale mDNS records
                    // for hours when a peer disappears without sending
                    // a goodbye, so peerSnapshot membership alone isn't
                    // a reliable signal of bridge reachability. Every
                    // 30 s we re-pull /v1/info from each directly-
                    // discovered peer; PeerAvatarSync.sync drops the
                    // peer from RelayBridgeRegistry on failure, which
                    // collapses gossiped-via-that-bridge peers to
                    // offline in the UI.
                    scope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(LIVENESS_PROBE_MS)
                            discoveryRepository.peerSnapshot.value.values.forEach { peer ->
                                launch { peerAvatarSync.sync(peer) }
                            }
                        }
                    }
                    peerSyncJob =
                        scope.launch {
                            var previous = emptySet<String>()
                            discoveryRepository.peerSnapshot.collect { snapshot ->
                                snapshot.values.forEach { peer ->
                                    peerRepository.recordSeen(peer)
                                }
                                // Fire an info+avatar sync for any peer that
                                // was absent from the previous snapshot (first
                                // discovery, or re-discovery after they
                                // restarted their service to pick up a name /
                                // avatar change).
                                val current = snapshot.keys
                                (current - previous).forEach { uuid ->
                                    val peer = snapshot[uuid] ?: return@forEach
                                    launch { peerAvatarSync.sync(peer) }
                                    launch { groupSyncer.sync(peer) }
                                }
                                previous = current
                            }
                        }
                } catch (ce: CancellationException) {
                    // Service is being torn down; onDestroy handles cleanup.
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start messaging stack", t)
                    stopSelf()
                }
            }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Order matters: cancel the launching job first so it cannot race with
        // teardown, then stop NSD and the server, then release the multicast
        // lock, and finally cancel the supervising scope.
        fullyStarted = false
        peerSyncJob?.cancel()
        peerSyncJob = null
        startJob?.cancel()
        startJob = null
        discoveryRepository.stop()
        runCatching { turnServer.stop() }
        messageServer.stop()
        releaseMulticastLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val exitIntent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXIT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val exitPendingIntent =
            PendingIntent.getActivity(
                this,
                EXIT_REQUEST_CODE,
                exitIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notification_action_exit),
                    exitPendingIntent,
                ).build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_discovery),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.notification_text)
            }
        manager.createNotificationChannel(channel)
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock =
            wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseMulticastLock() {
        multicastLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
    }

    companion object {
        private const val TAG = "DiscoveryFgService"
        private const val CHANNEL_ID = "ospchat_discovery"
        private const val NOTIFICATION_ID = 1
        private const val MULTICAST_LOCK_TAG = "ospchat-mdns"
        private const val EXIT_REQUEST_CODE = 100

        /**
         * Phase 4 multi-network bridging — interval between periodic
         * liveness probes. 30 s strikes a balance between "fast enough
         * for the UI to feel responsive when a bridge drops" and "rare
         * enough that we don't flood the network with /v1/info fetches."
         */
        private const val LIVENESS_PROBE_MS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DiscoveryForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DiscoveryForegroundService::class.java))
        }
    }
}
