package com.bgmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bgmanager.R
import com.bgmanager.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service untuk monitoring berkala.
 * Jalan di background, update notifikasi dengan info ringkas.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notifManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "bg_monitor_channel"
        const val NOTIF_ID = 1001
        const val EXTRA_ROOT_MODE = "root_mode"

        fun start(context: Context, isRoot: Boolean) {
            val intent = Intent(context, MonitorService::class.java).apply {
                putExtra(EXTRA_ROOT_MODE, isRoot)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isRoot = intent?.getBooleanExtra(EXTRA_ROOT_MODE, false) ?: false
        startForeground(NOTIF_ID, buildNotification("Memantau background service..."))
        startMonitoring(isRoot)
        return START_STICKY
    }

    private fun startMonitoring(isRoot: Boolean) {
        scope.launch {
            while (isActive) {
                try {
                    // Update notifikasi setiap 30 detik
                    val summary = getSummary(isRoot)
                    val notif = buildNotification(summary)
                    notifManager.notify(NOTIF_ID, notif)
                } catch (e: Exception) {
                    // ignore, lanjut loop
                }
                delay(30_000L)
            }
        }
    }

    private fun getSummary(isRoot: Boolean): String {
        // Versi ringkas — bisa diperluas dengan repo call
        return if (isRoot) "Mode root aktif — tap untuk detail"
               else "Memantau proses background"
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BG Service Manager")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi monitoring background service"
            setShowBadge(false)
        }
        notifManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
