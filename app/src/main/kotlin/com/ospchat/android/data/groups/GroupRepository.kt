package com.ospchat.android.data.groups

import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.net.dto.GroupMemberDto
import com.ospchat.android.net.dto.GroupSnapshotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/write surface over [GroupDao]. Joins the persisted group + member
 * tables with the local user's UUID to produce UI-friendly [GroupRecord]s
 * and to validate membership changes.
 *
 * Membership changes come from two places:
 *  - **Local** — the creator calls [createGroup] / [addMembers] / [removeMembers].
 *    These bump `membership_updated_at` to the wall-clock time so receivers
 *    correctly preserve ordering across multiple back-to-back edits.
 *  - **Remote** — a peer pushes a [GroupSnapshotDto] via [applySnapshot].
 *    The receiver only adopts the new member list when the sender is the
 *    group's creator AND the snapshot version is strictly newer.
 */
@Singleton
class GroupRepository
    @Inject
    constructor(
        private val groupDao: GroupDao,
        private val groupMessageDao: GroupMessageDao,
        private val peerDao: PeerDao,
        private val identityRepository: IdentityRepository,
    ) {
        fun observeAll(): Flow<List<GroupRecord>> =
            combine(
                groupDao.observeAll(),
                groupDao.observeUnreadCounts(),
                identityRepository.uuidFlow,
            ) { groups, unread, selfUuidOrNull ->
                val selfUuid = selfUuidOrNull.orEmpty()
                val unreadMap = unread.associate { it.groupId to it.count }
                groups.map { entity ->
                    val members = groupDao.membersOf(entity.id)
                    entity.toRecord(
                        memberCount = members.size,
                        unreadCount = unreadMap[entity.id] ?: 0,
                        selfUuid = selfUuid,
                    )
                }
            }

        fun observeContacts(kind: GroupKind): Flow<List<GroupRecord>> =
            observeAll().map { all ->
                all
                    .filter { it.kind == kind }
                    .sortedByDescending { it.createdAt }
            }

        fun observeOne(groupId: String): Flow<GroupRecord?> =
            combine(
                groupDao.observeOne(groupId),
                groupDao.observeUnreadCounts(),
                identityRepository.uuidFlow,
            ) { group, unread, selfUuidOrNull ->
                val entity = group ?: return@combine null
                val selfUuid = selfUuidOrNull.orEmpty()
                val unreadCount = unread.firstOrNull { it.groupId == groupId }?.count ?: 0
                val members = groupDao.membersOf(groupId)
                entity.toRecord(
                    memberCount = members.size,
                    unreadCount = unreadCount,
                    selfUuid = selfUuid,
                )
            }

        fun observeInfo(groupId: String): Flow<GroupInfo?> =
            combine(
                observeOne(groupId),
                groupDao.observeMembers(groupId),
            ) { record, members ->
                if (record == null) return@combine null
                val creatorNickname =
                    members.firstOrNull { it.memberUuid == record.creatorUuid }?.memberNickname
                        ?: peerDao.findByUuid(record.creatorUuid)?.nickname
                        ?: "Unknown"
                val entity = groupDao.findById(groupId) ?: return@combine null
                GroupInfo(
                    record = record,
                    creatorNickname = creatorNickname,
                    membershipUpdatedAt = entity.membershipUpdatedAt,
                    members = members,
                )
            }

        /**
         * Build a snapshot for [groupId] from the local copy. Returns `null`
         * if the group does not exist locally.
         */
        suspend fun snapshotOf(groupId: String): GroupSnapshotDto? {
            val entity = groupDao.findById(groupId) ?: return null
            val members = groupDao.membersOf(groupId)
            return entity.toSnapshot(members)
        }

        /**
         * Create a new group with the local user as creator and a starting
         * member set of [memberUuids] (must already exist in `peers`).
         * Returns the new group id.
         */
        suspend fun createGroup(
            name: String,
            kind: GroupKind,
            memberUuids: List<String>,
        ): String {
            val creatorUuid = identityRepository.ensureUuid()
            val creatorNickname = identityRepository.nicknameFlow.first().orEmpty()
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val entity =
                GroupEntity(
                    id = id,
                    name = name,
                    kind = kind.name,
                    creatorUuid = creatorUuid,
                    createdAt = now,
                    membershipUpdatedAt = now,
                )
            groupDao.upsert(entity)
            // Creator is always a member.
            groupDao.insertMember(
                GroupMemberEntity(
                    groupId = id,
                    memberUuid = creatorUuid,
                    memberNickname = creatorNickname,
                    joinedAt = now,
                ),
            )
            memberUuids.distinct().filter { it != creatorUuid }.forEach { uuid ->
                val nickname = peerDao.findByUuid(uuid)?.nickname ?: "Unknown"
                groupDao.insertMember(
                    GroupMemberEntity(
                        groupId = id,
                        memberUuid = uuid,
                        memberNickname = nickname,
                        joinedAt = now,
                    ),
                )
            }
            return id
        }

        /** Add [memberUuids] to [groupId]. Bumps `membership_updated_at`. */
        suspend fun addMembers(
            groupId: String,
            memberUuids: List<String>,
        ) {
            val entity = groupDao.findById(groupId) ?: return
            val now = System.currentTimeMillis()
            val existing = groupDao.membersOf(groupId).map { it.memberUuid }.toSet()
            memberUuids.distinct().filter { it !in existing }.forEach { uuid ->
                val nickname = peerDao.findByUuid(uuid)?.nickname ?: "Unknown"
                groupDao.insertMember(
                    GroupMemberEntity(
                        groupId = groupId,
                        memberUuid = uuid,
                        memberNickname = nickname,
                        joinedAt = now,
                    ),
                )
            }
            groupDao.upsert(entity.copy(membershipUpdatedAt = now))
        }

        /** Remove [memberUuids] from [groupId]. Bumps `membership_updated_at`. */
        suspend fun removeMembers(
            groupId: String,
            memberUuids: List<String>,
        ) {
            val entity = groupDao.findById(groupId) ?: return
            memberUuids.forEach { uuid ->
                if (uuid == entity.creatorUuid) return@forEach
                groupDao.removeMember(groupId, uuid)
            }
            groupDao.upsert(entity.copy(membershipUpdatedAt = System.currentTimeMillis()))
        }

        /**
         * Apply an inbound snapshot from [fromUuid]. Membership is only
         * updated when [fromUuid] equals the group's creator AND the snapshot
         * is newer than what we have. The group row itself (name, kind) is
         * authoritative from the creator only.
         */
        suspend fun applySnapshot(
            fromUuid: String,
            snapshot: GroupSnapshotDto,
        ) {
            val existing = groupDao.findById(snapshot.id)
            if (existing == null) {
                // First sighting: only adopt if the sender is the creator
                // (otherwise we'd accept rogue groups from arbitrary peers).
                if (fromUuid != snapshot.creatorUuid) return
                groupDao.upsert(snapshot.toEntity(lastReadAt = 0L))
                groupDao.replaceMembers(snapshot.id, snapshot.toMemberEntities())
                return
            }
            if (fromUuid != existing.creatorUuid) return
            if (snapshot.membershipUpdatedAt <= existing.membershipUpdatedAt) return
            groupDao.upsert(
                existing.copy(
                    name = snapshot.name,
                    membershipUpdatedAt = snapshot.membershipUpdatedAt,
                ),
            )
            groupDao.replaceMembers(snapshot.id, snapshot.toMemberEntities())
        }

        /**
         * Self-removal: drop the local user from [groupId] and purge messages.
         * The leaver is responsible for pushing the leave event to other
         * members; this method only mutates local state.
         */
        suspend fun applyLocalLeave(groupId: String) {
            val selfUuid = identityRepository.ensureUuid()
            groupDao.removeMember(groupId, selfUuid)
            groupMessageDao.deleteByGroup(groupId)
            groupDao.deleteGroup(groupId)
        }

        /** Inbound `/v1/groups/leave` from [fromUuid]: remove them from our copy. */
        suspend fun applyRemoteLeave(
            groupId: String,
            fromUuid: String,
        ) {
            val entity = groupDao.findById(groupId) ?: return
            if (fromUuid == entity.creatorUuid) {
                // Creator-leave is not allowed in v1. Ignore to avoid
                // orphaning the group.
                return
            }
            groupDao.removeMember(groupId, fromUuid)
            groupDao.upsert(entity.copy(membershipUpdatedAt = System.currentTimeMillis()))
        }

        /** Mark all messages in [groupId] as read up to now (clears unread badge). */
        suspend fun markRead(groupId: String) {
            groupDao.updateLastReadAt(groupId, System.currentTimeMillis())
        }

        suspend fun selfUuid(): String = identityRepository.ensureUuid()

        private fun GroupEntity.toRecord(
            memberCount: Int,
            unreadCount: Int,
            selfUuid: String,
        ): GroupRecord =
            GroupRecord(
                id = id,
                name = name,
                kind = runCatching { GroupKind.valueOf(kind) }.getOrDefault(GroupKind.CHAT),
                creatorUuid = creatorUuid,
                createdAt = createdAt,
                memberCount = memberCount,
                unreadCount = unreadCount,
                isCreator = creatorUuid == selfUuid,
            )

        private fun GroupEntity.toSnapshot(members: List<GroupMemberEntity>): GroupSnapshotDto =
            GroupSnapshotDto(
                id = id,
                name = name,
                kind = kind,
                creatorUuid = creatorUuid,
                createdAt = createdAt,
                membershipUpdatedAt = membershipUpdatedAt,
                members =
                    members.map {
                        GroupMemberDto(uuid = it.memberUuid, nickname = it.memberNickname)
                    },
            )

        private fun GroupSnapshotDto.toEntity(lastReadAt: Long): GroupEntity =
            GroupEntity(
                id = id,
                name = name,
                kind = kind,
                creatorUuid = creatorUuid,
                createdAt = createdAt,
                membershipUpdatedAt = membershipUpdatedAt,
                lastReadAt = lastReadAt,
            )

        private fun GroupSnapshotDto.toMemberEntities(): List<GroupMemberEntity> {
            val now = System.currentTimeMillis()
            return members.map {
                GroupMemberEntity(
                    groupId = id,
                    memberUuid = it.uuid,
                    memberNickname = it.nickname,
                    joinedAt = now,
                )
            }
        }
    }
