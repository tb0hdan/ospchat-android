package com.ospchat.android.ui.about

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.MainActivity
import com.ospchat.android.R
import com.ospchat.android.service.DiscoveryForegroundService
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.android.ui.avatar.AvatarModel

@Composable
fun AboutScreen(
    onSeedModeClick: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentNickname by viewModel.nickname.collectAsStateWithLifecycle()
    val selfAvatar by viewModel.selfAvatar.collectAsStateWithLifecycle()
    val relayEnabled by viewModel.relayEnabled.collectAsStateWithLifecycle()
    var editedNickname by remember(currentNickname) { mutableStateOf(currentNickname.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    var showExitConfirm by remember { mutableStateOf(false) }

    val avatarPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> viewModel.setAvatarFrom(uri) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.about_version_label, stringResource(R.string.app_version_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, ABOUT_WEBSITE.toUri()))
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(ABOUT_WEBSITE)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(R.string.about_settings_header),
            style = MaterialTheme.typography.titleLarge,
        )

        // Avatar block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(end = 16.dp)) {
                selfAvatar?.let { Avatar(model = it, size = 96.dp) }
            }
            Column {
                Text(
                    text = stringResource(R.string.about_avatar_header),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    TextButton(
                        onClick = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.about_avatar_change))
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    val canReset = selfAvatar is AvatarModel.Custom
                    TextButton(
                        onClick = { viewModel.resetAvatarToInitials() },
                        enabled = canReset,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.about_avatar_reset))
                    }
                }
            }
        }

        OutlinedTextField(
            value = editedNickname,
            onValueChange = { editedNickname = it.take(MAX_NICKNAME_LEN) },
            label = { Text(stringResource(R.string.nickname_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val canSave =
            editedNickname.trim().isNotEmpty() &&
                editedNickname.trim() != currentNickname?.trim()
        Button(
            onClick = {
                viewModel.saveNickname(editedNickname)
                keyboard?.hide()
            },
            enabled = canSave,
        ) {
            Text(stringResource(R.string.about_nickname_save))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Phase 4 multi-network bridging — relay opt-in. Enable when this
        // phone sits on a network that other peers can reach but their
        // intended targets cannot. Most users leave this off; the typical
        // bridge is a desktop on multiple LANs.
        Text(
            text = "Relay for contacts (messages + voice)",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                "Forward signed messages and serve as a TURN relay for voice calls " +
                    "between peers that can't reach each other directly. Messages and " +
                    "voice both stay end-to-end encrypted: the relay sees encrypted " +
                    "envelopes / SRTP datagrams only. Takes effect on next restart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = relayEnabled, onCheckedChange = { viewModel.setRelayEnabled(it) })
            Text(
                text = if (relayEnabled) "Relay enabled" else "Relay disabled",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Button(onClick = onSeedModeClick) {
            Text(stringResource(R.string.about_seed_mode))
        }

        Button(
            onClick = { showExitConfirm = true },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            Text(stringResource(R.string.about_exit))
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.about_exit_confirm_title)) },
            text = { Text(stringResource(R.string.about_exit_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    exitOspChat(context)
                }) {
                    Text(stringResource(R.string.about_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.about_exit_cancel))
                }
            },
        )
    }
}

/**
 * Stop the discovery foreground service and remove this task from the
 * recents list. Mirrors the path taken by the notification's Exit action,
 * so the two entry points share teardown semantics.
 */
private fun exitOspChat(context: android.content.Context) {
    DiscoveryForegroundService.stop(context)
    val activity = context as? ComponentActivity
    if (activity != null) {
        activity.finishAndRemoveTask()
    } else {
        // No Activity in scope — route through MainActivity so the
        // activity-side handler performs the teardown.
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXIT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
    }
}

private const val ABOUT_WEBSITE = "https://ospchat.com"
private const val MAX_NICKNAME_LEN = 32
