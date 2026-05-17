package com.ospchat.android.net.dto

import kotlinx.serialization.Serializable

/**
 * Wire schema for `POST /v1/reactions`. `emoji == null` means the sender is
 * *removing* their reaction from [messageId]; otherwise it's an
 * upsert that replaces whatever single reaction they had on the same
 * message.
 */
@Serializable
data class ReactionDto(
    val messageId: String,
    val fromUuid: String,
    val fromNickname: String,
    val emoji: String?,
    val reactedAt: Long,
)
