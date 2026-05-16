package com.ospchat.android.data.discovery

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton facade over [NsdPeerDiscovery]. The foreground service drives
 * [start] / [stop]; [PeerRepository][com.ospchat.android.data.peers.PeerRepository]
 * subscribes to [peerSnapshot]; the HTTP route layer uses [findPeer] for the
 * trust check.
 */
@Singleton
class DiscoveryRepository
    @Inject
    constructor(
        private val nsdPeerDiscovery: NsdPeerDiscovery,
    ) {
        /** Live snapshot of peers currently visible via NSD, keyed by UUID. */
        val peerSnapshot: StateFlow<Map<String, Peer>> = nsdPeerDiscovery.peers

        /** Synchronous snapshot lookup for hot paths like inbound HTTP requests. */
        fun findPeer(uuid: String): Peer? = nsdPeerDiscovery.peers.value[uuid]

        fun start(
            nickname: String,
            uuid: String,
            port: Int,
        ) = nsdPeerDiscovery.start(nickname, uuid, port)

        fun stop() = nsdPeerDiscovery.stop()
    }
