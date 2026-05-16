package com.ospchat.android.net.dto

import kotlinx.serialization.Serializable

/** Wire schema for `POST /v1/messages`. */
@Serializable
data class IncomingMessageDto(
    val id: String,
    val fromUuid: String,
    val fromNickname: String,
    val body: String,
    val sentAt: Long,
    val attachment: AttachmentDto? = null,
)

/**
 * Metadata for an image attachment. The binary itself is fetched separately
 * by the receiver via `GET /v1/attachments/{messageId}` on the sender.
 */
@Serializable
data class AttachmentDto(
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)

/** Wire schema for `POST /v1/read-receipts`. */
@Serializable
data class ReadReceiptDto(
    val fromUuid: String,
    val upToSentAt: Long,
)

/** Wire schema for the body of any 4xx / 5xx response. */
@Serializable
data class ErrorDto(
    val error: String,
    val detail: String? = null,
)
