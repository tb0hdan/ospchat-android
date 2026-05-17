package com.ospchat.android.data.groups

import android.util.Log
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.GroupSyncCursorDto
import com.ospchat.android.net.dto.GroupSyncPayloadDto
import com.ospchat.android.net.dto.GroupSyncRequestDto
import com.ospchat.android.net.dto.GroupSyncResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls group history from a peer we share at least one group with.
 *
 * Triggered from [DiscoveryForegroundService.peerSyncJob][com.ospchat.android.service.DiscoveryForegroundService]
 * on every NSD "absent → present" transition (same hook as
 * [PeerAvatarSync][com.ospchat.android.data.peers.PeerAvatarSync]).
 *
 * Bidirectional: we ask the peer for messages newer than what we have, and
 * the peer's server-side `/v1/groups/sync` returns its current snapshot plus
 * any missing messages. The peer's own catch-up runs symmetrically when we
 * appear in their NSD snapshot.
 */
@Singleton
class GroupSyncer
    @Inject
    constructor(
        private val groupDao: GroupDao,
        private val groupMessageDao: GroupMessageDao,
        private val groupRepository: GroupRepository,
        private val client: MessageClient,
        private val identityRepository: IdentityRepository,
    ) {
        suspend fun sync(peer: Peer) {
            val sharedGroupIds = groupDao.groupsContaining(peer.uuid)
            if (sharedGroupIds.isEmpty()) return

            // Self is always a member of any group we care about — filter
            // any group we are no longer in (e.g. removed by creator).
            val selfUuid = identityRepository.ensureUuid()
            val cursors =
                sharedGroupIds.mapNotNull { id ->
                    val members = groupDao.membersOf(id)
                    if (members.none { it.memberUuid == selfUuid }) return@mapNotNull null
                    val latest = groupMessageDao.latestSentAt(id) ?: 0L
                    GroupSyncCursorDto(groupId = id, latestSentAt = latest)
                }
            if (cursors.isEmpty()) return
            val request = GroupSyncRequestDto(fromUuid = selfUuid, cursors = cursors)
            val response: GroupSyncResponseDto =
                runCatching { client.syncGroups(peer, request) }
                    .getOrElse {
                        Log.w(TAG, "sync request to ${peer.uuid} failed", it)
                        return
                    }
            response.payloads.forEach { payload -> applyPayload(peer.uuid, payload) }
        }

        private suspend fun applyPayload(
            fromUuid: String,
            payload: GroupSyncPayloadDto,
        ) {
            groupRepository.applySnapshot(fromUuid = fromUuid, snapshot = payload.snapshot)
            val group = groupDao.findById(payload.snapshot.id) ?: return
            val kind = runCatching { GroupKind.valueOf(group.kind) }.getOrDefault(GroupKind.CHAT)
            val members = groupDao.membersOf(group.id).map { it.memberUuid }.toSet()
            payload.messages.forEach { msg ->
                if (msg.fromUuid !in members) return@forEach
                if (kind == GroupKind.BROADCAST && msg.fromUuid != group.creatorUuid) return@forEach
                // No notification for catch-up messages — these aren't fresh.
                groupMessageDao.insert(
                    GroupMessage(
                        id = msg.id,
                        groupId = group.id,
                        fromUuid = msg.fromUuid,
                        fromNickname = msg.fromNickname,
                        body = msg.body,
                        sentAt = msg.sentAt,
                        direction = GroupMessage.Direction.IN,
                        status = GroupMessage.Status.DELIVERED,
                    ).toEntity(),
                )
            }
        }

        /** Server-side responder: build the response for an inbound sync request. */
        suspend fun buildResponse(request: GroupSyncRequestDto): GroupSyncResponseDto {
            val selfUuid = identityRepository.ensureUuid()
            val payloads =
                request.cursors.mapNotNull { cursor ->
                    val group = groupDao.findById(cursor.groupId) ?: return@mapNotNull null
                    val members = groupDao.membersOf(group.id)
                    if (members.none { it.memberUuid == request.fromUuid }) return@mapNotNull null
                    if (members.none { it.memberUuid == selfUuid }) return@mapNotNull null
                    val snapshot = groupRepository.snapshotOf(group.id) ?: return@mapNotNull null
                    val missing =
                        groupMessageDao
                            .messagesAfter(group.id, cursor.latestSentAt)
                            .map {
                                com.ospchat.android.net.dto.GroupMessageDto(
                                    id = it.id,
                                    fromUuid = it.fromUuid,
                                    fromNickname = it.fromNickname,
                                    body = it.body,
                                    sentAt = it.sentAt,
                                )
                            }
                    GroupSyncPayloadDto(snapshot = snapshot, messages = missing)
                }
            return GroupSyncResponseDto(payloads = payloads)
        }

        private companion object {
            const val TAG = "GroupSyncer"
        }
    }
