package com.ospchat.android.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ospchat.shared.data.calls.Call
import com.ospchat.shared.data.calls.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level overlay that renders the [IncomingCallDialog] whenever an
 * incoming call is in `RINGING`. Placed above the NavHost so it can appear
 * over any current destination — the user doesn't get ripped away from
 * their current chat while deciding.
 *
 * Accept routes via [onAccept] (caller wires this to the call-screen
 * navigation) and concurrently transitions the call to `CONNECTING` via
 * the repository. Decline just POSTs hangup.
 */
@Composable
fun IncomingCallOverlay(
    onAccept: (callId: String) -> Unit,
    viewModel: IncomingCallViewModel = hiltViewModel(),
) {
    val ringing by viewModel.ringing.collectAsStateWithLifecycle()
    ringing?.let { call ->
        IncomingCallDialog(
            call = call,
            onAccept = {
                viewModel.accept(call.id)
                onAccept(call.id)
            },
            onDecline = { viewModel.decline(call.id) },
        )
    }
}

@HiltViewModel
class IncomingCallViewModel
    @Inject
    constructor(
        private val callRepository: CallRepository,
    ) : ViewModel() {
        val ringing: StateFlow<Call?> =
            callRepository.activeCall
                .map { call ->
                    call?.takeIf {
                        it.direction == Call.Direction.INCOMING && it.state == Call.State.RINGING
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun accept(callId: String) {
            viewModelScope.launch { callRepository.acceptCall(callId) }
        }

        fun decline(callId: String) {
            viewModelScope.launch { callRepository.hangUp(callId) }
        }
    }
