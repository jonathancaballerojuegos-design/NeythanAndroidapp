package com.jonathan.neythan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // URL de su servidor activo en Render
    private val RENDER_SERVER_URL = "https://neythanproyect-cloud.onrender.com"

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            iniciarServicioSegundoPlano()
            cargarInterfazNeythan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fondo negro puro AMOLED y pantalla activa durante interacción
        window.decorView.setBackgroundColor(Color.BLACK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this)
        setContentView(webView)

        configurarWebView()
        verificarPermisos()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        webView.setBackgroundColor(Color.BLACK)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        // Autoriza automáticamente el micrófono para las ondas de voz y wake word
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }
    }

    private fun verificarPermisos() {
        val permisosNecesarios = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisosNecesarios.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val faltantes = permisosNecesarios.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isEmpty()) {
            iniciarServicioSegundoPlano()
            cargarInterfazNeythan()
        } else {
            requestPermissionsLauncher.launch(faltantes.toTypedArray())
        }
    }

    private fun cargarInterfazNeythan() {
        if (webView.url == null) {
            webView.loadUrl(RENDER_SERVER_URL)
        }
    }

    private fun iniciarServicioSegundoPlano() {
        val serviceIntent = Intent(this, NeythanBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
