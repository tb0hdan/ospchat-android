package com.ospchat.android.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton facade over [NsdPeerDiscovery]. The foreground service drives
 * [start] / [stop]; ViewModels observe [peers].
 */
@Singleton
class DiscoveryRepository @Inject constructor(
    private val nsdPeerDiscovery: NsdPeerDiscovery,
) {

    val peers: Flow<List<Peer>> = nsdPeerDiscovery.peers.map { snapshot ->
        snapshot.values.sortedBy { it.nickname.lowercase() }
    }

    fun start(nickname: String, uuid: String) = nsdPeerDiscovery.start(nickname, uuid)

    fun stop() = nsdPeerDiscovery.stop()
}
