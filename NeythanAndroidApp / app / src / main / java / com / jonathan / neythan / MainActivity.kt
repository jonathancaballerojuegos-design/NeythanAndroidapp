package com.jonathan.neythan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISOS_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 80)
            setBackgroundColor(0xFF050811.toInt())
        }

        val titulo = TextView(this).apply {
            text = "NEYTHAN ANDROID"
            textSize = 24f
            setTextColor(0xFF00E5FF.toInt())
        }

        val desc = TextView(this).apply {
            text = "\n1. Concede permisos de Micrófono, Cámara y Notificaciones.\n2. Activa 'Mostrar sobre otras apps'.\n3. Inicia el asistente flotante en segundo plano."
            textSize = 15f
            setTextColor(0xFFE0F7FA.toInt())
        }

        val btnPermisos = Button(this).apply {
            text = "Conceder Permisos Base"
            setOnClickListener { solicitarPermisosBase() }
        }

        val btnOverlay = Button(this).apply {
            text = "Activar Superposición en Pantalla"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Superposición ya activada", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnIniciar = Button(this).apply {
            text = "Iniciar Neythan Flotante"
            setOnClickListener { iniciarServicioFlotante() }
        }

        layout.addView(titulo)
        layout.addView(desc)
        layout.addView(btnPermisos)
        layout.addView(btnOverlay)
        layout.addView(btnIniciar)
        setContentView(layout)
    }

    private fun solicitarPermisosBase() {
        val permisos = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permisos.toTypedArray(), PERMISOS_CODE)
    }

    private fun iniciarServicioFlotante() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero activa la superposición sobre otras apps", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Neythan activo en segundo plano", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
