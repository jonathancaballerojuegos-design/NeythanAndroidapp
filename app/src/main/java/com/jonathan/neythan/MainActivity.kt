package com.jonathan.neythan

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        private const val REQ_PERMISOS = 1001
        private const val GITHUB_USER = "jonathancaballerojuegos-design"
        private const val GITHUB_REPO = "NeythanAndroidapp"
    }

    private lateinit var hudView: NeythanGeodesicHudView
    private lateinit var scrollConsola: ScrollView
    private lateinit var tvConsola: TextView
    private lateinit var etComando: EditText
    private lateinit var btnMic: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Estado general y telemetria
    private var estadoActual = "ESCUCHANDO"
    private var subEstadoActual = "SISTEMA ACTIVO"
    private var modoActual = "GEMINI DIRECTO"
    private var nivelVozUsuario = 0f
    private var estaHablando = false
    private var escuchaActiva = true
    private var conversacionActivaHasta = 0L

    // 2 Nucleos Basicos (Siempre activos) y 5 Nucleos Tacticos (Dinamicos)
    private var nucleoBasico1Voz = true
    private var nucleoBasico2Memoria = true
    private var nucleoTactico1Cognitivo = false
    private var nucleoTactico2Vision = false
    private var nucleoTactico3Ejecucion = false
    private var nucleoTactico4Proactivo = false
    private var nucleoTactico5Supervisor = false

    // Supervision de GitHub con permiso previo
    private var falloGithubPendiente = ""
    private var esperandoConfirmacionFallo = false
    private var ultimoRunIdReportado = -1L

    // Historial en memoria (Nucleo Basico 2)
    private val historialTurnos = ArrayList<Pair<String, String>>()
    private val lineasConsola = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#040914")
        window.navigationBarColor = Color.parseColor("#040914")

        construirInterfazCompleta()
        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
        iniciarSupervisionDiscreta()
    }

    private fun construirInterfazCompleta() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040914"))
        }

        // 1. Lienzo superior: Nucleo Geodesico + 2 Basicos + 5 Tacticos + Espectrometro
        hudView = NeythanGeodesicHudView(this)
        val paramsHud = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            0.64f
        )
        root.addView(hudView, paramsHud)

        // 2. Panel inferior: Consola Neuronal y Barra de Comandos
        val panelConsola = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#071122"))
            setPadding(24, 14, 24, 18)
        }
        val paramsPanel = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            0.36f
        )

        val barraCabecera = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTituloConsola = TextView(this).apply {
            text = "CONSOLA NEURONAL DE INTERACCION"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnLimpiar = Button(this).apply {
            text = "LIMPIAR X"
            setTextColor(Color.parseColor("#80DEEA"))
            setBackgroundColor(Color.parseColor("#0D213A"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(18, 4, 18, 4)
            setOnClickListener {
                lineasConsola.clear()
                tvConsola.text = "Consola despejada, senor."
            }
        }
        barraCabecera.addView(tvTituloConsola)
        barraCabecera.addView(btnLimpiar)
        panelConsola.addView(barraCabecera)

        scrollConsola = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 10
                bottomMargin = 10
            }
        }
        tvConsola = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setLineSpacing(6f, 1.0f)
        }
        scrollConsola.addView(tvConsola)
        panelConsola.addView(scrollConsola)

        // Barra de entrada manual + boton de microfono
        val barraEntrada = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        etComando = EditText(this).apply {
            hint = "Escriba una orden para Neythan..."
            setHintTextColor(Color.parseColor("#546E7A"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0B1A30"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(22, 16, 22, 16)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    enviarTextoManual()
                    true
                } else {
                    false
                }
            }
        }

        val btnEnviar = Button(this).apply {
            text = ">"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 10
            }
            setOnClickListener { enviarTextoManual() }
        }

        btnMic = Button(this).apply {
            text = "MIC"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E676"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 10
            }
            setOnClickListener { alternarMicrofono() }
        }

        barraEntrada.addView(etComando)
        barraEntrada.addView(btnEnviar)
        barraEntrada.addView(btnMic)
        panelConsola.addView(barraEntrada)

        setContentView(root)
    }

    private fun registrarEnConsola(emisor: String, texto: String) {
        mainHandler.post {
            lineasConsola.add("$emisor: $texto")
            if (lineasConsola.size > 40) {
                lineasConsola.removeAt(0)
            }
            tvConsola.text = lineasConsola.joinToString("

")
            scrollConsola.post {
                scrollConsola.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun enviarTextoManual() {
        val texto = etComando.text.toString().trim()
        if (texto.isEmpty()) return
        etComando.setText("")
        conversacionActivaHasta = System.currentTimeMillis() + 25000L
        procesarOrdenDirecta(texto)
    }

    private fun alternarMicrofono() {
        escuchaActiva = !escuchaActiva
        if (escuchaActiva) {
            nucleoBasico1Voz = true
            btnMic.setBackgroundColor(Color.parseColor("#00E676"))
            estadoActual = "ESCUCHANDO"
            subEstadoActual = "VOZ ACTIVA"
            reanudarEscucha(300L)
        } else {
            detenerEscuchaTemporal()
            btnMic.setBackgroundColor(Color.parseColor("#FF5252"))
            estadoActual = "PAUSADO"
            subEstadoActual = "MIC SILENCIADO"
        }
    }

    private fun iniciarMotorVoz() {
        try {
            tts = TextToSpeech(this, this, "com.google.android.tts")
        } catch (_: Exception) {
            tts = TextToSpeech(this, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("es", "MX"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            tts?.setPitch(0.95f)
            tts?.setSpeechRate(1.05f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = true
                        estadoActual = "HABLANDO"
                        subEstadoActual = "SINTESIS ACTIVA"
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        apagarNucleosTacticos()
                        estadoActual = if (escuchaActiva) "ESCUCHANDO" else "EN ESPERA"
                        subEstadoActual = "SISTEMA LISTO"
                        conversacionActivaHasta = System.currentTimeMillis() + 20000L
                        if (escuchaActiva) reanudarEscucha(1200L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        apagarNucleosTacticos()
                        estadoActual = "ESCUCHANDO"
                        if (escuchaActiva) reanudarEscucha(1400L)
                    }
                }
            })
            ttsListo = true
            val saludo = generarSaludoConConciencia()
            hablar(saludo)
        } else {
            registrarEnConsola("N.E.Y.T.H.A.N.", "Núcleos en línea en modo visual, señor.")
            reanudarEscucha(1200L)
        }
    }

    private fun generarSaludoConConciencia(): String {
        val prefs = getSharedPreferences("neythan_memoria", Context.MODE_PRIVATE)
        val ultimoIdx = prefs.getInt("ultimo_saludo", -1)
        val hora = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toIntOrNull() ?: 12

        val saludos = when {
            hora in 5..11 -> listOf(
                "Buenos días, señor. Los dos núcleos básicos y cinco tácticos están en línea.",
                "Sistemas despertando con energía óptima esta mañana, señor. ¿Cuál es nuestra primera misión?",
                "Buen día, señor. Núcleo geodésico estabilizado y enlace directo activo."
            )
            hora in 12..18 -> listOf(
                "Buenas tardes, señor. Todos los núcleos tácticos están calibrados y a su disposición.",
                "Enlace directo establecido, señor. ¿En qué trabajamos esta tarde?",
                "Sistemas operativos al cien por ciento, señor. Listo para ejecutar sus órdenes."
            )
            else -> listOf(
                "Buenas noches, señor. Guardia nocturna activa con todos los núcleos sincronizados.",
                "Sistemas en línea, señor. ¿Qué necesita gestionar esta noche?",
                "Núcleo geodésico operativo y en silencio táctico, señor. A sus órdenes."
            )
        }

        var idx = (System.currentTimeMillis() % saludos.size).toInt()
        if (idx == ultimoIdx) idx = (idx + 1) % saludos.size
        prefs.edit().putInt("ultimo_saludo", idx).apply()
        return saludos[idx]
    }

    private fun apagarNucleosTacticos() {
        nucleoTactico1Cognitivo = false
        nucleoTactico2Vision = false
        nucleoTactico3Ejecucion = false
        nucleoTactico4Proactivo = false
        nucleoTactico5Supervisor = false
    }

    private fun verificarPermisosYArrancar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISOS)
                return
            }
        }
        iniciarServiciosYOptimizarBateria()
        reanudarEscucha(1200L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS && grantResults.isNotEmpty() && grantResults == PackageManager.PERMISSION_GRANTED) {
            iniciarServiciosYOptimizarBateria()
            reanudarEscucha(900L)
        } else {
            registrarEnConsola("N.E.Y.T.H.A.N.", "Permiso de micrófono requerido para la escucha continua, señor.")
        }
    }

    private fun iniciarServiciosYOptimizarBateria() {
        try {
            val bgIntent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bgIntent)
            } else {
                startService(bgIntent)
            }
        } catch (_: Exception) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun silenciarTonosSistema(silenciar: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val flag = if (silenciar) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, flag, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, flag, 0)
        } catch (_: Exception) {
        }
    }

    private fun configurarReconocedor() {
        try {
            speechRecognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!estaHablando && escuchaActiva) {
                        estadoActual = "ESCUCHANDO"
                        subEstadoActual = "CANAL DE VOZ ABIERTO"
                    }
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    nivelVozUsuario = rmsdB.coerceIn(0f, 12f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    nivelVozUsuario = 0f
                }

                override fun onError(error: Int) {
                    nivelVozUsuario = 0f
                    if (!estaHablando && escuchaActiva) {
                        reanudarEscucha(1500L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVozUsuario = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarTextoCapturado(matches)
                    } else if (escuchaActiva) {
                        reanudarEscucha(1200L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (escuchaActiva) reanudarEscucha(1800L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        if (escuchaActiva) {
            mainHandler.postDelayed(tareaEscucha, retrasoMs)
        }
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !escuchaActiva) return@Runnable
        try {
            silenciarTonosSistema(true)
            if (speechRecognizer == null) {
                configurarReconocedor()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            configurarReconocedor()
            reanudarEscucha(1800L)
        }
    }

    private fun detenerEscuchaTemporal() {
        mainHandler.removeCallbacks(tareaEscucha)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        silenciarTonosSistema(false)
    }

    private fun procesarTextoCapturado(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val palabrasClave = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val contieneWakeWord = palabrasClave.any { lower.contains(it) }
        val enVentanaActiva = System.currentTimeMillis() < conversacionActivaHasta

        if (!contieneWakeWord && !enVentanaActiva) {
            reanudarEscucha(1200L)
            return
        }

        var orden = limpio
        for (w in palabrasClave) {
            orden = orden.replace(Regex("(?i)\b$w\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            hablar("A sus órdenes, señor. ¿Qué ejecutamos?")
            return
        }

        procesarOrdenDirecta(orden)
    }

    private fun procesarOrdenDirecta(orden: String) {
        registrarEnConsola("Tú", orden)
        val lower = orden.lowercase(Locale.getDefault())

        // 0. Confirmacion de reporte de fallo en GitHub (Nucleo Tactico 5: Supervisor)
        if (esperandoConfirmacionFallo) {
            esperandoConfirmacionFallo = false
            if (lower.contains("si") || lower.contains("sí") || lower.contains("autorizo") || lower.contains("dime") || lower.contains("adelante")) {
                nucleoTactico5Supervisor = true
                hablar(falloGithubPendiente)
                falloGithubPendiente = ""
                return
            } else {
                falloGithubPendiente = ""
                hablar("Entendido, señor. Mantengo el registro en silencio.")
                return
            }
        }

        // 1. Consulta local de hora y fecha exacta (Nucleos Basicos 1 y 2)
        if (lower.contains("qué hora") || lower.contains("que hora") || lower.contains("dime la hora") || lower.contains("hora es")) {
            val horaExacta = SimpleDateFormat("hh:mm a", Locale("es", "MX")).format(Date())
            hablar("Son las $horaExacta en el reloj de su dispositivo, señor.")
            return
        }
        if (lower.contains("qué fecha") || lower.contains("que fecha") || lower.contains("qué día es") || lower.contains("que dia es")) {
            val fechaExacta = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(Date())
            hablar("Hoy es $fechaExacta, señor.")
            return
        }

        // 2. Estado de los 7 nucleos
        if (lower.contains("estado de los núcleos") || lower.contains("estado de los nucleos") || lower.contains("núcleos")) {
            nucleoTactico5Supervisor = true
            hablar("Los dos núcleos básicos de voz y memoria operan al cien por ciento, y los cinco núcleos tácticos están listos bajo demanda, señor.")
            return
        }

        // 3. Control de Spotify y Multimedia (Nucleo Tactico 3: Ejecucion)
        if (ejecutarControlMultimedia(lower)) {
            return
        }

        // 4. Burbuja Flotante (Nucleo Tactico 2: Vision/Superposicion)
        if (lower.contains("burbuja") || lower.contains("flotante")) {
            nucleoTactico2Vision = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                hablar("Autorice el permiso de superposición en pantalla para activar la burbuja, señor.")
            } else {
                startService(Intent(this, FloatingService::class.java))
                hablar("Burbuja táctica activada sobre otras aplicaciones, señor.")
            }
            return
        }

        // 5. Consulta Cognitiva Directa a Gemini (Nucleo Tactico 1: Cognitivo)
        estadoActual = "PROCESANDO"
        subEstadoActual = "NUCLEO COGNITIVO"
        nucleoTactico1Cognitivo = true
        consultarGeminiDirecto(orden)
    }

    private fun ejecutarControlMultimedia(lower: String): Boolean {
        if (lower.contains("pausa") || lower.contains("detén la música") || lower.contains("deten la musica")) {
            nucleoTactico3Ejecucion = true
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PAUSE)
            hablar("Reproducción pausada, señor.")
            return true
        }
        if (lower.contains("siguiente canción") || lower.contains("siguiente cancion") || lower.contains("cambia de canción")) {
            nucleoTactico3Ejecucion = true
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT)
            hablar("Saltando a la siguiente pista, señor.")
            return true
        }
        if (lower.contains("canción anterior") || lower.contains("cancion anterior")) {
            nucleoTactico3Ejecucion = true
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            hablar("Regresando a la pista anterior, señor.")
            return true
        }

        if (lower.contains("spotify") || lower.contains("pon música") || lower.contains("pon musica") || lower.contains("reproduce")) {
            nucleoTactico3Ejecucion = true
            val busqueda = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("pon música de", "")
                .replace("pon musica de", "")
                .replace("pon música", "")
                .replace("pon musica", "")
                .replace("pon", "")
                .replace("abre", "")
                .trim()

            try {
                if (busqueda.isNotEmpty()) {
                    val intentPlay = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage("com.spotify.music")
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(android.app.SearchManager.QUERY, busqueda)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intentPlay)
                    mainHandler.postDelayed({
                        enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY)
                    }, 1800L)
                    hablar("Reproduciendo $busqueda en Spotify, señor.")
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        mainHandler.postDelayed({
                            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY)
                        }, 1500L)
                        hablar("Abriendo Spotify y reanudando reproducción, señor.")
                    } else {
                        hablar("No encontré Spotify instalado en este dispositivo, señor.")
                    }
                }
            } catch (_: Exception) {
                try {
                    val uri = Uri.parse("spotify:search:${Uri.encode(busqueda)}")
                    val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallback)
                    hablar("Abriendo búsqueda de $busqueda en Spotify, señor.")
                } catch (_: Exception) {
                    hablar("No pude iniciar Spotify en este momento, señor.")
                }
            }
            return true
        }
        return false
    }

    private fun enviarEventoMultimedia(keyCode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }

    private fun obtenerFechaHoraExactaDispositivo(): String {
        val sdf = SimpleDateFormat("EEEE dd/MM/yyyy hh:mm:ss a", Locale("es", "MX"))
        val tz = TimeZone.getDefault().id
        return "${sdf.format(Date())} ($tz)"
    }

    private fun consultarGeminiDirecto(mensajeUsuario: String) {
        thread {
            val apiKey = try {
                BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) {
                ""
            }

            if (apiKey.isEmpty() || apiKey.contains("PON_AQUI")) {
                mainHandler.post {
                    hablar("Falta configurar su clave API en los secretos de compilación, señor.")
                }
                return@thread
            }

            val modelos = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")
            val fechaHoraReal = obtenerFechaHoraExactaDispositivo()

            val instruccionSistema = """
                Eres N.E.Y.T.H.A.N., un asistente de inteligencia artificial táctico, leal y altamente capaz estilo J.A.R.V.I.S. creado por Jonathan.
                Trata siempre al usuario como "señor".
                Fecha y hora actual exacta del teléfono: $fechaHoraReal. Usa exclusivamente este dato si te preguntan la hora o fecha.
                Responde en español de forma clara, completa, natural y concisa (máximo 3 o 4 oraciones bien cerradas) para ser leída en voz alta sin cortarse.
            """.trimIndent()

            val contentsArray = JSONArray()
            synchronized(historialTurnos) {
                for ((rol, texto) in historialTurnos.takeLast(6)) {
                    val obj = JSONObject().apply {
                        put("role", rol)
                        put("parts", JSONArray().put(JSONObject().put("text", texto)))
                    }
                    contentsArray.put(obj)
                }
            }
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
                }
            )

            val bodyJson = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruccionSistema))))
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1200)
                })
            }

            var respuestaFinal: String? = null

            for (modelo in modelos) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKey"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        connectTimeout = 10000
                        readTimeout = 15000
                        doOutput = true
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use {
                        it.write(bodyJson.toString())
                        it.flush()
                    }

                    if (conn.responseCode in 200..299) {
                        val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                        val json = JSONObject(raw)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val parts = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                ?.optJSONArray("parts")
                            if (parts != null) {
                                val sb = StringBuilder()
                                for (i in 0 until parts.length()) {
                                    val t = parts.getJSONObject(i).optString("text", "")
                                    if (t.isNotEmpty()) sb.append(t)
                                }
                                if (sb.isNotEmpty()) {
                                    respuestaFinal = sb.toString().replace("*", "").trim()
                                    conn.disconnect()
                                    break
                                }
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

            mainHandler.post {
                if (!respuestaFinal.isNullOrEmpty()) {
                    synchronized(historialTurnos) {
                        historialTurnos.add("user" to mensajeUsuario)
                        historialTurnos.add("model" to respuestaFinal!!)
                        while (historialTurnos.size > 12) historialTurnos.removeAt(0)
                    }
                    hablar(respuestaFinal!!)
                } else {
                    hablar("El enlace cognitivo experimentó una breve interferencia de red, señor. Intente nuevamente.")
                }
            }
        }
    }

    private fun iniciarSupervisionDiscreta() {
        mainHandler.postDelayed({
            thread {
                try {
                    nucleoTactico4Proactivo = true
                    val url = URL("https://api.github.com/repos/$GITHUB_USER/$GITHUB_REPO/actions/runs?per_page=1")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "NeythanAndroidHUD")
                        connectTimeout = 6000
                        readTimeout = 8000
                    }
                    if (conn.responseCode == 200) {
                        val txt = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                        val runs = JSONObject(txt).optJSONArray("workflow_runs")
                        if (runs != null && runs.length() > 0) {
                            val ultimo = runs.getJSONObject(0)
                            val runId = ultimo.optLong("id", -1L)
                            val conclusion = ultimo.optString("conclusion", "")
                            val numero = ultimo.optInt("run_number", 0)
                            if (conclusion == "failure" && runId != ultimoRunIdReportado) {
                                ultimoRunIdReportado = runId
                                falloGithubPendiente = "La ejecución número $numero en GitHub Actions finalizó con error de compilación, señor."
                                esperandoConfirmacionFallo = true
                                mainHandler.post {
                                    nucleoTactico5Supervisor = true
                                    hablar("Señor, detecté una anomalía en la última compilación de GitHub. ¿Me autoriza a informarle los detalles?")
                                }
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                } finally {
                    mainHandler.postDelayed({ nucleoTactico4Proactivo = false }, 2000L)
                }
            }
        }, 7000L)
    }

    private fun hablar(texto: String) {
        detenerEscuchaTemporal()
        registrarEnConsola("N.E.Y.T.H.A.N.", texto)
        estadoActual = "HABLANDO"

        solicitarFocoAudioMultimedia()

        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_HUD_MSG")
        } else {
            estaHablando = false
            apagarNucleosTacticos()
            estadoActual = "ESCUCHANDO"
            if (escuchaActiva) reanudarEscucha(1200L)
        }
    }

    private fun solicitarFocoAudioMultimedia() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.45).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75).toInt(), 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusReq)
            }
        } catch (_: Exception) {
        }
    }

    private fun iniciarBucleAnimacion() {
        mainHandler.post(object : Runnable {
            override fun run() {
                hudView.invalidate()
                mainHandler.postDelayed(this, 25L) // ~40 FPS fluidos
            }
        })
    }

    override fun onDestroy() {
        detenerEscuchaTemporal()
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    inner class NeythanGeodesicHudView(context: Context) : View(context) {

        private val paintLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val paintRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }

        private val vertices = ArrayList<FloatArray>()
        private val aristas = ArrayList<Pair<Int, Int>>()

        init {
            construirEsferaGeodesica()
        }

        private fun construirEsferaGeodesica() {
            val anillos = 5
            val segmentos = 10
            for (i in 0..anillos) {
                val phi = PI * i / anillos
                for (j in 0 until segmentos) {
                    val theta = 2.0 * PI * j / segmentos
                    val x = (sin(phi) * cos(theta)).toFloat()
                    val y = cos(phi).toFloat()
                    val z = (sin(phi) * sin(theta)).toFloat()
                    vertices.add(floatArrayOf(x, y, z))
                }
            }
            for (i in 0 until anillos) {
                for (j in 0 until segmentos) {
                    val actual = i * segmentos + j
                    val siguiente = i * segmentos + ((j + 1) % segmentos)
                    val abajo = (i + 1) * segmentos + j
                    val diagonal = (i + 1) * segmentos + ((j + 1) % segmentos)
                    aristas.add(actual to siguiente)
                    aristas.add(actual to abajo)
                    aristas.add(actual to diagonal)
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h * 0.48f
            val t = System.currentTimeMillis() / 1000.0

            val colorPrincipal = when {
                esperandoConfirmacionFallo -> Color.parseColor("#FF1744")
                estadoActual == "PROCESANDO" -> Color.parseColor("#FF9100")
                estadoActual == "HABLANDO" -> Color.parseColor("#00E676")
                estadoActual == "PAUSADO" -> Color.parseColor("#78909C")
                else -> Color.parseColor("#00E5FF")
            }

            // Fondo radial profundo
            paintRelleno.shader = RadialGradient(
                cx, cy, max(w, h) * 0.75f,
                Color.parseColor("#0A1D3A"),
                Color.parseColor("#040914"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paintRelleno)
            paintRelleno.shader = null

            // Barra Superior: 2 Nucleos Basicos
            dibujarNucleosBasicos(canvas, w)

            // Esquinas tacticas del visor
            dibujarEsquinasTacticas(canvas, w, h, colorPrincipal)

            // Anillos orbitales exteriores
            val radioBase = min(w, h) * 0.25f
            val pulso = (sin(t * 3.5) * 8.0).toFloat() + (nivelVozUsuario * 3.5f)
            val radioEsfera = radioBase + pulso

            paintLinea.color = colorPrincipal
            paintLinea.alpha = 70
            paintLinea.strokeWidth = 2.5f
            canvas.drawCircle(cx, cy, radioBase * 1.32f, paintLinea)

            val arcoStart = ((t * 45.0) % 360.0).toFloat()
            paintLinea.alpha = 180
            paintLinea.strokeWidth = 5f
            val ovalo = RectF(
                cx - radioBase * 1.24f, cy - radioBase * 1.24f,
                cx + radioBase * 1.24f, cy + radioBase * 1.24f
            )
            canvas.drawArc(ovalo, arcoStart, 75f, false, paintLinea)
            canvas.drawArc(ovalo, arcoStart + 180f, 75f, false, paintLinea)

            // Nucleo Geodesico 3D giratorio
            val velocidadGiro = when (estadoActual) {
                "PROCESANDO" -> 1.8
                "HABLANDO" -> 1.3
                else -> 0.55
            }
            val angY = t * velocidadGiro
            val angX = sin(t * 0.4) * 0.35
            val cosY = cos(angY).toFloat()
            val sinY = sin(angY).toFloat()
            val cosX = cos(angX).toFloat()
            val sinX = sin(angX).toFloat()

            val proyectados = Array(vertices.size) { FloatArray(3) }
            for (i in vertices.indices) {
                val v = vertices[i]
                val x1 = v[0] * cosY - v[2] * sinY
                val z1 = v[0] * sinY + v[2] * cosY
                val y2 = v[1] * cosX - z1 * sinX
                val z2 = v[1] * sinX + z1 * cosX
                proyectados[i][0] = cx + x1 * radioEsfera
                proyectados[i][1] = cy + y2 * radioEsfera
                proyectados[i][2] = z2
            }

            for ((a, b) in aristas) {
                val p1 = proyectados[a]
                val p2 = proyectados[b]
                val zProm = (p1[2] + p2[2]) * 0.5f
                val alpha = (((zProm + 1f) * 0.5f) * 180f + 35f).toInt().coerceIn(25, 220)
                paintLinea.color = colorPrincipal
                paintLinea.alpha = alpha
                paintLinea.strokeWidth = if (zProm > 0f) 2.6f else 1.2f
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintLinea)
            }

            for (p in proyectados) {
                if (p[2] > 0.1f) {
                    paintRelleno.color = Color.WHITE
                    paintRelleno.alpha = (p[2] * 210f).toInt().coerceIn(40, 230)
                    canvas.drawCircle(p[0], p[1], 3.5f, paintRelleno)
                }
            }

            // Etiqueta central N E Y T H A N
            paintTexto.textAlign = Paint.Align.CENTER
            paintTexto.color = Color.WHITE
            paintTexto.textSize = 36f
            paintTexto.isFakeBoldText = true
            canvas.drawText("N E Y T H A N", cx, cy - 6f, paintTexto)

            paintTexto.color = colorPrincipal
            paintTexto.textSize = 21f
            paintTexto.isFakeBoldText = false
            canvas.drawText("$estadoActual • $subEstadoActual", cx, cy + 28f, paintTexto)

            // Matriz de 5 Nucleos Tacticos debajo de la esfera
            dibujarNucleosTacticos(canvas, w, cy + radioBase * 1.46f)

            // Espectrometro de frecuencia y telemetria inferior
            dibujarEspectrometroYTelemetria(canvas, w, h, colorPrincipal, t)
        }

        private fun dibujarNucleosBasicos(canvas: Canvas, w: Float) {
            val yTop = 26f
            val alto = 44f
            val anchoCaja = (w - 90f) / 2f

            dibujarChipNucleo(
                canvas, 36f, yTop, anchoCaja, alto,
                "B1: VOZ / SENSOR", nucleoBasico1Voz, Color.parseColor("#00E5FF")
            )
            dibujarChipNucleo(
                canvas, 54f + anchoCaja, yTop, anchoCaja, alto,
                "B2: MEMORIA / ESTADO", nucleoBasico2Memoria, Color.parseColor("#00E676")
            )
        }

        private fun dibujarNucleosTacticos(canvas: Canvas, w: Float, yPos: Float) {
            val etiquetas = listOf(
                Triple("T1:COGN", nucleoTactico1Cognitivo, Color.parseColor("#FF9100")),
                Triple("T2:VIS", nucleoTactico2Vision, Color.parseColor("#00E5FF")),
                Triple("T3:EJEC", nucleoTactico3Ejecucion, Color.parseColor("#00E676")),
                Triple("T4:PROAC", nucleoTactico4Proactivo, Color.parseColor("#D500F9")),
                Triple("T5:SUP", nucleoTactico5Supervisor, Color.parseColor("#FF1744"))
            )
            val margen = 24f
            val espacio = 8f
            val anchoItem = (w - margen * 2f - espacio * 4f) / 5f
            for (i in etiquetas.indices) {
                val (label, activo, color) = etiquetas[i]
                val x = margen + i * (anchoItem + espacio)
                dibujarChipNucleo(canvas, x, yPos, anchoItem, 38f, label, activo, color)
            }
        }

        private fun dibujarChipNucleo(
            canvas: Canvas,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            texto: String,
            activo: Boolean,
            colorActivo: Int
        ) {
            val rect = RectF(x, y, x + w, y + h)
            paintRelleno.color = if (activo) colorActivo else Color.parseColor("#0B192E")
            paintRelleno.alpha = if (activo) 55 else 180
            canvas.drawRoundRect(rect, 10f, 10f, paintRelleno)

            paintLinea.color = if (activo) colorActivo else Color.parseColor("#263859")
            paintLinea.alpha = 220
            paintLinea.strokeWidth = 2f
            canvas.drawRoundRect(rect, 10f, 10f, paintLinea)

            paintTexto.textAlign = Paint.Align.CENTER
            paintTexto.color = if (activo) Color.WHITE else Color.parseColor("#607D8B")
            paintTexto.textSize = 18f
            paintTexto.isFakeBoldText = activo
            canvas.drawText(texto, x + w / 2f, y + h * 0.66f, paintTexto)
        }

        private fun dibujarEsquinasTacticas(canvas: Canvas, w: Float, h: Float, color: Int) {
            paintLinea.color = color
            paintLinea.alpha = 190
            paintLinea.strokeWidth = 4f
            val m = 22f
            val l = 34f
            val top = 84f
            val bottom = h - 18f
            // Superior izquierda
            canvas.drawLine(m, top, m + l, top, paintLinea)
            canvas.drawLine(m, top, m, top + l, paintLinea)
            // Superior derecha
            canvas.drawLine(w - m, top, w - m - l, top, paintLinea)
            canvas.drawLine(w - m, top, w - m, top + l, paintLinea)
            // Inferior izquierda
            canvas.drawLine(m, bottom, m + l, bottom, paintLinea)
            canvas.drawLine(m, bottom, m, bottom - l, paintLinea)
            // Inferior derecha
            canvas.drawLine(w - m, bottom, w - m - l, bottom, paintLinea)
            canvas.drawLine(w - m, bottom, w - m, bottom - l, paintLinea)
        }

        private fun dibujarEspectrometroYTelemetria(
            canvas: Canvas,
            w: Float,
            h: Float,
            color: Int,
            t: Double
        ) {
            val baseY = h - 56f
            val barras = 22
            val anchoTotal = w * 0.78f
            val inicioX = (w - anchoTotal) / 2f
            val anchoBarra = (anchoTotal / barras) * 0.65f
            val paso = anchoTotal / barras

            val intensidad = when {
                estaHablando -> 0.85f
                nivelVozUsuario > 0.5f -> (nivelVozUsuario / 10f).coerceIn(0.2f, 1.0f)
                estadoActual == "PROCESANDO" -> 0.55f
                else -> 0.15f
            }

            paintRelleno.color = color
            for (i in 0 until barras) {
                val onda = sin(t * 9.0 + i * 0.55).toFloat() * 0.5f + 0.5f
                val altoBarra = (8f + onda * 46f * intensidad)
                val x = inicioX + i * paso
                paintRelleno.alpha = (110 + onda * 130).toInt()
                canvas.drawRoundRect(
                    RectF(x, baseY - altoBarra, x + anchoBarra, baseY),
                    4f, 4f, paintRelleno
                )
            }

            val rt = Runtime.getRuntime()
            val memUsadaMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val hz = if (estaHablando) "480 HZ" else if (nivelVozUsuario > 0.5f) "320 HZ" else "60 HZ"

            paintTexto.textAlign = Paint.Align.CENTER
            paintTexto.color = Color.parseColor("#80DEEA")
            paintTexto.textSize = 19f
            paintTexto.isFakeBoldText = false
            canvas.drawText(
                "HZ: $hz  |  MEM: ${memUsadaMb}MB  |  NÚCLEOS: 2B+5T  |  $modoActual",
                w / 2f,
                h - 26f,
                paintTexto
            )
        }
    }
}
