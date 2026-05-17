package com.ospchat.android.domain.contacts

import com.ospchat.android.data.peers.PeerRepository
import javax.inject.Inject

/**
 * Promotes the peer identified by [uuid] to a saved contact. Idempotent —
 * calling on an already-saved contact is a no-op `UPDATE` on a single row.
 */
class AddToContactsUseCase
    @Inject
    constructor(
        private val peerRepository: PeerRepository,
    ) {
        suspend operator fun invoke(uuid: String) {
            peerRepository.setIsContact(uuid = uuid, isContact = true)
        }
    }
