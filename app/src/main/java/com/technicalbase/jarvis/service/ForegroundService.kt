package com.technicalbase.jarvis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.technicalbase.jarvis.bridge.LocalServer

class ForegroundService : Service() {

    companion object {
        private const val TAG = "Jarvis.Foreground"
        private const val CHANNEL_ID = "jarvis_foreground"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.technicalbase.jarvis.START"
        const val ACTION_STOP = "com.technicalbase.jarvis.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification())
                if (!LocalServer.isRunning()) {
                    LocalServer.start(applicationContext, 8766, "change-me-on-first-boot")
                    Log.i(TAG, "LocalServer started on 8766")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LocalServer.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis is running")
            .setContentText("Local bridge on 127.0.0.1:8766")
            .setSmallIcon(android.R.drawable.presence_online)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Jarvis",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
