package com.jonathan.neythan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
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
import java.util.Random
import java.util.TimeZone
import kotlin.concurrent.thread

class NeythanBackgroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "neythan_core_persistent_channel"
        const val NOTIF_ID = 2026
        const val ACTION_STOP_SERVICE = "com.jonathan.neythan.ACTION_STOP_SERVICE"
        val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var estaHablando = false
    private var servicioActivo = false
    private var conversacionActivaHasta = 0L
    private var backoffMs = 1500L
    private var modeloGeminiDetectado: String? = null

    private val historialBg = ArrayList<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate()
        servicioActivo = true
        crearCanalNotificacion()
        iniciarPrimerPlanoSeguro("Núcleo en vigilancia activa — diga 'Neythan' o 'Despierta'")
        adquirirWakeLockLigero()
        iniciarMotorVoz()
        programarEscucha(1200L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            servicioActivo = false
            stopSelf()
            return START_NOT_STICKY
        }
        servicioActivo = true
        if (!estaHablando) {
            programarEscucha(900L)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Reprograma el servicio si el usuario cierra la app desde recientes
        if (servicioActivo) {
            val restartIntent = Intent(applicationContext, NeythanBackgroundService::class.java)
            val pending = PendingIntent.getService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1500L,
                pending
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun adquirirWakeLockLigero() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Neythan::NeuralCoreWakeLock").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neythan Núcleo Neuronal",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Escucha continua en segundo plano sin sobrecalentamiento"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun construirNotificacion(estadoTexto: String): Notification {
        val abrirAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val piAbrir = PendingIntent.getActivity(
            this,
            0,
            abrirAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detenerIntent = Intent(this, NeythanBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val piDetener = PendingIntent.getService(
            this,
            1,
            detenerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("N.E.Y.T.H.A.N. — Segundo Plano Activo")
            .setContentText(estadoTexto)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(piAbrir)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Pausar", piDetener)
            .setOngoing(true)
            .build()
    }

    private fun iniciarPrimerPlanoSeguro(textoInicial: String) {
        val notif = construirNotificacion(textoInicial)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun actualizarNotificacion(texto: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, construirNotificacion(texto))
        } catch (_: Exception) {
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
            tts?.setSpeechRate(1.06f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = true
                        actualizarNotificacion("Respondiendo por voz...")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        conversacionActivaHasta = System.currentTimeMillis() + 20000L
                        actualizarNotificacion("En escucha pasiva — diga 'Neythan'")
                        if (servicioActivo) {
                            programarEscucha(1000L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        if (servicioActivo) {
                            programarEscucha(1400L)
                        }
                    }
                }
            })
            ttsListo = true
        }
    }

    private fun tienePermisoMicrofono(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun configurarReconocedor() {
        try {
            speechRecognizer?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    backoffMs = 1500L
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (!estaHablando && servicioActivo) {
                        // Retroceso adaptativo para evitar sobrecalentamiento de CPU/batería
                        backoffMs = (backoffMs + 500L).coerceAtMost(3500L)
                        programarEscucha(backoffMs)
                    }
                }

                override fun onResults(results: Bundle?) {
                    backoffMs = 1500L
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarVozSegundoPlano(matches[0])
                    } else if (servicioActivo && !estaHablando) {
                        programarEscucha(1400L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            if (servicioActivo) programarEscucha(2500L)
        }
    }

    private val tareaEscuchaBg = Runnable {
        if (!servicioActivo || estaHablando) return@Runnable
        if (!tienePermisoMicrofono()) {
            actualizarNotificacion("Permiso de micrófono requerido en ajustes")
            programarEscucha(5000L)
            return@Runnable
        }
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
            programarEscucha(2500L)
        }
    }

    private fun programarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscuchaBg)
        mainHandler.postDelayed(tareaEscuchaBg, retrasoMs)
    }

    private fun detenerEscuchaTemporal() {
        mainHandler.removeCallbacks(tareaEscuchaBg)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun procesarVozSegundoPlano(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val wakeWords = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val activado = wakeWords.any { lower.contains(it) }
        val enVentana = System.currentTimeMillis() < conversacionActivaHasta

        if (!activado && !enVentana) {
            programarEscucha(1500L)
            return
        }

        var orden = limpio
        for (w in wakeWords) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            val saludo = listOf(
                "Le escucho en segundo plano, señor.",
                "Presente, señor. ¿Qué necesita?",
                "A sus órdenes, señor. Dígame."
            )[Random().nextInt(3)]
            hablarEnSegundoPlano(saludo)
            return
        }

        ejecutarOrdenSegundoPlano(orden)
    }

    private fun ejecutarOrdenSegundoPlano(orden: String) {
        val low = orden.lowercase(Locale.getDefault())
        actualizarNotificacion("Procesando: $orden")

        // 1. Abrir interfaz principal HUD
        if (low.contains("abre tu interfaz") || low.contains("muéstrate") || low.contains("muestrate") ||
            low.contains("abre la app") || low.contains("pantalla principal")
        ) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            hablarEnSegundoPlano("Desplegando interfaz HUD principal, señor.")
            return
        }

        // 2. Controles multimedia directos
        if (low.contains("pausa") || low.contains("detén la música") || low.contains("deten la musica")) {
            enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PAUSE)
            hablarEnSegundoPlano("Música pausada, señor.")
            return
        }
        if (low.contains("siguiente canción") || low.contains("siguiente cancion") || low.contains("pasa la canción")) {
            enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT)
            hablarEnSegundoPlano("Siguiente pista, señor.")
            return
        }
        if (low.contains("canción anterior") || low.contains("cancion anterior") || low.contains("regresa la canción")) {
            enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            hablarEnSegundoPlano("Pista anterior, señor.")
            return
        }

        // 3. Reproducción automática en Spotify desde segundo plano
        if (low.contains("spotify") || low.contains("pon música") || low.contains("pon musica") ||
            low.contains("reproduce") || low.contains("ponme ") || low.contains("escuchar ")
        ) {
            reproducirSpotifyDesdeFondo(orden)
            return
        }

        // 4. Hora exacta del dispositivo
        if (low.contains("qué hora es") || low.contains("que hora es") || low.contains("dime la hora")) {
            val horaStr = SimpleDateFormat("h:mm a", Locale("es", "PA")).format(Date())
            hablarEnSegundoPlano("Son las $horaStr, señor.")
            return
        }

        // 5. Consulta neuronal directa a Gemini en segundo plano
        consultarGeminiEnFondo(orden)
    }

    private fun reproducirSpotifyDesdeFondo(ordenRaw: String) {
        val consulta = ordenRaw
            .replace(Regex("(?i)\\b(en spotify|spotify|reproduce|reproducir|pon música de|pon musica de|pon música|pon musica|pon|escuchar|toca|play)\\b"), "")
            .trim()

        try {
            if (consulta.isNotEmpty()) {
                val mediaIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                    setPackage("com.spotify.music")
                    putExtra(SearchManager.QUERY, consulta)
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(mediaIntent)
                mainHandler.postDelayed({ enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 2400L)
                mainHandler.postDelayed({ enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 3800L)
                hablarEnSegundoPlano("Reproduciendo $consulta en Spotify, señor.")
            } else {
                val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                    mainHandler.postDelayed({ enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 1800L)
                    hablarEnSegundoPlano("Iniciando Spotify, señor.")
                } else {
                    hablarEnSegundoPlano("No encontré Spotify instalado, señor.")
                }
            }
        } catch (_: Exception) {
            try {
                val uri = if (consulta.isNotEmpty()) Uri.parse("spotify:search:${Uri.encode(consulta)}") else Uri.parse("spotify:")
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                mainHandler.postDelayed({ enviarTeclaMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 2600L)
                hablarEnSegundoPlano("Abriendo Spotify con $consulta, señor.")
            } catch (_: Exception) {
                hablarEnSegundoPlano("No fue posible iniciar Spotify desde segundo plano, señor.")
            }
        }
    }

    private fun enviarTeclaMultimedia(keyCode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (_: Exception) {
        }
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

    private fun consultarGeminiEnFondo(mensajeUsuario: String) {
        val apiKeyLimpia = GEMINI_API_KEY.trim()
        if (apiKeyLimpia.isEmpty() || apiKeyLimpia.contains("PON_AQUI")) {
            hablarEnSegundoPlano("Señor, falta configurar la clave principal del sistema.")
            return
        }

        thread {
            val modelo = detectarModeloDisponible(apiKeyLimpia)
            val horaExacta = SimpleDateFormat("h:mm a (EEEE d 'de' MMMM)", Locale("es", "PA")).format(Date())
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
                    Eres Neythan, asistente de inteligencia artificial estilo J.A.R.V.I.S. operando en segundo plano en el teléfono de Jonathan Caballero.
                    Hablas en español con elegancia, calidez inteligente y respeto, llamándolo "señor".
                    HORA LOCAL REAL DEL TELÉFONO: $horaExacta, zona $zona.
                    Responde en máximo 2 oraciones claras y fluidas para ser escuchadas sin interrumpir lo que hace el usuario. Sin asteriscos ni emojis.
                """.trimIndent()

                val contents = JSONArray()
                for ((rol, txt) in historialBg.takeLast(4)) {
                    contents.put(
                        JSONObject()
                            .put("role", rol)
                            .put("parts", JSONArray().put(JSONObject().put("text", txt)))
                    )
                }
                contents.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
                )

                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruccion))))
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.75)
                        put("maxOutputTokens", 900)
                    })
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(body.toString())
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
                    if (resp.isEmpty()) resp = "Procesado en segundo plano, señor."
                    historialBg.add(Pair("user", mensajeUsuario))
                    historialBg.add(Pair("model", resp))
                    mainHandler.post { hablarEnSegundoPlano(resp) }
                } else {
                    modeloGeminiDetectado = null
                    mainHandler.post {
                        hablarEnSegundoPlano("Señor, detecté una anomalía de conexión en segundo plano. ¿Desea que revise el diagnóstico?")
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                modeloGeminiDetectado = null
                mainHandler.post {
                    hablarEnSegundoPlano("Señor, hay una interrupción de red en el dispositivo.")
                }
            }
        }
    }

    private fun hablarEnSegundoPlano(texto: String) {
        detenerEscuchaTemporal()
        solicitarFocoAudio()
        if (ttsListo && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_BG_UTTERANCE")
        } else {
            estaHablando = false
            programarEscucha(1500L)
        }
    }

    private fun solicitarFocoAudio() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    override fun onDestroy() {
        servicioActivo = false
        detenerEscuchaTemporal()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
