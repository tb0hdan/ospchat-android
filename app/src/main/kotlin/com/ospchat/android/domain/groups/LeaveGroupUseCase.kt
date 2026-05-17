package com.ospchat.android.domain.groups

import com.ospchat.android.data.groups.GroupDao
import com.ospchat.android.data.groups.GroupRepository
import javax.inject.Inject

/**
 * Self-removal. Pushes a `POST /v1/groups/leave` to every current member
 * BEFORE wiping the local copy so we still have a member list to enumerate
 * over. Creator-leave is not supported in v1 (the UI hides the option).
 */
class LeaveGroupUseCase
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        private val groupDao: GroupDao,
        private val groupBroadcaster: GroupBroadcaster,
    ) {
        suspend operator fun invoke(groupId: String) {
            val group = groupDao.findById(groupId) ?: return
            val selfUuid = groupRepository.selfUuid()
            if (group.creatorUuid == selfUuid) return // not allowed in v1
            groupBroadcaster.broadcastLeave(groupId = groupId)
            groupRepository.applyLocalLeave(groupId)
        }
    }
