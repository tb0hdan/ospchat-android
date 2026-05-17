package com.ospchat.android.domain.groups

import android.util.Log
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.groups.GroupDao
import com.ospchat.android.data.groups.GroupRepository
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.GroupLeaveDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes group-state events (snapshot, leave) to every reachable member.
 * Snapshot pushes are sent as zero-body `POST /v1/groups/membership` so the
 * receiver knows to refresh, except for v1 we piggy-back: every group
 * message push already carries the snapshot, so a dedicated membership
 * endpoint isn't required.
 *
 * In practice this class is invoked from the creator side after every
 * mutation so reachable members converge immediately; offline members will
 * pick the snapshot up on next message or catch-up sync.
 *
 * Snapshots are sent as a no-op group message via the messages endpoint?
 * No — that would persist a phantom row. Instead we use the syncer route
 * by initiating a sync request the other way around: we just `POST` the
 * leave/membership update so peers know to pull. But the cleanest approach
 * in v1 is to ship snapshots ONLY embedded in messages, and to ship the
 * leave event as a dedicated `POST /v1/groups/leave`. So `broadcastSnapshot`
 * here is best-effort: it pushes the leave/membership-bump as a sync
 * trigger via `POST /v1/groups/membership`, falling back gracefully when a
 * member is unreachable.
 */
@Singleton
class GroupBroadcaster
    @Inject
    constructor(
        private val groupDao: GroupDao,
        private val groupRepository: GroupRepository,
        private val peerDao: PeerDao,
        private val discoveryRepository: DiscoveryRepository,
        private val client: MessageClient,
    ) {
        suspend fun broadcastSnapshot(groupId: String) {
            val snapshot = groupRepository.snapshotOf(groupId) ?: return
            val selfUuid = groupRepository.selfUuid()
            // Push to every member (including newly-added) AND any uuid that
            // was just removed (so they can prune their local copy). We
            // approximate this by enumerating both the current member list
            // and any peer that has the group locally — but the latter
            // would require a remote query, so v1 only pushes to currently
            // listed members. Removed members converge on catch-up.
            val targets = groupDao.membersOf(groupId)
            targets.forEach { member ->
                if (member.memberUuid == selfUuid) return@forEach
                val peer = resolvePeer(member.memberUuid) ?: return@forEach
                runCatching { client.postGroupMembership(peer, snapshot) }
                    .onFailure { Log.w(TAG, "membership push to ${member.memberUuid} failed", it) }
            }
        }

        suspend fun broadcastLeave(groupId: String) {
            val selfUuid = groupRepository.selfUuid()
            val targets = groupDao.membersOf(groupId)
            val dto = GroupLeaveDto(groupId = groupId, fromUuid = selfUuid)
            targets.forEach { member ->
                if (member.memberUuid == selfUuid) return@forEach
                val peer = resolvePeer(member.memberUuid) ?: return@forEach
                runCatching { client.postGroupLeave(peer, dto) }
                    .onFailure { Log.w(TAG, "leave push to ${member.memberUuid} failed", it) }
            }
        }

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

        private companion object {
            const val TAG = "GroupBroadcaster"
        }
    }
