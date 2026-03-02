package com.example.llamaapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.llama.LlamaEngine
import com.example.llamaapp.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InferenceService : Service() {

    @Inject lateinit var engine: LlamaEngine

    private lateinit var wakeLock: PowerManager.WakeLock
    private var hintSession: Any? = null  // PerformanceHintManager.Session (API 31+)
    private val binder = LocalBinder()

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "inference_channel"
        const val ACTION_START_INFERENCE = "com.example.llamaapp.START_INFERENCE"
        const val ACTION_CANCEL_INFERENCE = "com.example.llamaapp.CANCEL_INFERENCE"
        const val ACTION_STOP_SERVICE = "com.example.llamaapp.STOP_SERVICE"
    }

    inner class LocalBinder : Binder() {
        fun getService(): InferenceService = this@InferenceService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LlamaApp::InferenceLock")

        if (Build.VERSION.SDK_INT >= 31) {
            val phm = getSystemService(PerformanceHintManager::class.java)
            val tids = intArrayOf(android.os.Process.myTid())
            hintSession = phm?.createHintSession(tids, 50_000_000L) // 50ms target
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Ready")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        when (intent?.action) {
            ACTION_START_INFERENCE -> {
                if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L) // 30 min max
                updateNotification("Generating...")
            }
            ACTION_CANCEL_INFERENCE -> {
                engine.cancelGeneration()
                if (wakeLock.isHeld) wakeLock.release()
                updateNotification("Cancelled")
            }
            ACTION_STOP_SERVICE -> {
                if (wakeLock.isHeld) wakeLock.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun onInferenceDone() {
        if (wakeLock.isHeld) wakeLock.release()
        updateNotification("Ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        if (Build.VERSION.SDK_INT >= 31) {
            (hintSession as? PerformanceHintManager.Session)?.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LLM Inference",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows inference status" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, InferenceService::class.java).apply { action = ACTION_CANCEL_INFERENCE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("LLaMA Inference")
            .setContentText(status)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(status))
    }
}
