package com.jonathan.neythan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onCreate() {
        super.onCreate()
        iniciarNotificacionServicio()
        mostrarNucleoFlotante()
    }

    private fun iniciarNotificacionServicio() {
        val channelId = "neythan_floating_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Núcleo Flotante Neythan",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Neythan Activo")
            .setContentText("Toque el núcleo flotante para abrir")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .build()

        startForeground(202, notification)
    }

    private fun mostrarNucleoFlotante() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bubbleView = object : View(this) {
            private val pOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            private val pInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AA00E5FF")
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val r = (minOf(width, height) / 2f) - 8f
                canvas.drawCircle(cx, cy, r, pOuter)
                canvas.drawCircle(cx, cy, r * 0.62f, pInner)
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            150,
            150,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 15 && diffY < 15) {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(bubbleView, params)
    }

    override fun onDestroy() {
        if (bubbleView != null) {
            windowManager?.removeView(bubbleView)
            bubbleView = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
