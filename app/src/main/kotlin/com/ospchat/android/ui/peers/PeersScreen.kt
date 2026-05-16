package com.ospchat.android.ui.peers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun PeersScreen(
    onPeerClick: (PeerRecord) -> Unit,
    viewModel: PeersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val peers by viewModel.peers.collectAsStateWithLifecycle()

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

    if (peers.isEmpty()) {
        EmptyPeers()
    } else {
        PeerList(peers = peers, onPeerClick = onPeerClick)
    }
}

@Composable
private fun EmptyPeers() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.peers_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeerList(
    peers: List<PeerRecord>,
    onPeerClick: (PeerRecord) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        items(peers, key = { it.uuid }) { peer ->
            PeerRow(peer = peer, onClick = { onPeerClick(peer) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PeerRow(
    peer: PeerRecord,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                            "offline"
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
