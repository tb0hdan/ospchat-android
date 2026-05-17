package com.ospchat.android.data.groups

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY created_at DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observeOne(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun findById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupEntity)

    @Query("UPDATE groups SET last_read_at = :readAt WHERE id = :id")
    suspend fun updateLastReadAt(
        id: String,
        readAt: Long,
    )

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY joined_at ASC")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun membersOf(groupId: String): List<GroupMemberEntity>

    @Query("SELECT DISTINCT group_id FROM group_members WHERE member_uuid = :peerUuid")
    suspend fun groupsContaining(peerUuid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE group_id = :groupId AND member_uuid = :memberUuid")
    suspend fun removeMember(
        groupId: String,
        memberUuid: String,
    )

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun clearMembers(groupId: String)

    /**
     * Replace the entire member set for [groupId] atomically. Used when the
     * creator pushes a new membership snapshot.
     */
    @Transaction
    suspend fun replaceMembers(
        groupId: String,
        members: List<GroupMemberEntity>,
    ) {
        clearMembers(groupId)
        members.forEach { insertMember(it) }
    }

    /** Per-group count of inbound messages newer than the group's `last_read_at`. */
    @Query(
        """
        SELECT m.group_id AS groupId, COUNT(*) AS count
        FROM group_messages m
        INNER JOIN groups g ON g.id = m.group_id
        WHERE m.direction = 'IN' AND m.sent_at > g.last_read_at
        GROUP BY m.group_id
        """,
    )
    fun observeUnreadCounts(): Flow<List<GroupUnreadCount>>
}

data class GroupUnreadCount(
    val groupId: String,
    val count: Int,
)
