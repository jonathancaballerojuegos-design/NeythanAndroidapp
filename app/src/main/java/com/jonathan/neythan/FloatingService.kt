package com.jonathan.neythan

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var escuchando = false
    private var linternaEncendida = false

    override fun onCreate() {
        super.onCreate()
        iniciarNotificacion()
        tts = TextToSpeech(this, this)
        configurarVentanaFlotante()
        iniciarReconocimientoVoz()
    }

    private fun iniciarNotificacion() {
        val channelId = "neythan_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Neythan Activo", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Neythan AI")
            .setContentText("Escuchando en segundo plano...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(101, notification)
    }

    private fun configurarVentanaFlotante() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(PuenteNativo(), "AndroidBridge")
            loadUrl("https://neythanproyect.onrender.com/?burbuja=1")
        }

        val tipoVentana = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            420, 420, tipoVentana,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var tiempoToque = 0L

        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    tiempoToque = System.currentTimeMillis()
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(webView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dt = System.currentTimeMillis() - tiempoToque
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dt < 250 && dx < 15 && dy < 15) {
                        hablar("¿Dime, Jonathan?")
                        reiniciarEscucha(500)
                    }
                    false
                }
                else -> false
            }
        }

        windowManager.addView(webView, params)
    }

    private fun iniciarReconocimientoVoz() {
        Handler(Looper.getMainLooper()).post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { escuchando = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { escuchando = false }
                    override fun onError(error: Int) {
                        escuchando = false
                        reiniciarEscucha(800)
                    }
                    override fun onResults(results: Bundle?) {
                        escuchando = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val texto = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                        if (texto.contains("neythan") || texto.contains("despierta")) {
                            val orden = texto.replace("neythan", "").replace("despierta", "").trim()
                            if (orden.isNotEmpty()) {
                                val escapado = orden.replace("'", "\'")
                                webView.evaluateJavascript("window.recibirOrdenDesdeNativo('$escapado')", null)
                            } else {
                                hablar("Ya estoy despierto, Jonathan. ¿Qué necesitas?")
                            }
                        }
                        reiniciarEscucha(400)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(intent)
            } catch (_: Exception) {
                reiniciarEscucha(1500)
            }
        }
    }

    private fun reiniciarEscucha(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!escuchando) iniciarReconocimientoVoz()
        }, delayMs)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "MX")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        webView.evaluateJavascript("window.animarBocaNativa(true)", null)
                    }
                }
                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        webView.evaluateJavascript("window.animarBocaNativa(false)", null)
                    }
                }
                override fun onError(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        webView.evaluateJavascript("window.animarBocaNativa(false)", null)
                    }
                }
            })
        }
    }

    private fun hablar(texto: String) {
        speechRecognizer?.cancel()
        val paramsTts = Bundle()
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, paramsTts, "neythan_utt")
    }

    inner class PuenteNativo {
        @JavascriptInterface
        fun hablarNativo(texto: String) {
            Handler(Looper.getMainLooper()).post { hablar(texto) }
        }

        @JavascriptInterface
        fun ejecutarComandoAndroid(cmd: String) {
            Handler(Looper.getMainLooper()).post {
                try {
                    when {
                        cmd == "linterna_on" || cmd == "linterna_off" -> {
                            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                            val camId = cm.cameraIdList.firstOrNull()
                            if (camId != null) {
                                linternaEncendida = (cmd == "linterna_on")
                                cm.setTorchMode(camId, linternaEncendida)
                            }
                        }
                        cmd.startsWith("spotify:") -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cmd)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                        cmd.startsWith("youtube:") -> {
                            val q = Uri.encode(cmd.removePrefix("youtube:"))
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                        cmd == "whatsapp" -> {
                            val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (intent != null) startActivity(intent)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        if (::webView.isInitialized) windowManager.removeView(webView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
