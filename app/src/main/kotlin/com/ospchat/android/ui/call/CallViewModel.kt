package com.ospchat.android.ui.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.shared.data.calls.Call
import com.ospchat.shared.data.calls.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [CallScreen]. Reads the currently-active call from
 * [CallRepository] and exposes the imperative call actions (mute / hangup).
 */
@HiltViewModel
class CallViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val callRepository: CallRepository,
    ) : ViewModel() {
        val callId: String =
            checkNotNull(savedStateHandle["callId"]) {
                "CallViewModel requires a 'callId' navigation argument"
            }

        val call: StateFlow<Call?> =
            callRepository.activeCall.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

        fun setMuted(muted: Boolean) {
            viewModelScope.launch { callRepository.setMuted(callId, muted) }
        }

        fun hangUp() {
            viewModelScope.launch { callRepository.hangUp(callId) }
        }
    }
