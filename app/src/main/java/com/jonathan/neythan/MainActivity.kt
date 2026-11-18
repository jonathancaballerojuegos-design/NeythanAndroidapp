package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"
    private val PERMISSION_REQUEST_CODE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.addJavascriptInterface(NeythanAndroidBridge(this), "NeythanNativo")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }
        }

        verificarPermisosEIniciar()
    }

    private fun verificarPermisosEIniciar() {
        val permisos = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            iniciarServiciosYCargar()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            iniciarServiciosYCargar()
        }
    }

    private fun iniciarServiciosYCargar() {
        val bgIntent = Intent(this, NeythanBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bgIntent)
        } else {
            startService(bgIntent)
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Active permiso de burbuja flotante para Neythan", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingService::class.java))
        }

        webView.loadUrl(SERVER_URL)
    }

    inner class NeythanAndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun reproducirSpotify(cancion: String) {
            runOnUiThread {
                try {
                    val uri = Uri.parse("spotify:search:${Uri.encode(cancion)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.spotify.music")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Spotify no está instalado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun abrirRedSocial(red: String, mensaje: String) {
            runOnUiThread {
                try {
                    when (red.lowercase()) {
                        "whatsapp" -> {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage("com.whatsapp")
                                putExtra(Intent.EXTRA_TEXT, mensaje)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                        "instagram" -> {
                            val launch = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                            if (launch != null) {
                                launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(launch)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo abrir $red", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun obtenerNotificacionesRecientes(): String {
            return JSONArray(NeythanNotificationListener.historialNotificaciones).toString()
        }

        @JavascriptInterface
        fun abrirAjustesNotificaciones() {
            runOnUiThread {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }

        @JavascriptInterface
        fun alternarBurbuja(activar: Boolean) {
            runOnUiThread {
                val intent = Intent(context, FloatingService::class.java)
                if (activar && Settings.canDrawOverlays(context)) {
                    context.startService(intent)
                } else {
                    context.stopService(intent)
                }
            }
        }
    }
}
