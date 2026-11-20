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
import android.graphics.RectF
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
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sin

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVER_URL = ""
        val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY
        private const val REQ_PERMISOS = 1001
        @Volatile
        var pantallaPrincipalActiva = false
    }

    private lateinit var hudView: NeythanHudCanvas
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var estadoActual = "REPOSO"
    private var spriteActualNombre = "reposo_feliz"
    private var subtituloPantalla = "Iniciando enlace directo con Gemini, señor..."
    private var nivelVozUsuario = 0f
    private var estaHablando = false
    private var bocaAbiertaFrame = false
    private var conversacionActivaHasta = 0L
    private var modoFormaManual = false
    private var indiceForma = 0

    private val historialTurnos = ArrayList<Pair<String, String>>()

    private val formasDisponibles = listOf(
        "reposo_feliz", "saltando_azul", "corriendo_rapido", "recargando_bateria",
        "celebrando_confeti", "enamorado_corazones", "malicioso_cian", "asco_verde",
        "gafas_oscuras", "analizando", "furia_roja", "durmiendo_reposo",
        "angulo_perfil_1", "angulo_espalda_1"
    )

    private val cacheSprites = HashMap<String, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pantallaPrincipalActiva = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        hudView = NeythanHudCanvas(this)
        setContentView(hudView)

        iniciarMotorVoz()
        verificarPermisosYArrancar()
        iniciarBucleAnimacion()
    }

    override fun onResume() {
        super.onResume()
        pantallaPrincipalActiva = true
        if (!estaHablando) {
            reanudarEscucha(700L)
        }
    }

    override fun onPause() {
        pantallaPrincipalActiva = false
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
                        reanudarEscucha(900L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        estaHablando = false
                        bocaAbiertaFrame = false
                        estadoActual = "ESCUCHANDO"
                        reanudarEscucha(1000L)
                    }
                }
            })
            ttsListo = true
            hablar("Enlace directo con Gemini operativo y libre de interferencias, señor.", "reposo_feliz")
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
        reanudarEscucha(1000L)
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
                    if (!estaHablando && pantallaPrincipalActiva) {
                        val pausa = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1800L else 900L
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            configurarReconocedor()
                        }
                        reanudarEscucha(pausa)
                    }
                }

                override fun onResults(results: Bundle?) {
                    nivelVozUsuario = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        procesarTextoCapturado(matches[0])
                    } else {
                        reanudarEscucha(900L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (_: Exception) {
            reanudarEscucha(1500L)
        }
    }

    private fun reanudarEscucha(retrasoMs: Long) {
        mainHandler.removeCallbacks(tareaEscucha)
        if (!pantallaPrincipalActiva || estaHablando) return
        mainHandler.postDelayed(tareaEscucha, retrasoMs)
    }

    private val tareaEscucha = Runnable {
        if (estaHablando || !pantallaPrincipalActiva) return@Runnable
        try {
            silenciarTonosSistema(true)
            if (speechRecognizer == null) {
                configurarReconocedor()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            configurarReconocedor()
            reanudarEscucha(1500L)
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
            estadoActual = "REPOSO"
            if (!modoFormaManual) {
                spriteActualNombre = "reposo_feliz"
            }
            reanudarEscucha(900L)
            return
        }

        var orden = limpio
        for (w in palabrasClave) {
            orden = orden.replace(Regex("(?i)\\b$w\\b[,:]?"), "").trim()
        }

        if (orden.isEmpty()) {
            hablar("A sus órdenes, señor. ¿En qué puedo asistirle?", "escuchando_mano_oido")
            return
        }

        subtituloPantalla = "Usted: $orden"
        estadoActual = "PROCESANDO"
        if (!modoFormaManual) {
            spriteActualNombre = "pensando"
        }

        if (ejecutarComandoLocalSiAplica(orden)) {
            return
        }

        consultarGeminiDirecto(orden)
    }

    private fun ejecutarComandoLocalSiAplica(orden: String): Boolean {
        val lower = orden.lowercase(Locale.getDefault())

        if (lower.contains("cambia de forma") || lower.contains("otra forma")) {
            modoFormaManual = true
            indiceForma = (indiceForma + 1) % formasDisponibles.size
            spriteActualNombre = formasDisponibles[indiceForma]
            hablar("Adoptando nueva forma visual, señor.", spriteActualNombre)
            return true
        }

        if (lower.contains("forma original") || lower.contains("modo automático")) {
            modoFormaManual = false
            spriteActualNombre = "reposo_feliz"
            hablar("Regresando a mi forma base original, señor.", spriteActualNombre)
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
                val msg = if (busqueda.isNotEmpty()) "Abriendo $busqueda en Spotify, señor." else "Abriendo Spotify, señor."
                hablar(msg, "celebrando_confeti")
            } catch (_: Exception) {
                hablar("No encontré la aplicación Spotify instalada, señor.", "alerta")
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
                hablar("Burbuja holográfica activada sobre otras aplicaciones, señor.", "saltando_azul")
            }
            return true
        }

        return false
    }

    private fun consultarGeminiDirecto(mensajeUsuario: String) {
        val claveLimpia = GEMINI_API_KEY.trim()
        if (claveLimpia.isEmpty() || claveLimpia == "null") {
            hablar("El secreto GEMINI_API_KEY llegó vacío en la compilación. Verifique el nombre exacto en GitHub Secrets, señor.", "alerta")
            return
        }

        thread {
            val modelos = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")
            var ultimoDiagnostico = "Sin respuesta de Google."

            for (modelo in modelos) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$claveLimpia"
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("User-Agent", "NeythanAndroid/1.0")
                        connectTimeout = 12000
                        readTimeout = 20000
                        doOutput = true
                    }

                    val instruccionSistema = JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put(
                                    "text",
                                    "Eres Neythan, un asistente personal de inteligencia artificial inspirado en J.A.R.V.I.S. y Alfred. " +
                                        "Hablas con respeto, elegancia, precisión y llamas 'señor' al usuario (Jonathan). " +
                                        "Responde siempre en español natural, directo y sin asteriscos ni formato markdown, ideal para lectura en voz alta."
                                )
                            )
                        )
                    }

                    val contenidos = JSONArray()
                    for ((rol, textoTurno) in historialTurnos.takeLast(6)) {
                        contenidos.put(
                            JSONObject().apply {
                                put("role", rol)
                                put("parts", JSONArray().put(JSONObject().put("text", textoTurno)))
                            }
                        )
                    }
                    contenidos.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", mensajeUsuario)))
                        }
                    )

                    val payload = JSONObject().apply {
                        put("system_instruction", instruccionSistema)
                        put("contents", contenidos)
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use {
                            it.readText()
                        }
                        conn.disconnect()

                        val jsonResp = JSONObject(responseText)
                        val respuestaTexto = jsonResp
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .replace("*", "")
                            .replace("#", "")
                            .trim()

                        historialTurnos.add(Pair("user", mensajeUsuario))
                        historialTurnos.add(Pair("model", respuestaTexto))

                        val spriteElegido = elegirSpritePorContexto(respuestaTexto)
                        mainHandler.post {
                            hablar(respuestaTexto, spriteElegido)
                        }
                        return@thread
                    } else {
                        val errStream = conn.errorStream ?: conn.inputStream
                        val errBody = if (errStream != null) {
                            BufferedReader(InputStreamReader(errStream, "UTF-8")).use { it.readText() }
                        } else {
                            ""
                        }
                        conn.disconnect()
                        val detalleMsg = try {
                            JSONObject(errBody).getJSONObject("error").optString("message", errBody)
                        } catch (_: Exception) {
                            errBody.take(120)
                        }
                        ultimoDiagnostico = "Error $code en $modelo: $detalleMsg"
                        if (code == 400 || code == 403 || code == 401) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    ultimoDiagnostico = "Fallo de red con $modelo: ${e.javaClass.simpleName} - ${e.message ?: "sin detalle"}"
                }
            }

            mainHandler.post {
                hablar("Diagnóstico de conexión, señor: $ultimoDiagnostico", "furia_roja")
            }
        }
    }

    private fun elegirSpritePorContexto(respuesta: String): String {
        if (modoFormaManual) return spriteActualNombre
        val txt = respuesta.lowercase(Locale.getDefault())
        return when {
            txt.contains("excelente") || txt.contains("logrado") || txt.contains("feliz") || txt.contains("éxito") -> "saltando_azul"
            txt.contains("rápido") || txt.contains("buscando") || txt.contains("ejecutando") -> "corriendo_rapido"
            txt.contains("batería") || txt.contains("energía") || txt.contains("cargando") -> "recargando_bateria"
            txt.contains("asco") || txt.contains("virus") || txt.contains("basura") -> "asco_verde"
            txt.contains("malicioso") || txt.contains("astuto") || txt.contains("secreto") -> "malicioso_cian"
            txt.contains("amor") || txt.contains("encanta") || txt.contains("corazón") -> "enamorado_corazones"
            txt.contains("error") || txt.contains("alerta") || txt.contains("peligro") -> "furia_roja"
            txt.contains("triste") || txt.contains("lamento") -> "triste_llorando"
            txt.contains("analizando") || txt.contains("pensando") || txt.contains("revisando") -> "analizando"
            txt.contains("dormir") || txt.contains("reposo") -> "durmiendo_reposo"
            else -> "reposo_feliz"
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
            reanudarEscucha(1000L)
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
                mainHandler.postDelayed(this, 140L)
            }
        })
    }

    override fun onDestroy() {
        pantallaPrincipalActiva = false
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
        private val paintAro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val paintTextoEstado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 38f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val paintSubtitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)

            val cx = width / 2f
            val cy = height * 0.45f
            val tiempo = System.currentTimeMillis() / 1000.0

            val colorEstado = when (estadoActual) {
                "ESCUCHANDO" -> Color.parseColor("#00A8FF")
                "PROCESANDO" -> Color.parseColor("#FFB300")
                "HABLANDO" -> Color.parseColor("#00E676")
                "ALERTA" -> Color.parseColor("#FF1744")
                else -> Color.parseColor("#00E5FF")
            }

            paintTextoEstado.color = colorEstado
            canvas.drawText("ESTADO: $estadoActual ●", cx, height * 0.12f, paintTextoEstado)

            val pulso = (sin(tiempo * 4.0) * 14.0).toFloat() + (nivelVozUsuario * 6f)
            val radioBase = (width.coerceAtMost(height) * 0.34f) + pulso

            paintAro.color = colorEstado
            paintAro.alpha = 140
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

            dibujarSubtituloMultilinea(canvas, subtituloPantalla, cx, height * 0.80f)
        }

        private fun dibujarSubtituloMultilinea(canvas: Canvas, texto: String, x: Float, yInicial: Float) {
            val maxChars = 34
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
            for (linea in lineas.take(4)) {
                canvas.drawText(linea, x, y, paintSubtitulo)
                y += 50f
            }
        }
    }
}
