package com.ospchat.android.ui.peers

import com.ospchat.shared.data.peers.PeerRecord

/**
 * Top-level state for the Contacts tab. Mirrors the sealed pattern used by
 * `IdentityUiState`: an explicit [Loading] case avoids flashing the empty
 * placeholder before the first DB read completes.
 */
sealed interface ContactsUiState {
    data object Loading : ContactsUiState

    data class Ready(
        val contacts: List<PeerRecord>,
        val peers: List<PeerRecord>,
    ) : ContactsUiState
}
