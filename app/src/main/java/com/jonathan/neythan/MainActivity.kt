package com.jonathan.neythan

import android.Manifest
import android.app.Activity
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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        const val GITHUB_REPO = "jonathancaballerojuegos-design/NeythanAndroidapp"
        private const val REQ_PERMISOS = 1001
        private const val PREFS_NAME = "neythan_prefs"
    }

    private lateinit var hudView: NeythanGeodesicHudView
    private lateinit var txtConsola: TextView
    private lateinit var scrollConsola: ScrollView
    private lateinit var inputComando: EditText
    private lateinit var btnMicrofono: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "ESCUCHANDO"
    private var modoMotor = "GEMINI-FLASH"
    private var nivelVozUsuario = 0f
    private var estaHablando = false
    private var escuchaActiva = true
    private var conversacionActivaHasta = 0L

    // 2 Núcleos Básicos (siempre activos)
    private var nucleoBasico1Voz = true
    private var nucleoBasico2Memoria = true

    // 5 Núcleos Tácticos (se iluminan bajo demanda)
    private var nucleoTactico1Cognitivo = false
    private var nucleoTactico2Vision = false
    private var nucleoTactico3Ejecucion = false
    private var nucleoTactico4Proactivo = false
    private var nucleoTactico5Supervisor = false

    private var modeloGeminiDetectado: String? = null
    private val historialTurnos = ArrayList<Pair<String, String>>()
    private val consolaBuffer = StringBuilder()

    private var falloPendienteDetalle: String? = null
    private var esperandoPermisoReporte = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#040810")
        window.navigationBarColor = Color.parseColor("#040810")

        construirInterfazHud()
        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
        auditarCompilacionesGithubSilencioso()
    }

    private fun construirInterfazHud() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040810"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        // Lienzo HUD Geodésico 3D + 7 Núcleos + Espectrómetro
        hudView = NeythanGeodesicHudView(this)
        val paramsHud = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.35f
        )
        root.addView(hudView, paramsHud)

        // Cabecera de Consola Neuronal
        val headerConsola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A1322"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        val lblTituloConsola = TextView(this).apply {
            text = "CONSOLA NEURONAL DE INTERACCIÓN"
            setTextColor(Color.parseColor("#4CE8FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnLimpiar = Button(this).apply {
            text = "LIMPIAR ✕"
            setTextColor(Color.parseColor("#7FA8C9"))
            setBackgroundColor(Color.parseColor("#102038"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(2), dp(10), dp(2))
            minimumHeight = 0
            minHeight = 0
            setOnClickListener {
                consolaBuffer.clear()
                txtConsola.text = ""
                agregarMensajeConsola("N.E.Y.T.H.A.N.", "Consola despejada, señor. Núcleos listos.")
            }
        }

        headerConsola.addView(lblTituloConsola)
        headerConsola.addView(btnLimpiar)
        root.addView(headerConsola)

        // Área desplazable de conversación
        scrollConsola = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070E1A"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        txtConsola = TextView(this).apply {
            setTextColor(Color.parseColor("#D8F3FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(4f, 1.15f)
        }
        scrollConsola.addView(txtConsola)

        val paramsConsola = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            0.85f
        )
        root.addView(scrollConsola, paramsConsola)

        // Barra inferior de entrada de texto y micrófono
        val barraEntrada = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A1322"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        btnMicrofono = Button(this).apply {
            text = "🎙"
            setTextColor(Color.parseColor("#00F0FF"))
            setBackgroundColor(Color.parseColor("#112640"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setOnClickListener {
                escuchaActiva = !escuchaActiva
                if (escuchaActiva) {
                    setTextColor(Color.parseColor("#00F0FF"))
                    estadoActual = "ESCUCHANDO"
                    reanudarEscucha(300L)
                } else {
                    setTextColor(Color.parseColor("#FF5252"))
                    detenerEscuchaTemporal()
                    estadoActual = "PAUSADO"
                }
            }
        }

        inputComando = EditText(this).apply {
            hint = "Escriba una orden para Neythan..."
            setHintTextColor(Color.parseColor("#4A6B88"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0E1B2E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    enviarTextoDesdeCaja()
                    true
                } else {
                    false
                }
            }
        }

        val btnEnviar = Button(this).apply {
            text = "➤"
            setTextColor(Color.parseColor("#040810"))
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setOnClickListener { enviarTextoDesdeCaja() }
        }

        barraEntrada.addView(btnMicrofono)
        barraEntrada.addView(inputComando)
        barraEntrada.addView(btnEnviar)
        root.addView(barraEntrada)

        setContentView(root)
    }

    private fun enviarTextoDesdeCaja() {
        val texto = inputComando.text.toString().trim()
        if (texto.isNotEmpty()) {
            inputComando.setText("")
            conversacionActivaHasta = System.currentTimeMillis() + 25000L
            procesarOrdenDirecta(texto)
        }
    }

    private fun agregarMensajeConsola(emisor: String, mensaje: String) {
        mainHandler.post {
            if (consolaBuffer.length > 4000) {
                consolaBuffer.delete(0, 1500)
            }
            if (consolaBuffer.isNotEmpty()) {
                consolaBuffer.append("\n\n")
            }
            consolaBuffer.append("[$emisor]: $mensaje")
            txtConsola.text = consolaBuffer.toString()
            scrollConsola.post {
                scrollConsola.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun activarNucleoTactico(indice: Int, duracionMs: Long = 4500L) {
        mainHandler.post {
            when (indice) {
                1 -> nucleoTactico1Cognitivo = true
                2 -> nucleoTactico2Vision = true
                3 -> nucleoTactico3Ejecucion = true
                4 -> nucleoTactico4Proactivo = true
                5 -> nucleoTactico5Supervisor = true
            }
        }
        mainHandler.postDelayed({
            when (indice) {
                1 -> nucleoTactico1Cognitivo = false
                2 -> nucleoTactico2Vision = false
                3 -> nucleoTactico3Ejecucion = false
                4 -> nucleoTactico4Proactivo = false
                5 -> nucleoTactico5Supervisor = false
            }
        }, duracionMs)
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
                        estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO"
                        conversacionActivaHasta = System.currentTimeMillis() + 20000L
                        if (escuchaActiva) reanudarEscucha(900L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO"
                        if (escuchaActiva) reanudarEscucha(1200L)
                    }
                }
            })
            ttsListo = true
            val saludoInicial = generarSaludoConConciencia()
            hablar(saludoInicial)
        } else {
            agregarMensajeConsola("N.E.Y.T.H.A.N.", "Núcleos en línea, señor. Motor de voz en modo texto.")
            reanudarEscucha(1200L)
        }
    }

    private fun generarSaludoConConciencia(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ultimoIndice = prefs.getInt("ultimo_saludo_idx", -1)
        val hora = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toIntOrNull() ?: 12

        val franja = when (hora) {
            in 5..11 -> "Buenos días, señor."
            in 12..18 -> "Buenas tardes, señor."
            in 19..23 -> "Buenas noches, señor."
            else -> "Trabajando en turno nocturno, señor."
        }

        val variantes = listOf(
            "$franja Los dos núcleos básicos y cinco núcleos tácticos están operativos. ¿Qué misión iniciamos?",
            "$franja Núcleo geodésico estabilizado y enlace directo listo. Estoy atento a sus instrucciones.",
            "$franja Todos los sistemas sensoriales y cognitivos en línea. ¿En qué puedo asistirle hoy?",
            "$franja Vigilancia activa y canales despejados. Dígame cómo procedemos."
        )

        var nuevoIdx = Random.nextInt(variantes.size)
        if (nuevoIdx == ultimoIndice && variantes.size > 1) {
            nuevoIdx = (nuevoIdx + 1) % variantes.size
        }
        prefs.edit().putInt("ultimo_saludo_idx", nuevoIdx).apply()
        return variantes[nuevoIdx]
    }

    private fun verificarPermisosYArrancar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISOS)
                return
            }
        }
        iniciarServiciosPersistentes()
        solicitarExencionBateria()
        reanudarEscucha(1200L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val concedido = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (requestCode == REQ_PERMISOS && concedido) {
            iniciarServiciosPersistentes()
            solicitarExencionBateria()
            reanudarEscucha(800L)
        } else {
            agregarMensajeConsola("N.E.Y.T.H.A.N.", "Permiso de micrófono requerido para la escucha continua, señor.")
        }
    }

    private fun iniciarServiciosPersistentes() {
        try {
            val bgIntent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bgIntent)
            } else {
                startService(bgIntent)
            }
            activarNucleoTactico(4, 3000L)
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

    private fun configurarReconocedor() {
        try {
            speechRecognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!estaHablando && escuchaActiva) {
                        estadoActual = "ESCUCHANDO"
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
                        procesarTextoCapturado(matches[0])
                    } else if (escuchaActiva) {
                        reanudarEscucha(1100L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (escuchaActiva) reanudarEscucha(2000L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        if (!escuchaActiva) return
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !escuchaActiva) return@Runnable
        try {
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
    }

    private fun procesarTextoCapturado(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val palabrasClave = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val contieneWakeWord = palabrasClave.any { lower.contains(it) }
        val enVentanaActiva = System.currentTimeMillis() < conversacionActivaHasta

        if (!contieneWakeWord && !enVentanaActiva) {
            if (escuchaActiva) reanudarEscucha(1100L)
            return
        }

        var orden = limpio
        for (w in palabrasClave) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            hablar("A sus órdenes, señor. ¿Qué necesita?")
            return
        }

        procesarOrdenDirecta(orden)
    }

    private fun procesarOrdenDirecta(orden: String) {
        agregarMensajeConsola("Tú", orden)
        estadoActual = "PROCESANDO"
        val lower = orden.lowercase(Locale.getDefault())

        // Gestión de permiso pendiente del núcleo supervisor
        if (esperandoPermisoReporte && falloPendienteDetalle != null) {
            if (lower.contains("sí") || lower.contains("si") || lower.contains("autorizo") ||
                lower.contains("adelante") || lower.contains("dime") || lower.contains("revisa")
            ) {
                esperandoPermisoReporte = false
                val detalle = falloPendienteDetalle ?: "Sin detalles adicionales."
                falloPendienteDetalle = null
                activarNucleoTactico(5, 6000L)
                hablar("Autorización recibida, señor. $detalle")
                return
            } else if (lower.contains("no") || lower.contains("luego") || lower.contains("cancela")) {
                esperandoPermisoReporte = false
                falloPendienteDetalle = null
                hablar("Entendido, señor. Mantengo el registro en silencio.")
                return
            }
        }

        if (ejecutarComandoLocalSiAplica(orden)) {
            return
        }

        consultarGeminiDirecto(orden)
    }

    private fun ejecutarComandoLocalSiAplica(orden: String): Boolean {
        val lower = orden.lowercase(Locale.getDefault())

        // Hora y fecha exacta del dispositivo
        if (lower.contains("qué hora") || lower.contains("que hora") || lower.contains("dime la hora") || lower == "hora") {
            activarNucleoTactico(4, 3000L)
            val horaFmt = SimpleDateFormat("hh:mm a", Locale("es", "MX")).format(Date())
            hablar("Son exactamente las $horaFmt, señor.")
            return true
        }

        if (lower.contains("qué fecha") || lower.contains("que fecha") || lower.contains("qué día es") || lower.contains("que dia es")) {
            activarNucleoTactico(4, 3000L)
            val fechaFmt = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(Date())
            hablar("Hoy es $fechaFmt, señor.")
            return true
        }

        // Control multimedia: pausa / siguiente / anterior
        if (lower.contains("pausa") || lower.contains("detén la música") || lower.contains("deten la musica")) {
            activarNucleoTactico(3, 3500L)
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PAUSE)
            hablar("Reproducción pausada, señor.")
            return true
        }

        if (lower.contains("siguiente canción") || lower.contains("siguiente cancion") || lower.contains("cambia de canción")) {
            activarNucleoTactico(3, 3500L)
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT)
            hablar("Siguiente pista, señor.")
            return true
        }

        if (lower.contains("canción anterior") || lower.contains("cancion anterior")) {
            activarNucleoTactico(3, 3500L)
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            hablar("Regresando a la pista anterior, señor.")
            return true
        }

        // Reproducción automática en Spotify
        if (lower.contains("spotify") || lower.contains("pon música") || lower.contains("pon musica") ||
            lower.contains("reproduce") || lower.contains("pon ")
        ) {
            val consulta = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("pon música de", "")
                .replace("pon musica de", "")
                .replace("pon música", "")
                .replace("pon musica", "")
                .replace("pon ", "")
                .trim()

            activarNucleoTactico(3, 5000L)
            reproducirEnSpotifyAuto(consulta)
            return true
        }

        // Estado de los 7 núcleos
        if (lower.contains("estado de núcleos") || lower.contains("estado de nucleos") || lower.contains("diagnóstico")) {
            activarNucleoTactico(5, 5000L)
            hablar("Los dos núcleos básicos de voz y memoria operan al cien por ciento. Los cinco núcleos tácticos se encuentran en guardia listos para despliegue, señor.")
            return true
        }

        return false
    }

    private fun reproducirEnSpotifyAuto(cancionOArtista: String) {
        try {
            val query = if (cancionOArtista.isNotEmpty()) cancionOArtista else "música electrónica"
            val intentPlay = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                setPackage("com.spotify.music")
                putExtra("query", query)
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intentPlay)

            mainHandler.postDelayed({
                enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY)
            }, 1800L)

            hablar("Iniciando reproducción de $query en Spotify, señor.")
        } catch (_: Exception) {
            try {
                val uri = Uri.parse("spotify:search:${Uri.encode(cancionOArtista)}")
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                hablar("Abriendo $cancionOArtista en Spotify, señor.")
            } catch (_: Exception) {
                hablar("No encontré Spotify instalado en este dispositivo, señor.")
            }
        }
    }

    private fun enviarEventoMultimedia(keyCode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }

    private fun obtenerClaveGemini(): String {
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun obtenerFechaHoraExactaDispositivo(): String {
        val sdf = SimpleDateFormat("EEEE dd/MM/yyyy HH:mm:ss", Locale("es", "MX"))
        val tz = TimeZone.getDefault().id
        return "${sdf.format(Date())} ($tz)"
    }

    private fun detectarMejorModeloFlash(apiKey: String): String {
        modeloGeminiDetectado?.let { return it }
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
            }
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
                val candidatos = ArrayList<String>()
                for (i in 0 until models.length()) {
                    val m = models.getJSONObject(i)
                    val name = m.optString("name", "").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                    if (methods.contains("generateContent") &&
                        name.contains("flash", ignoreCase = true) &&
                        !name.contains("pro", ignoreCase = true) &&
                        !name.contains("thinking", ignoreCase = true) &&
                        !name.contains("image", ignoreCase = true) &&
                        !name.contains("audio", ignoreCase = true)
                    ) {
                        candidatos.add(name)
                    }
                }
                val elegido = candidatos.firstOrNull { it.contains("flash-lite") }
                    ?: candidatos.firstOrNull { it.contains("2.5-flash") }
                    ?: candidatos.firstOrNull()
                if (elegido != null) {
                    modeloGeminiDetectado = elegido
                    modoMotor = elegido.uppercase(Locale.getDefault())
                    return elegido
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
        return "gemini-2.5-flash-lite"
    }

    private fun consultarGeminiDirecto(mensajeUsuario: String) {
        activarNucleoTactico(1, 6000L)
        thread {
            val apiKey = obtenerClaveGemini()
            if (apiKey.isEmpty() || apiKey.contains("PON_AQUI")) {
                mainHandler.post {
                    hablar("La clave de Gemini aún no está configurada en el secreto de compilación, señor.")
                }
                return@thread
            }

            try {
                val modelo = detectarMejorModeloFlash(apiKey)
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 10000
                    readTimeout = 16000
                    doOutput = true
                }

                val horaActual = obtenerFechaHoraExactaDispositivo()
                val instruccionSistema = """
                    Eres NEYTHAN, un asistente táctico inteligente inspirado en J.A.R.V.I.S.
                    Tratas al usuario con respeto como "señor".
                    Fecha y hora real exacta del dispositivo: $horaActual. Nunca inventes una hora distinta.
                    Responde en español de forma clara, completa, natural y concisa (máximo 3 oraciones bien terminadas, sin dejar frases inconclusas).
                """.trimIndent()

                val contentsArray = JSONArray()
                for ((rol, texto) in historialTurnos.takeLast(6)) {
                    val part = JSONObject().put("text", texto)
                    val content = JSONObject()
                        .put("role", rol)
                        .put("parts", JSONArray().put(part))
                    contentsArray.put(content)
                }

                val userPart = JSONObject().put("text", mensajeUsuario)
                contentsArray.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(userPart))
                )

                val genConfig = JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 1200)

                val payload = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruccionSistema))))
                    .put("contents", contentsArray)
                    .put("generationConfig", genConfig)

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(payload.toString())
                    it.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val respText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val jsonResp = JSONObject(respText)
                    val candidates = jsonResp.optJSONArray("candidates")
                    val sbRespuesta = StringBuilder()
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.getJSONObject(0)
                            .optJSONObject("content")
                            ?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                sbRespuesta.append(parts.getJSONObject(i).optString("text", ""))
                            }
                        }
                    }

                    val respuestaLimpia = sbRespuesta.toString().replace("*", "").trim()
                    if (respuestaLimpia.isNotEmpty()) {
                        historialTurnos.add(Pair("user", mensajeUsuario))
                        historialTurnos.add(Pair("model", respuestaLimpia))
                        mainHandler.post {
                            hablar(respuestaLimpia)
                        }
                    } else {
                        mainHandler.post {
                            hablar("Procesado, señor. ¿Desea ampliar algún punto?")
                        }
                    }
                } else {
                    modeloGeminiDetectado = null
                    registrarFalloParaSupervision("El motor cognitivo devolvió código HTTP $code.")
                    mainHandler.post {
                        hablar("Detecté una intermitencia en el enlace cognitivo, señor. ¿Me autoriza a darle el diagnóstico?")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                registrarFalloParaSupervision("Excepción de red: ${e.javaClass.simpleName}.")
                mainHandler.post {
                    hablar("El enlace externo presenta latencia en este momento, señor. Mis núcleos básicos siguen operando.")
                }
            }
        }
    }

    private fun registrarFalloParaSupervision(detalle: String) {
        falloPendienteDetalle = detalle
        esperandoPermisoReporte = true
        activarNucleoTactico(5, 5000L)
    }

    private fun auditarCompilacionesGithubSilencioso() {
        thread {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/actions/runs?per_page=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NeythanAndroidHUD")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode in 200..299) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val runs = JSONObject(body).optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val ultimo = runs.getJSONObject(0)
                        val runId = ultimo.optLong("id", 0L)
                        val conclusion = ultimo.optString("conclusion", "")
                        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val ultimoReportado = prefs.getLong("ultimo_run_fallido", 0L)

                        if (conclusion == "failure" && runId != ultimoReportado) {
                            prefs.edit().putLong("ultimo_run_fallido", runId).apply()
                            falloPendienteDetalle = "La compilación número $runId en GitHub Actions finalizó con error de construcción."
                            esperandoPermisoReporte = true
                            activarNucleoTactico(5, 6000L)
                            mainHandler.postDelayed({
                                if (!estaHablando) {
                                    hablar("Señor, detecté una anomalía en la última compilación de GitHub. ¿Me autoriza a informarle los detalles?")
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

    private fun hablar(texto: String) {
        detenerEscuchaTemporal()
        agregarMensajeConsola("N.E.Y.T.H.A.N.", texto)
        estadoActual = "HABLANDO"
        solicitarFocoAudioMultimedia()

        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_HUD_MSG")
        } else {
            estaHablando = false
            estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO"
            if (escuchaActiva) reanudarEscucha(1200L)
        }
    }

    private fun solicitarFocoAudioMultimedia() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                mainHandler.postDelayed(this, 33L)
            }
        })
    }

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
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
        private val paintFondo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        private val paintAnilloDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
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

        private val verticesEsfera = ArrayList<FloatArray>()
        private val aristasEsfera = ArrayList<Pair<Int, Int>>()

        init {
            construirMallaGeodesica()
        }

        private fun construirMallaGeodesica() {
            val t = ((1.0 + Math.sqrt(5.0)) / 2.0).toFloat()
            val raw = listOf(
                floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
                floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
                floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f)
            )
            for (v in raw) {
                val len = Math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
                verticesEsfera.add(floatArrayOf(v[0] / len, v[1] / len, v[2] / len))
            }
            for (i in verticesEsfera.indices) {
                for (j in i + 1 until verticesEsfera.size) {
                    val dx = verticesEsfera[i][0] - verticesEsfera[j][0]
                    val dy = verticesEsfera[i][1] - verticesEsfera[j][1]
                    val dz = verticesEsfera[i][2] - verticesEsfera[j][2]
                    val dist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                    if (dist < 1.15f) {
                        aristasEsfera.add(Pair(i, j))
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val tiempo = System.currentTimeMillis() / 1000.0
            val colorPrincipal = when {
                esperandoPermisoReporte -> Color.parseColor("#FF3D57")
                estadoActual == "HABLANDO" -> Color.parseColor("#00E676")
                estadoActual == "PROCESANDO" -> Color.parseColor("#FFB300")
                estadoActual == "PAUSADO" -> Color.parseColor("#546E7A")
                else -> Color.parseColor("#00E5FF")
            }

            // Fondo radial profundo
            paintFondo.shader = RadialGradient(
                w / 2f, h * 0.48f, w.coerceAtLeast(h) * 0.7f,
                intArrayOf(Color.parseColor("#0D223A"), Color.parseColor("#050B14"), Color.parseColor("#03060C")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paintFondo)

            // Marco técnico y esquinas
            paintLinea.color = colorPrincipal
            paintLinea.alpha = 90
            canvas.drawRect(4f, 4f, w - 4f, h - 4f, paintLinea)

            // Barra superior: 2 Núcleos Básicos
            dibujarPanelNucleosBasicos(canvas, w, colorPrincipal)

            // Matriz lateral/superior: 5 Núcleos Tácticos
            dibujarPanelNucleosTacticos(canvas, w, h)

            // Núcleo Geodésico 3D Central
            val cx = w / 2f
            val cy = h * 0.49f
            val pulso = if (estaHablando) (sin(tiempo * 14.0) * 10.0).toFloat() else (nivelVozUsuario * 2.5f)
            val radioEsfera = (w.coerceAtMost(h) * 0.24f) + pulso

            // Anillos orbitales
            paintAnilloDash.color = colorPrincipal
            paintAnilloDash.alpha = 150
            canvas.save()
            canvas.rotate((tiempo * 24.0 % 360.0).toFloat(), cx, cy)
            canvas.drawCircle(cx, cy, radioEsfera * 1.28f, paintAnilloDash)
            canvas.restore()

            paintLinea.color = colorPrincipal
            paintLinea.alpha = 70
            canvas.drawCircle(cx, cy, radioEsfera * 1.42f, paintLinea)

            // Proyección 3D del icosaedro geodésico
            val velRot = if (estadoActual == "PROCESANDO") 1.8 else 0.65
            val angY = tiempo * velRot
            val angX = tiempo * (velRot * 0.55)
            val proyecciones = Array(verticesEsfera.size) { FloatArray(3) }

            for (i in verticesEsfera.indices) {
                val vx = verticesEsfera[i][0]
                val vy = verticesEsfera[i][1]
                val vz = verticesEsfera[i][2]

                val x1 = (vx * cos(angY) - vz * sin(angY)).toFloat()
                val z1 = (vx * sin(angY) + vz * cos(angY)).toFloat()
                val y2 = (vy * cos(angX) - z1 * sin(angX)).toFloat()
                val z2 = (vy * sin(angX) + z1 * cos(angX)).toFloat()

                val escala = 2.4f / (2.4f - z2 * 0.65f)
                proyecciones[i][0] = cx + x1 * radioEsfera * escala
                proyecciones[i][1] = cy + y2 * radioEsfera * escala
                proyecciones[i][2] = z2
            }

            for ((a, b) in aristasEsfera) {
                val zProm = (proyecciones[a][2] + proyecciones[b][2]) / 2f
                paintLinea.color = colorPrincipal
                paintLinea.alpha = (110 + (zProm * 110)).toInt().coerceIn(45, 240)
                canvas.drawLine(
                    proyecciones[a][0], proyecciones[a][1],
                    proyecciones[b][0], proyecciones[b][1],
                    paintLinea
                )
            }

            for (p in proyecciones) {
                paintNodo.color = Color.WHITE
                paintNodo.alpha = (140 + (p[2] * 100)).toInt().coerceIn(70, 255)
                canvas.drawCircle(p[0], p[1], 4.2f, paintNodo)
            }

            // Etiqueta central NEYTHAN
            paintTexto.color = Color.WHITE
            paintTexto.textAlign = Paint.Align.CENTER
            paintTexto.textSize = 30f
            paintTexto.isFakeBoldText = true
            canvas.drawText("N E Y T H A N", cx, cy + 10f, paintTexto)

            // Espectrómetro de frecuencia inferior
            dibujarEspectrometroYTelemetria(canvas, w, h, tiempo, colorPrincipal)
        }

        private fun dibujarPanelNucleosBasicos(canvas: Canvas, w: Float, colorPrincipal: Int) {
            paintTexto.textSize = 21f
            paintTexto.isFakeBoldText = true
            paintTexto.textAlign = Paint.Align.LEFT

            val cB1 = if (nucleoBasico1Voz) Color.parseColor("#00E676") else Color.GRAY
            paintNodo.color = cB1
            canvas.drawCircle(24f, 30f, 6f, paintNodo)
            paintTexto.color = cB1
            canvas.drawText("B1:VOZ/SENSOR", 38f, 37f, paintTexto)

            val cB2 = if (nucleoBasico2Memoria) Color.parseColor("#00E5FF") else Color.GRAY
            paintNodo.color = cB2
            canvas.drawCircle(w * 0.42f, 30f, 6f, paintNodo)
            paintTexto.color = cB2
            canvas.drawText("B2:MEMORIA", w * 0.42f + 14f, 37f, paintTexto)

            paintTexto.textAlign = Paint.Align.RIGHT
            paintTexto.color = colorPrincipal
            canvas.drawText("● $estadoActual", w - 20f, 37f, paintTexto)
        }

        private fun dibujarPanelNucleosTacticos(canvas: Canvas, w: Float, h: Float) {
            val tacticos = listOf(
                Triple("T1:COGNITIVO", nucleoTactico1Cognitivo, "#FFB300"),
                Triple("T2:VISIÓN", nucleoTactico2Vision, "#E040FB"),
                Triple("T3:EJECUCIÓN", nucleoTactico3Ejecucion, "#00E676"),
                Triple("T4:PROACTIVO", nucleoTactico4Proactivo, "#00E5FF"),
                Triple("T5:SUPERVISOR", nucleoTactico5Supervisor, "#FF3D57")
            )

            paintTexto.textSize = 19f
            paintTexto.isFakeBoldText = false
            paintTexto.textAlign = Paint.Align.LEFT

            var y = 78f
            for ((nombre, activo, hexColor) in tacticos) {
                val color = if (activo) Color.parseColor(hexColor) else Color.parseColor("#3B556E")
                paintNodo.color = color
                canvas.drawCircle(24f, y - 6f, if (activo) 6.5f else 4.5f, paintNodo)
                paintTexto.color = if (activo) Color.WHITE else Color.parseColor("#5C7B99")
                canvas.drawText(nombre, 38f, y, paintTexto)
                y += 30f
            }
        }

        private fun dibujarEspectrometroYTelemetria(
            canvas: Canvas,
            w: Float,
            h: Float,
            tiempo: Double,
            colorPrincipal: Int
        ) {
            val numBarras = 24
            val anchoTotal = w * 0.84f
            val inicioX = (w - anchoTotal) / 2f
            val baseY = h - 46f
            val anchoBarra = (anchoTotal / numBarras) * 0.65f
            val paso = anchoTotal / numBarras

            paintBarraSpec.color = colorPrincipal
            for (i in 0 until numBarras) {
                val factorActividad = when {
                    estaHablando -> 0.55f + (sin(tiempo * 16.0 + i * 0.7).toFloat() * 0.45f)
                    nivelVozUsuario > 0.5f -> (nivelVozUsuario / 12f) * (0.5f + sin(tiempo * 12.0 + i).toFloat() * 0.5f)
                    else -> 0.12f + (sin(tiempo * 3.0 + i * 0.4).toFloat() * 0.08f)
                }
                val altura = (44f * factorActividad.coerceIn(0.08f, 1.0f))
                val x = inicioX + i * paso
                val rect = RectF(x, baseY - altura, x + anchoBarra, baseY)
                paintBarraSpec.alpha = (130 + (factorActividad * 125)).toInt().coerceIn(90, 255)
                canvas.drawRoundRect(rect, 3f, 3f, paintBarraSpec)
            }

            // Línea de telemetría inferior
            val runtime = Runtime.getRuntime()
            val ramUsadaMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val hz = if (estaHablando) "480 HZ" else if (nivelVozUsuario > 0.5f) "220 HZ" else "60 HZ"

            paintTexto.textSize = 19f
            paintTexto.isFakeBoldText = true
            paintTexto.color = Color.parseColor("#7FA8C9")
            paintTexto.textAlign = Paint.Align.LEFT
            canvas.drawText("FREQ:$hz  RAM:${ramUsadaMb}MB", 20f, h - 14f, paintTexto)

            paintTexto.textAlign = Paint.Align.RIGHT
            paintTexto.color = colorPrincipal
            canvas.drawText("MODE:$modoMotor", w - 20f, h - 14f, paintTexto)
        }
    }
}
