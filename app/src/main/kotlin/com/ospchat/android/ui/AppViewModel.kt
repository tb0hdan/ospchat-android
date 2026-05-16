package com.ospchat.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.identity.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface IdentityUiState {
    data object Loading : IdentityUiState
    data object NeedsNickname : IdentityUiState
    data class Ready(val nickname: String) : IdentityUiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    identityRepository: IdentityRepository,
) : ViewModel() {

    val uiState: StateFlow<IdentityUiState> = identityRepository.nicknameFlow
        .map { nick ->
            if (nick.isNullOrBlank()) IdentityUiState.NeedsNickname else IdentityUiState.Ready(nick)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdentityUiState.Loading)
}
