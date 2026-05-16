package com.ospchat.android.data.peers

import android.util.Log
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.net.client.MessageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies every currently-known peer that our `/v1/info` has changed —
 * each peer receives a `POST /v1/notify-refresh`, which on their side
 * fires their local [PeerAvatarSync] for us. They then pull the fresh
 * `/v1/info` and (if the avatar hash differs) `/v1/avatar` over HTTP.
 *
 * This is the avatar-change propagation path. We deliberately avoid
 * bouncing the foreground service for avatar changes: the multicast-lock
 * release that comes with a service bounce destabilises the Wi-Fi stack
 * (we observed `NoRouteToHostException` / `ConnectTimeoutException` for
 * tens of seconds after a bounce on some networks).
 *
 * Each notification fires in parallel on an internal IO scope so the
 * call site doesn't block waiting for unresponsive peers.
 */
@Singleton
class PeerInfoNotifier
    @Inject
    constructor(
        private val client: MessageClient,
        private val discoveryRepository: DiscoveryRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun broadcastRefresh() {
            val peers = discoveryRepository.peerSnapshot.value.values
            for (peer in peers) {
                scope.launch {
                    runCatching { client.notifyRefresh(peer) }
                        .onFailure { Log.w(TAG, "notifyRefresh to ${peer.uuid} failed", it) }
                }
            }
        }

        private companion object {
            const val TAG = "PeerInfoNotifier"
        }
    }
