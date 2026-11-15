package com.jonathan.neythan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
    private val CHANNEL_ID = "NeythanForegroundService"

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var escuchandoOrdenDirecta = false
    private var hablandoActualmente = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        val notificacion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neythan Activo")
            .setContentText("Escuchando 'Neythan' o 'Despierta' en segundo plano")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(1, notificacion)
        tts = TextToSpeech(this, this)
        mostrarVentanaFlotante()
        iniciarReconocimientoVozNativo()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("es", "MX"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            ttsListo = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    hablandoActualmente = true
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(true);")
                }

                override fun onDone(utteranceId: String?) {
                    hablandoActualmente = false
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(false);")
                    mainHandler.postDelayed({ reiniciarEscucha() }, 250)
                }

                override fun onError(utteranceId: String?) {
                    hablandoActualmente = false
                    ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(false);")
                    mainHandler.postDelayed({ reiniciarEscucha() }, 250)
                }
            })
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

    private fun mostrarVentanaFlotante() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val sizePx = (170 * density).toInt()

        params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 120
        params.y = 320

        webView = WebView(this)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.addJavascriptInterface(PuenteNativo(), "AndroidNativo")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val limpiarCss = """
                    (function() {
                        var style = document.createElement('style');
                        style.innerHTML = 'button, .btn-cerrar, .close-btn, #btn-cerrar-burbuja, [class*="close"], [id*="close"] { display: none !important; opacity: 0 !important; pointer-events: none !important; } html, body { background: transparent !important; overflow: hidden !important; }';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                ejecutarJS(limpiarCss)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var esArrastre = false

        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    esArrastre = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        esArrastre = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(webView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!esArrastre) {
                        activarEscuchaDirectaPorToque()
                    }
                    true
                }
                else -> false
            }
        }

        webView.loadUrl("https://neythanproyect.onrender.com/?burbuja=1")
        windowManager.addView(webView, params)
    }

    private fun activarEscuchaDirectaPorToque() {
        detenerVoz()
        escuchandoOrdenDirecta = true
        ejecutarJS("if(window.cambiarEstadoNativo) window.cambiarEstadoNativo('escuchando');")
        mainHandler.postDelayed({ iniciarCicloReconocimiento() }, 150)
    }

    private fun iniciarReconocimientoVozNativo() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (!hablandoActualmente) {
                        escuchandoOrdenDirecta = false
                        ejecutarJS("if(window.cambiarEstadoNativo) window.cambiarEstadoNativo('reposo');")
                        mainHandler.postDelayed({ reiniciarEscucha() }, 600)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val texto = matches?.firstOrNull()?.trim() ?: ""
                    procesarTextoReconocido(texto)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            iniciarCicloReconocimiento()
        }
    }

    private fun iniciarCicloReconocimiento() {
        if (hablandoActualmente) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            mainHandler.postDelayed({ reiniciarEscucha() }, 1000)
        }
    }

    private fun reiniciarEscucha() {
        if (hablandoActualmente) return
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                iniciarCicloReconocimiento()
            } catch (e: Exception) {
                iniciarReconocimientoVozNativo()
            }
        }
    }

    private fun procesarTextoReconocido(textoCrudo: String) {
        if (textoCrudo.isEmpty()) {
            escuchandoOrdenDirecta = false
            reiniciarEscucha()
            return
        }

        val lower = textoCrudo.lowercase(Locale.getDefault())
        val tieneWakeWord = lower.contains("neythan") || lower.contains("nathan") || lower.contains("despierta")

        if (escuchandoOrdenDirecta || tieneWakeWord) {
            escuchandoOrdenDirecta = false
            val limpio = textoCrudo
                .replace(Regex("(?i)\b(neythan|nathan|despierta)\b[,\s]*"), "")
                .trim()

            if (limpio.isEmpty()) {
                escuchandoOrdenDirecta = true
                ejecutarJS("if(window.cambiarEstadoNativo) window.cambiarEstadoNativo('escuchando');")
                hablarNativo("¿Dime, Jonathan?")
            } else {
                ejecutarJS("if(window.cambiarEstadoNativo) window.cambiarEstadoNativo('pensando');")
                val escapado = limpio.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
                ejecutarJS("if(window.enviarOrdenDesdeNativo) window.enviarOrdenDesdeNativo('$escapado');")
            }
        } else {
            reiniciarEscucha()
        }
    }

    private fun hablarNativo(texto: String) {
        if (!ttsListo || texto.isBlank()) {
            mainHandler.postDelayed({ reiniciarEscucha() }, 300)
            return
        }
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            val paramsTts = Bundle()
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, paramsTts, "NEYTHAN_UTTERANCE")
        }
    }

    private fun detenerVoz() {
        mainHandler.post {
            try {
                tts?.stop()
                hablandoActualmente = false
                ejecutarJS("if(window.animarBocaNativa) window.animarBocaNativa(false);")
            } catch (_: Exception) {}
        }
    }

    private fun ejecutarJS(script: String) {
        mainHandler.post {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    inner class PuenteNativo {
        @JavascriptInterface
        fun hablar(texto: String) {
            hablarNativo(texto)
        }

        @JavascriptInterface
        fun abrirApp(paquete: String, busqueda: String) {
            try {
                if (paquete.contains("spotify")) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(if (busqueda.isNotEmpty()) "spotify:search:$busqueda" else "spotify:")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage(paquete)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
            if (::webView.isInitialized) {
                windowManager.removeView(webView)
            }
        } catch (_: Exception) {}
    }
}
