package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"
    private val AUDIO_PERMISSION_CODE = 1001
    private val OVERLAY_PERMISSION_CODE = 1002

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Puente nativo para que la interfaz web controle apps, Spotify y burbuja
        webView.addJavascriptInterface(NeythanAndroidBridge(this), "NeythanNativo")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }

        verificarPermisosEIniciar()
    }

    private fun verificarPermisosEIniciar() {
        val permisosFaltantes = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permisosFaltantes.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisosFaltantes.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permisosFaltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisosFaltantes.toTypedArray(), AUDIO_PERMISSION_CODE)
        } else {
            iniciarServiciosYCargar()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        iniciarServiciosYCargar()
    }

    private fun iniciarServiciosYCargar() {
        val bgIntent = Intent(this, NeythanBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bgIntent)
        } else {
            startService(bgIntent)
        }
        webView.loadUrl(SERVER_URL)
    }

    inner class NeythanAndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun reproducirSpotify(cancionOArtista: String) {
            runOnUiThread {
                try {
                    val query = Uri.encode(cancionOArtista)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(cancionOArtista)}"))
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(webIntent)
                }
            }
        }

        @JavascriptInterface
        fun abrirAplicacion(nombrePaquete: String) {
            runOnUiThread {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(nombrePaquete)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "Aplicacion no encontrada en el dispositivo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun activarBurbujaFlotante() {
            runOnUiThread {
                if (!Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "Autorice mostrar sobre otras apps para activar el nucleo flotante", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    val floatIntent = Intent(context, FloatingService::class.java)
                    context.startService(floatIntent)
                }
            }
        }
    }
}
