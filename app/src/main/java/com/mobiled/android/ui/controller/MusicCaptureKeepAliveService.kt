package com.mobiled.android.ui.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobiled.android.R

/**
 * Keeps the Android process important while the Music engine captures playback
 * audio in MusicFragment. The actual AudioRecord/MediaProjection session
 * remains owned by MusicFragment; the service signals task removal or shutdown
 * so the Fragment can release that session.
 */
class MusicCaptureKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "mobiled_music_capture"
        private const val NOTIFICATION_ID = 4101
    }

    var captureReady: Boolean = false
        private set
    var captureStopListener: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_item_music)
            .setContentTitle("MobileD Music")
            .setContentText("Music effect active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            captureReady = true
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music keep-alive foreground service could not start", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        notifyCaptureStopped()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        notifyCaptureStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notifyCaptureStopped() {
        captureReady = false
        val listener = captureStopListener
        captureStopListener = null
        listener?.invoke()
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): MusicCaptureKeepAliveService = this@MusicCaptureKeepAliveService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "MobileD Music capture",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
