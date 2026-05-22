package com.ospchat.android.ui.seed

import android.graphics.Bitmap

sealed interface SeedModeUiState {
    data object Loading : SeedModeUiState

    data class Ready(
        val hotspotIp: String?,
        val releaseTag: String?,
        val packages: List<SeedPackageRow>,
        val selected: Set<String>,
        val isRefreshing: Boolean,
        val isDownloading: Boolean,
        val serverUrl: String?,
        val qrBitmap: Bitmap?,
    ) : SeedModeUiState
}

data class SeedPackageRow(
    val id: String,
    val displayName: String,
    val platformLabel: String,
    val fileName: String,
    val sizeBytes: Long,
    val isBuiltin: Boolean,
    val isCached: Boolean,
    val isDownloadable: Boolean,
    val downloadProgress: Float?,
)
