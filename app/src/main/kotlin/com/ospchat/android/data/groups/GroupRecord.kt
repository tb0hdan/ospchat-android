package com.ospchat.android.data.groups

/**
 * UI-facing group list row. [isCreator] is derived from the local user's UUID
 * against [creatorUuid]; [memberCount] excludes nothing (the creator is
 * always counted).
 */
data class GroupRecord(
    val id: String,
    val name: String,
    val kind: GroupKind,
    val creatorUuid: String,
    val createdAt: Long,
    val memberCount: Int,
    val unreadCount: Int,
    val isCreator: Boolean,
)
