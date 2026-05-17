package com.ospchat.android.data.peers

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY last_seen_at DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PeerEntity)

    @Query("UPDATE peers SET last_read_at = :lastReadAt WHERE uuid = :uuid")
    suspend fun updateLastReadAt(
        uuid: String,
        lastReadAt: Long,
    )

    @Query(
        "UPDATE peers SET avatar_hash = :hash, avatar_local_path = :localPath WHERE uuid = :uuid",
    )
    suspend fun updateAvatar(
        uuid: String,
        hash: String?,
        localPath: String?,
    )

    @Query("UPDATE peers SET is_contact = :isContact WHERE uuid = :uuid")
    suspend fun setIsContact(
        uuid: String,
        isContact: Boolean,
    )
}
