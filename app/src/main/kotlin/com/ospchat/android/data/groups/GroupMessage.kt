package com.ospchat.android.data.groups

/**
 * UI-facing group message. Same shape as [Message][com.ospchat.android.data.messages.Message]
 * minus attachments + read-receipts (out of scope for v1 groups).
 */
data class GroupMessage(
    val id: String,
    val groupId: String,
    val fromUuid: String,
    val fromNickname: String,
    val body: String,
    val sentAt: Long,
    val direction: Direction,
    val status: Status,
) {
    enum class Direction { IN, OUT }

    enum class Status { SENDING, DELIVERED, FAILED }
}

internal fun GroupMessageEntity.toDomain(): GroupMessage =
    GroupMessage(
        id = id,
        groupId = groupId,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        body = body,
        sentAt = sentAt,
        direction = GroupMessage.Direction.valueOf(direction),
        status =
            runCatching { GroupMessage.Status.valueOf(status) }
                .getOrDefault(GroupMessage.Status.DELIVERED),
    )

internal fun GroupMessage.toEntity(): GroupMessageEntity =
    GroupMessageEntity(
        id = id,
        groupId = groupId,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        body = body,
        sentAt = sentAt,
        direction = direction.name,
        status = status.name,
    )
