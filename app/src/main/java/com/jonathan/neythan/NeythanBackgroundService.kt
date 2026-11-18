package com.jonathan.neythan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

class NeythanBackgroundService : Service() {

    private val CHANNEL_ID = "neythan_core_channel"
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neythan Activo")
            .setContentText("Escucha de bajo consumo: diga 'Neythan' o 'Despierta'")
            .setSmallIcon(R.drawable.nucleo_icono)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(101, notification)
        iniciarEscuchaPalabraClave()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nucleo Neythan",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun iniciarEscuchaPalabraClave() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }

            override fun onError(error: Int) {
                isListening = false
                // Pausa de 2.5 segundos entre ciclos para evitar sobrecalentamiento
                handler.postDelayed({ reiniciarEscucha() }, 2500)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                procesarTexto(results)
                handler.postDelayed({ reiniciarEscucha() }, 1500)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                procesarTexto(partialResults)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        reiniciarEscucha()
    }

    private fun reiniciarEscucha() {
        if (!isListening) {
            try {
                speechRecognizer?.startListening(recognizerIntent)
            } catch (_: Exception) {
                handler.postDelayed({ reiniciarEscucha() }, 4000)
            }
        }
    }

    private fun procesarTexto(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (texto in matches) {
            val limpio = texto.lowercase(Locale.getDefault())
            if (limpio.contains("neythan") || limpio.contains("nathan") || limpio.contains("despierta")) {
                val wakeIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(wakeIntent)
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
    }
}
