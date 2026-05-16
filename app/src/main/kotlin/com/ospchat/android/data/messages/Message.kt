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
) {
    enum class Direction { IN, OUT }

    /**
     * Outbound message lifecycle: [SENDING] → [DELIVERED] → [READ], or
     * [SENDING] → [FAILED] (terminal until the user retries).
     *
     * Inbound messages are always recorded as [DELIVERED] — they only exist
     * locally if we received and persisted them.
     */
    enum class Status { SENDING, DELIVERED, READ, FAILED }
}
