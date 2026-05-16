package com.ospchat.android.data.peers

import android.util.Log
import com.ospchat.android.data.avatar.AvatarStore
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.net.client.MessageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a peer's `/v1/info` and, when the announced `avatarHash` differs
 * from the locally-cached one, pulls fresh avatar bytes via `/v1/avatar` and
 * persists them. Triggered by [com.ospchat.android.service.DiscoveryForegroundService]
 * whenever a peer transitions from "not in NSD snapshot" to "in NSD
 * snapshot".
 *
 * The HTTP calls are retried with backoff because immediately after a peer
 * bounces its service (which is how nickname / avatar changes propagate)
 * the network briefly drops connections — we observed `ConnectTimeout` and
 * `NoRouteToHost` exceptions on the very first attempt with the timing
 * tight enough that retries fix it.
 */
@Singleton
class PeerAvatarSync
    @Inject
    constructor(
        private val client: MessageClient,
        private val peerDao: PeerDao,
        private val avatarStore: AvatarStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Fire-and-forget variant for callers (route handlers) that don't
         * want to block on the sync completing. The work runs on an
         * internal IO scope so it survives past the request lifetime.
         */
        fun triggerSync(peer: Peer) {
            scope.launch { sync(peer) }
        }

        suspend fun sync(peer: Peer) {
            val info = withRetry(label = "/v1/info", peerUuid = peer.uuid) { client.getInfo(peer) } ?: return

            val entity = peerDao.findByUuid(peer.uuid) ?: return
            val newHash = info.avatarHash
            if (newHash == entity.avatarHash) return

            if (newHash == null) {
                // Peer cleared their avatar — drop our cached files.
                avatarStore.cleanupPeerExcept(peer.uuid, keepHash = null)
                peerDao.updateAvatar(peer.uuid, hash = null, localPath = null)
                return
            }

            val savedPath =
                withRetry(label = "/v1/avatar", peerUuid = peer.uuid) {
                    client.fetchAvatar(peer) { stream ->
                        avatarStore.writePeer(peer.uuid, newHash, stream).absolutePath
                    }
                } ?: return

            avatarStore.cleanupPeerExcept(peer.uuid, keepHash = newHash)
            peerDao.updateAvatar(peer.uuid, hash = newHash, localPath = savedPath)
        }

        /**
         * Run [block] up to four times, sleeping 0 / 500 ms / 2 s / 5 s
         * between attempts. Returns the first successful result, or `null`
         * if every attempt threw.
         */
        private suspend fun <T> withRetry(
            label: String,
            peerUuid: String,
            block: suspend () -> T,
        ): T? {
            val backoffs = longArrayOf(0L, 500L, 2_000L, 5_000L)
            backoffs.forEachIndexed { index, backoff ->
                if (backoff > 0) delay(backoff)
                val result = runCatching { block() }
                if (result.isSuccess) return result.getOrNull()
                Log.w(
                    TAG,
                    "Fetch $label from $peerUuid failed (attempt ${index + 1}/${backoffs.size})",
                    result.exceptionOrNull(),
                )
            }
            return null
        }

        private companion object {
            const val TAG = "PeerAvatarSync"
        }
    }
