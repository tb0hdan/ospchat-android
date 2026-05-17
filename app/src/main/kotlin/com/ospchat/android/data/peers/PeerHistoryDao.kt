package com.ospchat.android.data.peers

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAddressIfAbsent(entity: PeerAddressEntity)

    @Query(
        "UPDATE peer_addresses SET last_seen_at = :lastSeenAt " +
            "WHERE uuid = :uuid AND host = :host AND port = :port",
    )
    suspend fun touchAddress(
        uuid: String,
        host: String,
        port: Int,
        lastSeenAt: Long,
    )

    @Query("SELECT * FROM peer_addresses WHERE uuid = :uuid ORDER BY last_seen_at DESC")
    fun observeAddresses(uuid: String): Flow<List<PeerAddressEntity>>

    /**
     * Keep only the [keep] most-recent address rows for [uuid]; delete the
     * rest. No-op when the peer has fewer than [keep] rows.
     */
    @Query(
        "DELETE FROM peer_addresses WHERE uuid = :uuid AND (uuid, host, port) NOT IN (" +
            "SELECT uuid, host, port FROM peer_addresses WHERE uuid = :uuid " +
            "ORDER BY last_seen_at DESC LIMIT :keep" +
            ")",
    )
    suspend fun pruneAddresses(
        uuid: String,
        keep: Int,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNicknameIfAbsent(entity: PeerNicknameEntity)

    @Query(
        "UPDATE peer_nicknames SET last_seen_at = :lastSeenAt " +
            "WHERE uuid = :uuid AND nickname = :nickname",
    )
    suspend fun touchNickname(
        uuid: String,
        nickname: String,
        lastSeenAt: Long,
    )

    @Query("SELECT * FROM peer_nicknames WHERE uuid = :uuid ORDER BY last_seen_at DESC")
    fun observeNicknames(uuid: String): Flow<List<PeerNicknameEntity>>

    /**
     * Keep only the [keep] most-recent nickname rows for [uuid]; delete the
     * rest. No-op when the peer has fewer than [keep] rows.
     */
    @Query(
        "DELETE FROM peer_nicknames WHERE uuid = :uuid AND (uuid, nickname) NOT IN (" +
            "SELECT uuid, nickname FROM peer_nicknames WHERE uuid = :uuid " +
            "ORDER BY last_seen_at DESC LIMIT :keep" +
            ")",
    )
    suspend fun pruneNicknames(
        uuid: String,
        keep: Int,
    )
}
