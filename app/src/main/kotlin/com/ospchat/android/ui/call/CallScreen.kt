package com.ospchat.android.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.android.ui.avatar.AvatarModel
import com.ospchat.android.ui.avatar.computeInitials
import com.ospchat.shared.data.calls.Call
import com.ospchat.shared.data.calls.statusLabel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    onClose: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val call by viewModel.call.collectAsStateWithLifecycle()
    val current = call

    // Wait for at least one non-null emission before allowing `null` to mean
    // "call ended". The Flow is initialized with `null`, so without this
    // guard `LaunchedEffect` would pop the screen on first composition,
    // before the Room query has had a chance to emit the live row.
    var hasLoaded by remember(viewModel.callId) { mutableStateOf(false) }
    LaunchedEffect(current?.id) {
        if (current != null && current.id == viewModel.callId) {
            hasLoaded = true
        } else if (hasLoaded) {
            // Either no active call any more (remote hangup, NO_ANSWER
            // timeout, FAILED) or a different call is active. Pop the screen.
            onClose()
        }
    }

    if (current == null || current.id != viewModel.callId) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    var muted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(
                model =
                    AvatarModel.Initials(
                        letters = computeInitials(current.peerNickname),
                        seed = current.peerUuid,
                    ),
                size = 128.dp,
            )
            Text(text = current.peerNickname, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = current.composeStatusLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                IconButton(
                    onClick = {
                        muted = !muted
                        viewModel.setMuted(muted)
                    },
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (muted) "Unmute" else "Mute",
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.hangUp()
                        onClose()
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "Hang up")
                }
            }
        }
    }
}

@Composable
private fun Call.composeStatusLabel(): String {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id, state) {
        while (state == Call.State.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return statusLabel(now)
}

/**
 * Modal incoming-call overlay rendered above whatever screen the user is
 * on, so context isn't ripped away while they decide. Accept routes to the
 * call screen; decline POSTs `/v1/call/hangup` and dismisses.
 */
@Composable
fun IncomingCallDialog(
    call: Call,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(12.dp),
        title = { Text(text = call.peerNickname) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Avatar(
                    model =
                        AvatarModel.Initials(
                            letters = computeInitials(call.peerNickname),
                            seed = call.peerUuid,
                        ),
                    size = 88.dp,
                )
                Text(
                    text = "Incoming voice call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Accept")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Decline")
                }
            }
        },
    )
}
