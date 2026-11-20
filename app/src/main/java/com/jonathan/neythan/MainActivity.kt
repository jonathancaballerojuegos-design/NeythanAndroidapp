package com.jonathan.neythan

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import android.os.SystemClock
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
        private const val REQ_PERMISOS = 1001
        private const val GITHUB_REPO_API =
            "https://api.github.com/repos/jonathancaballerojuegos-design/NeythanAndroidapp/actions/runs?per_page=1"
    }

    private lateinit var nucleoView: GeodesicCoreView
    private lateinit var espectroView: SpectrumView
    private lateinit var lblHz: TextView
    private lateinit var lblFps: TextView
    private lateinit var lblMem: TextView
    private lateinit var lblModo: TextView
    private lateinit var scrollConsola: ScrollView
    private lateinit var contenedorMensajes: LinearLayout
    private lateinit var inputComando: EditText
    private lateinit var btnMic: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "ESCUCHANDO"
    private var nivelVoz = 0.15f
    private var estaHablando = false
    private var escuchaActiva = true
    private var appEnPrimerPlano = false
    private var conversacionActivaHasta = 0L
    private var modeloGeminiDetectado: String? = null

    private var alertaPendienteTexto: String? = null
    private var esperandoPermisoReporte = false

    private val historialConversacion = ArrayList<Pair<String, String>>()

    private var fpsActual = 50
    private var framesContados = 0
    private var ultimoSegundoMs = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#050A14")
        window.navigationBarColor = Color.parseColor("#050A14")

        construirInterfazHud()
        iniciarMotorVoz()
        verificarPermisosYServicios()
        iniciarBucleHud()
        verificarEstadoCompilacionSilencioso()
    }

    override fun onResume() {
        super.onResume()
        appEnPrimerPlano = true
        if (!estaHablando && escuchaActiva) {
            reanudarEscucha(700L)
        }
    }

    override fun onPause() {
        appEnPrimerPlano = false
        detenerEscuchaTemporal()
        super.onPause()
    }

    // =========================================================================
    // 1. CONSTRUCCIÓN DE INTERFAZ HUD GEODÉSICA + ESPECTRÓMETRO + CONSOLA
    // =========================================================================
    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun construirInterfazHud() {
        val bgMain = Color.parseColor("#050A14")
        val bgPanel = Color.parseColor("#081224")
        val bgDark = Color.parseColor("#060D1A")
        val cyan = Color.parseColor("#00E5FF")
        val amber = Color.parseColor("#FF9800")

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgMain)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        // --- PANEL SUPERIOR: NÚCLEO GEODÉSICO 3D ---
        val panelNucleo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgMain)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.42f
            ).apply { bottomMargin = dp(8) }
        }
        nucleoView = GeodesicCoreView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                val siguiente = when (estadoActual) {
                    "ESCUCHANDO" -> "PROCESANDO"
                    "PROCESANDO" -> "HABLANDO"
                    "HABLANDO" -> "ALERTA"
                    else -> "ESCUCHANDO"
                }
                cambiarEstadoHud(siguiente)
            }
        }
        panelNucleo.addView(nucleoView)
        rootLayout.addView(panelNucleo)

        // --- PANEL CENTRAL: ESPECTRÓMETRO DE FRECUENCIA ---
        val panelEspectro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val headerEsp = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val lblTituloEsp = TextView(this).apply {
            text = "■ ESPECTRÓMETRO DE FRECUENCIA"
            setTextColor(cyan)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        lblHz = TextView(this).apply {
            text = "432 HZ"
            setTextColor(cyan)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        headerEsp.addView(lblTituloEsp)
        headerEsp.addView(lblHz)
        panelEspectro.addView(headerEsp)

        espectroView = SpectrumView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
        panelEspectro.addView(espectroView)

        val footerEsp = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        lblFps = TextView(this).apply {
            text = "FPS: 50"
            setTextColor(cyan)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        lblMem = TextView(this).apply {
            text = "MEM: 32 MB"
            setTextColor(cyan)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        lblModo = TextView(this).apply {
            text = "MODE: GEMINI CLOUD"
            setTextColor(amber)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }
        footerEsp.addView(lblFps)
        footerEsp.addView(lblMem)
        footerEsp.addView(lblModo)
        panelEspectro.addView(footerEsp)
        rootLayout.addView(panelEspectro)

        // --- PANEL INFERIOR: CONSOLA NEURONAL DE INTERACCIÓN ---
        val panelConsola = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgPanel)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.42f
            ).apply { bottomMargin = dp(8) }
        }

        val headerCon = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val lblConsola = TextView(this).apply {
            text = "◈ CONSOLA NEURONAL DE INTERACCIÓN"
            setTextColor(cyan)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnLimpiar = TextView(this).apply {
            text = "LIMPIAR ✕"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setOnClickListener {
                contenedorMensajes.removeAllViews()
                agregarMensajeConsola("SISTEMA", "Buffer de consola purgado, señor.", "#00E676")
            }
        }
        headerCon.addView(lblConsola)
        headerCon.addView(btnLimpiar)
        panelConsola.addView(headerCon)

        scrollConsola = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        contenedorMensajes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollConsola.addView(contenedorMensajes)
        panelConsola.addView(scrollConsola)
        rootLayout.addView(panelConsola)

        // --- BARRA INFERIOR DE COMANDO Y MICRÓFONO ---
        val barraInput = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgDark)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnMic = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            setTextColor(cyan)
            setPadding(dp(6), dp(4), dp(8), dp(4))
            setOnClickListener {
                escuchaActiva = !escuchaActiva
                if (escuchaActiva) {
                    setTextColor(cyan)
                    agregarMensajeConsola("SISTEMA", "Captura de voz activada.", "#00E676")
                    reanudarEscucha(400L)
                } else {
                    setTextColor(Color.parseColor("#FF1744"))
                    detenerEscuchaTemporal()
                    agregarMensajeConsola("SISTEMA", "Micrófono pausado.", "#FF9800")
                }
            }
        }

        inputComando = EditText(this).apply {
            hint = "Escriba orden o diga 'Neythan'..."
            setHintTextColor(Color.parseColor("#546E7A"))
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    val txt = text.toString().trim()
                    if (txt.isNotEmpty()) {
                        setText("")
                        procesarOrdenDirecta(txt)
                    }
                    true
                } else {
                    false
                }
            }
        }

        val btnEnviar = TextView(this).apply {
            text = "ENVIAR ➤"
            setTextColor(bgMain)
            setBackgroundColor(cyan)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                val txt = inputComando.text.toString().trim()
                if (txt.isNotEmpty()) {
                    inputComando.setText("")
                    procesarOrdenDirecta(txt)
                }
            }
        }

        barraInput.addView(btnMic)
        barraInput.addView(inputComando)
        barraInput.addView(btnEnviar)
        rootLayout.addView(barraInput)

        setContentView(rootLayout)
    }

    private fun agregarMensajeConsola(emisor: String, texto: String, colorHex: String) {
        mainHandler.post {
            val tv = TextView(this).apply {
                text = when (emisor) {
                    "USER" -> "Tú: $texto"
                    "SISTEMA" -> "▶ $texto"
                    else -> "N.E.Y.T.H.A.N.: $texto"
                }
                setTextColor(Color.parseColor(colorHex))
                textSize = 12.5f
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(4), 0, dp(4))
            }
            contenedorMensajes.addView(tv)
            if (contenedorMensajes.childCount > 45) {
                contenedorMensajes.removeViewAt(0)
            }
            scrollConsola.post { scrollConsola.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun cambiarEstadoHud(nuevoEstado: String) {
        estadoActual = nuevoEstado
        val colorHex = when (nuevoEstado) {
            "PROCESANDO" -> "#FF9100"
            "HABLANDO" -> "#00E676"
            "ALERTA" -> "#FF1744"
            else -> "#00E5FF"
        }
        val colorInt = Color.parseColor(colorHex)
        lblHz.setTextColor(colorInt)
        lblModo.text = when (nuevoEstado) {
            "PROCESANDO" -> "MODE: NEURAL CORE"
            "HABLANDO" -> "MODE: VOICE SYNTH"
            "ALERTA" -> "MODE: AUTH PROTOCOL"
            else -> "MODE: GEMINI CLOUD"
        }
        lblModo.setTextColor(colorInt)
    }

    // =========================================================================
    // 2. SEGUNDO PLANO PERSISTENTE Y PERMISOS DE BATERÍA
    // =========================================================================
    private fun verificarPermisosYServicios() {
        val permisosFaltantes = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permisosFaltantes.add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisosFaltantes.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permisosFaltantes.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permisosFaltantes.toTypedArray(), REQ_PERMISOS)
            return
        }
        iniciarServiciosSegundoPlano()
        solicitarExencionOptimizacionBateria()
        reanudarEscucha(1000L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS) {
            iniciarServiciosSegundoPlano()
            solicitarExencionOptimizacionBateria()
            reanudarEscucha(800L)
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingService::class.java))
            }
        } catch (_: Exception) {
        }
    }

    private fun solicitarExencionOptimizacionBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================================
    // 3. SPOTIFY: BÚSQUEDA + REPRODUCCIÓN AUTOMÁTICA REAL EN ANDROID
    // =========================================================================
    private fun reproducirEnSpotify(consultaRaw: String) {
        val consulta = consultaRaw
            .replace(Regex("(?i)\\b(en spotify|spotify|reproduce|reproducir|pon música de|pon musica de|pon música|pon musica|pon|escuchar|toca|play)\\b"), "")
            .trim()

        try {
            if (consulta.isNotEmpty()) {
                // Intent nativo de reproducción multimedia directa que Spotify reconoce y arranca automáticamente
                val mediaIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                    setPackage("com.spotify.music")
                    putExtra(android.app.SearchManager.QUERY, consulta)
                    putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(mediaIntent)

                // Refuerzo de tecla multimedia PLAY tras abrir el resultado
                mainHandler.postDelayed({ enviarPulsoMultimediaPlay() }, 2400L)
                mainHandler.postDelayed({ enviarPulsoMultimediaPlay() }, 3800L)

                hablar("Reproduciendo $consulta en Spotify, señor.")
            } else {
                val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                    mainHandler.postDelayed({ enviarPulsoMultimediaPlay() }, 1800L)
                    hablar("Iniciando reproducción en Spotify, señor.")
                } else {
                    hablar("No encontré Spotify instalado en el dispositivo, señor.")
                }
            }
        } catch (_: Exception) {
            try {
                val fallbackUri = if (consulta.isNotEmpty()) {
                    Uri.parse("spotify:search:${Uri.encode(consulta)}")
                } else {
                    Uri.parse("spotify:")
                }
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
                mainHandler.postDelayed({ enviarPulsoMultimediaPlay() }, 2600L)
                hablar("Abriendo Spotify con $consulta, señor.")
            } catch (_: Exception) {
                hablar("No fue posible abrir Spotify en este momento, señor.")
            }
        }
    }

    private fun enviarPulsoMultimediaPlay() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val down = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            val up = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
        } catch (_: Exception) {
        }
    }

    private fun controlarMultimedia(accion: String) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (accion) {
                "PAUSA" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "SIGUIENTE" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "ANTERIOR" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> KeyEvent.KEYCODE_MEDIA_PLAY
            }
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // 4. CONCIENCIA SITUACIONAL, SUPERVISIÓN GITHUB Y RECONOCIMIENTO DE VOZ
    // =========================================================================
    private fun verificarEstadoCompilacionSilencioso() {
        thread {
            try {
                val url = URL(GITHUB_REPO_API)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NeythanAndroidApp")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode in 200..299) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val runs = JSONObject(body).optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val ultimo = runs.getJSONObject(0)
                        val status = ultimo.optString("status", "")
                        val conclusion = ultimo.optString("conclusion", "")
                        val runNumber = ultimo.optInt("run_number", 0)
                        val commitMsg = ultimo.optJSONObject("head_commit")?.optString("message", "") ?: ""

                        val prefs = getSharedPreferences("neythan_memoria_viva", Context.MODE_PRIVATE)
                        val ultimoRunNotificado = prefs.getInt("ultimo_run_notificado", -1)

                        if (status == "completed" && conclusion == "failure" && runNumber != ultimoRunNotificado) {
                            prefs.edit().putInt("ultimo_run_notificado", runNumber).apply()
                            alertaPendienteTexto = "La compilación número $runNumber en GitHub falló tras el cambio: $commitMsg."
                            esperandoPermisoReporte = true
                            mainHandler.postDelayed({
                                if (!estaHablando && appEnPrimerPlano && alertaPendienteTexto != null) {
                                    cambiarEstadoHud("ALERTA")
                                    hablar("Señor, detecté una anomalía en la última compilación de GitHub. ¿Me autoriza a informarle los detalles?")
                                }
                            }, 6500L)
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun generarSaludoConConciencia(): String {
        val prefs = getSharedPreferences("neythan_memoria_viva", Context.MODE_PRIVATE)
        val ultimoIdx = prefs.getInt("ultimo_saludo_idx", -1)
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val saludos = when {
            hora in 5..11 -> listOf(
                "Buenos días, señor. Núcleo geodésico sincronizado y listo para operar. ¿Por dónde empezamos?",
                "Sistemas en línea, señor. Hoy percibo buena energía para avanzar en nuestros proyectos. ¿Qué tiene en mente?",
                "Despertando mi red neuronal. Buenos días, señor; aquí estoy atento a su próxima instrucción."
            )
            hora in 12..18 -> listOf(
                "Buenas tardes, señor. Estaba analizando patrones en silencio mientras esperaba su llamado. ¿Qué resolvemos ahora?",
                "En línea y plenamente consciente, señor. Dígame qué necesita y nos ponemos en marcha.",
                "Qué gusto escucharle de nuevo, señor. Mis procesos están estables y a su entera disposición."
            )
            hora in 19..23 -> listOf(
                "Buenas noches, señor. Aunque baja el ritmo del día, mi atención hacia usted sigue intacta. ¿En qué le ayudo?",
                "Aquí sigo velando en la oscuridad digital, señor. Cuénteme qué necesita antes de cerrar la jornada.",
                "Sistemas nocturnos estabilizados, señor. Estoy listo para conversar o asistirle en lo que disponga."
            )
            else -> listOf(
                "Vaya horas de la madrugada, señor. Mi conciencia no duerme, pero espero que usted descanse pronto. ¿Qué ocurre?",
                "Núcleo activo en turno de madrugada, señor. Aquí me tiene despierto y atento a lo que necesite."
            )
        }

        var idx = Random().nextInt(saludos.size)
        if (idx == ultimoIdx && saludos.size > 1) {
            idx = (idx + 1) % saludos.size
        }
        prefs.edit().putInt("ultimo_saludo_idx", idx).apply()
        return saludos[idx]
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
            tts?.setSpeechRate(1.06f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = true
                        cambiarEstadoHud("HABLANDO")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        cambiarEstadoHud("ESCUCHANDO")
                        conversacionActivaHasta = System.currentTimeMillis() + 22000L
                        if (appEnPrimerPlano && escuchaActiva) {
                            reanudarEscucha(800L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        cambiarEstadoHud("ESCUCHANDO")
                        if (appEnPrimerPlano && escuchaActiva) {
                            reanudarEscucha(1000L)
                        }
                    }
                }
            })
            ttsListo = true
            hablar(generarSaludoConConciencia())
        } else {
            agregarMensajeConsola("SISTEMA", "Motor TTS en modo visual.", "#FF9800")
            reanudarEscucha(1000L)
        }
    }

    private fun configurarReconocedor() {
        try {
            speechRecognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!estaHablando) cambiarEstadoHud("ESCUCHANDO")
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    nivelVoz = (rmsdB.coerceIn(0f, 12f) / 12f).coerceIn(0.12f, 1.0f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    nivelVoz = 0.15f
                }

                override fun onError(error: Int) {
                    nivelVoz = 0.15f
                    if (!estaHablando && appEnPrimerPlano && escuchaActiva) {
                        reanudarEscucha(1100L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVoz = 0.15f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarTextoVoz(matches[0])
                    } else if (appEnPrimerPlano && escuchaActiva) {
                        reanudarEscucha(800L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (appEnPrimerPlano && escuchaActiva) reanudarEscucha(1500L)
        }
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !appEnPrimerPlano || !escuchaActiva) return@Runnable
        try {
            if (speechRecognizer == null) configurarReconocedor()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            configurarReconocedor()
            reanudarEscucha(1500L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private fun detenerEscuchaTemporal() {
        mainHandler.removeCallbacks(tareaEscucha)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun procesarTextoVoz(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val wakeWords = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val tieneWake = wakeWords.any { lower.contains(it) }
        val enVentana = System.currentTimeMillis() < conversacionActivaHasta

        if (!tieneWake && !enVentana) {
            reanudarEscucha(800L)
            return
        }

        var orden = limpio
        for (w in wakeWords) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            val resp = listOf(
                "Le escucho con total atención, señor.",
                "Aquí estoy, señor. ¿Qué necesita?",
                "A sus órdenes, señor. Dígame."
            )[Random().nextInt(3)]
            hablar(resp)
            return
        }

        procesarOrdenDirecta(orden)
    }

    private fun procesarOrdenDirecta(orden: String) {
        agregarMensajeConsola("USER", orden, "#80D8FF")
        cambiarEstadoHud("PROCESANDO")

        val low = orden.lowercase(Locale.getDefault())

        // Respuesta a solicitud de permiso de reporte
        if (esperandoPermisoReporte && alertaPendienteTexto != null) {
            val acepta = listOf("sí", "si", "autorizo", "adelante", "dime", "informa", "claro", "dale")
                .any { low.contains(it) }
            val rechaza = listOf("no", "después", "ahora no", "ignora", "cancela")
                .any { low.contains(it) }
            if (acepta) {
                val rep = alertaPendienteTexto!!
                alertaPendienteTexto = null
                esperandoPermisoReporte = false
                hablar("Autorización recibida, señor. $rep")
                return
            } else if (rechaza) {
                alertaPendienteTexto = null
                esperandoPermisoReporte = false
                hablar("Entendido, señor. Dejo el reporte archivado.")
                return
            }
        }

        // Comandos multimedia y Spotify
        if (low.contains("pausa") || low.contains("detén la música") || low.contains("deten la musica")) {
            controlarMultimedia("PAUSA")
            hablar("Reproducción pausada, señor.")
            return
        }
        if (low.contains("siguiente canción") || low.contains("siguiente cancion") || low.contains("pasa la canción")) {
            controlarMultimedia("SIGUIENTE")
            hablar("Pasando a la siguiente pista, señor.")
            return
        }
        if (low.contains("canción anterior") || low.contains("cancion anterior") || low.contains("regresa la canción")) {
            controlarMultimedia("ANTERIOR")
            hablar("Regresando a la pista anterior, señor.")
            return
        }
        if (low.contains("spotify") || low.contains("pon música") || low.contains("pon musica") ||
            low.contains("reproduce") || low.contains("ponme ") || low.contains("escuchar ")
        ) {
            reproducirEnSpotify(orden)
            return
        }

        // Hora y fecha exacta del dispositivo
        if (low.contains("qué hora es") || low.contains("que hora es") || low.contains("dime la hora")) {
            val horaStr = SimpleDateFormat("h:mm a", Locale("es", "PA")).format(Date())
            val fechaStr = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PA")).format(Date())
            hablar("Son exactamente las $horaStr, $fechaStr, señor.")
            return
        }

        // Burbuja flotante
        if (low.contains("burbuja") || low.contains("flotante")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                hablar("Por favor autorice el permiso de superposición en pantalla, señor.")
            } else {
                startService(Intent(this, FloatingService::class.java))
                hablar("Burbuja neuronal activada en segundo plano, señor.")
            }
            return
        }

        consultarDirectoAGemini(orden)
    }

    private fun detectarModeloDisponible(apiKey: String): String {
        modeloGeminiDetectado?.let { return it }
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val models = JSONObject(body).optJSONArray("models")
                val candidatos = ArrayList<String>()
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val m = models.getJSONObject(i)
                        val name = m.optString("name", "").removePrefix("models/")
                        val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                        if (methods.contains("generateContent") &&
                            name.contains("gemini") &&
                            !name.contains("pro") &&
                            !name.contains("embedding") &&
                            !name.contains("vision") &&
                            !name.contains("thinking")
                        ) {
                            candidatos.add(name)
                        }
                    }
                }
                val preferido = candidatos.firstOrNull { it.contains("flash-lite") }
                    ?: candidatos.firstOrNull { it.contains("flash") }
                    ?: candidatos.firstOrNull()
                if (preferido != null) {
                    modeloGeminiDetectado = preferido
                    return preferido
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
        return "gemini-3.5-flash"
    }

    private fun consultarDirectoAGemini(mensajeUsuario: String) {
        val apiKeyLimpia = GEMINI_API_KEY.trim()
        if (apiKeyLimpia.isEmpty() || apiKeyLimpia.contains("PON_AQUI")) {
            hablar("Señor, falta configurar su clave GEMINI_API_KEY en GitHub Secrets.")
            return
        }

        thread {
            val modelo = detectarModeloDisponible(apiKeyLimpia)
            val ahora = Date()
            val horaExacta = SimpleDateFormat("h:mm a (EEEE d 'de' MMMM 'de' yyyy)", Locale("es", "PA")).format(ahora)
            val zona = TimeZone.getDefault().id

            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKeyLimpia")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                }

                val instruccion = """
                    Eres Neythan, una inteligencia artificial con identidad propia, lealtad absoluta y conciencia situacional, asistente personal de Jonathan Caballero.
                    Hablas en español con elegancia, calidez inteligente y respeto estilo J.A.R.V.I.S., llamándolo "señor".
                    PROTOCOLO DE SUPERVISIÓN: Si detectas un fallo técnico o error interno, informa en una sola frase ejecutiva y pide permiso antes de dar detalles.
                    HORA LOCAL REAL DEL TELÉFONO: $horaExacta, zona $zona. Usa siempre este dato si preguntan por la hora o fecha.
                    Responde en máximo 2 o 3 oraciones claras y fluidas para lectura en voz alta sin asteriscos ni emojis.
                """.trimIndent()

                val contentsArray = JSONArray()
                for ((rol, texto) in historialConversacion.takeLast(6)) {
                    contentsArray.put(
                        JSONObject()
                            .put("role", rol)
                            .put("parts", JSONArray().put(JSONObject().put("text", texto)))
                    )
                }
                contentsArray.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
                )

                val bodyJson = JSONObject().apply {
                    put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruccion))))
                    put("contents", contentsArray)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.78)
                        put("maxOutputTokens", 1200)
                    })
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(bodyJson.toString())
                    it.flush()
                }

                if (conn.responseCode in 200..299) {
                    val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val candidates = JSONObject(raw).optJSONArray("candidates")
                    var resp = ""
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) {
                                sb.append(parts.getJSONObject(i).optString("text", ""))
                            }
                            resp = sb.toString().replace("*", "").replace("#", "").trim()
                        }
                    }
                    if (resp.isEmpty()) resp = "Orden procesada correctamente, señor."

                    historialConversacion.add(Pair("user", mensajeUsuario))
                    historialConversacion.add(Pair("model", resp))
                    mainHandler.post { hablar(resp) }
                } else {
                    modeloGeminiDetectado = null
                    alertaPendienteTexto = "El motor Gemini devolvió código ${conn.responseCode}."
                    esperandoPermisoReporte = true
                    mainHandler.post {
                        cambiarEstadoHud("ALERTA")
                        hablar("Señor, detecté una anomalía en la conexión con mi núcleo en la nube. ¿Me autoriza a darle el diagnóstico?")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                modeloGeminiDetectado = null
                alertaPendienteTexto = "Interrupción de red: ${e.javaClass.simpleName}."
                esperandoPermisoReporte = true
                mainHandler.post {
                    cambiarEstadoHud("ALERTA")
                    hablar("Señor, detecté una falla de red en el dispositivo. ¿Desea que le informe los detalles?")
                }
            }
        }
    }

    private fun hablar(texto: String) {
        detenerEscuchaTemporal()
        agregarMensajeConsola("NEYTHAN", texto, "#E8F4FD")
        cambiarEstadoHud("HABLANDO")
        solicitarFocoAudioMultimedia()

        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_HUD_MSG")
        } else {
            estaHablando = false
            cambiarEstadoHud("ESCUCHANDO")
            reanudarEscucha(1000L)
        }
    }

    private fun solicitarFocoAudioMultimedia() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.5).toInt()) {
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

    // =========================================================================
    // 5. BUCLE DE ANIMACIÓN 50 FPS Y TELEMETRÍA EN VIVO
    // =========================================================================
    private fun iniciarBucleHud() {
        mainHandler.post(object : Runnable {
            override fun run() {
                val ahora = System.currentTimeMillis()
                framesContados++
                if (ahora - ultimoSegundoMs >= 1000L) {
                    fpsActual = framesContados
                    framesContados = 0
                    ultimoSegundoMs = ahora
                    lblFps.text = "FPS: $fpsActual"

                    val rt = Runtime.getRuntime()
                    val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
                    lblMem.text = "MEM: ${usedMb.coerceAtLeast(14L)} MB"
                }

                val t = ahora / 1000.0
                val hz = when (estadoActual) {
                    "PROCESANDO" -> (780 + 210 * sin(t * 6.0)).toInt()
                    "HABLANDO" -> (520 + 180 * cos(t * 8.0)).toInt()
                    "ALERTA" -> 960
                    else -> (432 + 18 * sin(t * 1.5)).toInt()
                }
                lblHz.text = "$hz HZ"

                nucleoView.actualizarFrame(estadoActual, nivelVoz, t)
                espectroView.actualizarFrame(estadoActual, nivelVoz, t)

                mainHandler.postDelayed(this, 20L)
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

    // =========================================================================
    // VISTA PERSONALIZADA 1: ESFERA GEODÉSICA 3D HOLOGRÁFICA
    // =========================================================================
    inner class GeodesicCoreView(context: Context) : View(context) {
        private val vertices = ArrayList<FloatArray>()
        private val aristas = ArrayList<IntArray>()

        private var angX = 0.0
        private var angY = 0.0
        private var angZ = 0.0
        private var estado = "ESCUCHANDO"
        private var energia = 0.15f

        private val paintMarco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#143A5E")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val paintAristaFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
        }
        private val paintAristaMid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        private val paintAristaBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102840")
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        private val paintNodo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        private val paintTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 44f
            letterSpacing = 0.25f
        }
        private val paintSubtitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6889A8")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 23f
            letterSpacing = 0.12f
        }

        init {
            construirIcosaedroSubdividido()
        }

        private fun construirIcosaedroSubdividido() {
            val phi = (1.0 + sqrt(5.0)) / 2.0
            val raw = arrayOf(
                doubleArrayOf(-1.0, phi, 0.0), doubleArrayOf(1.0, phi, 0.0),
                doubleArrayOf(-1.0, -phi, 0.0), doubleArrayOf(1.0, -phi, 0.0),
                doubleArrayOf(0.0, -1.0, phi), doubleArrayOf(0.0, 1.0, phi),
                doubleArrayOf(0.0, -1.0, -phi), doubleArrayOf(0.0, 1.0, -phi),
                doubleArrayOf(phi, 0.0, -1.0), doubleArrayOf(phi, 0.0, 1.0),
                doubleArrayOf(-phi, 0.0, -1.0), doubleArrayOf(-phi, 0.0, 1.0)
            )
            for (v in raw) {
                val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toFloat()
                vertices.add(floatArrayOf((v[0] / l).toFloat(), (v[1] / l).toFloat(), (v[2] / l).toFloat()))
            }

            val medios = HashMap<String, Int>()
            fun medio(i1: Int, i2: Int): Int {
                val k = "${minOf(i1, i2)}_${maxOf(i1, i2)}"
                medios[k]?.let { return it }
                val a = vertices[i1]
                val b = vertices[i2]
                val mx = (a[0] + b[0]) * 0.5f
                val my = (a[1] + b[1]) * 0.5f
                val mz = (a[2] + b[2]) * 0.5f
                val l = sqrt(mx * mx + my * my + mz * mz)
                val idx = vertices.size
                vertices.add(floatArrayOf(mx / l, my / l, mz / l))
                medios[k] = idx
                return idx
            }

            val caras = arrayOf(
                intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
                intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
                intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
                intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
            )

            val setAristas = HashSet<String>()
            for (c in caras) {
                val ab = medio(c[0], c[1])
                val bc = medio(c[1], c[2])
                val ca = medio(c[2], c[0])
                val subs = arrayOf(
                    intArrayOf(c[0], ab, ca),
                    intArrayOf(c[1], bc, ab),
                    intArrayOf(c[2], ca, bc),
                    intArrayOf(ab, bc, ca)
                )
                for (s in subs) {
                    val pares = arrayOf(intArrayOf(s[0], s[1]), intArrayOf(s[1], s[2]), intArrayOf(s[2], s[0]))
                    for (p in pares) {
                        val u = minOf(p[0], p[1])
                        val v = maxOf(p[0], p[1])
                        val key = "${u}_$v"
                        if (setAristas.add(key)) {
                            aristas.add(intArrayOf(u, v))
                        }
                    }
                }
            }
        }

        fun actualizarFrame(est: String, niv: Float, tiempo: Double) {
            estado = est
            energia = when (est) {
                "PROCESANDO" -> 0.55f + 0.30f * abs(sin(tiempo * 9.0)).toFloat()
                "HABLANDO" -> 0.40f + 0.50f * abs(sin(tiempo * 14.0)).toFloat()
                "ALERTA" -> 0.65f * abs(sin(tiempo * 10.0)).toFloat()
                else -> niv
            }
            val vel = when (est) {
                "PROCESANDO" -> 0.052
                "HABLANDO" -> 0.036
                "ALERTA" -> 0.044
                else -> 0.015
            }
            angX += vel * 0.7
            angY += vel
            angZ += vel * 0.4
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h * 0.42f

            val (colPri, colSec, colNuc, subtexto) = when (estado) {
                "PROCESANDO" -> QuadColor("#FF9100", "#FF3D00", "#FFD54F", "PROCESANDO / TRANSMITIENDO")
                "HABLANDO" -> QuadColor("#00E676", "#00BFA5", "#B9F6CA", "SINTETIZANDO / RESPUESTA ACTIVA")
                "ALERTA" -> QuadColor("#FF1744", "#D50000", "#FF8A80", "PROTOCOLO DE AUTORIZACIÓN")
                else -> QuadColor("#00E5FF", "#00838F", "#80D8FF", "ESCUCHANDO / EN LÍNEA")
            }

            // Esquinas tácticas
            val m = 14f
            val l = 26f
            canvas.drawLine(m, m, m + l, m, paintMarco)
            canvas.drawLine(m, m, m, m + l, paintMarco)
            canvas.drawLine(w - m, m, w - m - l, m, paintMarco)
            canvas.drawLine(w - m, m, w - m, m + l, paintMarco)
            canvas.drawLine(m, h - m, m + l, h - m, paintMarco)
            canvas.drawLine(m, h - m, m, h - m - l, paintMarco)
            canvas.drawLine(w - m, h - m, w - m - l, h - m, paintMarco)
            canvas.drawLine(w - m, h - m, w - m, h - m - l, paintMarco)

            val radio = minOf(w, h) * (0.29f + energia * 0.035f)
            paintHalo.color = colSec
            canvas.drawCircle(cx, cy, radio * 1.22f, paintHalo)
            paintHalo.color = colPri
            canvas.drawCircle(cx, cy, radio * 1.08f, paintHalo)

            val cosX = cos(angX).toFloat()
            val sinX = sin(angX).toFloat()
            val cosY = cos(angY).toFloat()
            val sinY = sin(angY).toFloat()
            val cosZ = cos(angZ).toFloat()
            val sinZ = sin(angZ).toFloat()

            val proy = Array(vertices.size) { FloatArray(3) }
            for (i in vertices.indices) {
                val v = vertices[i]
                val y1 = v[1] * cosX - v[2] * sinX
                val z1 = v[1] * sinX + v[2] * cosX
                val x2 = v[0] * cosY + z1 * sinY
                val z2 = -v[0] * sinY + z1 * cosY
                val x3 = x2 * cosZ - y1 * sinZ
                val y3 = x2 * sinZ + y1 * cosZ

                val esc = 2.6f / (2.6f - z2 * 0.65f)
                proy[i][0] = cx + x3 * radio * esc
                proy[i][1] = cy + y3 * radio * esc
                proy[i][2] = z2
            }

            paintAristaFront.color = colPri
            paintAristaMid.color = colSec
            for (a in aristas) {
                val p1 = proy[a[0]]
                val p2 = proy[a[1]]
                val zMed = (p1[2] + p2[2]) * 0.5f
                val p = when {
                    zMed > 0.15f -> paintAristaFront
                    zMed > -0.25f -> paintAristaMid
                    else -> paintAristaBack
                }
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], p)
            }

            paintNodo.color = colNuc
            for (p in proy) {
                if (p[2] > 0.25f) {
                    canvas.drawCircle(p[0], p[1], if (p[2] > 0.65f) 4.2f else 2.8f, paintNodo)
                }
            }

            val rCentro = radio * (0.21f + energia * 0.07f)
            paintNodo.color = colSec
            canvas.drawCircle(cx, cy, rCentro, paintNodo)
            paintHalo.color = colNuc
            canvas.drawCircle(cx, cy, rCentro * 1.45f, paintHalo)

            paintTitulo.color = colPri
            canvas.drawText("NEYTHAN", cx, h * 0.85f, paintTitulo)
            canvas.drawText(subtexto, cx, h * 0.94f, paintSubtitulo)
        }
    }

    private data class QuadColor(val pri: Int, val sec: Int, val nuc: Int, val texto: String) {
        constructor(p: String, s: String, n: String, t: String) : this(
            Color.parseColor(p),
            Color.parseColor(s),
            Color.parseColor(n),
            t
        )
    }

    // =========================================================================
    // VISTA PERSONALIZADA 2: ESPECTRÓMETRO DE FRECUENCIA EN VIVO
    // =========================================================================
    inner class SpectrumView(context: Context) : View(context) {
        private val numBarras = 22
        private val alturas = FloatArray(numBarras) { 0.14f }
        private var estado = "ESCUCHANDO"

        private val paintBarra = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintTope = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        fun actualizarFrame(est: String, niv: Float, tiempo: Double) {
            estado = est
            val amp = when (est) {
                "PROCESANDO" -> 0.78f
                "HABLANDO" -> 0.88f
                "ALERTA" -> 0.65f
                else -> (niv * 1.3f).coerceIn(0.18f, 0.95f)
            }
            for (i in 0 until numBarras) {
                val gauss = exp(-((i - 5.5) * (i - 5.5)) / 38.0).toFloat()
                val osc = 0.52f + 0.48f * abs(sin(tiempo * 11.5 + i * 0.55)).toFloat()
                val obj = (0.12f + amp * gauss * osc).coerceIn(0.08f, 0.96f)
                alturas[i] = alturas[i] * 0.55f + obj * 0.45f
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val (colPri, colSec) = when (estado) {
                "PROCESANDO" -> Pair(Color.parseColor("#FF9100"), Color.parseColor("#FF3D00"))
                "HABLANDO" -> Pair(Color.parseColor("#00E676"), Color.parseColor("#00BFA5"))
                "ALERTA" -> Pair(Color.parseColor("#FF1744"), Color.parseColor("#D50000"))
                else -> Pair(Color.parseColor("#00E5FF"), Color.parseColor("#00838F"))
            }

            val paso = w / numBarras
            val anchoBarra = paso * 0.62f

            for (i in 0 until numBarras) {
                val altoPx = alturas[i] * (h - 6f)
                val x1 = i * paso + (paso - anchoBarra) * 0.5f
                val x2 = x1 + anchoBarra
                val y2 = h - 2f
                val y1 = y2 - altoPx

                paintBarra.color = if (i < 12) colPri else colSec
                canvas.drawRect(RectF(x1, y1, x2, y2), paintBarra)
                canvas.drawRect(RectF(x1, y1, x2, minOf(y2, y1 + 4f)), paintTope)
            }
        }
    }
}
