package com.ospchat.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ospchat.android.R
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.identity.IdentityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that hosts the NSD registration + discovery for as long
 * as the user wants to be visible. It holds a [WifiManager.MulticastLock]
 * because many Wi-Fi drivers filter multicast packets (mDNS) when the
 * application is not actively listening.
 *
 * The multicast lock is acquired on the main thread inside [onStartCommand],
 * so there's no race between an async acquire and [onDestroy].
 *
 * Returns [START_NOT_STICKY] — discovery is only useful when the UI starts
 * it, so we don't want the system to relaunch this service in isolation.
 */
@AndroidEntryPoint
class DiscoveryForegroundService : Service() {

    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var discoveryRepository: DiscoveryRepository
    @Inject lateinit var wifiManager: WifiManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        acquireMulticastLock()
        if (startJob?.isActive != true) {
            startJob = scope.launch {
                val nickname = identityRepository.nicknameFlow.first()
                if (nickname.isNullOrBlank()) {
                    Log.w(TAG, "No nickname set; stopping service")
                    stopSelf()
                    return@launch
                }
                val uuid = identityRepository.ensureUuid()
                discoveryRepository.start(nickname, uuid)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        startJob = null
        discoveryRepository.stop()
        releaseMulticastLock()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_discovery),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.notification_text)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
    }

    companion object {
        private const val TAG = "DiscoveryFgService"
        private const val CHANNEL_ID = "ospchat_discovery"
        private const val NOTIFICATION_ID = 1
        private const val MULTICAST_LOCK_TAG = "ospchat-mdns"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DiscoveryForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DiscoveryForegroundService::class.java))
        }
    }
}
