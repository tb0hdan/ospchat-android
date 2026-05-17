package com.ospchat.android.data.groups

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message posted to a group. Mirrors [MessageEntity][com.ospchat.android.data.messages.MessageEntity]
 * but keyed by [groupId] instead of `peer_uuid`. Group messages have no
 * read-receipt tracking in v1, so [status] only takes values
 * `SENDING` / `DELIVERED` / `FAILED`.
 */
@Entity(
    tableName = "group_messages",
    indices = [Index(value = ["group_id", "sent_at"])],
)
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "from_uuid") val fromUuid: String,
    @ColumnInfo(name = "from_nickname") val fromNickname: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "status") val status: String,
)
