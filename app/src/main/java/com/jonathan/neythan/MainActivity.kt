package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.setBackgroundColor(Color.BLACK)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        tts = TextToSpeech(this, this)
        subirVolumenMultimedia()

        webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(NeythanAndroidBridge(), "NeythanAndroid")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }

        solicitarPermisos()
        iniciarServicioFondo()
        webView.loadUrl(SERVER_URL)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts?.setLanguage(Locale("es", "ES"))
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.05f)
            ttsListo = true
        }
    }

    private fun subirVolumenMultimedia() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol < (maxVol * 0.6).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class NeythanAndroidBridge {

        @JavascriptInterface
        fun speak(texto: String) {
            runOnUiThread {
                subirVolumenMultimedia()
                if (ttsListo && texto.isNotBlank()) {
                    val textoLimpio = texto.replace(Regex("[*#_`]"), "").trim()
                    tts?.speak(textoLimpio, TextToSpeech.QUEUE_FLUSH, null, "NEYTHAN_VOZ")
                }
            }
        }

        @JavascriptInterface
        fun activarBurbuja() {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    val intent = Intent(this@MainActivity, FloatingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
        }

        @JavascriptInterface
        fun reproducirSpotify(cancion: String) {
            runOnUiThread {
                try {
                    val query = URLEncoder.encode(cancion, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://open.spotify.com/search/${URLEncoder.encode(cancion, "UTF-8")}")
                    )
                    startActivity(webIntent)
                }
            }
        }

        @JavascriptInterface
        fun abrirApp(nombrePaquete: String) {
            runOnUiThread {
                val launchIntent = packageManager.getLaunchIntentForPackage(nombrePaquete)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        }

        @JavascriptInterface
        fun prepararWhatsApp(mensaje: String) {
            runOnUiThread {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(Intent.EXTRA_TEXT, mensaje)
                    sendIntent.setPackage("com.whatsapp")
                    startActivity(sendIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JavascriptInterface
        fun leerNotificaciones(): String {
            return try {
                val prefs = getSharedPreferences("NeythanNotificaciones", Context.MODE_PRIVATE)
                val historial = prefs.getString("ultimas", "") ?: ""
                if (historial.isBlank()) {
                    "No hay notificaciones recientes registradas o el permiso de acceso a notificaciones aún no está activo."
                } else {
                    historial
                }
            } catch (e: Exception) {
                "No se pudieron leer las notificaciones en este momento."
            }
        }

        @JavascriptInterface
        fun abrirPermisoNotificaciones() {
            runOnUiThread {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun solicitarPermisos() {
        val permisos = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), 101)
        }
    }

    private fun iniciarServicioFondo() {
        val intent = Intent(this, NeythanBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
