package com.ospchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Lightweight foreground service whose only job is to keep the mic alive
 * while a call is active. Started by [CallForegroundService.start] when the
 * call enters CONNECTING and stopped on hangup / failure.
 *
 * Android 14+ requires `foregroundServiceType="microphone"` (and the matching
 * runtime permission) for any FG service that records audio while the screen
 * is off or the app is backgrounded. Lives separately from
 * [DiscoveryForegroundService] so the discovery + server stay up across
 * calls and so the FG-service-type flag can stay clean (microphone-only
 * here, connectedDevice-only there).
 */
class CallForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("OSPChat call in progress")
                .setContentText("Microphone is in use")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            // `FOREGROUND_SERVICE_TYPE_MICROPHONE` was introduced in API 30
            // and became required for mic-recording FG services in API 31.
            // The manifest already declares `foregroundServiceType="microphone"`
            // unconditionally, so we always pass the matching type here on
            // API 30+. Passing 0 on API 31+ throws
            // `InvalidForegroundServiceTypeException` at runtime.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Active call",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while a voice call is in progress."
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ospchat_call_active"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
