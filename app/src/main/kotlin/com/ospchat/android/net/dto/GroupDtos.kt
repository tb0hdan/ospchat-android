package com.ospchat.android.net.dto

import kotlinx.serialization.Serializable

/**
 * Authoritative description of a group's identity + membership at a point in
 * time. Carried on every group-related request so receivers can auto-create
 * (or upgrade) their local copy. Receivers only accept membership changes if
 * the snapshot is sent by the [creatorUuid] AND its [membershipUpdatedAt]
 * is newer than the local copy.
 */
@Serializable
data class GroupSnapshotDto(
    val id: String,
    val name: String,
    val kind: String,
    val creatorUuid: String,
    val createdAt: Long,
    val membershipUpdatedAt: Long,
    val members: List<GroupMemberDto>,
)

@Serializable
data class GroupMemberDto(
    val uuid: String,
    val nickname: String,
)

/** Wire schema for one inbound group message. */
@Serializable
data class GroupMessageDto(
    val id: String,
    val fromUuid: String,
    val fromNickname: String,
    val body: String,
    val sentAt: Long,
)

/** `POST /v1/groups/messages` body. */
@Serializable
data class GroupMessagePostDto(
    val snapshot: GroupSnapshotDto,
    val message: GroupMessageDto,
)

/** Per-group sync cursor: caller's latest known `sent_at` for [groupId]. */
@Serializable
data class GroupSyncCursorDto(
    val groupId: String,
    val latestSentAt: Long,
)

/** `POST /v1/groups/sync` body. */
@Serializable
data class GroupSyncRequestDto(
    val fromUuid: String,
    val cursors: List<GroupSyncCursorDto>,
)

/** Per-group sync payload: snapshot the caller should adopt + missing messages. */
@Serializable
data class GroupSyncPayloadDto(
    val snapshot: GroupSnapshotDto,
    val messages: List<GroupMessageDto>,
)

/** `POST /v1/groups/sync` response. */
@Serializable
data class GroupSyncResponseDto(
    val payloads: List<GroupSyncPayloadDto>,
)

/** `POST /v1/groups/leave` body. The [fromUuid] is the member who is leaving. */
@Serializable
data class GroupLeaveDto(
    val groupId: String,
    val fromUuid: String,
)
