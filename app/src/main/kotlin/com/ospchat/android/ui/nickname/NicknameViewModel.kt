package com.ospchat.android.ui.nickname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.identity.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NicknameViewModel
    @Inject
    constructor(
        private val identityRepository: IdentityRepository,
    ) : ViewModel() {
        fun save(nickname: String) {
            val trimmed = nickname.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                identityRepository.setNickname(trimmed)
                identityRepository.ensureUuid()
            }
        }
    }
