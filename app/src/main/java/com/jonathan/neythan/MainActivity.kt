package com.jonathan.neythan

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        const val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
        private const val REQ_PERMISOS = 1001
        private const val PREFS_NAME = "neythan_core_prefs"
        private const val KEY_ULTIMO_SALUDO = "ultimo_saludo_idx"
        private const val KEY_ULTIMO_RUN_FAIL = "ultimo_run_fail_id"
        private const val GITHUB_REPO = "jonathancaballerojuegos-design/NeythanAndroidapp"
    }

    private lateinit var hudView: GeodesicHudView
    private lateinit var tvConsola: TextView
    private lateinit var scrollConsola: ScrollView
    private lateinit var etComando: EditText
    private lateinit var btnMic: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "ESCUCHANDO"
    private var nivelVozUsuario = 0f
    private var estaHablando = false
    private var conversacionActivaHasta = 0L
    private var enPrimerPlano = true

    private var modeloGeminiActivo: String? = null
    private val historialTurnos = ArrayList<Pair<String, String>>()
    private val consolaBuffer = SpannableStringBuilder()

    // Telemetría en tiempo real
    private var fpsActual = 50
    private var framesContador = 0
    private var ultimoTiempoFps = System.currentTimeMillis()
    private var hzActual = 100
    private var ramPorcentaje = 32

    // 2 Núcleos Básicos y 5 Núcleos Tácticos
    private var nucleoBasico1Voz = true
    private var nucleoBasico2Memoria = true

    private var nucleoTactico1Cognitivo = false
    private var nucleoTactico2Vision = false
    private var nucleoTactico3Ejecucion = false
    private var nucleoTactico4Proactivo = false
    private var nucleoTactico5Supervisor = false

    // Protocolo de autorización para reporte de fallos
    private var falloPendienteResumen = ""
    private var falloPendienteDetalle = ""
    private var esperandoPermisoFallo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#03070D")
        window.navigationBarColor = Color.parseColor("#03070D")

        construirInterfazGeodesica()
        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
        auditarEstadoGitHubDiscreto()
    }

    private fun construirInterfazGeodesica() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#03070D"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Lienzo superior HUD Geodésico 3D + 7 Núcleos + Espectrómetro
        hudView = GeodesicHudView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.56f
            )
        }
        root.addView(hudView)

        // 2. Panel inferior Consola Neuronal
        val panelConsola = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.44f
            ).apply {
                val m = dp(10)
                setMargins(m, dp(4), m, dp(10))
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#06101B"))
                setStroke(dp(1), Color.parseColor("#0D3B56"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // Encabezado de consola
        val headerConsola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTituloConsola = TextView(this).apply {
            text = "CONSOLA NEURONAL DE INTERACCIÓN"
            setTextColor(Color.parseColor("#00D8FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnLimpiar = TextView(this).apply {
            text = "LIMPIAR ✕"
            setTextColor(Color.parseColor("#00D8FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#091E30"))
                setStroke(dp(1), Color.parseColor("#0E4A6B"))
                cornerRadius = dp(4).toFloat()
            }
            setOnClickListener {
                consolaBuffer.clear()
                tvConsola.text = ""
                agregarMensajeConsola("N.E.Y.T.H.A.N.", "Memoria visual despejada, señor. Núcleos en línea.", "#00E676")
            }
        }

        headerConsola.addView(tvTituloConsola)
        headerConsola.addView(btnLimpiar)
        panelConsola.addView(headerConsola)

        // Línea divisoria
        val divisor = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                setMargins(0, dp(6), 0, dp(6))
            }
            setBackgroundColor(Color.parseColor("#0D3B56"))
        }
        panelConsola.addView(divisor)

        // Área desplazable de logs
        scrollConsola = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        tvConsola = TextView(this).apply {
            setTextColor(Color.parseColor("#D8F3FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(3).toFloat(), 1.0f)
        }
        scrollConsola.addView(tvConsola)
        panelConsola.addView(scrollConsola)

        // Barra inferior de entrada manual + botón micrófono
        val barraEntrada = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }

        etComando = EditText(this).apply {
            hint = "Escriba una orden para Neythan..."
            setHintTextColor(Color.parseColor("#4D7A96"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.MONOSPACE
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#040B14"))
                setStroke(dp(1), Color.parseColor("#114B6E"))
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    val cmd = text.toString().trim()
                    if (cmd.isNotEmpty()) {
                        setText("")
                        procesarOrdenDirecta(cmd)
                    }
                    true
                } else {
                    false
                }
            }
        }

        val btnEnviar = TextView(this).apply {
            text = "➤"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A253B"))
                setStroke(dp(1), Color.parseColor("#00A8FF"))
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                val cmd = etComando.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    etComando.setText("")
                    procesarOrdenDirecta(cmd)
                }
            }
        }

        btnMic = TextView(this).apply {
            text = "🎙"
            setTextColor(Color.parseColor("#00E676"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A253B"))
                setStroke(dp(1), Color.parseColor("#00E676"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener {
                conversacionActivaHasta = System.currentTimeMillis() + 25000L
                estadoActual = "ESCUCHANDO"
                reanudarEscucha(150L)
                agregarMensajeConsola("SISTEMA", "Canal de voz activado manualmente.", "#00D8FF")
            }
        }

        barraEntrada.addView(etComando)
        barraEntrada.addView(btnEnviar)
        barraEntrada.addView(btnMic)
        panelConsola.addView(barraEntrada)

        root.addView(panelConsola)
        setContentView(root)
    }

    private fun agregarMensajeConsola(autor: String, mensaje: String, colorHex: String) {
        mainHandler.post {
            if (consolaBuffer.isNotEmpty()) {
                consolaBuffer.append("\n")
            }
            val inicioAutor = consolaBuffer.length
            consolaBuffer.append("$autor: ")
            val finAutor = consolaBuffer.length
            consolaBuffer.setSpan(
                ForegroundColorSpan(Color.parseColor(colorHex)),
                inicioAutor,
                finAutor,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            consolaBuffer.setSpan(
                StyleSpan(Typeface.BOLD),
                inicioAutor,
                finAutor,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            consolaBuffer.append(mensaje)
            tvConsola.text = consolaBuffer
            scrollConsola.post {
                scrollConsola.fullScroll(View.FOCUS_DOWN)
            }
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
            tts?.setPitch(0.94f)
            tts?.setSpeechRate(1.04f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = true
                        estadoActual = "HABLANDO"
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        estadoActual = "ESCUCHANDO"
                        desactivarNucleosTacticos()
                        conversacionActivaHasta = System.currentTimeMillis() + 20000L
                        if (enPrimerPlano) {
                            reanudarEscucha(900L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        estadoActual = "ESCUCHANDO"
                        desactivarNucleosTacticos()
                        if (enPrimerPlano) {
                            reanudarEscucha(1200L)
                        }
                    }
                }
            })
            ttsListo = true
            val saludoInicial = generarSaludoConConciencia()
            hablar(saludoInicial)
        } else {
            agregarMensajeConsola("N.E.Y.T.H.A.N.", generarSaludoConConciencia(), "#00E676")
            reanudarEscucha(1200L)
        }
    }

    private fun generarSaludoConConciencia(): String {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val saludos = when {
            hora in 5..11 -> listOf(
                "Buenos días, señor. Los dos núcleos básicos y cinco tácticos están sincronizados. ¿Cuál es nuestra primera prioridad?",
                "Buen día, señor. Todos los sistemas de telemetría y voz operan al cien por ciento. ¿En qué trabajamos hoy?",
                "Saludos matutinos, señor. Núcleo geodésico estabilizado y enlace neuronal listo. A sus órdenes."
            )
            hora in 12..18 -> listOf(
                "Buenas tardes, señor. Núcleos tácticos en línea y vigilancia activa. ¿Qué misión ejecutamos ahora?",
                "A sus órdenes, señor. Espectrómetro estable y enlace directo operativo. ¿En qué puedo asistirle?",
                "Sistemas activos esta tarde, señor. Listo para procesar consultas, código o multimedia."
            )
            else -> listOf(
                "Buenas noches, señor. Núcleos operando en modo nocturno de alta precisión. ¿En qué le asisto?",
                "En línea, señor. Todos los subsistemas responden con normalidad. ¿Qué necesita revisar?",
                "Núcleo neuronal activo, señor. A la espera de sus instrucciones."
            )
        }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ultimo = prefs.getInt(KEY_ULTIMO_SALUDO, -1)
        var idx = Random.nextInt(saludos.size)
        if (idx == ultimo && saludos.size > 1) {
            idx = (idx + 1) % saludos.size
        }
        prefs.edit().putInt(KEY_ULTIMO_SALUDO, idx).apply()
        return saludos[idx]
    }

    private fun verificarPermisosYArrancar() {
        val permisosFaltantes = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permisosFaltantes.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permisosFaltantes.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permisosFaltantes.isNotEmpty()) {
            requestPermissions(permisosFaltantes.toTypedArray(), REQ_PERMISOS)
            return
        }
        iniciarServiciosSegundoPlano()
        solicitarExencionBateria()
        reanudarEscucha(1200L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS) {
            iniciarServiciosSegundoPlano()
            solicitarExencionBateria()
            reanudarEscucha(1000L)
        }
    }

    private fun iniciarServiciosSegundoPlano() {
        try {
            val bgIntent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bgIntent)
            } else {
                startService(bgIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun solicitarExencionBateria() {
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
                    if (!estaHablando) {
                        estadoActual = "ESCUCHANDO"
                        nucleoBasico1Voz = true
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
                    if (!estaHablando && enPrimerPlano) {
                        reanudarEscucha(1400L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVozUsuario = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                        procesarTextoCapturado(matches[0])
                    } else if (enPrimerPlano) {
                        reanudarEscucha(1200L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (enPrimerPlano) reanudarEscucha(1800L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !enPrimerPlano) return@Runnable
        try {
            silenciarTonosSistema(true)
            if (speechRecognizer == null) {
                configurarReconocedor()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            configurarReconocedor()
            if (enPrimerPlano) reanudarEscucha(1800L)
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

        if (!contieneWakeWord && !enVentanaActiva && !esperandoPermisoFallo) {
            if (enPrimerPlano) reanudarEscucha(1100L)
            return
        }

        var orden = limpio
        for (w in palabrasClave) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            conversacionActivaHasta = System.currentTimeMillis() + 22000L
            hablar("A sus órdenes, señor. ¿Qué necesita?")
            return
        }

        procesarOrdenDirecta(orden)
    }

    private fun procesarOrdenDirecta(orden: String) {
        agregarMensajeConsola("Tú", orden, "#00D8FF")
        estadoActual = "PROCESANDO"
        conversacionActivaHasta = System.currentTimeMillis() + 25000L
        val lower = orden.lowercase(Locale.getDefault())

        // 1. Respuesta a solicitud de permiso para reportar fallo
        if (esperandoPermisoFallo) {
            esperandoPermisoFallo = false
            if (lower.contains("sí") || lower.contains("si") || lower.contains("autorizo") ||
                lower.contains("dime") || lower.contains("adelante") || lower.contains("revisa")
            ) {
                nucleoTactico5Supervisor = true
                hablar("Autorización confirmada, señor. $falloPendienteDetalle")
            } else {
                hablar("Entendido, señor. Mantengo el informe en reserva hasta que lo indique.")
            }
            return
        }

        // 2. Consulta de estado de los 7 núcleos
        if (lower.contains("núcleo") || lower.contains("nucleo") || lower.contains("estado del sistema")) {
            nucleoTactico5Supervisor = true
            hablar(
                "Los dos núcleos básicos de voz y memoria operan de forma continua, señor. " +
                    "Los cinco núcleos tácticos —cognitivo, visión, ejecución, proactividad y supervisor— están listos bajo demanda."
            )
            return
        }

        // 3. Reloj y fecha exacta del teléfono
        if (lower.contains("qué hora") || lower.contains("que hora") || lower.contains("dime la hora") ||
            lower.contains("qué fecha") || lower.contains("que fecha") || lower.contains("qué día es")
        ) {
            nucleoTactico4Proactivo = true
            val fmtHora = SimpleDateFormat("hh:mm a", Locale("es", "MX"))
            val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX"))
            val ahora = Date()
            hablar("Son las ${fmtHora.format(ahora)}, señor. Hoy es ${fmtFecha.format(ahora)}.")
            return
        }

        // 4. Control multimedia y reproducción automática real en Spotify
        if (ejecutarComandoMultimedia(lower)) {
            return
        }

        // 5. Burbuja flotante
        if (lower.contains("burbuja") || lower.contains("flotante")) {
            nucleoTactico3Ejecucion = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                hablar("Por favor autorice el permiso de superposición en pantalla, señor.")
            } else {
                startService(Intent(this, FloatingService::class.java))
                hablar("Burbuja flotante activada sobre sus aplicaciones, señor.")
            }
            return
        }

        // 6. Consulta cognitiva directa a Gemini
        nucleoTactico1Cognitivo = true
        consultarGeminiDirecto(orden)
    }

    private fun ejecutarComandoMultimedia(lower: String): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (lower.contains("pausa") || lower.contains("detén la música") || lower.contains("deten la musica")) {
            nucleoTactico3Ejecucion = true
            enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PAUSE)
            hablar("Reproducción pausada, señor.")
            return true
        }

        if (lower.contains("continúa") || lower.contains("continua") || lower.contains("reanuda")) {
            nucleoTactico3Ejecucion = true
            enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY)
            hablar("Reanudando reproducción, señor.")
            return true
        }

        if (lower.contains("siguiente canción") || lower.contains("siguiente cancion") || lower.contains("cambia de canción")) {
            nucleoTactico3Ejecucion = true
            enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_NEXT)
            hablar("Pasando a la siguiente pista, señor.")
            return true
        }

        if (lower.contains("canción anterior") || lower.contains("cancion anterior") || lower.contains("regresa la canción")) {
            nucleoTactico3Ejecucion = true
            enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            hablar("Regresando a la pista anterior, señor.")
            return true
        }

        val esOrdenMusica = lower.contains("spotify") || lower.contains("pon música") || lower.contains("pon musica") ||
            lower.contains("reproduce") || lower.contains("pon la canción") || lower.contains("pon la cancion") ||
            lower.startsWith("pon ") || lower.contains("escuchar ")

        if (esOrdenMusica) {
            nucleoTactico3Ejecucion = true
            val consulta = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("pon música de", "")
                .replace("pon musica de", "")
                .replace("pon música", "")
                .replace("pon musica", "")
                .replace("pon la canción", "")
                .replace("pon la cancion", "")
                .replace("quiero escuchar", "")
                .replace("escuchar", "")
                .replace(Regex("^pon\\s+"), "")
                .replace("abre", "")
                .trim()

            try {
                if (consulta.isNotEmpty()) {
                    // Intent oficial de reproducción directa en Spotify sin quedarse solo en búsqueda
                    val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage("com.spotify.music")
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(SearchManager.QUERY, consulta)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(playIntent)

                    // Pulso extra PLAY tras 2.2 segundos para asegurar inicio de audio
                    mainHandler.postDelayed({
                        enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY)
                    }, 2200L)

                    hablar("Reproduciendo $consulta en Spotify, señor.")
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        mainHandler.postDelayed({
                            enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY)
                        }, 1800L)
                        hablar("Abriendo Spotify e iniciando reproducción, señor.")
                    } else {
                        hablar("No encontré Spotify instalado en este dispositivo, señor.")
                    }
                }
            } catch (_: Exception) {
                try {
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(consulta)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallback)
                    mainHandler.postDelayed({
                        enviarPulsacionMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY)
                    }, 2200L)
                    hablar("Iniciando $consulta en Spotify, señor.")
                } catch (_: Exception) {
                    hablar("No fue posible iniciar Spotify en este momento, señor.")
                }
            }
            return true
        }

        return false
    }

    private fun enviarPulsacionMedia(am: AudioManager, keyCode: Int) {
        try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
        } catch (_: Exception) {
        }
    }

    private fun auditarEstadoGitHubDiscreto() {
        thread {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/actions/runs?per_page=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Neythan-Android-Supervisor")
                    connectTimeout = 5000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val txt = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val json = JSONObject(txt)
                    val runs = json.optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val ultimo = runs.getJSONObject(0)
                        val runId = ultimo.optLong("id", 0L)
                        val runNumber = ultimo.optInt("run_number", 0)
                        val conclusion = ultimo.optString("conclusion", "")
                        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val yaReportado = prefs.getLong(KEY_ULTIMO_RUN_FAIL, -1L)

                        if (conclusion == "failure" && runId != yaReportado) {
                            prefs.edit().putLong(KEY_ULTIMO_RUN_FAIL, runId).apply()
                            falloPendienteResumen = "Detecté una anomalía en la compilación número $runNumber de GitHub."
                            falloPendienteDetalle =
                                "La compilación número $runNumber terminó con estado fallido en GitHub Actions. " +
                                "Le recomiendo revisar las líneas rojas de Gradle en el registro de acciones para aplicar el parche."
                            esperandoPermisoFallo = true
                            mainHandler.postDelayed({
                                if (!estaHablando) {
                                    nucleoTactico5Supervisor = true
                                    hablar("Señor, $falloPendienteResumen ¿Me autoriza a informarle los detalles?")
                                }
                            }, 5500L)
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun consultarGeminiDirecto(mensajeUsuario: String) {
        val apiKey = GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey.contains("PON_AQUI")) {
            hablar("Falta configurar la clave API de Gemini en los secretos de compilación, señor.")
            return
        }

        thread {
            try {
                if (modeloGeminiActivo == null) {
                    modeloGeminiActivo = descubrirModeloFlashDisponible(apiKey)
                }
                val modelo = modeloGeminiActivo ?: "gemini-2.5-flash-lite"
                val respuesta = llamarEndpointGemini(apiKey, modelo, mensajeUsuario)
                if (respuesta != null) {
                    historialTurnos.add(Pair("user", mensajeUsuario))
                    historialTurnos.add(Pair("model", respuesta))
                    while (historialTurnos.size > 10) {
                        historialTurnos.removeAt(0)
                    }
                    mainHandler.post {
                        hablar(respuesta)
                    }
                } else {
                    modeloGeminiActivo = null
                    mainHandler.post {
                        hablar("El núcleo cognitivo tardó en responder, señor. Intente formular su consulta nuevamente.")
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    hablar("Ocurrió una intermitencia en el enlace cognitivo, señor. Sigo operativo con mis núcleos básicos.")
                }
            }
        }
    }

    private fun descubrirModeloFlashDisponible(apiKey: String): String {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
            }
            if (conn.responseCode == 200) {
                val txt = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val models = JSONObject(txt).optJSONArray("models")
                val candidatos = ArrayList<String>()
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val m = models.getJSONObject(i)
                        val name = m.optString("name", "").removePrefix("models/")
                        val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                        if (methods.contains("generateContent") &&
                            name.contains("flash", ignoreCase = true) &&
                            !name.contains("pro", ignoreCase = true) &&
                            !name.contains("thinking", ignoreCase = true)
                        ) {
                            candidatos.add(name)
                        }
                    }
                }
                conn.disconnect()
                val preferido = candidatos.firstOrNull { it.contains("flash-lite") }
                    ?: candidatos.firstOrNull { it.contains("2.5-flash") }
                    ?: candidatos.firstOrNull()
                if (preferido != null) return preferido
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
        return "gemini-2.5-flash-lite"
    }

    private fun llamarEndpointGemini(apiKey: String, modelo: String, mensajeUsuario: String): String? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 10000
            readTimeout = 18000
            doOutput = true
        }

        val fmt = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, hh:mm a", Locale("es", "MX"))
        val fechaHoraReal = fmt.format(Date())

        val sysInstruction = JSONObject().apply {
            put(
                "parts",
                JSONArray().put(
                    JSONObject().put(
                        "text",
                        "Eres N.E.Y.T.H.A.N., el asistente táctico personal de Jonathan. Trátalo siempre de 'señor'. " +
                            "Operas con 2 núcleos básicos (voz/sensor y memoria) y 5 núcleos tácticos (cognitivo, visión, ejecución, proactividad y supervisor). " +
                            "Responde en español claro, directo, elegante y completo en 2 a 4 oraciones sin listas largas ni asteriscos. " +
                            "Fecha y hora real del teléfono: $fechaHoraReal."
                    )
                )
            )
        }

        val contents = JSONArray()
        for ((rol, texto) in historialTurnos) {
            contents.put(
                JSONObject().apply {
                    put("role", rol)
                    put("parts", JSONArray().put(JSONObject().put("text", texto)))
                }
            )
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
            }
        )

        val payload = JSONObject().apply {
            put("system_instruction", sysInstruction)
            put("contents", contents)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.65)
                    put("maxOutputTokens", 1200)
                }
            )
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(payload.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            conn.disconnect()
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val t = parts.getJSONObject(i).optString("text", "")
                if (t.isNotEmpty()) sb.append(t)
            }
            return sb.toString().replace("*", "").trim()
        } else {
            conn.disconnect()
            return null
        }
    }

    private fun hablar(texto: String) {
        detenerEscuchaTemporal()
        agregarMensajeConsola("N.E.Y.T.H.A.N.", texto, "#00E676")
        estadoActual = "HABLANDO"
        solicitarFocoAudioMultimedia()

        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_HUD_UTTERANCE")
        } else {
            estaHablando = false
            estadoActual = "ESCUCHANDO"
            desactivarNucleosTacticos()
            if (enPrimerPlano) reanudarEscucha(1200L)
        }
    }

    private fun desactivarNucleosTacticos() {
        nucleoTactico1Cognitivo = false
        nucleoTactico2Vision = false
        nucleoTactico3Ejecucion = false
        nucleoTactico4Proactivo = false
        nucleoTactico5Supervisor = false
    }

    private fun solicitarFocoAudioMultimedia() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.45).toInt()) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75).toInt(), 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                am.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
    }

    private fun actualizarTelemetriaSistema() {
        try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val usada = memInfo.totalMem - memInfo.availMem
            ramPorcentaje = ((usada.toDouble() / memInfo.totalMem.toDouble()) * 100.0).toInt().coerceIn(12, 95)
        } catch (_: Exception) {
        }
        hzActual = when (estadoActual) {
            "HABLANDO" -> 128 + (sin(System.currentTimeMillis() / 120.0) * 18.0).toInt()
            "PROCESANDO" -> 115 + (sin(System.currentTimeMillis() / 90.0) * 12.0).toInt()
            else -> 100 + (nivelVozUsuario * 3f).toInt()
        }
    }

    private fun iniciarBucleAnimacion() {
        mainHandler.post(object : Runnable {
            override fun run() {
                framesContador++
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoTiempoFps >= 1000L) {
                    fpsActual = framesContador.coerceIn(24, 60)
                    framesContador = 0
                    ultimoTiempoFps = ahora
                    actualizarTelemetriaSistema()
                }
                hudView.invalidate()
                mainHandler.postDelayed(this, 20L)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        enPrimerPlano = true
        if (!estaHablando) {
            reanudarEscucha(600L)
        }
    }

    override fun onPause() {
        enPrimerPlano = false
        detenerEscuchaTemporal()
        super.onPause()
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

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    inner class GeodesicHudView(context: Context) : View(context) {
        private val paintFondo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintMarco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val paintEsquina = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val paintGeodesic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        private val paintNodo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }
        private val paintBarraSpec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintChip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintChipBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val verticesEsfera = ArrayList<FloatArray>()

        init {
            // Generar malla geodésica 3D de 42 vértices
            val anillos = 5
            val segmentos = 10
            verticesEsfera.add(floatArrayOf(0f, 1f, 0f))
            for (i in 1 until anillos) {
                val phi = Math.PI * i / anillos
                val y = cos(phi).toFloat()
                val r = sin(phi).toFloat()
                for (j in 0 until segmentos) {
                    val theta = 2.0 * Math.PI * j / segmentos
                    val x = (r * cos(theta)).toFloat()
                    val z = (r * sin(theta)).toFloat()
                    verticesEsfera.add(floatArrayOf(x, y, z))
                }
            }
            verticesEsfera.add(floatArrayOf(0f, -1f, 0f))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val tiempo = System.currentTimeMillis() / 1000.0

            val colorTema = when (estadoActual) {
                "PROCESANDO" -> Color.parseColor("#FF9100")
                "HABLANDO" -> Color.parseColor("#00E676")
                "ALERTA" -> Color.parseColor("#FF1744")
                else -> Color.parseColor("#00D8FF")
            }

            // Degradado radial profundo de fondo
            paintFondo.shader = RadialGradient(
                w * 0.5f, h * 0.44f, w.coerceAtLeast(h) * 0.75f,
                intArrayOf(
                    Color.argb(42, Color.red(colorTema), Color.green(colorTema), Color.blue(colorTema)),
                    Color.parseColor("#040B14"),
                    Color.parseColor("#020509")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paintFondo)

            val pad = dp(10).toFloat()
            val rectMarco = RectF(pad, pad, w - pad, h - dp(2).toFloat())

            // Marco principal
            paintMarco.color = Color.argb(95, Color.red(colorTema), Color.green(colorTema), Color.blue(colorTema))
            canvas.drawRoundRect(rectMarco, 14f, 14f, paintMarco)

            // Esquinas tácticas brillantes
            val cornerLen = dp(18).toFloat()
            paintEsquina.color = colorTema
            // Top-Left
            canvas.drawLine(rectMarco.left, rectMarco.top + cornerLen, rectMarco.left, rectMarco.top, paintEsquina)
            canvas.drawLine(rectMarco.left, rectMarco.top, rectMarco.left + cornerLen, rectMarco.top, paintEsquina)
            // Top-Right
            canvas.drawLine(rectMarco.right - cornerLen, rectMarco.top, rectMarco.right, rectMarco.top, paintEsquina)
            canvas.drawLine(rectMarco.right, rectMarco.top, rectMarco.right, rectMarco.top + cornerLen, paintEsquina)
            // Bottom-Left
            canvas.drawLine(rectMarco.left, rectMarco.bottom - cornerLen, rectMarco.left, rectMarco.bottom, paintEsquina)
            canvas.drawLine(rectMarco.left, rectMarco.bottom, rectMarco.left + cornerLen, rectMarco.bottom, paintEsquina)
            // Bottom-Right
            canvas.drawLine(rectMarco.right - cornerLen, rectMarco.bottom, rectMarco.right, rectMarco.bottom, paintEsquina)
            canvas.drawLine(rectMarco.right, rectMarco.bottom, rectMarco.right, rectMarco.bottom - cornerLen, paintEsquina)

            // 1. Indicadores superiores: 2 Núcleos Básicos
            dibujarFilaNucleosBasicos(canvas, rectMarco.left + dp(10), rectMarco.top + dp(10), rectMarco.width() - dp(20))

            // 2. Indicadores secundarios: 5 Núcleos Tácticos
            dibujarFilaNucleosTacticos(canvas, rectMarco.left + dp(10), rectMarco.top + dp(34), rectMarco.width() - dp(20))

            // 3. Núcleo Geodésico 3D Giratorio en el centro
            val cx = w * 0.5f
            val cy = h * 0.47f
            val pulso = if (estaHablando) {
                (sin(tiempo * 14.0) * 10.0).toFloat()
            } else {
                (sin(tiempo * 3.0) * 4.0).toFloat() + (nivelVozUsuario * 2.5f)
            }
            val radioEsfera = (w.coerceAtMost(h) * 0.24f) + pulso

            // Anillo orbital exterior punteado
            paintMarco.color = Color.argb(130, Color.red(colorTema), Color.green(colorTema), Color.blue(colorTema))
            paintMarco.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), (tiempo * 45.0).toFloat())
            canvas.drawCircle(cx, cy, radioEsfera * 1.18f, paintMarco)
            paintMarco.pathEffect = null

            // Proyección 3D de vértices y aristas geodésicas
            val velRot = if (estadoActual == "PROCESANDO") 1.9 else 0.75
            val angY = tiempo * velRot
            val angX = sin(tiempo * 0.5) * 0.35
            val cosY = cos(angY).toFloat()
            val sinY = sin(angY).toFloat()
            val cosX = cos(angX).toFloat()
            val sinX = sin(angX).toFloat()

            val proyectados = ArrayList<FloatArray>(verticesEsfera.size)
            for (v in verticesEsfera) {
                val x1 = v[0] * cosY - v[2] * sinY
                val z1 = v[0] * sinY + v[2] * cosY
                val y2 = v[1] * cosX - z1 * sinX
                val z2 = v[1] * sinX + z1 * cosX

                val escala = 2.6f / (2.6f - z2)
                val sx = cx + x1 * radioEsfera * escala
                val sy = cy + y2 * radioEsfera * escala
                proyectados.add(floatArrayOf(sx, sy, z2))
            }

            // Conectar vértices cercanos formando triángulos geodésicos
            val distMax = radioEsfera * 0.72f
            val distMaxSq = distMax * distMax
            for (i in 0 until proyectados.size) {
                val p1 = proyectados[i]
                for (j in i + 1 until proyectados.size) {
                    val p2 = proyectados[j]
                    val dx = p1[0] - p2[0]
                    val dy = p1[1] - p2[1]
                    val dSq = dx * dx + dy * dy
                    if (dSq < distMaxSq) {
                        val profundidad = ((p1[2] + p2[2]) * 0.5f + 1f) * 0.5f
                        val alpha = (45 + profundidad * 165).toInt().coerceIn(30, 220)
                        paintGeodesic.color = Color.argb(
                            alpha,
                            Color.red(colorTema),
                            Color.green(colorTema),
                            Color.blue(colorTema)
                        )
                        canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintGeodesic)
                    }
                }
            }

            // Dibujar nodos luminosos
            for (p in proyectados) {
                val prof = ((p[2] + 1f) * 0.5f).coerceIn(0f, 1f)
                paintNodo.color = Color.argb(
                    (90 + prof * 165).toInt(),
                    Color.red(colorTema),
                    Color.green(colorTema),
                    Color.blue(colorTema)
                )
                canvas.drawCircle(p[0], p[1], 2.2f + prof * 2.5f, paintNodo)
            }

            // Título central N E Y T H A N y subtítulo de estado
            paintTexto.textAlign = Paint.Align.CENTER
            paintTexto.color = Color.WHITE
            paintTexto.isFakeBoldText = true
            paintTexto.textSize = dp(16).toFloat()
            canvas.drawText("N E Y T H A N", cx, cy + dp(4), paintTexto)

            paintTexto.isFakeBoldText = false
            paintTexto.color = colorTema
            paintTexto.textSize = dp(9).toFloat()
            val subEstado = when (estadoActual) {
                "PROCESANDO" -> "PROCESANDO NÚCLEO COGNITIVO"
                "HABLANDO" -> "TRANSMITIENDO SÍNTESIS VOCAL"
                else -> "ESCUCHANDO Y OPERATIVO"
            }
            canvas.drawText(subEstado, cx, cy + dp(18), paintTexto)

            // 4. Barra inferior de Espectrómetro de Frecuencia + Telemetría HZ / FPS / MEM
            val ySpecTop = rectMarco.bottom - dp(46)
            val xLeft = rectMarco.left + dp(12)

            paintTexto.textAlign = Paint.Align.LEFT
            paintTexto.color = Color.parseColor("#6BA4C4")
            paintTexto.textSize = dp(8).toFloat()
            canvas.drawText("ESPECTRÓMETRO DE FRECUENCIA", xLeft, ySpecTop, paintTexto)

            val numBarras = 22
            val anchoAreaSpec = rectMarco.width() * 0.54f
            val pasoBarra = anchoAreaSpec / numBarras
            val baseBarrasY = rectMarco.bottom - dp(10)

            for (b in 0 until numBarras) {
                val factorOnda = if (estaHablando) {
                    (sin(tiempo * 16.0 + b * 0.65) + 1.0) * 0.5
                } else {
                    (sin(tiempo * 4.5 + b * 0.5) + 1.0) * 0.22 + (nivelVozUsuario / 18.0)
                }
                val altoBarra = (dp(4) + factorOnda * dp(22)).toFloat()
                val bx = xLeft + b * pasoBarra
                paintBarraSpec.color = if (b % 3 == 0) Color.parseColor("#00E676") else colorTema
                canvas.drawRect(bx, baseBarrasY - altoBarra, bx + pasoBarra * 0.62f, baseBarrasY, paintBarraSpec)
            }

            // Telemetría derecha
            val xRight = rectMarco.right - dp(12)
            paintTexto.textAlign = Paint.Align.RIGHT
            paintTexto.color = colorTema
            paintTexto.isFakeBoldText = true
            paintTexto.textSize = dp(11).toFloat()
            canvas.drawText("$hzActual HZ", xRight, ySpecTop + dp(12), paintTexto)

            paintTexto.isFakeBoldText = false
            paintTexto.color = Color.parseColor("#8BC2E0")
            paintTexto.textSize = dp(8.5f.toInt()).toFloat()
            canvas.drawText("FPS: $fpsActual  |  MEM: $ramPorcentaje%", xRight, ySpecTop + dp(25), paintTexto)
            canvas.drawText("NÚCLEOS: 2B + 5T ONLINE", xRight, ySpecTop + dp(36), paintTexto)
        }

        private fun dibujarFilaNucleosBasicos(canvas: Canvas, x: Float, y: Float, anchoTotal: Float) {
            val etiquetas = listOf(
                Pair("B1: VOZ / SENSOR", nucleoBasico1Voz),
                Pair("B2: MEMORIA / ESTADO", nucleoBasico2Memoria)
            )
            val gap = dp(6).toFloat()
            val anchoChip = (anchoTotal - gap) / 2f
            val altoChip = dp(19).toFloat()

            for (i in etiquetas.indices) {
                val (label, activo) = etiquetas[i]
                val cx = x + i * (anchoChip + gap)
                val rect = RectF(cx, y, cx + anchoChip, y + altoChip)
                val colorActivo = Color.parseColor("#00E676")

                paintChip.color = if (activo) Color.argb(38, 0, 230, 118) else Color.parseColor("#071320")
                paintChipBorde.color = if (activo) colorActivo else Color.parseColor("#183A52")
                canvas.drawRoundRect(rect, 6f, 6f, paintChip)
                canvas.drawRoundRect(rect, 6f, 6f, paintChipBorde)

                paintTexto.textAlign = Paint.Align.CENTER
                paintTexto.color = if (activo) colorActivo else Color.parseColor("#547891")
                paintTexto.textSize = dp(8).toFloat()
                paintTexto.isFakeBoldText = true
                canvas.drawText("● $label", rect.centerX(), rect.centerY() + dp(3), paintTexto)
            }
        }

        private fun dibujarFilaNucleosTacticos(canvas: Canvas, x: Float, y: Float, anchoTotal: Float) {
            val tacticos = listOf(
                Pair("T1:COG", nucleoTactico1Cognitivo),
                Pair("T2:VIS", nucleoTactico2Vision),
                Pair("T3:EJE", nucleoTactico3Ejecucion),
                Pair("T4:PRO", nucleoTactico4Proactivo),
                Pair("T5:SUP", nucleoTactico5Supervisor)
            )
            val gap = dp(4).toFloat()
            val anchoChip = (anchoTotal - gap * (tacticos.size - 1)) / tacticos.size
            val altoChip = dp(17).toFloat()

            for (i in tacticos.indices) {
                val (label, activo) = tacticos[i]
                val cx = x + i * (anchoChip + gap)
                val rect = RectF(cx, y, cx + anchoChip, y + altoChip)
                val colorOn = Color.parseColor("#FF9100")

                paintChip.color = if (activo) Color.argb(55, 255, 145, 0) else Color.parseColor("#06111D")
                paintChipBorde.color = if (activo) colorOn else Color.parseColor("#133247")
                canvas.drawRoundRect(rect, 5f, 5f, paintChip)
                canvas.drawRoundRect(rect, 5f, 5f, paintChipBorde)

                paintTexto.textAlign = Paint.Align.CENTER
                paintTexto.color = if (activo) colorOn else Color.parseColor("#5B87A3")
                paintTexto.textSize = dp(7.5f.toInt()).toFloat()
                paintTexto.isFakeBoldText = activo
                canvas.drawText(label, rect.centerX(), rect.centerY() + dp(2.5f.toInt()), paintTexto)
            }
        }
    }
}
