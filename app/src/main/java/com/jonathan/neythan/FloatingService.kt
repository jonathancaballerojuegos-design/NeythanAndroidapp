package com.jonathan.neythan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var escuchandoOrdenDirecta = false
    private var servicioActivo = true

    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "NeythanForegroundChannel"
    private val URL_SERVIDOR = "https://neythanproyect.onrender.com/?burbuja=1"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neythan Activo")
            .setContentText("Escuchando en segundo plano: 'Neythan' o 'Despierta'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        tts = TextToSpeech(this, this)
        configurarVozNativa()
        mostrarVentanaFlotante()

        handler.postDelayed({ iniciarEscuchaContinua() }, 1500)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("es", "MX"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            tts?.setSpeechRate(1.05f)
            ttsListo = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(true);")
                }

                override fun onDone(utteranceId: String?) {
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(false);")
                    handler.postDelayed({ iniciarEscuchaContinua() }, 300)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(false);")
                    handler.postDelayed({ iniciarEscuchaContinua() }, 300)
                }
            })
        }
    }

    private fun configurarVozNativa() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!servicioActivo) return
                val pausa = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 450L
                handler.postDelayed({ iniciarEscuchaContinua() }, pausa)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoEscuchado = matches[0].trim()
                    procesarTextoVoz(textoEscuchado)
                } else {
                    handler.postDelayed({ iniciarEscuchaContinua() }, 350)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun iniciarEscuchaContinua() {
        if (!servicioActivo) return
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            handler.postDelayed({ iniciarEscuchaContinua() }, 1000)
        }
    }

    private fun procesarTextoVoz(texto: String) {
        val lower = texto.lowercase(Locale.getDefault())

        if (escuchandoOrdenDirecta) {
            escuchandoOrdenDirecta = false
            enviarAlWebView(texto)
            return
        }

        val tieneWakeWord = lower.contains("neythan") || lower.contains("nathan") || lower.contains("despierta")
        if (tieneWakeWord) {
            var ordenLimpia = lower
                .replace("neythan", "")
                .replace("nathan", "")
                .replace("despierta", "")
                .trim()

            if (ordenLimpia.isEmpty()) {
                escuchandoOrdenDirecta = true
                ejecutarJS("if(window.reaccionarToqueNativo) window.reaccionarToqueNativo('escuchando');")
                hablarNativo("¿Dime, Jonathan?")
            } else {
                enviarAlWebView(ordenLimpia)
            }
        } else {
            handler.postDelayed({ iniciarEscuchaContinua() }, 300)
        }
    }

    private fun enviarAlWebView(orden: String) {
        val seguro = orden
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        ejecutarJS("if(window.recibirOrdenNativa) { window.recibirOrdenNativa('$seguro'); }")
    }

    private fun hablarNativo(texto: String) {
        if (!ttsListo) {
            handler.postDelayed({ iniciarEscuchaContinua() }, 400)
            return
        }
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}

        val paramsTts = Bundle()
        paramsTts.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NeythanMsg")
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, paramsTts, "NeythanMsg")
    }

    private fun ejecutarJS(script: String) {
        handler.post {
            try {
                webView.evaluateJavascript(script, null)
            } catch (_: Exception) {}
        }
    }

    private fun mostrarVentanaFlotante() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val anchoPx = (175 * density).toInt()
        val altoPx = (175 * density).toInt()

        params = WindowManager.LayoutParams(
            anchoPx,
            altoPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 220

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            addJavascriptInterface(PuenteAndroid(), "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val cssLimpio = """
                        (function() {
                            var s = document.createElement('style');
                            s.innerHTML = 'html, body { background: transparent !important; overflow: hidden !important; } ' +
                                          'button, header, .controles, .chips, #chat-box, #estado, #pensamiento, #panel-perfil, .btn-flotante, [id*="cerrar"], [id*="salir"] { display: none !important; } ' +
                                          'canvas { background: transparent !important; }';
                            document.head.appendChild(s);
                            window.ES_APP_NATIVA = true;
                        })();
                    """.trimIndent()
                    evaluateJavascript(cssLimpio, null)
                }
            }

            loadUrl(URL_SERVIDOR)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var tiempoToque = 0L
        var seMovio = false

        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    tiempoToque = System.currentTimeMillis()
                    seMovio = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        seMovio = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(webView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duracion = System.currentTimeMillis() - tiempoToque
                    if (!seMovio && duracion < 400) {
                        escuchandoOrdenDirecta = true
                        ejecutarJS("if(window.reaccionarToqueNativo) window.reaccionarToqueNativo('escuchando');")
                        hablarNativo("¿Sí, Jonathan?")
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(webView, params)
    }

    inner class PuenteAndroid {
        @JavascriptInterface
        fun hablar(texto: String) {
            handler.post {
                hablarNativo(texto)
            }
        }

        @JavascriptInterface
        fun abrirApp(uriStr: String) {
            handler.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun cerrarBurbuja() {
            handler.post {
                stopSelf()
            }
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio en Segundo Plano Neythan",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        servicioActivo = false
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
            windowManager.removeView(webView)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
