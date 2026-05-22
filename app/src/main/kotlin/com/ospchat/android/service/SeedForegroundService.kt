package com.ospchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ospchat.android.MainActivity
import com.ospchat.android.R
import com.ospchat.android.seed.SeedRepository
import com.ospchat.android.seed.SeedServer
import com.ospchat.android.seed.SeedServerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the Seed Mode HTTP server for as long as the user wants the device
 * to act as an installer distribution point. Resolves the hotspot-side IP
 * itself (so the seed only runs when a hotspot is actually active) and
 * binds the embedded Ktor engine to `0.0.0.0:8080`.
 *
 * Runs independently of [DiscoveryForegroundService] — peer messaging and
 * seed serving can be active simultaneously.
 */
@AndroidEntryPoint
class SeedForegroundService : Service() {
    @Inject internal lateinit var seedRepository: SeedRepository

    @Inject internal lateinit var serverState: SeedServerState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private var server: SeedServer? = null

    @Volatile private var fullyStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val hotspotIp = seedRepository.hotspotIp()
        if (hotspotIp == null) {
            // The hotspot is the trust + reachability boundary for this
            // server; without it there's no point binding 8080 on a regular
            // Wi-Fi LAN. Bail out instead of leaving a stale notification.
            Log.w(TAG, "No hotspot interface — refusing to start")
            stopSelf()
            return START_NOT_STICKY
        }
        val url = SeedServer.urlFor(hotspotIp)
        startForegroundNotification(url)
        if (fullyStarted || startJob?.isActive == true) return START_NOT_STICKY
        startJob =
            scope.launch {
                try {
                    val s = SeedServer(seedRepository)
                    s.start(hotspotIp)
                    server = s
                    fullyStarted = true
                    serverState.markRunning(url)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start seed server on $hotspotIp:${SeedServer.PORT}", t)
                    stopSelf()
                }
            }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fullyStarted = false
        startJob?.cancel()
        startJob = null
        server?.stop()
        server = null
        serverState.markStopped()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification(url: String) {
        val stopIntent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_STOP_SEED
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val stopPendingIntent =
            PendingIntent.getActivity(
                this,
                STOP_REQUEST_CODE,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_seed_title))
                .setContentText(getString(R.string.notification_seed_text, url))
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notification_seed_action_stop),
                    stopPendingIntent,
                ).build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            // `FOREGROUND_SERVICE_TYPE_DATA_SYNC` is API 29+. Below Q the
            // type is implied by the manifest and 0 is the required value.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_seed),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.seed_mode_blurb)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SeedFgService"
        private const val CHANNEL_ID = "ospchat_seed"
        private const val NOTIFICATION_ID = 3
        private const val STOP_REQUEST_CODE = 200

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SeedForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SeedForegroundService::class.java))
        }
    }
}
