package com.ospchat.android.domain.contacts

import com.ospchat.android.data.peers.PeerRepository
import javax.inject.Inject

/**
 * Demotes the peer identified by [uuid] back to a transient peer. The peer
 * row itself is preserved so message history and avatar caching survive.
 */
class RemoveFromContactsUseCase
    @Inject
    constructor(
        private val peerRepository: PeerRepository,
    ) {
        suspend operator fun invoke(uuid: String) {
            peerRepository.setIsContact(uuid = uuid, isContact = false)
        }
    }
