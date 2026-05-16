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
    @ColumnInfo(name = "attachment_mime") val attachmentMime: String? = null,
    @ColumnInfo(name = "attachment_size_bytes") val attachmentSizeBytes: Long? = null,
    @ColumnInfo(name = "attachment_width") val attachmentWidth: Int? = null,
    @ColumnInfo(name = "attachment_height") val attachmentHeight: Int? = null,
    @ColumnInfo(name = "attachment_local_path") val attachmentLocalPath: String? = null,
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
        status = runCatching { Message.Status.valueOf(status) }.getOrDefault(Message.Status.DELIVERED),
        attachment =
            attachmentMime?.let { mime ->
                Attachment(
                    mimeType = mime,
                    sizeBytes = attachmentSizeBytes ?: 0L,
                    width = attachmentWidth ?: 0,
                    height = attachmentHeight ?: 0,
                    localPath = attachmentLocalPath,
                )
            },
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
        attachmentMime = attachment?.mimeType,
        attachmentSizeBytes = attachment?.sizeBytes,
        attachmentWidth = attachment?.width,
        attachmentHeight = attachment?.height,
        attachmentLocalPath = attachment?.localPath,
    )
