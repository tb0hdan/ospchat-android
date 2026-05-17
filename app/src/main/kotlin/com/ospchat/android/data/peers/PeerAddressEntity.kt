package com.ospchat.android.data.peers

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One distinct `host:port` we have ever observed for [uuid]. Composite primary
 * key on `(uuid, host, port)` so the same address is recorded once per peer
 * regardless of how many NSD events update its `lastSeenAt`.
 */
@Entity(tableName = "peer_addresses", primaryKeys = ["uuid", "host", "port"])
data class PeerAddressEntity(
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "host") val host: String,
    @ColumnInfo(name = "port") val port: Int,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)
