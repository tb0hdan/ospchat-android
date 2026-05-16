package com.ospchat.android.ui.peers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.R
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.service.DiscoveryForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(viewModel: PeersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val ownNickname by viewModel.ownNickname.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Start the service whether or not the notification permission is
        // granted — discovery itself works either way; only the ongoing
        // notification will be silenced on API 33+ if denied.
        DiscoveryForegroundService.start(context)
    }

    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.peers_title))
                        ownNickname?.let { name ->
                            Text(
                                text = "${stringResource(R.string.peers_self)}: $name",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (peers.isEmpty()) {
            EmptyPeers(padding)
        } else {
            PeerList(
                peers = peers,
                padding = padding,
                onPeerClick = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.peers_messaging_soon),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}

@Composable
private fun EmptyPeers(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
    peers: List<Peer>,
    padding: PaddingValues,
    onPeerClick: (Peer) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.Top,
    ) {
        items(peers, key = { it.uuid }) { peer ->
            PeerRow(peer = peer, onClick = { onPeerClick(peer) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = peer.nickname,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${peer.host}:${peer.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
