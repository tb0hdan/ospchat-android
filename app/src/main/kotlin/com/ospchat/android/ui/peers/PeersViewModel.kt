package com.ospchat.android.ui.peers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PeersViewModel @Inject constructor(
    identityRepository: IdentityRepository,
    discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    val peers: StateFlow<List<Peer>> = discoveryRepository.peers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownNickname: StateFlow<String?> = identityRepository.nicknameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
