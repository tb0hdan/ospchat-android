package com.ospchat.android.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.service.DiscoveryForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val identityRepository: IdentityRepository,
    ) : ViewModel() {
        val nickname: StateFlow<String?> =
            identityRepository.nicknameFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /**
         * Persists the new nickname and bounces the discovery foreground
         * service so NSD re-advertises with the new name. The short delay
         * gives Android time to dispatch `onDestroy` on the old instance
         * before we start a fresh one.
         */
        fun saveNickname(value: String) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                identityRepository.setNickname(trimmed)
                DiscoveryForegroundService.stop(context)
                delay(SERVICE_RESTART_DELAY_MS)
                DiscoveryForegroundService.start(context)
            }
        }

        private companion object {
            const val SERVICE_RESTART_DELAY_MS = 300L
        }
    }
