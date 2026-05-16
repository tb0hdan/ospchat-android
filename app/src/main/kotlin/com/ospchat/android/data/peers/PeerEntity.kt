package com.ospchat.android.data.peers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A peer we have seen at least once via NSD. Persisted by UUID so the peer
 * survives IP address changes, app restarts, and going offline.
 *
 * [lastReadAt] is the high-water mark of inbound messages the local user has
 * acknowledged from this peer; anything newer counts as unread.
 */
@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "last_host") val lastHost: String,
    @ColumnInfo(name = "last_port") val lastPort: Int,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long = 0L,
)
