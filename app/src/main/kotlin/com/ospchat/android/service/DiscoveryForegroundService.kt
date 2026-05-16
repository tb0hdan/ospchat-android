package com.ospchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ospchat.android.R
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerRepository
import com.ospchat.android.net.server.MessageServer
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

    @Inject lateinit var messageServer: MessageServer

    @Inject lateinit var wifiManager: WifiManager

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
                try {
                    val port = messageServer.start(uuid = uuid, nickname = nickname)
                    discoveryRepository.start(
                        nickname = nickname,
                        uuid = uuid,
                        port = port,
                    )
                    fullyStarted = true
                    peerSyncJob =
                        scope.launch {
                            discoveryRepository.peerSnapshot.collect { snapshot ->
                                snapshot.values.forEach { peer ->
                                    peerRepository.recordSeen(peer)
                                }
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
        messageServer.stop()
        releaseMulticastLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DiscoveryForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DiscoveryForegroundService::class.java))
        }
    }
}
