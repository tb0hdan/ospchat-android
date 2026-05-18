package com.ospchat.android.ui.peers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.shared.data.peers.PeerInfo
import com.ospchat.shared.data.peers.PeerRepository
import com.ospchat.shared.domain.contacts.AddToContactsUseCase
import com.ospchat.shared.domain.contacts.RemoveFromContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeersViewModel
    @Inject
    constructor(
        private val peerRepository: PeerRepository,
        private val addToContacts: AddToContactsUseCase,
        private val removeFromContacts: RemoveFromContactsUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<ContactsUiState> =
            combine(
                peerRepository.observeContacts(),
                peerRepository.observeVisiblePeers(),
            ) { contacts, peers ->
                ContactsUiState.Ready(contacts = contacts, peers = peers) as ContactsUiState
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState.Loading)

        private val infoTargetUuid = MutableStateFlow<String?>(null)

        val peerInfo: StateFlow<PeerInfo?> =
            infoTargetUuid
                .flatMapLatest { uuid ->
                    if (uuid == null) flowOf(null) else peerRepository.observeInfo(uuid)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun onAddContact(uuid: String) {
            viewModelScope.launch { addToContacts(uuid) }
        }

        fun onRemoveContact(uuid: String) {
            viewModelScope.launch { removeFromContacts(uuid) }
        }

        fun showInfo(uuid: String) {
            infoTargetUuid.value = uuid
        }

        fun dismissInfo() {
            infoTargetUuid.value = null
        }
    }
