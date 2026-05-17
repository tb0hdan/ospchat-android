package com.ospchat.android.data.reactions

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "reactions",
    primaryKeys = ["message_id", "from_uuid"],
)
data class ReactionEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "from_uuid") val fromUuid: String,
    @ColumnInfo(name = "from_nickname") val fromNickname: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "reacted_at") val reactedAt: Long,
)

internal fun ReactionEntity.toDomain(): Reaction =
    Reaction(
        messageId = messageId,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        emoji = emoji,
        reactedAt = reactedAt,
    )
