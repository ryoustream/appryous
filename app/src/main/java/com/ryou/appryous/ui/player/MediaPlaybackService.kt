package com.ryou.appryous.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.ryou.appryous.R

/**
 * Foreground service untuk media playback notification.
 *
 * mpv tidak punya built-in MediaSession — service ini membuat notifikasi
 * "Now Playing" dengan kontrol basic (pause/play) agar Android tidak kill
 * proses saat video berjalan di background / PiP.
 *
 * Untuk kontrol penuh via MediaSession, PlayerActivity mengelola state
 * langsung via MPVLib callback dan update notifikasi dari sini.
 */
class MediaPlaybackService : LifecycleService() {

    companion object {
        const val ACTION_START   = "com.ryou.appryous.MEDIA_START"
        const val ACTION_STOP    = "com.ryou.appryous.MEDIA_STOP"
        const val ACTION_PAUSE   = "com.ryou.appryous.MEDIA_PAUSE"
        const val EXTRA_TITLE    = "title"
        const val CHANNEL_ID     = "appryous_media"
        const val NOTIF_ID       = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
                startForeground(NOTIF_ID, buildNotification(title))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE -> {
                // Update notification icon — handled by PlayerActivity
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildNotification(title: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_play)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AppRyous media playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
