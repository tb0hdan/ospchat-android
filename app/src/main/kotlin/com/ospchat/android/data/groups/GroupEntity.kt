package com.ospchat.android.data.groups

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A group conversation persisted on this device. Every member of a group
 * stores their own copy of the row; the [creatorUuid] identifies who has the
 * authority to mutate the member list. [membershipUpdatedAt] is the
 * snapshot version: the creator bumps it on every add/remove, and receivers
 * only accept newer snapshots from the creator.
 *
 * [lastReadAt] is a per-device unread high-water mark; the unread count is
 * the number of inbound messages with `sent_at > last_read_at`.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "creator_uuid") val creatorUuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "membership_updated_at") val membershipUpdatedAt: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long = 0L,
)
