package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // URL de su servidor en Render
    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar motor nativo de voz en español
        tts = TextToSpeech(this, this)

        // Asegurar volumen multimedia audible al iniciar
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (currentVol < maxVol / 3) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75).toInt(), 0)
        }

        requestAllPermissions()
        startBackgroundService()

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.addJavascriptInterface(NeythanAndroidBridge(), "NeythanAndroid")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }
        }

        webView.loadUrl(SERVER_URL)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.05f)
            ttsReady = true
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startBackgroundService() {
        val intent = Intent(this, NeythanBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    inner class NeythanAndroidBridge {

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                if (ttsReady && text.isNotBlank()) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NEYTHAN_UTTERANCE")
                }
            }
        }

        @JavascriptInterface
        fun stopSpeak() {
            runOnUiThread {
                tts?.stop()
            }
        }

        @JavascriptInterface
        fun playSpotify(query: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Spotify no está instalado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun startBubble() {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startService(Intent(this@MainActivity, FloatingService::class.java))
                }
            }
        }

        @JavascriptInterface
        fun getNotifications(): String {
            return NeythanNotificationListener.obtenerResumenNotificaciones()
        }

        @JavascriptInterface
        fun openApp(appName: String) {
            runOnUiThread {
                val pkg = when (appName.lowercase()) {
                    "whatsapp" -> "com.whatsapp"
                    "instagram" -> "com.instagram.android"
                    "spotify" -> "com.spotify.music"
                    else -> ""
                }
                if (pkg.isNotEmpty()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this@MainActivity, "Aplicación no encontrada: $appName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
