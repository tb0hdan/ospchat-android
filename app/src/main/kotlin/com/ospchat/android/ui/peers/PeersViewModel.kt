package com.ospchat.android.ui.peers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.data.peers.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PeersViewModel
    @Inject
    constructor(
        peerRepository: PeerRepository,
    ) : ViewModel() {
        val peers: StateFlow<List<PeerRecord>> =
            peerRepository
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
