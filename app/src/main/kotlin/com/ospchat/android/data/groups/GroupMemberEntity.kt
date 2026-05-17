package com.ospchat.android.data.groups

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One member of a group. [memberNickname] is snapshotted at add-time so the
 * UI can still label the row when the member is offline / unknown to the
 * `peers` table. Composite primary key `(group_id, member_uuid)` enforces
 * one row per member per group.
 */
@Entity(tableName = "group_members", primaryKeys = ["group_id", "member_uuid"])
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_uuid") val memberUuid: String,
    @ColumnInfo(name = "member_nickname") val memberNickname: String,
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
)
