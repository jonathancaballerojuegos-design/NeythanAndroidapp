package com.jonathan.neythan

import android.app.Notification
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

class NeythanBackgroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val INTERVALO_MS = 8 * 60 * 1000L
    private val CHANNEL_ID = "neythan_core_channel"

    private val tareaMantenimiento = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val url = URL("https://neythanproyect-cloud.onrender.com/health")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }.start()
            handler.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neythan Activo")
            .setContentText("Núcleo en espera de baja energía")
            .setSmallIcon(R.drawable.nucleo_icono)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(101, notification)
        handler.post(tareaMantenimiento)
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio Neythan",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tareaMantenimiento)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
