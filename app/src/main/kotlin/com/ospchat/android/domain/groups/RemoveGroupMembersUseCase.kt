package com.ospchat.android.domain.groups

import com.ospchat.android.data.groups.GroupRepository
import javax.inject.Inject

/**
 * Creator-only operation. Removes [memberUuids] from [groupId] and pushes
 * the updated snapshot to the (now-reduced) member set so they learn about
 * the change. The removed members themselves do not receive the push;
 * they discover their removal on next catch-up sync.
 */
class RemoveGroupMembersUseCase
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        private val groupBroadcaster: GroupBroadcaster,
    ) {
        suspend operator fun invoke(
            groupId: String,
            memberUuids: List<String>,
        ) {
            groupRepository.removeMembers(groupId = groupId, memberUuids = memberUuids)
            groupBroadcaster.broadcastSnapshot(groupId)
        }
    }
