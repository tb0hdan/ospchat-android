package com.ospchat.android.data.messages

data class Message(
    val id: String,
    val peerUuid: String,
    val fromUuid: String,
    val fromNickname: String,
    val body: String,
    val sentAt: Long,
    val direction: Direction,
    val status: Status,
    val attachment: Attachment? = null,
) {
    enum class Direction { IN, OUT }

    enum class Status { SENDING, DELIVERED, READ, FAILED }
}

/**
 * Image attachment metadata. [localPath] is null while the bytes are still
 * being fetched from the remote peer; once non-null, the file is available
 * under [com.ospchat.android.data.attachments.AttachmentStore].
 */
data class Attachment(
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val localPath: String?,
)
