package com.ospchat.android.data.peers

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One distinct nickname [uuid] has ever published via NSD. Composite primary
 * key on `(uuid, nickname)` so a peer who reverts to an old name does not
 * create a duplicate row — only `lastSeenAt` advances.
 */
@Entity(tableName = "peer_nicknames", primaryKeys = ["uuid", "nickname"])
data class PeerNicknameEntity(
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)
