package com.jonathan.neythan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NeythanBackgroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val intervaloMs = 8 * 60 * 1000L // 8 minutos para evitar sobrecalentamiento

    private val tareaMantenimiento = object : Runnable {
        override fun run() {
            executor.execute {
                try {
                    val url = URL("${MainActivity.SERVER_URL}/health")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }
            handler.postDelayed(this, intervaloMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "neythan_bg_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Enlace Nube Neythan",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Neythan en segundo plano")
            .setContentText("Enlace ligero activo sin consumo excesivo")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(101, notification)
        handler.post(tareaMantenimiento)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tareaMantenimiento)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
