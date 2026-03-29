package com.codrivelog.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codrivelog.app.R
import com.codrivelog.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps the drive timer running while the app is in
 * the background.
 *
 * Start with [ACTION_START] to begin timing; send [ACTION_STOP] to stop and
 * persist the session via the repository.
 */
@AndroidEntryPoint
class DriveTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { /* TODO: start elapsed-time tracking */ }
            ACTION_STOP  -> { stopSelf() }
        }
        return START_STICKY
    }

    // ---- Private helpers ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.codrivelog.app.ACTION_START_DRIVE"
        const val ACTION_STOP  = "com.codrivelog.app.ACTION_STOP_DRIVE"

        private const val CHANNEL_ID       = "drive_timer_channel"
        private const val NOTIFICATION_ID  = 1
    }
}
