package com.ospchat.android.data.peers

import com.ospchat.android.data.discovery.Peer

/**
 * UI-facing peer record: persisted identity (UUID + last-known address)
 * joined with the live NSD state and an unread-message count.
 */
data class PeerRecord(
    val uuid: String,
    val nickname: String,
    val host: String,
    val port: Int,
    val isOnline: Boolean,
    val lastSeenAt: Long,
    val unreadCount: Int,
    val avatarLocalPath: String?,
) {
    fun toPeer(): Peer = Peer(uuid = uuid, nickname = nickname, host = host, port = port)
}
