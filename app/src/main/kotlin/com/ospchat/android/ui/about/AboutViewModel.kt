package com.ospchat.android.ui.about

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.avatar.AvatarRepository
import com.ospchat.android.data.avatar.AvatarStore
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.service.DiscoveryForegroundService
import com.ospchat.android.ui.avatar.AvatarModel
import com.ospchat.android.ui.avatar.computeInitials
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val identityRepository: IdentityRepository,
        private val avatarRepository: AvatarRepository,
        private val avatarStore: AvatarStore,
    ) : ViewModel() {
        val nickname: StateFlow<String?> =
            identityRepository.nicknameFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /**
         * The local user's avatar to render, recomputed whenever nickname or
         * the persisted custom-avatar hash changes. Null while DataStore is
         * still warming up.
         */
        val selfAvatar: StateFlow<AvatarModel?> =
            combine(
                identityRepository.nicknameFlow,
                identityRepository.avatarHashFlow,
                identityRepository.uuidFlow,
            ) { nick, hash, uuid ->
                if (nick == null || uuid == null) {
                    null
                } else if (hash != null) {
                    AvatarModel.Custom(avatarStore.selfFile(hash).absolutePath)
                } else {
                    AvatarModel.Initials(letters = computeInitials(nick), seed = uuid)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun saveNickname(value: String) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                identityRepository.setNickname(trimmed)
                bounceService()
            }
        }

        fun setAvatarFrom(uri: Uri?) {
            if (uri == null) return
            viewModelScope.launch {
                // No service bounce: `AvatarRepository` fires a
                // `POST /v1/notify-refresh` to every known peer over HTTP,
                // so they pull the new avatar without us churning NSD /
                // the multicast lock.
                avatarRepository.setFromUri(uri)
            }
        }

        fun resetAvatarToInitials() {
            viewModelScope.launch {
                avatarRepository.clear()
            }
        }

        private suspend fun bounceService() {
            DiscoveryForegroundService.stop(context)
            delay(SERVICE_RESTART_DELAY_MS)
            DiscoveryForegroundService.start(context)
        }

        private companion object {
            const val SERVICE_RESTART_DELAY_MS = 300L
        }
    }
