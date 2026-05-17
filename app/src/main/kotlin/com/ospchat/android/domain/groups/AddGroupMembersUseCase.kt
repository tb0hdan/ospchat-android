package com.ospchat.android.domain.groups

import com.ospchat.android.data.groups.GroupRepository
import javax.inject.Inject

/**
 * Creator-only operation. Adds [memberUuids] to [groupId] (idempotent on
 * already-present members) and rebroadcasts the updated snapshot.
 */
class AddGroupMembersUseCase
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        private val groupBroadcaster: GroupBroadcaster,
    ) {
        suspend operator fun invoke(
            groupId: String,
            memberUuids: List<String>,
        ) {
            groupRepository.addMembers(groupId = groupId, memberUuids = memberUuids)
            groupBroadcaster.broadcastSnapshot(groupId)
        }
    }
