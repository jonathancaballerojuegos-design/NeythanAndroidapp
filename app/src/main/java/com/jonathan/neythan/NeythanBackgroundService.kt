package com.jonathan.neythan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.os.SystemClock
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
import kotlin.concurrent.thread

class NeythanBackgroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "neythan_bg_channel_v3"
        const val NOTIF_ID = 7001
        const val ACTION_PAUSAR_ESCUCHA = "com.jonathan.neythan.PAUSAR_ESCUCHA"
        const val ACTION_REANUDAR_ESCUCHA = "com.jonathan.neythan.REANUDAR_ESCUCHA"
        @Volatile
        var pantallaPrincipalActiva: Boolean = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estaHablando = false
    private var escuchaPausadaPorUI = false
    private var conversacionActivaHasta = 0L
    private var backoffMs = 1600L

    private val historialMemoria = ArrayList<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate()
        iniciarNotificacionPrimerPlano()
        iniciarMotorVoz()
        programarEscucha(1400L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSAR_ESCUCHA -> {
                escuchaPausadaPorUI = true
                liberarMicrofonoEstricto()
            }
            ACTION_REANUDAR_ESCUCHA -> {
                escuchaPausadaPorUI = false
                if (!estaHablando) {
                    programarEscucha(900L)
                }
            }
            else -> {
                if (!pantallaPrincipalActiva && !escuchaPausadaPorUI && !estaHablando) {
                    programarEscucha(1200L)
                }
            }
        }
        return START_STICKY
    }

    private fun iniciarNotificacionPrimerPlano() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Núcleo Activo Neythan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Escucha continua y núcleos básicos de Neythan en segundo plano"
                setShowBadge(false)
            }
            manager.createNotificationChannel(canal)
        }

        val abrirAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            abrirAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notificacion = builder
            .setContentTitle("N.E.Y.T.H.A.N. — Núcleos Básicos Activos")
            .setContentText("Escucha silenciosa en segundo plano lista, señor.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notificacion)
        }
    }

    private fun iniciarMotorVoz() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
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
            tts?.setSpeechRate(1.04f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = true
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        if (utteranceId == "NEYTHAN_BG_FINAL") {
                            conversacionActivaHasta = System.currentTimeMillis() + 18000L
                            backoffMs = 1500L
                            if (!pantallaPrincipalActiva && !escuchaPausadaPorUI) {
                                programarEscucha(1100L)
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        if (!pantallaPrincipalActiva && !escuchaPausadaPorUI) {
                            programarEscucha(1800L)
                        }
                    }
                }
            })
            ttsListo = true
        } else {
            ttsListo = false
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

    private fun liberarMicrofonoEstricto() {
        mainHandler.removeCallbacks(tareaEscucha)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        silenciarTonosSistema(false)
    }

    private fun inicializarReconocedor() {
        liberarMicrofonoEstricto()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    backoffMs = (backoffMs + 400L).coerceAtMost(3600L)
                    if (!estaHablando && !pantallaPrincipalActiva && !escuchaPausadaPorUI) {
                        programarEscucha(backoffMs)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                        backoffMs = 1500L
                        procesarVozSegundoPlano(matches[0])
                    } else {
                        backoffMs = (backoffMs + 350L).coerceAtMost(3600L)
                        if (!estaHablando && !pantallaPrincipalActiva && !escuchaPausadaPorUI) {
                            programarEscucha(backoffMs)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            programarEscucha(2500L)
        }
    }

    private fun programarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || pantallaPrincipalActiva || escuchaPausadaPorUI) return@Runnable
        try {
            silenciarTonosSistema(true)
            if (speechRecognizer == null) {
                inicializarReconocedor()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            inicializarReconocedor()
            programarEscucha(2200L)
        }
    }

    private fun procesarVozSegundoPlano(textoRaw: String) {
        val limpio = textoRaw.trim()
        val lower = limpio.lowercase(Locale.getDefault())
        val wakeWords = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val detectado = wakeWords.any { lower.contains(it) }
        val enVentanaActiva = System.currentTimeMillis() < conversacionActivaHasta

        if (!detectado && !enVentanaActiva) {
            backoffMs = (backoffMs + 300L).coerceAtMost(3600L)
            programarEscucha(backoffMs)
            return
        }

        var orden = limpio
        for (w in wakeWords) {
            orden = orden.replace(Regex("(?i)\b$w\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            hablarEnSegundoPlano("A sus órdenes en segundo plano, señor. ¿Qué necesita?", esAvisoIntermedio = false)
            return
        }

        if (ejecutarComandoRapido(orden)) {
            return
        }

        val esAnalisisLargo = esOrdenDeAnalisis(orden)
        if (esAnalisisLargo) {
            hablarEnSegundoPlano(
                "Iniciando análisis solicitado, señor. Le informaré en cuanto culmine.",
                esAvisoIntermedio = true
            )
        }

        consultarGeminiDirecto(orden, esAnalisisLargo)
    }

    private fun esOrdenDeAnalisis(orden: String): Boolean {
        val l = orden.lowercase(Locale.getDefault())
        val disparadores = listOf(
            "analiza", "análisis", "analisis", "diagnostica", "diagnóstico", "diagnostico",
            "revisa", "evalúa", "evalua", "audita", "investiga", "compara", "resume"
        )
        return disparadores.any { l.contains(it) }
    }

    private fun ejecutarComandoRapido(orden: String): Boolean {
        val lower = orden.lowercase(Locale.getDefault())

        if (lower.contains("qué hora es") || lower.contains("que hora es") || lower.contains("dime la hora")) {
            val sdf = SimpleDateFormat("hh:mm a", Locale("es", "ES"))
            val horaActual = sdf.format(Date())
            hablarEnSegundoPlano("Son las $horaActual, señor.", esAvisoIntermedio = false)
            return true
        }

        if (lower.contains("qué fecha es") || lower.contains("que fecha es") || lower.contains("qué día es") || lower.contains("que dia es")) {
            val sdf = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            val fechaActual = sdf.format(Date())
            hablarEnSegundoPlano("Hoy es $fechaActual, señor.", esAvisoIntermedio = false)
            return true
        }

        if (lower.contains("muéstrate") || lower.contains("muestrate") || lower.contains("abre tu interfaz") || lower.contains("abre neythan")) {
            hablarEnSegundoPlano("Desplegando interfaz táctica en pantalla, señor.", esAvisoIntermedio = false)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            return true
        }

        if (lower.contains("pausa") || lower.contains("detén la música") || lower.contains("deten la musica")) {
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PAUSE)
            hablarEnSegundoPlano("Reproducción pausada, señor.", esAvisoIntermedio = false)
            return true
        }

        if (lower.contains("siguiente canción") || lower.contains("siguiente cancion") || lower.contains("cambia de canción")) {
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT)
            hablarEnSegundoPlano("Siguiente pista en reproducción, señor.", esAvisoIntermedio = false)
            return true
        }

        if (lower.contains("canción anterior") || lower.contains("cancion anterior")) {
            enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            hablarEnSegundoPlano("Regresando a la pista anterior, señor.", esAvisoIntermedio = false)
            return true
        }

        if (lower.contains("spotify") || lower.contains("pon música") || lower.contains("pon musica") || lower.contains("reproduce")) {
            val busqueda = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("pon música", "")
                .replace("pon musica", "")
                .replace("pon", "")
                .replace("abre", "")
                .trim()
            reproducirEnSpotify(busqueda)
            return true
        }

        return false
    }

    private fun reproducirEnSpotify(busqueda: String) {
        try {
            if (busqueda.isNotEmpty()) {
                val playSearchIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                    setPackage("com.spotify.music")
                    putExtra(android.app.SearchManager.QUERY, busqueda)
                    putExtra("android.intent.extra.focus", "vnd.android.cursor.item/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(playSearchIntent)
                mainHandler.postDelayed({ enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 1600L)
                hablarEnSegundoPlano("Reproduciendo $busqueda en Spotify, señor.", esAvisoIntermedio = false)
            } else {
                val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launch != null) {
                    startActivity(launch)
                    mainHandler.postDelayed({ enviarEventoMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY) }, 1500L)
                    hablarEnSegundoPlano("Iniciando reproducción en Spotify, señor.", esAvisoIntermedio = false)
                } else {
                    val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(uriIntent)
                    hablarEnSegundoPlano("Abriendo Spotify, señor.", esAvisoIntermedio = false)
                }
            }
        } catch (_: Exception) {
            hablarEnSegundoPlano("No logré iniciar Spotify en este momento, señor.", esAvisoIntermedio = false)
        }
    }

    private fun enviarEventoMultimedia(keyCode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (_: Exception) {
        }
    }

    private fun obtenerFechaHoraExactaDispositivo(): String {
        val sdf = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, hh:mm a (zzzz)", Locale("es", "ES"))
        return sdf.format(Date())
    }

    private fun consultarGeminiDirecto(mensajeUsuario: String, esAnalisisLargo: Boolean) {
        liberarMicrofonoEstricto()
        thread {
            val apiKey = try {
                BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) {
                ""
            }

            if (apiKey.isEmpty()) {
                mainHandler.post {
                    hablarEnSegundoPlano(
                        "La clave de Gemini no está configurada en esta compilación, señor.",
                        esAvisoIntermedio = false
                    )
                }
                return@thread
            }

            val modelos = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")
            val fechaHora = obtenerFechaHoraExactaDispositivo()
            val instruccionSistema = """
                Eres N.E.Y.T.H.A.N., un asistente virtual táctico inspirado en J.A.R.V.I.S.
                Tratas al usuario con respeto formal como 'señor', con personalidad viva, precisa y ejecutiva.
                Actualmente estás respondiendo en modo de segundo plano por voz, por lo que tus respuestas deben ser claras, completas y naturales para ser escuchadas sin cortarse.
                Fecha y hora exacta actual del dispositivo: $fechaHora. Usa siempre este dato si se pregunta por la hora o fecha.
                Si el usuario solicita un análisis, entrega una síntesis estructurada y concluye confirmando que el análisis ha culminado.
            """.trimIndent()

            var respuestaFinal: String? = null

            for (modelo in modelos) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKey"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        connectTimeout = 12000
                        readTimeout = 22000
                        doOutput = true
                    }

                    val contentsArray = JSONArray()
                    for ((rol, textoHist) in historialMemoria.takeLast(6)) {
                        contentsArray.put(JSONObject().apply {
                            put("role", rol)
                            put("parts", JSONArray().put(JSONObject().put("text", textoHist)))
                        })
                    }
                    contentsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
                    })

                    val body = JSONObject().apply {
                        put("system_instruction", JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", instruccionSistema)))
                        })
                        put("contents", contentsArray)
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.7)
                            put("maxOutputTokens", 1200)
                        })
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use {
                        it.write(body.toString())
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
                            if (parts != null && parts.length() > 0) {
                                val sb = StringBuilder()
                                for (i in 0 until parts.length()) {
                                    sb.append(parts.getJSONObject(i).optString("text", ""))
                                }
                                val txt = sb.toString().replace("*", "").trim()
                                if (txt.isNotEmpty()) {
                                    respuestaFinal = txt
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
                    historialMemoria.add(Pair("user", mensajeUsuario))
                    historialMemoria.add(Pair("model", respuestaFinal!!))
                    val prefijo = if (esAnalisisLargo && !respuestaFinal!!.lowercase(Locale.getDefault()).contains("análisis culminado")) {
                        "Análisis culminado, señor. "
                    } else {
                        ""
                    }
                    hablarEnSegundoPlano(prefijo + respuestaFinal!!, esAvisoIntermedio = false)
                } else {
                    hablarEnSegundoPlano(
                        "No logré completar la consulta en este momento debido a una intermitencia de red, señor.",
                        esAvisoIntermedio = false
                    )
                }
            }
        }
    }

    private fun hablarEnSegundoPlano(texto: String, esAvisoIntermedio: Boolean) {
        liberarMicrofonoEstricto()
        estaHablando = true
        solicitarFocoAudioMultimedia()

        if (!ttsListo || tts == null) {
            iniciarMotorVoz()
            estaHablando = false
            if (!esAvisoIntermedio && !pantallaPrincipalActiva && !escuchaPausadaPorUI) {
                programarEscucha(1800L)
            }
            return
        }

        val utteranceId = if (esAvisoIntermedio) "NEYTHAN_BG_INTERMEDIO" else "NEYTHAN_BG_FINAL"
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
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

    override fun onDestroy() {
        liberarMicrofonoEstricto()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
