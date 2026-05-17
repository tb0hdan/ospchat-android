package com.ospchat.android.data.reactions

/**
 * A reaction placed on a message. At most one reaction per user per message;
 * a new reaction from the same user replaces the previous one.
 */
data class Reaction(
    val messageId: String,
    val fromUuid: String,
    val fromNickname: String,
    val emoji: String,
    val reactedAt: Long,
)
