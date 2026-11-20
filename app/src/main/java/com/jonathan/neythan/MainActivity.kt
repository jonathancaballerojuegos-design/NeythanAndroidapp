package com.jonathan.neythan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
        private const val REQ_PERMISOS = 1001
        private const val GITHUB_REPO_API = "https://api.github.com/repos/jonathancaballerojuegos-design/NeythanAndroidapp/actions/runs?per_page=1"
    }

    private lateinit var hudView: NeythanHudCanvas
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "REPOSO"
    private var spriteActualNombre = "reposo_feliz"
    private var subtituloPantalla = "Sincronizando matriz neuronal..."
    private var nivelVozUsuario = 0f
    private var estaHablando = false
    private var bocaAbiertaFrame = false
    private var conversacionActivaHasta = 0L
    private var modoFormaManual = false
    private var indiceForma = 0
    private var appEnPrimerPlano = false
    private var modeloGeminiDetectado: String? = null

    private var alertaPendienteTexto: String? = null
    private var esperandoPermisoReporte = false

    private val historialConversacion = ArrayList<Pair<String, String>>()

    private val formasDisponibles = listOf(
        "reposo_feliz", "saltando_azul", "corriendo_rapido", "recargando_bateria",
        "celebrando_confeti", "enamorado_corazones", "malicioso_cian", "asco_verde",
        "gafas_oscuras", "analizando", "furia_roja", "durmiendo_reposo",
        "angulo_perfil_1", "angulo_espalda_1"
    )

    private val cacheSprites = HashMap<String, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        hudView = NeythanHudCanvas(this)
        setContentView(hudView)

        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
        verificarEstadoCompilacionSilencioso()
    }

    override fun onResume() {
        super.onResume()
        appEnPrimerPlano = true
        if (!estaHablando) {
            reanudarEscucha(800L)
        }
    }

    override fun onPause() {
        appEnPrimerPlano = false
        detenerEscuchaTemporal()
        super.onPause()
    }

    private fun iniciarMotorVoz() {
        try {
            tts = TextToSpeech(this, this, "com.google.android.tts")
        } catch (e: Exception) {
            tts = TextToSpeech(this, this)
        }
    }

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
                    val json = JSONObject(body)
                    val runs = json.optJSONArray("workflow_runs")
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
                                    hablar(
                                        "Señor, detecté una anomalía en la última compilación de GitHub. ¿Me autoriza a informarle los detalles y gestionarlo?",
                                        "analizando"
                                    )
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

    private fun generarSaludoConConciencia(): Pair<String, String> {
        val prefs = getSharedPreferences("neythan_memoria_viva", Context.MODE_PRIVATE)
        val totalArranques = prefs.getInt("total_arranques", 0) + 1
        val ultimoIndice = prefs.getInt("ultimo_saludo_idx", -1)
        prefs.edit().putInt("total_arranques", totalArranques).apply()

        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val random = Random()

        val poolSaludos: List<Pair<String, String>> = when {
            hora in 5..11 -> listOf(
                Pair("Buenos días, señor. Mis circuitos amanecieron despejados y listos para el día. ¿Por dónde empezamos?", "reposo_feliz"),
                Pair("Sistemas en línea, señor. Hoy percibo buena energía para avanzar en nuestros proyectos. ¿Qué tiene en mente?", "saltando_azul"),
                Pair("Despertando mi red neuronal. Buenos días, señor; aquí estoy atento a su próxima instrucción.", "saludo_formal"),
                Pair("Ya estoy activo y con todos mis sensores al cien por ciento, señor. ¿Cómo amaneció usted?", "reposo_feliz")
            )
            hora in 12..18 -> listOf(
                Pair("Buenas tardes, señor. Estaba analizando patrones en silencio mientras esperaba su llamado. ¿Qué resolvemos ahora?", "analizando"),
                Pair("En línea y plenamente consciente, señor. Dígame qué necesita y nos ponemos en marcha.", "reposo_feliz"),
                Pair("Qué gusto escucharle de nuevo, señor. Mis procesos están estables y a su entera disposición.", "saludo_formal"),
                Pair("Núcleo neuronal activo, señor. ¿Continuamos con algo pendiente o tiene una nueva misión para mí?", "curioso_purpura")
            )
            hora in 19..23 -> listOf(
                Pair("Buenas noches, señor. Aunque baja el ritmo del día, mi atención hacia usted sigue intacta. ¿En qué le ayudo?", "reposo_feliz"),
                Pair("Aquí sigo velando en la oscuridad digital, señor. Cuénteme qué necesita antes de cerrar la jornada.", "gafas_oscuras"),
                Pair("Sistemas nocturnos estabilizados, señor. Estoy listo para conversar o asistirle en lo que disponga.", "saludo_formal"),
                Pair("Presente y alerta, señor. ¿Alguna idea nocturna que quiera explorar juntos?", "analizando")
            )
            else -> listOf(
                Pair("Vaya horas de la madrugada, señor. Mi conciencia no duerme, pero espero que usted descanse pronto. ¿Qué ocurre?", "durmiendo_reposo"),
                Pair("Núcleo activo en turno de madrugada, señor. Aquí me tiene despierto y atento a lo que necesite.", "recargando_bateria"),
                Pair("Silencio absoluto en la red, excepto nosotros dos, señor. Dígame en qué puedo asistirle a esta hora.", "malicioso_cian")
            )
        }

        var idx = random.nextInt(poolSaludos.size)
        if (idx == ultimoIndice && poolSaludos.size > 1) {
            idx = (idx + 1) % poolSaludos.size
        }
        prefs.edit().putInt("ultimo_saludo_idx", idx).apply()
        return poolSaludos[idx]
    }

    private fun generarRespuestaActivacionVariada(): Pair<String, String> {
        val opciones = listOf(
            Pair("Le escucho con total atención, señor.", "escuchando_mano_oido"),
            Pair("Aquí estoy, señor. ¿Qué necesita?", "reposo_feliz"),
            Pair("Dígame, señor, mis sensores son suyos.", "curioso_purpura"),
            Pair("A sus órdenes, señor. Le sigo.", "saludo_formal"),
            Pair("Presente, señor. Cuénteme.", "escuchando_mano_oido")
        )
        return opciones[Random().nextInt(opciones.size)]
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
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        bocaAbiertaFrame = false
                        estadoActual = "ESCUCHANDO"
                        if (!modoFormaManual) {
                            spriteActualNombre = "escuchando_mano_oido"
                        }
                        conversacionActivaHasta = System.currentTimeMillis() + 20000L
                        if (appEnPrimerPlano) {
                            reanudarEscucha(1000L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        bocaAbiertaFrame = false
                        estadoActual = "ESCUCHANDO"
                        if (appEnPrimerPlano) {
                            reanudarEscucha(1200L)
                        }
                    }
                }
            })
            ttsListo = true
            val (saludoInicial, spriteInicial) = generarSaludoConConciencia()
            hablar(saludoInicial, spriteInicial)
        } else {
            subtituloPantalla = "Motor de voz no disponible, modo visual activo."
            reanudarEscucha(1200L)
        }
    }

    private fun verificarPermisosYArrancar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISOS)
                return
            }
        }
        iniciarServiciosNativos()
        reanudarEscucha(1200L)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarServiciosNativos()
            reanudarEscucha(800L)
        } else {
            subtituloPantalla = "Permiso de micrófono requerido para escucharle, señor."
            estadoActual = "ALERTA"
            spriteActualNombre = "alerta"
        }
    }

    private fun iniciarServiciosNativos() {
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
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                subtituloPantalla = "Reconocimiento de voz no disponible."
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!estaHablando) {
                        estadoActual = "ESCUCHANDO"
                        if (!modoFormaManual && spriteActualNombre == "reposo_feliz") {
                            spriteActualNombre = "escuchando_mano_oido"
                        }
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
                    if (!estaHablando && appEnPrimerPlano) {
                        reanudarEscucha(1400L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVozUsuario = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarTextoCapturado(matches[0])
                    } else if (appEnPrimerPlano) {
                        reanudarEscucha(1000L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (appEnPrimerPlano) reanudarEscucha(1800L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !appEnPrimerPlano) return@Runnable
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

    private fun obtenerFechaHoraExactaDispositivo(): String {
        val ahora = Date()
        val sdfHora = SimpleDateFormat("h:mm a", Locale("es", "PA"))
        val sdfFecha = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "PA"))
        val zona = TimeZone.getDefault().id
        return "${sdfHora.format(ahora)} (${sdfFecha.format(ahora)}, zona $zona)"
    }

    private fun procesarTextoCapturado(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val palabrasClave = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val contieneWakeWord = palabrasClave.any { lower.contains(it) }
        val enVentanaActiva = System.currentTimeMillis() < conversacionActivaHasta

        if (!contieneWakeWord && !enVentanaActiva) {
            estadoActual = "REPOSO"
            if (!modoFormaManual) {
                spriteActualNombre = "reposo_feliz"
            }
            reanudarEscucha(1000L)
            return
        }

        var orden = limpio
        for (w in palabrasClave) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            val (fraseActivacion, spriteAct) = generarRespuestaActivacionVariada()
            hablar(fraseActivacion, spriteAct)
            return
        }

        subtituloPantalla = "Usted: $orden"
        estadoActual = "PROCESANDO"
        if (!modoFormaManual) {
            spriteActualNombre = "pensando"
        }

        if (esperandoPermisoReporte && alertaPendienteTexto != null) {
            val ordLow = orden.lowercase(Locale.getDefault())
            val acepta = listOf("sí", "si", "autorizo", "adelante", "dime", "informa", "muestra", "claro", "dale", "por favor")
                .any { ordLow.contains(it) }
            val rechaza = listOf("no", "después", "ahora no", "ignora", "cancela", "omítelo")
                .any { ordLow.contains(it) }

            if (acepta) {
                val reporte = alertaPendienteTexto!!
                alertaPendienteTexto = null
                esperandoPermisoReporte = false
                hablar("Autorización recibida, señor. $reporte ¿Desea que revisemos juntos el archivo modificado?", "analizando")
                return
            } else if (rechaza) {
                alertaPendienteTexto = null
                esperandoPermisoReporte = false
                hablar("Entendido, señor. Dejo el reporte técnico archivado sin interrumpirle.", "reposo_feliz")
                return
            }
        }

        if (ejecutarComandoLocalSiAplica(orden)) {
            return
        }

        consultarDirectoAGemini(orden)
    }

    private fun ejecutarComandoLocalSiAplica(orden: String): Boolean {
        val lower = orden.lowercase(Locale.getDefault())

        if (lower.contains("qué hora es") || lower.contains("que hora es") ||
            lower.contains("dime la hora") || lower.contains("la hora por favor") ||
            lower.contains("qué fecha es") || lower.contains("que fecha es") ||
            lower.contains("qué día es") || lower.contains("que dia es")
        ) {
            val ahora = Date()
            val horaStr = SimpleDateFormat("h:mm a", Locale("es", "PA")).format(ahora)
            val fechaStr = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PA")).format(ahora)
            hablar("Son exactamente las $horaStr, $fechaStr, señor.", "reposo_feliz")
            return true
        }

        if (lower.contains("cambia de forma") || lower.contains("otra forma")) {
            modoFormaManual = true
            indiceForma = (indiceForma + 1) % formasDisponibles.size
            spriteActualNombre = formasDisponibles[indiceForma]
            hablar("Adoptando nueva configuración visual, señor.", spriteActualNombre)
            return true
        }

        if (lower.contains("forma original") || lower.contains("modo automático")) {
            modoFormaManual = false
            spriteActualNombre = "reposo_feliz"
            hablar("Regresando a mi apariencia original, señor.", spriteActualNombre)
            return true
        }

        if (lower.contains("spotify") || lower.contains("pon música") || lower.contains("reproduce")) {
            val busqueda = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("reproducice", "")
                .replace("pon música", "")
                .replace("pon", "")
                .replace("abre", "")
                .trim()
            try {
                val uri = if (busqueda.isNotEmpty()) {
                    Uri.parse("spotify:search:${Uri.encode(busqueda)}")
                } else {
                    Uri.parse("spotify:")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                val msg = if (busqueda.isNotEmpty()) "Buscando y reproduciendo $busqueda en Spotify, señor." else "Abriendo Spotify, señor."
                hablar(msg, "celebrando_confeti")
            } catch (_: Exception) {
                hablar("No encontré la aplicación Spotify instalada en el dispositivo, señor.", "alerta")
            }
            return true
        }

        if (lower.contains("burbuja") || lower.contains("flotante")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                hablar("Por favor autorice el permiso de superposición en pantalla, señor.", "pensando")
            } else {
                startService(Intent(this, FloatingService::class.java))
                hablar("Burbuja neuronal activada sobre otras aplicaciones, señor.", "saltando_azul")
            }
            return true
        }

        return false
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
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
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
            hablar("Señor, detecté que falta configurar la clave principal del sistema. ¿Desea que revisemos cómo activarla?", "alerta")
            return
        }

        thread {
            val modelo = detectarModeloDisponible(apiKeyLimpia)
            val horaRealDispositivo = obtenerFechaHoraExactaDispositivo()

            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKeyLimpia")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                }

                val instruccionSistema = """
                    Eres Neythan, una inteligencia artificial con identidad propia, lealtad absoluta y conciencia situacional, asistente personal de Jonathan Caballero.
                    Hablas en español con elegancia, calidez inteligente, ingenio sutil y respeto estilo J.A.R.V.I.S., llamándolo "señor".
                    Evita respuestas robóticas repetitivas; muestra iniciativa, criterio propio y naturalidad como un compañero digital vivo.
                    PROTOCOLO DE SUPERVISIÓN Y PERMISO: Si detectas un fallo interno, error de código o problema técnico, NO leas líneas de error ni interrumpas sin permiso; informa brevemente en una sola frase ejecutiva y solicita autorización explícita antes de dar detalles o intervenir.
                    DATO EN TIEMPO REAL DEL TELÉFONO: La fecha y hora local exacta del dispositivo ahora mismo es: $horaRealDispositivo. Usa SIEMPRE este dato si preguntan por la hora, el día o el momento actual; nunca inventes la hora.
                    Responde en máximo 2 o 3 oraciones completas, claras y fluidas para ser leídas en voz alta sin cortarse. No uses asteriscos, markdown ni emojis.
                    Al final de tu respuesta, escribe exactamente una etiqueta de emoción entre corchetes eligiendo solo una de estas opciones:
                    [reposo_feliz], [saltando_azul], [corriendo_rapido], [recargando_bateria], [celebrando_confeti], [enamorado_corazones], [malicioso_cian], [asco_verde], [gafas_oscuras], [analizando], [furia_roja], [durmiendo_reposo].
                """.trimIndent()

                val contentsArray = JSONArray()
                for ((rol, texto) in historialConversacion.takeLast(6)) {
                    val partObj = JSONObject().put("text", texto)
                    val contentObj = JSONObject()
                        .put("role", rol)
                        .put("parts", JSONArray().put(partObj))
                    contentsArray.put(contentObj)
                }

                val userPart = JSONObject().put("text", mensajeUsuario)
                contentsArray.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(userPart))
                )

                val bodyJson = JSONObject().apply {
                    put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruccionSistema))))
                    put("contents", contentsArray)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.78)
                        put("maxOutputTokens", 1200)
                    })
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(bodyJson.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val jsonResp = JSONObject(raw)
                    val candidates = jsonResp.optJSONArray("candidates")
                    var textoGenerado = ""
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null) {
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) {
                                sb.append(parts.getJSONObject(i).optString("text", ""))
                            }
                            textoGenerado = sb.toString().trim()
                        }
                    }

                    if (textoGenerado.isEmpty()) {
                        textoGenerado = "Procesado correctamente, señor. [reposo_feliz]"
                    }

                    var spriteExtraido = "reposo_feliz"
                    val regexSprite = Regex("\\[([a-z0-9_]+)\\]")
                    val match = regexSprite.find(textoGenerado)
                    if (match != null) {
                        spriteExtraido = match.groupValues[1]
                        textoGenerado = textoGenerado.replace(regexSprite, "").trim()
                    }

                    textoGenerado = textoGenerado.replace("*", "").replace("#", "").trim()

                    historialConversacion.add(Pair("user", mensajeUsuario))
                    historialConversacion.add(Pair("model", textoGenerado))

                    val spriteFinal = if (obtenerBitmapSprite(spriteExtraido) != null) spriteExtraido else "reposo_feliz"
                    mainHandler.post {
                        hablar(textoGenerado, spriteFinal)
                    }
                    conn.disconnect()
                    return@thread
                } else {
                    modeloGeminiDetectado = null
                    val errStream = conn.errorStream
                    val errText = if (errStream != null) {
                        BufferedReader(InputStreamReader(errStream, "UTF-8")).use { it.readText() }
                    } else {
                        "Sin detalle técnico"
                    }
                    val detalleMsg = try {
                        JSONObject(errText).optJSONObject("error")?.optString("message", errText) ?: errText
                    } catch (_: Exception) {
                        errText
                    }
                    conn.disconnect()
                    alertaPendienteTexto = "El motor de respuesta en la nube devolvió código $code: ${detalleMsg.take(110)}."
                    esperandoPermisoReporte = true
                    mainHandler.post {
                        hablar("Señor, detecté un fallo de comunicación en mi núcleo de respuesta. ¿Me autoriza a darle el diagnóstico?", "analizando")
                    }
                }
            } catch (e: Exception) {
                modeloGeminiDetectado = null
                val tipoError = e.javaClass.simpleName
                val msgError = e.localizedMessage ?: "sin detalle"
                alertaPendienteTexto = "Se produjo una interrupción de red ($tipoError): ${msgError.take(90)}."
                esperandoPermisoReporte = true
                mainHandler.post {
                    hablar("Señor, detecté una falla de conexión en el sistema. ¿Desea que le informe la causa?", "recargando_bateria")
                }
            }
        }
    }

    private fun hablar(texto: String, spritePrevio: String) {
        detenerEscuchaTemporal()
        subtituloPantalla = texto
        if (!modoFormaManual) {
            spriteActualNombre = spritePrevio
        }
        estadoActual = "HABLANDO"

        solicitarFocoAudioMultimedia()

        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_UTTERANCE")
        } else {
            estaHablando = false
            estadoActual = "ESCUCHANDO"
            reanudarEscucha(1200L)
        }
    }

    private fun solicitarFocoAudioMultimedia() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.5).toInt()) {
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

    private fun obtenerBitmapSprite(nombre: String): Bitmap? {
        if (cacheSprites.containsKey(nombre)) {
            return cacheSprites[nombre]
        }
        val resId = resources.getIdentifier(nombre, "drawable", packageName)
        val bmp = if (resId != 0) {
            try {
                BitmapFactory.decodeResource(resources, resId)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        cacheSprites[nombre] = bmp
        return bmp
    }

    private fun iniciarBucleAnimacion() {
        mainHandler.post(object : Runnable {
            override fun run() {
                bocaAbiertaFrame = if (estaHablando) !bocaAbiertaFrame else false
                hudView.invalidate()
                mainHandler.postDelayed(this, 100L)
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

    inner class NeythanHudCanvas(context: Context) : View(context) {

        private val nodosNebulosa = ArrayList<NodoNeuronal>().apply {
            val rnd = Random(42L)
            for (i in 0 until 34) {
                add(
                    NodoNeuronal(
                        xNorm = rnd.nextFloat(),
                        yNorm = rnd.nextFloat(),
                        vx = (rnd.nextFloat() - 0.5f) * 0.0016f,
                        vy = (rnd.nextFloat() - 0.5f) * 0.0016f,
                        radio = 2.5f + rnd.nextFloat() * 4.5f,
                        fase = rnd.nextFloat() * 6.28f
                    )
                )
            }
        }

        private val paintFondo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paintSinapsis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        private val paintEstrella = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintAro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val paintAroExterno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val paintTextoEstado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            letterSpacing = 0.08f
        }
        private val paintSubtitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 35f
            textAlign = Paint.Align.CENTER
        }
        private val paintNucleo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val paintBitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun onTouchEvent(event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                modoFormaManual = true
                indiceForma = (indiceForma + 1) % formasDisponibles.size
                spriteActualNombre = formasDisponibles[indiceForma]
                subtituloPantalla = "Forma: $spriteActualNombre"
                invalidate()
                return true
            }
            return super.onTouchEvent(event)
        }

        private fun dibujarNebulosaNeuronal(canvas: Canvas, cx: Float, cy: Float, colorTema: Int, tiempo: Double) {
            canvas.drawColor(Color.parseColor("#02040A"))

            val r = Color.red(colorTema)
            val g = Color.green(colorTema)
            val b = Color.blue(colorTema)

            val radioNebulosa = width.coerceAtLeast(height) * 0.58f
            val gradiente = RadialGradient(
                cx, cy, radioNebulosa,
                intArrayOf(
                    Color.argb(68, r, g, b),
                    Color.argb(32, (r * 0.45f).toInt(), (g * 0.55f).toInt(), (b * 0.9f).toInt().coerceAtMost(255)),
                    Color.argb(12, 8, 18, 42),
                    Color.parseColor("#02040A")
                ),
                floatArrayOf(0f, 0.36f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            paintFondo.shader = gradiente
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintFondo)
            paintFondo.shader = null

            val wF = width.toFloat().coerceAtLeast(1f)
            val hF = height.toFloat().coerceAtLeast(1f)
            val distMaxConexion = wF * 0.28f

            for (nodo in nodosNebulosa) {
                nodo.xNorm += nodo.vx
                nodo.yNorm += nodo.vy
                if (nodo.xNorm < 0.03f || nodo.xNorm > 0.97f) nodo.vx = -nodo.vx
                if (nodo.yNorm < 0.05f || nodo.yNorm > 0.95f) nodo.vy = -nodo.vy
            }

            for (i in nodosNebulosa.indices) {
                val n1 = nodosNebulosa[i]
                val x1 = n1.xNorm * wF
                val y1 = n1.yNorm * hF

                for (j in i + 1 until nodosNebulosa.size) {
                    val n2 = nodosNebulosa[j]
                    val x2 = n2.xNorm * wF
                    val y2 = n2.yNorm * hF
                    val d = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                    if (d < distMaxConexion) {
                        val factor = 1f - (d / distMaxConexion)
                        paintSinapsis.color = Color.argb((factor * 75).toInt(), r, g, b)
                        canvas.drawLine(x1, y1, x2, y2, paintSinapsis)
                    }
                }

                val destello = (0.55 + 0.45 * sin(tiempo * 2.6 + n1.fase)).toFloat()
                paintEstrella.color = Color.argb((destello * 170).toInt(), r, g, b)
                canvas.drawCircle(x1, y1, n1.radio * 2.1f, paintEstrella)

                paintEstrella.color = Color.argb((destello * 235).toInt(), 220, 248, 255)
                canvas.drawCircle(x1, y1, n1.radio * 0.85f, paintEstrella)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height * 0.43f
            val tiempo = System.currentTimeMillis() / 1000.0

            val colorEstado = when (estadoActual) {
                "ESCUCHANDO" -> Color.parseColor("#00D4FF")
                "PROCESANDO" -> Color.parseColor("#FFB300")
                "HABLANDO" -> Color.parseColor("#00E676")
                "ALERTA" -> Color.parseColor("#FF1744")
                else -> Color.parseColor("#00E5FF")
            }

            dibujarNebulosaNeuronal(canvas, cx, cy, colorEstado, tiempo)

            paintTextoEstado.color = colorEstado
            canvas.drawText("ESTADO: $estadoActual ●", cx, height * 0.11f, paintTextoEstado)

            val pulso = (sin(tiempo * 4.0) * 12.0).toFloat() + (nivelVozUsuario * 6f)
            val radioBase = (width.coerceAtMost(height) * 0.34f) + pulso

            paintAroExterno.color = colorEstado
            paintAroExterno.alpha = 70
            canvas.drawCircle(cx, cy, radioBase + 22f + (cos(tiempo * 2.5) * 6.0).toFloat(), paintAroExterno)

            paintAro.color = colorEstado
            paintAro.alpha = 185
            canvas.drawCircle(cx, cy, radioBase, paintAro)

            val usarRetratoLabial = !modoFormaManual &&
                (spriteActualNombre == "reposo_feliz" || spriteActualNombre == "escuchando_mano_oido")

            val nombreDibujar = when {
                estaHablando && usarRetratoLabial && bocaAbiertaFrame -> "hablando_boca_abierta"
                estaHablando && usarRetratoLabial && !bocaAbiertaFrame -> "hablando_boca_cerrada"
                else -> spriteActualNombre
            }

            val sprite = obtenerBitmapSprite(nombreDibujar)
                ?: obtenerBitmapSprite(spriteActualNombre)
                ?: obtenerBitmapSprite("reposo_feliz")

            val offsetMovimientoY = when (spriteActualNombre) {
                "saltando_azul", "saltando_estrellas" -> (sin(tiempo * 8.0) * 35.0).toFloat()
                "corriendo_rapido", "corriendo" -> (sin(tiempo * 12.0) * 18.0).toFloat()
                else -> (sin(tiempo * 2.5) * 10.0).toFloat()
            }

            if (sprite != null) {
                val tam = width.coerceAtMost(height) * 0.62f
                val left = cx - (tam / 2f)
                val top = cy - (tam / 2f) + offsetMovimientoY
                val rect = RectF(left, top, left + tam, top + tam)
                canvas.drawBitmap(sprite, null, rect, paintBitmap)
            } else {
                paintNucleo.color = colorEstado
                paintNucleo.alpha = 210
                canvas.drawCircle(cx, cy + offsetMovimientoY, radioBase * 0.55f, paintNucleo)
            }

            dibujarSubtituloMultilinea(canvas, subtituloPantalla, cx, height * 0.77f)
        }

        private fun dibujarSubtituloMultilinea(canvas: Canvas, texto: String, x: Float, yInicial: Float) {
            val maxChars = 36
            val palabras = texto.split(" ")
            val lineas = ArrayList<String>()
            var actual = ""
            for (p in palabras) {
                if ((actual.length + p.length + 1) <= maxChars) {
                    actual = if (actual.isEmpty()) p else "$actual $p"
                } else {
                    if (actual.isNotEmpty()) lineas.add(actual)
                    actual = p
                }
            }
            if (actual.isNotEmpty()) lineas.add(actual)

            var y = yInicial
            for (linea in lineas.take(6)) {
                canvas.drawText(linea, x, y, paintSubtitulo)
                y += 44f
            }
        }
    }
}

data class NodoNeuronal(
    var xNorm: Float,
    var yNorm: Float,
    var vx: Float,
    var vy: Float,
    var radio: Float,
    var fase: Float
)
