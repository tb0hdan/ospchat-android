package com.ospchat.android.data.groups

/** Heavy detail bundle for the group Info dialog. Loaded on demand. */
data class GroupInfo(
    val record: GroupRecord,
    val creatorNickname: String,
    val membershipUpdatedAt: Long,
    val members: List<GroupMemberEntity>,
)
