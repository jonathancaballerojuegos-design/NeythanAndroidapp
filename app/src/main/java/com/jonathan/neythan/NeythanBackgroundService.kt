package com.jonathan.neythan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

    private val CHANNEL_ID = "NeythanActiveChannel"
    private val NOTIFICATION_ID = 101
    private val RENDER_PING_URL = "https://neythanproyect-cloud.onrender.com/ping"

    // Hilo único y temporizador de bajo consumo para evitar sobrecalentamiento
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val intervaloPingMs = 480_000L // 8 minutos (mantiene Render despierto sin gastar batería)

    private val tareaMantenimiento = object : Runnable {
        override fun run() {
            mantenerServidorActivo()
            handler.postDelayed(this, intervaloPingMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        startForeground(NOTIFICATION_ID, construirNotificacion())
        handler.post(tareaMantenimiento)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun mantenerServidorActivo() {
        executor.execute {
            try {
                val url = URL(RENDER_PING_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
                // Silencioso si no hay red temporalmente para evitar ciclos de CPU
            }
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Núcleo Neythan en Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la conexión de voz y enlace con el servidor"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun construirNotificacion(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neythan Activo")
            .setContentText("Enlace de voz y servidor sincronizados")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tareaMantenimiento)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
