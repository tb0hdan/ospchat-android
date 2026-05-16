package com.ospchat.android.data.messages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peer_uuid = :peerUuid ORDER BY sent_at ASC")
    fun observeByPeer(peerUuid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun findById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
    )

    /**
     * Marks our outbound messages to [peerUuid] as READ for everything sent
     * at or before [upToSentAt]. Only DELIVERED messages are upgraded — we
     * don't touch SENDING (still in flight) or FAILED rows.
     */
    @Query(
        """
        UPDATE messages
        SET status = 'READ'
        WHERE peer_uuid = :peerUuid
          AND direction = 'OUT'
          AND sent_at <= :upToSentAt
          AND status = 'DELIVERED'
        """,
    )
    suspend fun markOutboundRead(
        peerUuid: String,
        upToSentAt: Long,
    )

    /**
     * Per-peer count of inbound messages newer than that peer's
     * `last_read_at`. Peers with zero unread are absent from the result.
     */
    @Query(
        """
        SELECT m.peer_uuid AS peerUuid, COUNT(*) AS count
        FROM messages m
        INNER JOIN peers p ON p.uuid = m.peer_uuid
        WHERE m.direction = 'IN' AND m.sent_at > p.last_read_at
        GROUP BY m.peer_uuid
        """,
    )
    fun observeUnreadCounts(): Flow<List<UnreadCount>>
}

data class UnreadCount(
    val peerUuid: String,
    val count: Int,
)
