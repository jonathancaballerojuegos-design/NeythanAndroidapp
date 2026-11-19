package com.jonathan.neythan

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    companion object {
        const val SERVER_URL = "https://neythanproyect-cloud.onrender.com"
    }
    private lateinit var avatarImageView: ImageView
    private lateinit var txtEstado: TextView
    private lateinit var txtChat: TextView
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var estaHablando = false
    private var estadoActual = "reposo_feliz"
    private var frameHabla = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var escuchandoActivo = false
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val animacionBoca = object : Runnable {
        override fun run() {
            if (estaHablando) {
                frameHabla = !frameHabla
                aplicarSprite(if (frameHabla) "hablando_boca_abierta" else "hablando_senalando")
                handler.postDelayed(this, 170L)
            } else {
                aplicarSprite(estadoActual)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        avatarImageView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        aplicarSprite("reposo_feliz")
        ObjectAnimator.ofFloat(avatarImageView, "translationY", -14f, 14f).apply {
            duration = 2200L; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }
        var dX = 0f; var dY = 0f
        avatarImageView.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dX = v.x - e.rawX; dY = v.y - e.rawY; true }
                MotionEvent.ACTION_MOVE -> { v.animate().x(e.rawX + dX).y(e.rawY + dY).setDuration(0).start(); true }
                else -> false
            }
        }
        root.addView(avatarImageView, FrameLayout.LayoutParams(620, 620, Gravity.CENTER))
        txtEstado = TextView(this).apply { text = "ESTADO: EN LINEA"; setTextColor(Color.parseColor("#00E5FF")); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER }
        txtChat = TextView(this).apply { text = "A sus órdenes, señor."; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(24, 60, 24, 16) }
        root.addView(txtChat, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.addView(txtEstado, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
        tts = TextToSpeech(this, this, "com.google.android.tts")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        } else {
            iniciarEscucha()
        }
    }

    private fun aplicarSprite(nombre: String) {
        val id = resources.getIdentifier(nombre.lowercase(Locale.getDefault()), "drawable", packageName)
        if (id != 0) avatarImageView.setImageResource(id)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "MX")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { handler.post { estaHablando = true; handler.post(animacionBoca) } }
                override fun onDone(id: String?) { handler.post { estaHablando = false; aplicarSprite(estadoActual); iniciarEscucha() } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { handler.post { estaHablando = false; iniciarEscucha() } }
            })
            ttsListo = true
            hablar("Sistemas nativos en línea, señor.")
        }
    }

    private fun hablar(texto: String) {
        txtChat.text = "Neythan: $texto"
        if (ttsListo) {
            speechRecognizer?.cancel()
            escuchandoActivo = false
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "NEYTHAN")
        }
    }

    private fun iniciarEscucha() {
        if (estaHablando || escuchandoActivo) return
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { escuchandoActivo = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(r: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { escuchandoActivo = false }
                override fun onError(e: Int) { escuchandoActivo = false; handler.postDelayed({ iniciarEscucha() }, 700) }
                override fun onResults(r: Bundle?) {
                    escuchandoActivo = false
                    val frase = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (frase.lowercase(Locale.getDefault()).contains("neythan") || frase.lowercase(Locale.getDefault()).contains("despierta")) {
                        estadoActual = "escuchando_mano_oido"
                        aplicarSprite(estadoActual)
                        enviarAlServidor(frase)
                    } else {
                        iniciarEscucha()
                    }
                }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun enviarAlServidor(mensaje: String) {
        estadoActual = "pensando_dudando"
        aplicarSprite(estadoActual)
        executor.execute {
            try {
                val conn = (URL("$SERVER_URL/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); doOutput = true
                }
                OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().put("mensaje", mensaje).toString()) }
                val res = JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() })
                handler.post {
                    estadoActual = res.optString("emocion", "reposo_feliz")
                    aplicarSprite(estadoActual)
                    hablar(res.optString("respuesta", "A sus órdenes, señor."))
                }
            } catch (_: Exception) {
                handler.post { estadoActual = "alerta_enojado"; aplicarSprite(estadoActual); hablar("Error de enlace con la nube, señor.") }
            }
        }
    }
}
