package com.ospchat.android.ui.peers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.R
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.service.DiscoveryForegroundService
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.android.ui.avatar.AvatarModel
import com.ospchat.android.ui.avatar.computeInitials

@Composable
fun PeersScreen(
    onPeerClick: (PeerRecord) -> Unit,
    viewModel: PeersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val peerInfo by viewModel.peerInfo.collectAsStateWithLifecycle()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            // Start the service whether or not the notification permission is
            // granted — discovery itself works either way; only the ongoing
            // notification will be silenced on API 33+ if denied.
            DiscoveryForegroundService.start(context)
        }

    LaunchedEffect(Unit) {
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DiscoveryForegroundService.start(context)
        }
    }

    when (val state = uiState) {
        ContactsUiState.Loading -> {
            Unit
        }

        is ContactsUiState.Ready -> {
            ContactsTabContent(
                state = state,
                onPeerClick = onPeerClick,
                onAddContact = viewModel::onAddContact,
                onRemoveContact = viewModel::onRemoveContact,
                onShowInfo = viewModel::showInfo,
            )
        }
    }

    peerInfo?.let { info ->
        PeerInfoDialog(info = info, onDismiss = viewModel::dismissInfo)
    }
}

@Composable
private fun ContactsTabContent(
    state: ContactsUiState.Ready,
    onPeerClick: (PeerRecord) -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onShowInfo: (String) -> Unit,
) {
    var contactsExpanded by rememberSaveable { mutableStateOf(true) }
    var peersExpanded by rememberSaveable { mutableStateOf(true) }

    val contactsTitle = stringResource(R.string.peers_section_contacts)
    val contactsEmpty = stringResource(R.string.peers_section_contacts_empty)
    val peersTitle = stringResource(R.string.peers_section_peers)
    val peersEmpty = stringResource(R.string.peers_section_peers_empty)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        peerSection(
            peers = state.contacts,
            keyPrefix = "c",
            expanded = contactsExpanded,
            onToggle = { contactsExpanded = !contactsExpanded },
            title = contactsTitle,
            emptyHint = contactsEmpty,
            onPeerClick = onPeerClick,
            onAddContact = onAddContact,
            onRemoveContact = onRemoveContact,
            onShowInfo = onShowInfo,
        )
        peerSection(
            peers = state.peers,
            keyPrefix = "p",
            expanded = peersExpanded,
            onToggle = { peersExpanded = !peersExpanded },
            title = peersTitle,
            emptyHint = peersEmpty,
            onPeerClick = onPeerClick,
            onAddContact = onAddContact,
            onRemoveContact = onRemoveContact,
            onShowInfo = onShowInfo,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PeerRow(
    peer: PeerRecord,
    onClick: () -> Unit,
    onAddContact: () -> Unit,
    onRemoveContact: () -> Unit,
    onShowInfo: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(model = peer.toAvatarModel(), size = 44.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.nickname,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        if (peer.isOnline) {
                            "${peer.host}:${peer.port}"
                        } else {
                            stringResource(R.string.peers_offline)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peer.unreadCount > 0) {
                UnreadIndicator(count = peer.unreadCount)
                Spacer(modifier = Modifier.size(12.dp))
            }
            StatusDot(isOnline = peer.isOnline)
        }
        PeerActionMenu(
            expanded = menuExpanded,
            isContact = peer.isContact,
            onAddContact = onAddContact,
            onRemoveContact = onRemoveContact,
            onShowInfo = onShowInfo,
            onDismiss = { menuExpanded = false },
        )
    }
}

@Composable
private fun UnreadIndicator(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Email,
            contentDescription = stringResource(R.string.peers_unread_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Badge { Text(count.toString()) }
    }
}

internal fun PeerRecord.toAvatarModel(): AvatarModel =
    if (avatarLocalPath != null) {
        AvatarModel.Custom(avatarLocalPath)
    } else {
        AvatarModel.Initials(letters = computeInitials(nickname), seed = uuid)
    }

@Composable
private fun StatusDot(isOnline: Boolean) {
    val color =
        if (isOnline) {
            Color(0xFF22C55E) // green-500
        } else {
            MaterialTheme.colorScheme.outline
        }
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
    )
}
