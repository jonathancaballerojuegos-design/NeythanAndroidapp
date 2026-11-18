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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // REEMPLAZA ESTA URL POR EL ENLACE EXACTO DE TU SERVICIO EN RENDER (SIN '/' AL FINAL)
    private val SERVER_URL = "https://neythanproyect-cloud.onrender.com"

    private lateinit var webView: WebView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            iniciarServicioSegundoPlano()
            cargarInterfazNeythan()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fondo negro puro AMOLED y pantalla activa mientras interactuas
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            // Mantiene la navegacion dentro de la app sin abrir Chrome externo
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }

            // Concede acceso al microfono para ondas de voz reales y activacion
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    runOnUiThread {
                        request?.grant(request.resources)
                    }
                }
            }
        }

        setContentView(webView)
        verificarPermisosEIniciar()
    }

    private fun verificarPermisosEIniciar() {
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
            requestPermissionLauncher.launch(faltantes.toTypedArray())
        }
    }

    private fun iniciarServicioSegundoPlano() {
        val serviceIntent = Intent(this, NeythanBackgroundService::class.java).apply {
            putExtra("SERVER_URL", SERVER_URL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun cargarInterfazNeythan() {
        webView.loadUrl(SERVER_URL)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
