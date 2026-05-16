package com.ospchat.android.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ospchat.android.MainActivity
import com.ospchat.android.R
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.messages.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val activeChatTracker: ActiveChatTracker,
    ) {
        private val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            ensureChannel()
        }

        /**
         * Posts a notification for an inbound message, unless any of:
         *   - The user is currently looking at the chat for this peer.
         *   - The system is in any DND mode (we explicitly suppress rather
         *     than rely on channel filtering).
         *   - The `POST_NOTIFICATIONS` runtime permission is denied on API 33+.
         */
        fun notifyIncoming(
            peer: Peer,
            message: Message,
        ) {
            if (activeChatTracker.activePeerUuid == peer.uuid) return
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
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
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(peer.nickname)
                    .setContentText(message.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(openChatPendingIntent(peer.uuid))
                    .setAutoCancel(true)
                    .setWhen(message.sentAt)
                    .build()

            notificationManager.notify(peer.uuid.hashCode(), notification)
        }

        fun cancel(peerUuid: String) {
            notificationManager.cancel(peerUuid.hashCode())
        }

        private fun openChatPendingIntent(peerUuid: String): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, "ospchat://chat/$peerUuid".toUri()).apply {
                    setClass(context, MainActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                peerUuid.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannel() {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_messages),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_messages_desc)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            notificationManager.createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL_ID = "ospchat_messages"
        }
    }
