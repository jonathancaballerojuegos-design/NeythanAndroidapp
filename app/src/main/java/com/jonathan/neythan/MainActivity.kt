package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var mensajePendienteTTS: String? = null
    private lateinit var audioManager: AudioManager

    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"
    private val PERMISSION_REQUEST_CODE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this, "com.google.android.tts")

        webView = WebView(this)
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(webView)

        configurarWebView()
        verificarPermisos()
        iniciarServicioSegundoPlano()

        webView.loadUrl(SERVER_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(NeythanAndroidBridge(), "NeythanAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                simularToqueManosLibres()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }
        }
    }

    private fun simularToqueManosLibres() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val now = SystemClock.uptimeMillis()
                val x = (webView.width / 2).toFloat()
                val y = (webView.height / 2).toFloat()
                val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val upEvent = MotionEvent.obtain(now + 50, now + 50, MotionEvent.ACTION_UP, x, y, 0)
                webView.dispatchTouchEvent(downEvent)
                webView.dispatchTouchEvent(upEvent)
                downEvent.recycle()
                upEvent.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1200)
    }

    private fun verificarPermisos() {
        val permisos = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun iniciarServicioSegundoPlano() {
        try {
            val intent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("es", "ES"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            ttsListo = true
            mensajePendienteTTS?.let {
                hablarNativo(it)
                mensajePendienteTTS = null
            }
        } else {
            tts = TextToSpeech(this) { fallbackStatus ->
                if (fallbackStatus == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale("es", "ES"))
                    ttsListo = true
                }
            }
        }
    }

    private fun hablarNativo(texto: String) {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < maxVol / 3) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.7).toInt(), 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }

            val params = Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_UTTERANCE")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class NeythanAndroidBridge {

        @JavascriptInterface
        fun speak(texto: String) {
            runOnUiThread {
                if (ttsListo) {
                    hablarNativo(texto)
                } else {
                    mensajePendienteTTS = texto
                }
            }
        }

        @JavascriptInterface
        fun abrirApp(paqueteOApp: String) {
            runOnUiThread {
                try {
                    if (paqueteOApp.lowercase().contains("spotify")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } else {
                        val launchIntent = packageManager.getLaunchIntentForPackage(paqueteOApp)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JavascriptInterface
        fun reproducirSpotify(cancion: String) {
            runOnUiThread {
                try {
                    val query = Uri.encode(cancion)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JavascriptInterface
        fun activarBurbuja() {
            runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@MainActivity, FloatingService::class.java)
                        startService(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JavascriptInterface
        fun leerNotificaciones(): String {
            val prefs = getSharedPreferences("NeythanNotificaciones", Context.MODE_PRIVATE)
            return prefs.getString("ultimas_notificaciones", "No hay notificaciones recientes.") ?: "No hay notificaciones recientes."
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
