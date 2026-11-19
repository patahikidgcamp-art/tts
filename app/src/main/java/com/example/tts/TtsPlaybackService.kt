package com.example.tts

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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class TtsPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.notification_playing_title)
                val isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                val notification = buildNotification(title, isPaused)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                sendBroadcast(Intent(BROADCAST_CONTROL).setPackage(packageName).putExtra(EXTRA_CMD, CMD_TOGGLE))
            }
            ACTION_STOP_SERVICE -> {
                sendBroadcast(Intent(BROADCAST_CONTROL).setPackage(packageName).putExtra(EXTRA_CMD, CMD_STOP))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_DISMISS_ONLY -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(subtitle: String, isPaused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (isPaused) getString(R.string.notification_paused_title)
                else getString(R.string.notification_playing_title)
            )
            .setContentText(subtitle)
            .setContentIntent(openIntent)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                0,
                if (isPaused) getString(R.string.action_resume) else getString(R.string.action_pause),
                toggleIntent
            )
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "vanivox_tts_playback"
        const val NOTIFICATION_ID = 4102
        const val ACTION_START_OR_UPDATE = "com.example.tts.START_OR_UPDATE"
        const val ACTION_TOGGLE_PAUSE = "com.example.tts.TOGGLE_PAUSE"
        const val ACTION_STOP_SERVICE = "com.example.tts.STOP_SERVICE"
        const val ACTION_DISMISS_ONLY = "com.example.tts.DISMISS_ONLY"
        const val BROADCAST_CONTROL = "com.example.tts.BROADCAST_CONTROL"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
        const val EXTRA_CMD = "extra_cmd"
        const val CMD_TOGGLE = "toggle"
        const val CMD_STOP = "stop"

        fun updateForeground(context: Context, title: String, isPaused: Boolean) {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_PAUSED, isPaused)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_DISMISS_ONLY)
            runCatching { context.startService(intent) }
        }
    }
}
