package com.ospchat.android.domain.groups

import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupRepository
import javax.inject.Inject

/**
 * Creates a new group with the local user as creator and propagates the
 * initial snapshot to every starting member via [GroupBroadcaster].
 *
 * Returns the new group id so the caller can navigate straight into it.
 */
class CreateGroupUseCase
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        private val groupBroadcaster: GroupBroadcaster,
    ) {
        suspend operator fun invoke(
            name: String,
            kind: GroupKind,
            memberUuids: List<String>,
        ): String {
            val id = groupRepository.createGroup(name = name, kind = kind, memberUuids = memberUuids)
            groupBroadcaster.broadcastSnapshot(id)
            return id
        }
    }
