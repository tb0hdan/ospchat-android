package com.ospchat.android.service

import android.content.Context
import com.ospchat.shared.data.calls.Call
import com.ospchat.shared.data.calls.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glues call lifecycle to the [CallForegroundService]: starts the service
 * when a call enters CONNECTING / CONNECTED (mic actually in use) and stops
 * it when the active call disappears.
 *
 * RINGING incoming calls intentionally don't start the service — the mic
 * isn't recording yet, only the [com.ospchat.android.notifications.CallNotifier]
 * is ringing.
 *
 * Singleton, started once from `OSPChatApp.onCreate()`.
 */
@Singleton
class CallServiceController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val callRepository: CallRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start() {
            scope.launch {
                callRepository.activeCall.collectLatest { call ->
                    val micActive =
                        call != null &&
                            (call.state == Call.State.CONNECTING || call.state == Call.State.CONNECTED)
                    if (micActive) {
                        CallForegroundService.start(context)
                    } else {
                        CallForegroundService.stop(context)
                    }
                }
            }
        }
    }
