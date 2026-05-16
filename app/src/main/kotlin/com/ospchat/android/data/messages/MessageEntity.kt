package com.ospchat.android.data.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["peer_uuid", "sent_at"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "peer_uuid") val peerUuid: String,
    @ColumnInfo(name = "from_uuid") val fromUuid: String,
    @ColumnInfo(name = "from_nickname") val fromNickname: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "status") val status: String,
)

internal fun MessageEntity.toDomain(): Message =
    Message(
        id = id,
        peerUuid = peerUuid,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        body = body,
        sentAt = sentAt,
        direction = Message.Direction.valueOf(direction),
        // A future app version might write a status string we don't know about
        // yet (e.g. a "SEEN" beyond READ). Fall back to DELIVERED so the row
        // still renders instead of crashing the conversation.
        status = runCatching { Message.Status.valueOf(status) }.getOrDefault(Message.Status.DELIVERED),
    )

internal fun Message.toEntity(): MessageEntity =
    MessageEntity(
        id = id,
        peerUuid = peerUuid,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        body = body,
        sentAt = sentAt,
        direction = direction.name,
        status = status.name,
    )
