package com.jonathan.neythan

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
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
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        const val GITHUB_OWNER = "jonathancaballerojuegos-design"
        const val GITHUB_REPO_NAME = "NeythanAndroidapp"
        const val GITHUB_REPO = "$GITHUB_OWNER/$GITHUB_REPO_NAME"
        private const val REQ_PERMISOS = 1001
        private const val REQ_SELECCIONAR_CARPETA = 1002
        private const val PREFS_NAME = "neythan_prefs"
    }

    data class ArchivoIndexadoIA(
        val nombre: String,
        val rutaOuri: String,
        val uri: Uri?,
        val mimeType: String,
        val tamanoKb: Long,
        val fechaModificacion: Long,
        val extractoTexto: String
    )

    private lateinit var hudView: NeythanGeodesicHudView
    private lateinit var txtConsola: TextView
    private lateinit var scrollConsola: ScrollView
    private lateinit var inputComando: EditText
    private lateinit var btnMicrofono: Button
    private lateinit var panelAlertaSupervisor: LinearLayout
    private lateinit var txtAlertaSupervisor: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "ESCUCHANDO"
    private var modoMotor = "GEMINI-DIRECTO"
    private var nivelVozUsuario = 0f
    private var pulsoSilabaVoz = 0f
    private var estaHablando = false
    private var escuchandoEnCurso = false
    private var escuchaActiva = true
    private var conversacionActivaHasta = 0L
    private var backoffEscuchaMs = 700L

    // 2 Núcleos Básicos (siempre activos)
    private var nucleoBasico1Voz = true
    private var nucleoBasico2Memoria = true

    // 5 Núcleos Tácticos (se iluminan bajo demanda)
    private var nucleoTactico1Cognitivo = false
    private var nucleoTactico2Vision = false
    private var nucleoTactico3Ejecucion = false
    private var nucleoTactico4Proactivo = false
    private var nucleoTactico5Supervisor = false

    private var modeloGeminiDetectado: String = "gemini-2.5-flash"
    private val historialTurnos = ArrayList<Pair<String, String>>()
    private val consolaBuffer = StringBuilder()

    // Estado de Supervisión y Notas
    private var falloPendienteDetalle: String? = null
    private var esperandoPermisoReporte = false

    // Índice local del Gestor de Archivos con IA
    private val indiceArchivosIA = ArrayList<ArchivoIndexadoIA>()
    private var ultimoArchivoEncontrado: ArchivoIndexadoIA? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#040810")
        window.navigationBarColor = Color.parseColor("#040810")

        construirInterfazHud()
        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
        indexarArchivosDispositivoSilencioso()
        auditarCompilacionesGithubSilencioso()
    }

    override fun onResume() {
        super.onResume()
        try {
            stopService(Intent(this, NeythanBackgroundService::class.java))
        } catch (_: Exception) {
        }
        if (escuchaActiva && !estaHablando) {
            destruirReconocedorSeguro()
            reanudarEscucha(700L)
        }
    }

    override fun onPause() {
        super.onPause()
        destruirReconocedorSeguro()
        if (escuchaActiva) {
            mainHandler.postDelayed({
                iniciarServiciosPersistentes()
            }, 350L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        destruirReconocedorSeguro()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    private fun construirInterfazHud() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040810"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        panelAlertaSupervisor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2A0A14"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            visibility = View.GONE
        }
        txtAlertaSupervisor = TextView(this).apply {
            setTextColor(Color.parseColor("#FF8A9B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
        }
        val filaBotonesSup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val btnAutorizarSup = Button(this).apply {
            text = "AUTORIZAR Y GUARDAR EN NOTAS"
            setTextColor(Color.parseColor("#040810"))
            setBackgroundColor(Color.parseColor("#FF5268"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setOnClickListener {
                panelAlertaSupervisor.visibility = View.GONE
                ejecutarDiagnosticoYGuardarEnNotas()
            }
        }
        val btnPosponerSup = Button(this).apply {
            text = "POSPONER"
            setTextColor(Color.parseColor("#D8F3FF"))
            setBackgroundColor(Color.parseColor("#1B2838"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
            setOnClickListener {
                panelAlertaSupervisor.visibility = View.GONE
                esperandoPermisoReporte = false
            }
        }
        filaBotonesSup.addView(btnAutorizarSup)
        filaBotonesSup.addView(btnPosponerSup)
        panelAlertaSupervisor.addView(txtAlertaSupervisor)
        panelAlertaSupervisor.addView(filaBotonesSup)
        root.addView(panelAlertaSupervisor)

        hudView = NeythanGeodesicHudView(this)
        val paramsHud = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.32f
        )
        root.addView(hudView, paramsHud)

        val headerConsola = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A1322"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val lblTituloConsola = TextView(this).apply {
            text = "CONSOLA NEURONAL + GESTOR IA"
            setTextColor(Color.parseColor("#4CE8FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnArchivosIA = Button(this).apply {
            text = "ARCHIVOS IA"
            setTextColor(Color.parseColor("#00E676"))
            setBackgroundColor(Color.parseColor("#102A24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minimumHeight = 0
            minHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(6), 0) }
            setOnClickListener {
                abrirSelectorCarpetaIA()
            }
        }

        val btnLimpiar = Button(this).apply {
            text = "LIMPIAR X"
            setTextColor(Color.parseColor("#7FA8C9"))
            setBackgroundColor(Color.parseColor("#102038"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minimumHeight = 0
            minHeight = 0
            setOnClickListener {
                consolaBuffer.clear()
                txtConsola.text = ""
                agregarMensajeConsola("N.E.Y.T.H.A.N.", "Consola despejada, señor. Núcleos y gestor IA listos.")
            }
        }
        headerConsola.addView(lblTituloConsola)
        headerConsola.addView(btnArchivosIA)
        headerConsola.addView(btnLimpiar)
        root.addView(headerConsola)

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
            0.88f
        )
        root.addView(scrollConsola, paramsConsola)

        val barraEntrada = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A1322"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        btnMicrofono = Button(this).apply {
            text = "MIC"
            setTextColor(Color.parseColor("#00F0FF"))
            setBackgroundColor(Color.parseColor("#112640"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setOnClickListener {
                escuchaActiva = !escuchaActiva
                if (escuchaActiva) {
                    setTextColor(Color.parseColor("#00F0FF"))
                    estadoActual = "ESCUCHANDO"
                    backoffEscuchaMs = 700L
                    destruirReconocedorSeguro()
                    reanudarEscucha(300L)
                } else {
                    setTextColor(Color.parseColor("#FF5252"))
                    destruirReconocedorSeguro()
                    estadoActual = "PAUSADO"
                }
            }
        }
        inputComando = EditText(this).apply {
            hint = "Orden, consulta o abrir app..."
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
            text = "ENVIAR"
            setTextColor(Color.parseColor("#040810"))
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
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
            conversacionActivaHasta = System.currentTimeMillis() + 30000L
            procesarOrdenDirecta(texto)
        }
    }

    private fun agregarMensajeConsola(emisor: String, mensaje: String) {
        mainHandler.post {
            if (consolaBuffer.length > 8000) {
                consolaBuffer.delete(0, 2500)
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

    private fun activarNucleoTactico(indice: Int, duracionMs: Long = 5000L) {
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
                        pulsoSilabaVoz = 0.85f
                        destruirReconocedorSeguro()
                        estadoActual = "HABLANDO"
                    }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    val longitudPalabra = (end - start).coerceIn(1, 14)
                    val energia = (0.45f + (longitudPalabra / 14f) * 0.55f).coerceIn(0.4f, 1.0f)
                    mainHandler.post {
                        pulsoSilabaVoz = energia
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        pulsoSilabaVoz = 0f
                        estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO"
                        conversacionActivaHasta = System.currentTimeMillis() + 30000L
                        backoffEscuchaMs = 700L
                        if (escuchaActiva) reanudarEscucha(600L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        pulsoSilabaVoz = 0f
                        estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO"
                        if (escuchaActiva) reanudarEscucha(800L)
                    }
                }
            })
            ttsListo = true
            val saludoInicial = generarSaludoConConciencia()
            hablar(saludoInicial)
        } else {
            agregarMensajeConsola("N.E.Y.T.H.A.N.", "Núcleos en línea, señor. Motor de voz en modo texto.")
            reanudarEscucha(900L)
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
            "$franja Los núcleos tácticos, el lanzador universal de aplicaciones y el enlace directo a Gemini están operativos. ¿Qué misión iniciamos?",
            "$franja Núcleo geodésico estabilizado, escucha continua calibrada y conexión directa con Gemini lista. Estoy atento a sus instrucciones.",
            "$franja Todos los sistemas sensoriales, documentales y cognitivos en línea. ¿En qué puedo asistirle hoy?",
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
        val permisosSolicitar = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permisosSolicitar.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                permisosSolicitar.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permisosSolicitar.isNotEmpty()) {
            requestPermissions(permisosSolicitar.toTypedArray(), REQ_PERMISOS)
            return
        }
        solicitarExencionBateria()
        reanudarEscucha(1000L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS) {
            indexarArchivosDispositivoSilencioso()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                solicitarExencionBateria()
                reanudarEscucha(700L)
            } else {
                agregarMensajeConsola("N.E.Y.T.H.A.N.", "Permiso de micrófono requerido para la escucha continua, señor.")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SELECCIONAR_CARPETA && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("carpeta_ia_uri", treeUri.toString())
                .apply()
            activarNucleoTactico(3, 5000L)
            indexarCarpetaSAF(treeUri)
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

    private fun destruirReconocedorSeguro() {
        mainHandler.removeCallbacks(tareaEscucha)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        escuchandoEnCurso = false
        nivelVozUsuario = 0f
    }

    private fun configurarReconocedor() {
        try {
            destruirReconocedorSeguro()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                agregarMensajeConsola("SUPERVISOR", "Servicio de reconocimiento de voz no disponible en el sistema.")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    escuchandoEnCurso = true
                    if (!estaHablando && escuchaActiva) {
                        estadoActual = "ESCUCHANDO"
                    }
                }

                override fun onBeginningOfSpeech() {
                    backoffEscuchaMs = 700L
                }

                override fun onRmsChanged(rmsdB: Float) {
                    nivelVozUsuario = rmsdB.coerceIn(0f, 12f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    nivelVozUsuario = 0f
                    escuchandoEnCurso = false
                }

                override fun onError(error: Int) {
                    nivelVozUsuario = 0f
                    escuchandoEnCurso = false
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_AUDIO
                    ) {
                        destruirReconocedorSeguro()
                    }
                    if (!estaHablando && escuchaActiva) {
                        backoffEscuchaMs = (backoffEscuchaMs + 300L).coerceAtMost(2200L)
                        reanudarEscucha(backoffEscuchaMs)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVozUsuario = 0f
                    escuchandoEnCurso = false
                    backoffEscuchaMs = 700L
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarTextoCapturado(matches[0])
                    } else if (escuchaActiva && !estaHablando) {
                        reanudarEscucha(700L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val parciales = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!parciales.isNullOrEmpty() && parciales[0].isNotBlank()) {
                        estadoActual = "CAPTANDO..."
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            escuchandoEnCurso = false
            if (escuchaActiva) reanudarEscucha(1500L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        if (!escuchaActiva || estaHablando) return
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !escuchaActiva || escuchandoEnCurso) return@Runnable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return@Runnable
        }
        try {
            if (speechRecognizer == null) {
                configurarReconocedor()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            }
            escuchandoEnCurso = true
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            destruirReconocedorSeguro()
            if (escuchaActiva) reanudarEscucha(1500L)
        }
    }

    private fun hablar(texto: String) {
        agregarMensajeConsola("N.E.Y.T.H.A.N.", texto)
        if (ttsListo && tts != null) {
            val params = Bundle()
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_UTTERANCE")
        } else {
            if (escuchaActiva) reanudarEscucha(900L)
        }
    }

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    // =========================================================================
    // LANZADOR UNIVERSAL DE APLICACIONES INSTALADAS EN ANDROID
    // =========================================================================

    private fun normalizarTexto(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return sinAcentos.trim()
    }

    private fun abrirAplicacionPorNombre(nombreSolicitado: String): Boolean {
        val limpio = normalizarTexto(nombreSolicitado)
            .replace(Regex("^(la aplicacion|la app|aplicacion|app|por favor)\\s+"), "")
            .replace(Regex("\\s+(por favor)$"), "")
            .trim()

        if (limpio.isEmpty()) return false

        val alias = mapOf(
            "camara" to "camera",
            "configuracion" to "settings",
            "ajustes" to "settings",
            "galeria" to "gallery",
            "fotos" to "photos",
            "calculadora" to "calculator",
            "reloj" to "clock",
            "alarmas" to "clock",
            "calendario" to "calendar",
            "contactos" to "contacts",
            "telefono" to "phone",
            "mensajes" to "messages",
            "correo" to "gmail",
            "navegador" to "chrome",
            "mapas" to "maps",
            "archivos" to "files",
            "notas" to "notes"
        )
        val terminoAlternativo = alias[limpio] ?: limpio

        try {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val appsInstaladas = pm.queryIntentActivities(mainIntent, 0)

            var mejorPaquete: String? = null
            var mejorNombreReal: String? = null

            for (resolveInfo in appsInstaladas) {
                val label = normalizarTexto(resolveInfo.loadLabel(pm).toString())
                val pkg = resolveInfo.activityInfo.packageName
                if (label == limpio || label == terminoAlternativo) {
                    mejorPaquete = pkg
                    mejorNombreReal = resolveInfo.loadLabel(pm).toString()
                    break
                }
            }

            if (mejorPaquete == null) {
                for (resolveInfo in appsInstaladas) {
                    val label = normalizarTexto(resolveInfo.loadLabel(pm).toString())
                    val pkg = resolveInfo.activityInfo.packageName.lowercase(Locale.getDefault())
                    if (label.contains(limpio) ||
                        label.contains(terminoAlternativo) ||
                        pkg.contains(limpio.replace(" ", "")) ||
                        pkg.contains(terminoAlternativo.replace(" ", ""))
                    ) {
                        mejorPaquete = resolveInfo.activityInfo.packageName
                        mejorNombreReal = resolveInfo.loadLabel(pm).toString()
                        break
                    }
                }
            }

            if (mejorPaquete != null) {
                val launchIntent = pm.getLaunchIntentForPackage(mejorPaquete)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activarNucleoTactico(3, 5000L)
                    hablar("Abriendo $mejorNombreReal, señor.")
                    mainHandler.postDelayed({
                        try {
                            startActivity(launchIntent)
                        } catch (_: Exception) {
                        }
                    }, 450L)
                    return true
                }
            }
        } catch (e: Exception) {
            agregarMensajeConsola("SUPERVISOR", "Error al buscar app '$limpio': ${e.message}")
        }
        return false
    }

    // =========================================================================
    // CONEXIÓN DIRECTA CON LA API DE GEMINI (SIN RENDER)
    // =========================================================================

    private fun obtenerClaveGeminiSegura(): String {
        return try {
            val field = BuildConfig::class.java.getDeclaredField("GEMINI_API_KEY")
            (field.get(null) as? String)?.trim() ?: ""
        } catch (_: Exception) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("gemini_api_key_local", "") ?: ""
        }
    }

    private fun consultarGeminiDirecto(promptUsuario: String) {
        val apiKey = obtenerClaveGeminiSegura()
        if (apiKey.isEmpty()) {
            hablar("Señor, el enlace directo está listo, pero BuildConfig.GEMINI_API_KEY llegó vacío. Verifique el secreto en GitHub Actions.")
            return
        }

        mainHandler.post { estadoActual = "PROCESANDO" }
        activarNucleoTactico(1, 7000L)

        thread {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modeloGeminiDetectado:generateContent?key=$apiKey"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 15000
                    readTimeout = 25000
                    doOutput = true
                }

                val contentsArray = JSONArray()
                val instruccionSistema = "Eres N.E.Y.T.H.A.N., un asistente táctico personal con estilo formal, directo y eficiente tipo J.A.R.V.I.S. Dirígete al usuario como señor. Responde en español de forma clara y concisa para lectura en voz alta."

                contentsArray.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", instruccionSistema)))
                )
                contentsArray.put(
                    JSONObject()
                        .put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", "Entendido, señor. Todos los sistemas operativos.")))
                )

                for ((rol, texto) in historialTurnos) {
                    val geminiRole = if (rol == "user") "user" else "model"
                    contentsArray.put(
                        JSONObject()
                            .put("role", geminiRole)
                            .put("parts", JSONArray().put(JSONObject().put("text", texto)))
                    )
                }

                contentsArray.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", promptUsuario)))
                )

                val bodyJson = JSONObject().put("contents", contentsArray)

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(bodyJson.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respuestaCruda = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (code in 200..299) {
                    val jsonResp = JSONObject(respuestaCruda)
                    val candidates = jsonResp.optJSONArray("candidates")
                    val textoIa = candidates
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "")
                        ?.replace("*", "")
                        ?.trim() ?: ""

                    if (textoIa.isNotEmpty()) {
                        historialTurnos.add(Pair("user", promptUsuario))
                        historialTurnos.add(Pair("model", textoIa))
                        while (historialTurnos.size > 14) {
                            historialTurnos.removeAt(0)
                        }
                        hablar(textoIa)
                    } else {
                        hablar("Recibí una respuesta vacía del núcleo cognitivo, señor.")
                    }
                } else {
                    mainHandler.post { estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO" }
                    hablar("El enlace directo con Gemini devolvió código $code, señor. Revise la consola.")
                    agregarMensajeConsola("SUPERVISOR", "HTTP $code: ${respuestaCruda.take(300)}")
                }
            } catch (e: Exception) {
                mainHandler.post { estadoActual = if (escuchaActiva) "ESCUCHANDO" else "PAUSADO" }
                hablar("Error de red al conectar con Gemini, señor: ${e.message}")
            }
        }
    }

    // =========================================================================
    // PROCESAMIENTO DE VOZ, ÓRDENES, GESTOR DE ARCHIVOS Y HUD GEODÉSICO
    // =========================================================================

    private fun procesarTextoCapturado(textoCrudo: String) {
        val limpio = textoCrudo.trim()
        if (limpio.isEmpty()) {
            if (escuchaActiva) reanudarEscucha(700L)
            return
        }
        val lower = limpio.lowercase(Locale.getDefault())
        val dentroDeVentana = System.currentTimeMillis() < conversacionActivaHasta
        val contieneWakeWord = lower.contains("neythan") ||
            lower.contains("nathan") ||
            lower.contains("neitan") ||
            lower.contains("despierta")

        if (contieneWakeWord || dentroDeVentana) {
            conversacionActivaHasta = System.currentTimeMillis() + 30000L
            val orden = limpio
                .replace(Regex("(?i)\\b(neythan|nathan|neitan|despierta)\\b[,:\\s]*"), "")
                .trim()
            if (orden.isEmpty()) {
                hablar("A sus órdenes, señor. Dígame.")
            } else {
                procesarOrdenDirecta(orden)
            }
        } else {
            if (escuchaActiva) reanudarEscucha(700L)
        }
    }

    private fun procesarOrdenDirecta(orden: String) {
        agregarMensajeConsola("SEÑOR", orden)
        val lower = normalizarTexto(orden)

        if (esperandoPermisoReporte && (lower.contains("autorizo") || lower == "si" || lower.contains("guarda"))) {
            panelAlertaSupervisor.visibility = View.GONE
            ejecutarDiagnosticoYGuardarEnNotas()
            return
        }

        if (lower.contains("buscar archivo") || lower.contains("busca archivo") || lower.contains("abrir archivo")) {
            activarNucleoTactico(3, 4500L)
            val consulta = orden.replace(Regex("(?i)(buscar|busca|abrir)\\s+archivo\\s*"), "").trim()
            buscarEnIndiceArchivos(consulta)
            return
        }

        val matchAbrir = Regex("(?i)^(abre|abrir|inicia|iniciar|ejecuta|ejecutar|lanza|lanzar)\\s+(.+)$").find(orden.trim())
        if (matchAbrir != null) {
            val nombreApp = matchAbrir.groupValues[2].trim()
            if (abrirAplicacionPorNombre(nombreApp)) {
                return
            } else {
                hablar("No encontré la aplicación $nombreApp instalada en este dispositivo, señor.")
                return
            }
        }

        consultarGeminiDirecto(orden)
    }

    private fun iniciarBucleAnimacion() {
        val runnableAnim = object : Runnable {
            override fun run() {
                if (::hudView.isInitialized) {
                    hudView.actualizarPulso(
                        nivelVozUsuario,
                        pulsoSilabaVoz,
                        estaHablando,
                        estadoActual,
                        nucleoTactico1Cognitivo,
                        nucleoTactico2Vision,
                        nucleoTactico3Ejecucion,
                        nucleoTactico4Proactivo,
                        nucleoTactico5Supervisor
                    )
                }
                mainHandler.postDelayed(this, 33L)
            }
        }
        mainHandler.post(runnableAnim)
    }

    private fun abrirSelectorCarpetaIA() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQ_SELECCIONAR_CARPETA)
        } catch (_: Exception) {
            agregarMensajeConsola("N.E.Y.T.H.A.N.", "No fue posible abrir el selector de carpetas en este dispositivo, señor.")
        }
    }

    private fun indexarCarpetaSAF(treeUri: Uri) {
        thread {
            try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val cursor = contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )
                var conteo = 0
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val nombre = c.getString(1) ?: "sin_nombre"
                        val mime = c.getString(2) ?: "application/octet-stream"
                        val tamano = c.getLong(3) / 1024L
                        val fecha = c.getLong(4)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        indiceArchivosIA.add(
                            ArchivoIndexadoIA(nombre, fileUri.toString(), fileUri, mime, tamano, fecha, "")
                        )
                        conteo++
                    }
                }
                agregarMensajeConsola("GESTOR IA", "Carpeta vinculada: $conteo archivos indexados correctamente.")
            } catch (e: Exception) {
                agregarMensajeConsola("GESTOR IA", "Error al indexar carpeta: ${e.message}")
            }
        }
    }

    private fun indexarArchivosDispositivoSilencioso() {
        thread {
            try {
                val dirDescargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (dirDescargas != null && dirDescargas.exists() && dirDescargas.canRead()) {
                    dirDescargas.listFiles()?.take(50)?.forEach { archivo ->
                        if (archivo.isFile) {
                            indiceArchivosIA.add(
                                ArchivoIndexadoIA(
                                    nombre = archivo.name,
                                    rutaOuri = archivo.absolutePath,
                                    uri = Uri.fromFile(archivo),
                                    mimeType = "text/plain",
                                    tamanoKb = archivo.length() / 1024L,
                                    fechaModificacion = archivo.lastModified(),
                                    extractoTexto = ""
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun buscarEnIndiceArchivos(consulta: String) {
        if (consulta.isEmpty()) {
            hablar("Indíqueme el nombre o palabra clave del archivo que desea localizar, señor.")
            return
        }
        val encontrado = indiceArchivosIA.firstOrNull {
            it.nombre.contains(consulta, ignoreCase = true)
        }
        if (encontrado != null) {
            ultimoArchivoEncontrado = encontrado
            hablar("Archivo localizado, señor: ${encontrado.nombre} (${encontrado.tamanoKb} KB).")
        } else {
            hablar("No encontré ningún archivo coincidente con '$consulta' en el índice actual, señor.")
        }
    }

    private fun auditarCompilacionesGithubSilencioso() {
        activarNucleoTactico(5, 3500L)
    }

    private fun ejecutarDiagnosticoYGuardarEnNotas() {
        esperandoPermisoReporte = false
        activarNucleoTactico(5, 4500L)
        val detalle = falloPendienteDetalle ?: "Sin anomalías críticas registradas."
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Reporte NeythanSupervisor", detalle))
            val intentNota = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Técnico N.E.Y.T.H.A.N.")
                putExtra(Intent.EXTRA_TEXT, detalle)
            }
            startActivity(Intent.createChooser(intentNota, "Guardar diagnóstico en Notas"))
            hablar("Diagnóstico copiado al portapapeles y listo para guardar en sus notas, señor.")
        } catch (_: Exception) {
            hablar("Diagnóstico copiado al portapapeles, señor.")
        }
    }

    class NeythanGeodesicHudView(context: Context) : View(context) {
        private var fase = 0f
        private var vozUsuario = 0f
        private var pulsoVoz = 0f
        private var hablando = false
        private var estado = "ESCUCHANDO"
        private var t1 = false
        private var t2 = false
        private var t3 = false
        private var t4 = false
        private var t5 = false

        private val paintAnillo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#00E5FF")
        }
        private val paintAnilloPunteado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#2A7B9B")
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        private val paintNucleo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8CEEFF")
            textSize = 30f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        fun actualizarPulso(
            vUsuario: Float,
            pVoz: Float,
            isHablando: Boolean,
            st: String,
            n1: Boolean,
            n2: Boolean,
            n3: Boolean,
            n4: Boolean,
            n5: Boolean
        ) {
            vozUsuario = vUsuario
            pulsoVoz = pVoz
            hablando = isHablando
            estado = st
            t1 = n1; t2 = n2; t3 = n3; t4 = n4; t5 = n5
            fase = (fase + 1.8f) % 360f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radioBase = (minOf(width, height) * 0.28f).coerceAtLeast(60f)
            val impulso = if (hablando) pulsoVoz * 26f else (vozUsuario * 2.2f)
            val radioDinamico = radioBase + impulso

            paintNucleo.shader = RadialGradient(
                cx, cy, radioDinamico * 1.4f,
                intArrayOf(
                    Color.parseColor("#6600F0FF"),
                    Color.parseColor("#220088CC"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radioDinamico * 1.4f, paintNucleo)

            canvas.save()
            canvas.rotate(fase, cx, cy)
            canvas.drawCircle(cx, cy, radioDinamico, paintAnillo)
            canvas.restore()

            canvas.save()
            canvas.rotate(-fase * 0.7f, cx, cy)
            canvas.drawCircle(cx, cy, radioDinamico * 1.22f, paintAnilloPunteado)
            canvas.restore()

            val tacticos = listOf(t1, t2, t3, t4, t5)
            for (i in tacticos.indices) {
                val angulo = Math.toRadians((i * 72.0) - 90.0)
                val nx = cx + (radioBase * 1.55f * cos(angulo)).toFloat()
                val ny = cy + (radioBase * 1.55f * sin(angulo)).toFloat()
                val pNodo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = if (tacticos[i]) Color.parseColor("#00FF9D") else Color.parseColor("#16324A")
                }
                canvas.drawCircle(nx, ny, 10f, pNodo)
            }

            canvas.drawText("N.E.Y.T.H.A.N. // $estado", cx, height - 24f, paintTexto)
        }
    }
}
