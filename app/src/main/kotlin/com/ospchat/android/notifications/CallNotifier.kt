package com.ospchat.android.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ospchat.android.MainActivity
import com.ospchat.shared.data.calls.Call
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.ospchat.shared.notifications.CallNotifier as SharedCallNotifier

/**
 * Android implementation of the shared [SharedCallNotifier]. Two responsibilities:
 *  1. Post a high-importance heads-up notification on incoming calls (no
 *     action buttons — accept/decline live in the in-app
 *     `IncomingCallDialog` overlay). The notification's tap target is a
 *     deep link into the in-call screen (`ospchat://call/{callId}`),
 *     which both routes the user to the right UI and brings the activity
 *     to the foreground when the screen is off.
 *  2. Loop the system's default ringtone via [RingtoneManager] for the
 *     duration of the call's RINGING phase. Stopped on [cancel].
 *
 * The full CallStyle / fullScreenIntent treatment is deliberately deferred
 * to a phase-2 polish — `USE_FULL_SCREEN_INTENT` is a Play Store-restricted
 * permission on Android 14+, and CallStyle requires answer+decline
 * `PendingIntent`s which in turn need a `BroadcastReceiver`. Phase 1 keeps
 * the surface area small.
 */
@Singleton
class CallNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SharedCallNotifier {
        private val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // (activeRingtone, activeCallId) is mutated as a pair; @Synchronized
        // on every public method (and the private start/stop helpers)
        // prevents the torn-state window where one field is updated before
        // the other.
        private var activeRingtone: Ringtone? = null
        private var activeCallId: String? = null

        init {
            ensureChannel()
        }

        @Synchronized
        override fun notifyIncomingCall(call: Call) {
            // Audio cue first — if notifications are blocked the ring still plays.
            startRingtone(call.id)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notification: Notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                    .setContentTitle(call.peerNickname)
                    .setContentText("Incoming voice call")
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(openCallPendingIntent(call.id))
                    .build()
            notificationManager.notify(call.id.hashCode(), notification)
        }

        @Synchronized
        override fun cancel(callId: String) {
            if (activeCallId == callId) {
                stopRingtoneLocked()
                activeCallId = null
            }
            notificationManager.cancel(callId.hashCode())
        }

        @Synchronized
        private fun startRingtone(callId: String) {
            stopRingtoneLocked()
            runCatching {
                val uri =
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone.audioAttributes =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = true
                }
                ringtone.play()
                activeRingtone = ringtone
                activeCallId = callId
            }
        }

        private fun stopRingtoneLocked() {
            activeRingtone?.takeIf { it.isPlaying }?.stop()
            activeRingtone = null
        }

        private fun openCallPendingIntent(callId: String): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, "ospchat://call/$callId".toUri()).apply {
                    setClass(context, MainActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel() {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Incoming voice calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifies you when another OSPChat user calls you."
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            notificationManager.createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL_ID = "ospchat_calls"
        }
    }
