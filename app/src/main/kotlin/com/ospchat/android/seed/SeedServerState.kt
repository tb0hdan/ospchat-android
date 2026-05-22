package com.ospchat.android.seed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared lifecycle state for the Seed Mode HTTP server. The
 * [com.ospchat.android.service.SeedForegroundService] writes; the
 * [com.ospchat.android.ui.seed.SeedModeViewModel] reads. Lets the screen
 * stay correctly hydrated when the user navigates back into Seed Mode while
 * the server is already running in the background.
 *
 * Mirrors how [com.ospchat.shared.data.discovery.DiscoveryRepository] is
 * shared between [com.ospchat.android.service.DiscoveryForegroundService]
 * and the peer-list view model.
 */
@Singleton
internal class SeedServerState
    @Inject
    constructor() {
        private val _serverUrl = MutableStateFlow<String?>(null)

        /** The URL the server is currently bound to, or null if stopped. */
        val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

        fun markRunning(url: String) {
            _serverUrl.value = url
        }

        fun markStopped() {
            _serverUrl.value = null
        }
    }
