package com.ospchat.android.ui.seed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ospchat.android.R
import com.ospchat.android.seed.SeedManifest
import com.ospchat.android.seed.SeedPackageInfo
import com.ospchat.android.seed.SeedRepository
import com.ospchat.android.seed.SeedServerState
import com.ospchat.android.seed.catalog.PackageSource
import com.ospchat.android.service.SeedForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
internal class SeedModeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SeedRepository,
        private val serverState: SeedServerState,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SeedModeUiState>(SeedModeUiState.Loading)
        val uiState: StateFlow<SeedModeUiState> = _uiState.asStateFlow()

        private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val errors: SharedFlow<String> = _errors.asSharedFlow()

        /** Most recent manifest from the repository — source of `downloadUrl` etc. for download calls. */
        @Volatile private var latestManifest: SeedManifest? = null

        /**
         * Descriptor ids currently being downloaded → 0f..1f. The `onProgress`
         * callback from `SeedRepository.downloadPackage` runs on
         * `Dispatchers.IO` (the download loop's context); the rest of the
         * ViewModel mutates this map from `viewModelScope` (Main). A
         * concurrent map keeps both producers safe.
         */
        private val downloadProgress = ConcurrentHashMap<String, Float>()

        /** descriptor ids the user has ticked. */
        private val selected = mutableSetOf<String>()

        init {
            viewModelScope.launch { refreshManifest(initial = true) }
            viewModelScope.launch { observeServerState() }
            viewModelScope.launch { pollHotspot() }
        }

        fun refresh() {
            viewModelScope.launch { refreshManifest(initial = false) }
        }

        fun toggleSelected(id: String) {
            if (selected.contains(id)) selected.remove(id) else selected.add(id)
            updateState { it.copy(selected = selected.toSet()) }
        }

        fun downloadSelected() {
            val manifest = latestManifest ?: return
            val targets =
                manifest.packages.filter { info ->
                    selected.contains(info.descriptor.id) &&
                        !info.isCached &&
                        info.downloadUrl != null
                }
            if (targets.isEmpty()) return
            updateState { it.copy(isDownloading = true) }
            viewModelScope.launch {
                try {
                    for (info in targets) {
                        downloadProgress[info.descriptor.id] = 0f
                        publishProgress()
                        val result =
                            runCatching {
                                repository.downloadPackage(info) { downloaded, total ->
                                    val pct =
                                        if (total > 0) {
                                            (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                    downloadProgress[info.descriptor.id] = pct
                                    publishProgress()
                                }
                            }
                        downloadProgress.remove(info.descriptor.id)
                        result.fold(
                            onSuccess = { markCached(info.descriptor.id) },
                            onFailure = { t ->
                                Log.w(TAG, "downloadPackage(${info.descriptor.id}) failed", t)
                                _errors.tryEmit(
                                    context.getString(
                                        R.string.seed_mode_error_download_failed,
                                        info.descriptor.displayName,
                                    ),
                                )
                                publishProgress()
                            },
                        )
                    }
                } finally {
                    refreshManifest(initial = false)
                    updateState { it.copy(isDownloading = false) }
                }
            }
        }

        fun clearCache() {
            viewModelScope.launch {
                updateState { it.copy(isRefreshing = true) }
                runCatching { repository.clearCache() }
                    .onFailure { Log.w(TAG, "clearCache failed", it) }
                refreshManifest(initial = false)
            }
        }

        fun startServer() {
            val ip = (_uiState.value as? SeedModeUiState.Ready)?.hotspotIp
            if (ip == null) {
                _errors.tryEmit(context.getString(R.string.seed_mode_error_no_hotspot))
                return
            }
            SeedForegroundService.start(context)
        }

        fun stopServer() {
            SeedForegroundService.stop(context)
        }

        private suspend fun refreshManifest(initial: Boolean) {
            if (!initial) updateState { it.copy(isRefreshing = true) }
            val manifest = repository.loadManifest()
            latestManifest = manifest
            if (manifest.releaseTag == null && !initial) {
                _errors.tryEmit(context.getString(R.string.seed_mode_error_fetch_failed))
            }
            val hotspotIp = repository.hotspotIp()
            val previousQr = (_uiState.value as? SeedModeUiState.Ready)?.qrBitmap
            val previousDownloading = (_uiState.value as? SeedModeUiState.Ready)?.isDownloading == true
            _uiState.value =
                SeedModeUiState.Ready(
                    hotspotIp = hotspotIp,
                    releaseTag = manifest.releaseTag,
                    packages = manifest.packages.map { it.toRow() },
                    selected = selected.toSet(),
                    isRefreshing = false,
                    isDownloading = previousDownloading,
                    serverUrl = serverState.serverUrl.value,
                    qrBitmap = previousQr,
                )
            val currentUrl = serverState.serverUrl.value
            if (currentUrl != null && previousQr == null) refreshQrBitmap(currentUrl)
        }

        private suspend fun observeServerState() {
            serverState.serverUrl.collect { url ->
                updateState { it.copy(serverUrl = url) }
                if (url != null) refreshQrBitmap(url) else updateState { it.copy(qrBitmap = null) }
            }
        }

        private suspend fun pollHotspot() {
            while (true) {
                delay(HOTSPOT_POLL_MS)
                val ip = repository.hotspotIp()
                updateState {
                    if (it.hotspotIp == ip) it else it.copy(hotspotIp = ip)
                }
            }
        }

        private fun refreshQrBitmap(url: String) {
            val bitmap =
                runCatching { renderQr(url) }
                    .onFailure { Log.w(TAG, "QR render failed for $url", it) }
                    .getOrNull() ?: return
            updateState { it.copy(qrBitmap = bitmap) }
        }

        private fun renderQr(text: String): Bitmap {
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints)
            val w = matrix.width
            val h = matrix.height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        }

        /**
         * Reflects a just-finished successful download immediately so the row's
         * status flips from "Not downloaded" to "Cached" without waiting for the
         * end-of-batch [refreshManifest]. Updates both [latestManifest] (used by
         * subsequent download passes) and the visible state row.
         */
        private fun markCached(id: String) {
            latestManifest?.let { current ->
                latestManifest =
                    current.copy(
                        packages =
                            current.packages.map { info ->
                                if (info.descriptor.id == id) info.copy(isCached = true) else info
                            },
                    )
            }
            updateState { ready ->
                ready.copy(
                    packages =
                        ready.packages.map { row ->
                            if (row.id == id) row.copy(isCached = true, downloadProgress = null) else row
                        },
                )
            }
        }

        private fun publishProgress() {
            updateState { ready ->
                ready.copy(
                    packages =
                        ready.packages.map { row ->
                            val pct = downloadProgress[row.id]
                            if (pct == null && row.downloadProgress == null) {
                                row
                            } else {
                                row.copy(downloadProgress = pct)
                            }
                        },
                )
            }
        }

        private fun updateState(transform: (SeedModeUiState.Ready) -> SeedModeUiState.Ready) {
            val ready = _uiState.value as? SeedModeUiState.Ready ?: return
            _uiState.value = transform(ready)
        }

        private fun SeedPackageInfo.toRow(): SeedPackageRow =
            SeedPackageRow(
                id = descriptor.id,
                displayName = descriptor.displayName,
                platformLabel = descriptor.platform.displayName,
                fileName = fileName,
                sizeBytes = sizeBytes,
                isBuiltin = descriptor.source is PackageSource.SelfApk,
                isCached = isCached,
                isDownloadable = downloadUrl != null && descriptor.source !is PackageSource.SelfApk,
                downloadProgress = downloadProgress[descriptor.id],
            )

        private companion object {
            const val TAG = "SeedModeViewModel"
            const val QR_SIZE_PX = 512
            const val HOTSPOT_POLL_MS = 2_000L
        }
    }
