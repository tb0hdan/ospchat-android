package com.ospchat.android.ui.seed

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeedModeScreen(
    onBack: () -> Unit,
    viewModel: SeedModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.seed_mode_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (val state = uiState) {
                SeedModeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is SeedModeUiState.Ready -> {
                    SeedModeContent(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun SeedModeContent(
    state: SeedModeUiState.Ready,
    viewModel: SeedModeViewModel,
) {
    val context = LocalContext.current
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.seed_mode_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HotspotCard(
            hotspotIp = state.hotspotIp,
            serverUrl = state.serverUrl,
            qrBitmap = state.qrBitmap,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.seed_mode_packages_header),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { viewModel.refresh() }, enabled = !state.isRefreshing) {
                Text(stringResource(R.string.seed_mode_refresh))
            }
        }
        state.releaseTag?.let { tag ->
            Text(
                text = stringResource(R.string.seed_mode_release_label, tag),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.packages.forEach { row ->
            PackageRow(
                row = row,
                isSelected = state.selected.contains(row.id),
                onToggle = { viewModel.toggleSelected(row.id) },
            )
        }

        Button(
            onClick = { viewModel.downloadSelected() },
            modifier = Modifier.fillMaxWidth(),
            enabled =
                !state.isDownloading &&
                    state.packages.any { row ->
                        state.selected.contains(row.id) && row.isDownloadable && !row.isCached
                    },
        ) {
            Text(stringResource(R.string.seed_mode_download_selected))
        }

        OutlinedButton(
            onClick = { showClearCacheConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            enabled =
                !state.isDownloading &&
                    state.serverUrl == null &&
                    state.packages.any { row -> !row.isBuiltin && row.isCached },
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Text(stringResource(R.string.seed_mode_clear_cache))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (state.serverUrl == null) {
            Button(
                onClick = { viewModel.startServer() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.hotspotIp != null,
            ) {
                Text(stringResource(R.string.seed_mode_start_server))
            }
        } else {
            OutlinedButton(
                onClick = { viewModel.stopServer() },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringResource(R.string.seed_mode_stop_server))
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.seed_mode_clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.seed_mode_clear_cache_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        viewModel.clearCache()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.seed_mode_clear_cache_confirm_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.seed_mode_clear_cache_cancel))
                }
            },
        )
    }
}

@Composable
private fun HotspotCard(
    hotspotIp: String?,
    serverUrl: String?,
    qrBitmap: android.graphics.Bitmap?,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val statusText = serverUrl ?: hotspotIp
            if (statusText != null) {
                Text(
                    text = stringResource(R.string.seed_mode_hotspot_active, statusText),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Text(
                    text = stringResource(R.string.seed_mode_hotspot_inactive),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.seed_mode_open_settings))
                }
            }
            QrSlot(
                serverUrl = serverUrl,
                qrBitmap = qrBitmap,
            )
        }
    }
}

@Composable
private fun QrSlot(
    serverUrl: String?,
    qrBitmap: android.graphics.Bitmap?,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(QR_DISPLAY_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when {
                qrBitmap != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.seed_mode_qr_content_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                serverUrl != null -> {
                    CircularProgressIndicator()
                }

                else -> {
                    Text(
                        text = stringResource(R.string.seed_mode_qr_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageRow(
    row: SeedPackageRow,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = !row.isBuiltin && row.isDownloadable && row.downloadProgress == null,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = packageStatusLabel(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val progress = row.downloadProgress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun packageStatusLabel(row: SeedPackageRow): String {
    val pct = row.downloadProgress
    if (pct != null) {
        return stringResource(R.string.seed_mode_downloading, (pct * 100).toInt())
    }
    val sizeLabel =
        if (row.sizeBytes > 0) {
            stringResource(R.string.seed_mode_size, "%.1f".format(row.sizeBytes / 1_048_576.0))
        } else {
            ""
        }
    val statusLabel =
        when {
            row.isBuiltin -> stringResource(R.string.seed_mode_builtin)
            row.isCached -> stringResource(R.string.seed_mode_cached)
            else -> stringResource(R.string.seed_mode_not_downloaded)
        }
    return if (sizeLabel.isBlank()) {
        statusLabel
    } else {
        stringResource(R.string.seed_mode_status_separator, statusLabel, sizeLabel)
    }
}

private val QR_DISPLAY_SIZE = 240.dp
