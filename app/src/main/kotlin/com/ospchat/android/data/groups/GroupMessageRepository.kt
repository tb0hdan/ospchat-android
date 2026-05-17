package com.ospchat.android.data.groups

import android.util.Log
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.GroupMessageDto
import com.ospchat.android.net.dto.GroupMessagePostDto
import com.ospchat.android.net.dto.GroupSnapshotDto
import com.ospchat.android.notifications.MessageNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mesh delivery for group messages. Posting member sends the message + a
 * fresh [GroupSnapshotDto] to every other current member who is reachable
 * (in NSD snapshot, or with a last-known host:port in the `peers` table).
 *
 * The outbound row's [GroupMessage.Status] is `DELIVERED` if at least one
 * recipient accepted the POST, `FAILED` otherwise. No retry queue in v1 —
 * catch-up sync on re-discovery fills in offline members.
 */
@Singleton
class GroupMessageRepository
    @Inject
    constructor(
        private val groupDao: GroupDao,
        private val groupMessageDao: GroupMessageDao,
        private val peerDao: PeerDao,
        private val client: MessageClient,
        private val identityRepository: IdentityRepository,
        private val discoveryRepository: DiscoveryRepository,
        private val groupRepository: GroupRepository,
        private val notifier: MessageNotifier,
    ) {
        fun messagesFor(groupId: String): Flow<List<GroupMessage>> =
            groupMessageDao
                .observeByGroup(groupId)
                .map { rows -> rows.map(GroupMessageEntity::toDomain) }

        /**
         * Local user posts [body] to [groupId]. Returns success if the
         * message reached at least one recipient; failure (with the row
         * marked `FAILED`) if every delivery attempt failed.
         */
        suspend fun send(
            groupId: String,
            body: String,
        ): Result<Unit> {
            val group =
                groupDao.findById(groupId)
                    ?: return Result.failure(IllegalArgumentException("unknown group"))
            val selfUuid = identityRepository.ensureUuid()
            val selfNickname = identityRepository.nicknameFlow.first().orEmpty()
            val kind = runCatching { GroupKind.valueOf(group.kind) }.getOrDefault(GroupKind.CHAT)
            if (kind == GroupKind.BROADCAST && selfUuid != group.creatorUuid) {
                return Result.failure(IllegalStateException("only creator can post in a broadcast channel"))
            }
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val message =
                GroupMessage(
                    id = messageId,
                    groupId = groupId,
                    fromUuid = selfUuid,
                    fromNickname = selfNickname,
                    body = body,
                    sentAt = now,
                    direction = GroupMessage.Direction.OUT,
                    status = GroupMessage.Status.SENDING,
                )
            groupMessageDao.insert(message.toEntity())
            val snapshot =
                groupRepository.snapshotOf(groupId)
                    ?: return Result.failure(IllegalStateException("missing snapshot"))
            return deliver(groupId, snapshot, message, excludeUuids = setOf(selfUuid))
        }

        /**
         * Apply an inbound group message. The snapshot is consumed by
         * [GroupRepository.applySnapshot] before persistence so the sender's
         * group is auto-created on first contact.
         */
        suspend fun receive(
            fromPeer: Peer,
            post: GroupMessagePostDto,
        ): Boolean {
            // Adopt / refresh the group state first.
            groupRepository.applySnapshot(fromUuid = fromPeer.uuid, snapshot = post.snapshot)
            val group =
                groupDao.findById(post.snapshot.id)
                    ?: return false // Snapshot rejected; nothing to write.
            // Sender must be in the current member list.
            val members = groupDao.membersOf(group.id)
            if (members.none { it.memberUuid == fromPeer.uuid }) return false
            val kind = runCatching { GroupKind.valueOf(group.kind) }.getOrDefault(GroupKind.CHAT)
            if (kind == GroupKind.BROADCAST && fromPeer.uuid != group.creatorUuid) {
                // Reject non-creator posts on broadcast channels.
                return false
            }
            val message =
                GroupMessage(
                    id = post.message.id,
                    groupId = group.id,
                    fromUuid = post.message.fromUuid,
                    fromNickname = post.message.fromNickname,
                    body = post.message.body,
                    sentAt = post.message.sentAt,
                    direction = GroupMessage.Direction.IN,
                    status = GroupMessage.Status.DELIVERED,
                )
            groupMessageDao.insert(message.toEntity())
            notifier.notifyIncomingGroup(group = group, message = message)
            return true
        }

        /**
         * Push [snapshot] + [message] to every current member of [groupId]
         * except [excludeUuids]. Returns success if at least one delivery
         * succeeded; updates the local row's status accordingly.
         */
        private suspend fun deliver(
            groupId: String,
            snapshot: GroupSnapshotDto,
            message: GroupMessage,
            excludeUuids: Set<String>,
        ): Result<Unit> {
            val dto = message.toDto()
            val post = GroupMessagePostDto(snapshot = snapshot, message = dto)
            val members = groupDao.membersOf(groupId).filter { it.memberUuid !in excludeUuids }
            var anySuccess = false
            members.forEach { member ->
                val peer = resolvePeer(member.memberUuid) ?: return@forEach
                val ok =
                    runCatching { client.postGroupMessage(peer, post) }
                        .onFailure { Log.w(TAG, "post failed to ${member.memberUuid}", it) }
                        .isSuccess
                if (ok) anySuccess = true
            }
            groupMessageDao.updateStatus(
                id = message.id,
                status =
                    if (anySuccess) {
                        GroupMessage.Status.DELIVERED.name
                    } else {
                        GroupMessage.Status.FAILED.name
                    },
            )
            return if (anySuccess) Result.success(Unit) else Result.failure(NoReachableMember)
        }

        /**
         * Resolve a member UUID to a [Peer] for HTTP delivery. Prefers the
         * live NSD address; falls back to the last-known host:port from the
         * `peers` table. Returns null when neither is available.
         */
        private suspend fun resolvePeer(memberUuid: String): Peer? {
            val live = discoveryRepository.findPeer(memberUuid)
            if (live != null) return live
            val entity = peerDao.findByUuid(memberUuid) ?: return null
            return Peer(
                uuid = entity.uuid,
                nickname = entity.nickname,
                host = entity.lastHost,
                port = entity.lastPort,
            )
        }

        private object NoReachableMember : RuntimeException("no reachable member")

        private companion object {
            const val TAG = "GroupMessageRepo"
        }
    }

internal fun GroupMessage.toDto(): GroupMessageDto =
    GroupMessageDto(
        id = id,
        fromUuid = fromUuid,
        fromNickname = fromNickname,
        body = body,
        sentAt = sentAt,
    )
