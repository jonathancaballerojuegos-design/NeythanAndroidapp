package com.jonathan.neythan

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
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
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        // VERIFICAR QUE COINCIDA CON SU URL DE RENDER SIN BARRA AL FINAL
        const val SERVER_URL = "https://neythanproyect-cloud.onrender.com"
        private const val REQ_PERMISOS = 1001
    }

    private lateinit var hudView: NeythanHudView
    private lateinit var txtEstado: TextView
    private lateinit var txtChat: TextView
    private lateinit var panelControles: LinearLayout
    private lateinit var inputTexto: EditText

    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var mensajePendiente: String? = null
    private var estaHablando = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var escuchandoActivo = false
    private var conversacionActivaHasta = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        construirInterfazNativa()
        inicializarAudioYMotorVoz()
        verificarPermisosEIniciar()
    }

    private fun construirInterfazNativa() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        hudView = NeythanHudView(this)
        root.addView(
            hudView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 16)
        }

        txtEstado = TextView(this).apply {
            text = "ESTADO: INICIANDO SISTEMAS NATIVOS"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        txtChat = TextView(this).apply {
            text = "A sus órdenes, señor."
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(24, 18, 24, 18)
            setBackgroundColor(Color.parseColor("#66051218"))
        }

        topBar.addView(txtEstado)
        val paramsChat = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
        topBar.addView(txtChat, paramsChat)

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 32)
        }

        panelControles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC07131A"))
            setPadding(20, 20, 20, 20)
        }

        val filaBotones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnEscuchar = crearBotonHud("ESCUCHAR") {
            conversacionActivaHasta = System.currentTimeMillis() + 30000L
            iniciarEscuchaNativa()
        }

        val btnBurbuja = crearBotonHud("BURBUJA") {
            activarBurbujaFlotante()
        }

        val btnProbarVoz = crearBotonHud("PROBAR VOZ") {
            hablarNativo("Sistemas de voz nativos calibrados y en línea, señor.")
        }

        filaBotones.addView(btnEscuchar)
        filaBotones.addView(btnBurbuja)
        filaBotones.addView(btnProbarVoz)

        val filaTexto = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        inputTexto = EditText(this).apply {
            hint = "Escriba una orden..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnEnviar = crearBotonHud("ENVIAR") {
            val texto = inputTexto.text.toString().trim()
            if (texto.isNotEmpty()) {
                inputTexto.setText("")
                procesarOrdenUsuario(texto)
            }
        }

        filaTexto.addView(inputTexto)
        filaTexto.addView(btnEnviar)

        panelControles.addView(filaBotones)
        panelControles.addView(filaTexto)

        val btnToggle = Button(this).apply {
            text = "⚙ CONTROLES"
            setTextColor(Color.parseColor("#00E5FF"))
            setBackgroundColor(Color.parseColor("#4400E5FF"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setOnClickListener {
                if (panelControles.visibility == View.GONE) {
                    panelControles.visibility = View.VISIBLE
                    text = "✕ OCULTAR"
                } else {
                    panelControles.visibility = View.GONE
                    text = "⚙ CONTROLES"
                }
            }
        }

        val toggleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END
            topMargin = 10
        }

        bottomContainer.addView(panelControles)
        bottomContainer.addView(btnToggle, toggleParams)

        root.addView(
            bottomContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun crearBotonHud(titulo: String, accion: () -> Unit): Button {
        return Button(this).apply {
            text = titulo
            setTextColor(Color.parseColor("#00E5FF"))
            setBackgroundColor(Color.parseColor("#3300E5FF"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 10
            }
            layoutParams = p
            setOnClickListener { accion() }
        }
    }

    private fun inicializarAudioYMotorVoz() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol < (maxVol * 0.6).toInt()) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.8).toInt(), 0)
        }

        tts = TextToSpeech(this, this, "com.google.android.tts")
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
                    handler.post {
                        estaHablando = true
                        detenerEscuchaNativa()
                        hudView.establecerEstado("hablando", "nucleo_hud")
                        txtEstado.text = "ESTADO: HABLANDO · TRANSMITIENDO"
                    }
                }

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        estaHablando = false
                        hudView.establecerEstado("activo", "nucleo_hud")
                        txtEstado.text = "ESTADO: ESCUCHANDO · DIGA NEYTHAN"
                        handler.postDelayed({ iniciarEscuchaNativa() }, 350)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post {
                        estaHablando = false
                        hudView.establecerEstado("activo", "nucleo_hud")
                        handler.postDelayed({ iniciarEscuchaNativa() }, 500)
                    }
                }
            })
            ttsListo = true
            val pendiente = mensajePendiente
            if (pendiente != null) {
                mensajePendiente = null
                hablarNativo(pendiente)
            } else {
                hablarNativo("Sistemas nativos en línea, señor.")
            }
        } else {
            ttsListo = false
            iniciarEscuchaNativa()
        }
    }

    private fun hablarNativo(texto: String) {
        txtChat.text = "Neythan: $texto"
        if (!ttsListo || tts == null) {
            mensajePendiente = texto
            return
        }
        detenerEscuchaNativa()
        solicitarFocoAudio()

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_UTTERANCE")
    }

    private fun solicitarFocoAudio() {
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
    }

    private fun verificarPermisosEIniciar() {
        val faltantes = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            faltantes.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                faltantes.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), REQ_PERMISOS)
        } else {
            iniciarServiciosYEscucha()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS) {
            iniciarServiciosYEscucha()
        }
    }

    private fun iniciarServiciosYEscucha() {
        try {
            val bgIntent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bgIntent)
            } else {
                startService(bgIntent)
            }
        } catch (_: Exception) {
        }
        handler.postDelayed({ iniciarEscuchaNativa() }, 800)
    }

    private fun iniciarEscuchaNativa() {
        if (estaHablando || escuchandoActivo) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        escuchandoActivo = true
                        if (!estaHablando) {
                            hudView.establecerEstado("escuchando", "nucleo_hud")
                            txtEstado.text = "ESTADO: ESCUCHANDO · MICRÓFONO ACTIVO"
                        }
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        hudView.actualizarNivelVoz(rmsdB)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        escuchandoActivo = false
                    }

                    override fun onError(error: Int) {
                        escuchandoActivo = false
                        if (!estaHablando) {
                            handler.postDelayed({ iniciarEscuchaNativa() }, 700)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        escuchandoActivo = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val frase = matches?.firstOrNull()?.trim() ?: ""
                        if (frase.isNotEmpty()) {
                            evaluarFraseCapturada(frase)
                        } else if (!estaHablando) {
                            handler.postDelayed({ iniciarEscuchaNativa() }, 400)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            escuchandoActivo = false
            handler.postDelayed({ iniciarEscuchaNativa() }, 1500)
        }
    }

    private fun detenerEscuchaNativa() {
        try {
            escuchandoActivo = false
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun evaluarFraseCapturada(frase: String) {
        val lower = frase.lowercase(Locale.getDefault())
        val aliases = listOf("neythan", "nathan", "neitan", "natan", "neytan", "naytan", "despierta")
        val contieneWakeWord = aliases.any { lower.contains(it) }
        val enVentanaActiva = System.currentTimeMillis() < conversacionActivaHasta

        if (contieneWakeWord || enVentanaActiva) {
            conversacionActivaHasta = System.currentTimeMillis() + 25000L
            var ordenLimpia = frase
            for (alias in aliases) {
                ordenLimpia = ordenLimpia.replace(Regex("(?i)\b$alias\b"), "").trim()
            }
            if (ordenLimpia.isEmpty()) {
                hablarNativo("A sus órdenes, señor. ¿En qué le ayudo?")
            } else {
                procesarOrdenUsuario(ordenLimpia)
            }
        } else {
            handler.postDelayed({ iniciarEscuchaNativa() }, 300)
        }
    }

    private fun procesarOrdenUsuario(orden: String) {
        txtChat.text = "Usted: $orden"
        hudView.establecerEstado("procesando", "nucleo_hud")
        txtEstado.text = "ESTADO: PROCESANDO EN LA NUBE"

        val lower = orden.lowercase(Locale.getDefault())
        if (lower.contains("spotify") || lower.startsWith("pon ") || lower.startsWith("reproduce")) {
            val limpieza = lower
                .replace("en spotify", "")
                .replace("spotify", "")
                .replace("reproduce", "")
                .replace("reproducice", "")
                .replace("pon", "")
                .replace("música", "")
                .replace("musica", "")
                .trim()
            abrirSpotifyNativo(limpieza)
            hablarNativo(if (limpieza.isNotEmpty()) "Reproduciendo $limpieza en Spotify, señor." else "Abriendo Spotify, señor.")
            return
        }

        executor.execute {
            try {
                val url = URL("$SERVER_URL/chat")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 12000
                    readTimeout = 15000
                    doOutput = true
                }

                val notifs = NeythanNotificationListener.obtenerResumenNotificaciones()
                val payload = JSONObject().apply {
                    put("mensaje", orden)
                    put("notificaciones", notifs)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respuestaRaw = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
                val json = JSONObject(respuestaRaw)

                val respuestaVoz = json.optString("respuesta", "A sus órdenes, señor.")
                val emocion = json.optString("emocion", "activo")
                val forma = json.optString("forma", "nucleo_hud")
                val accion = json.optString("accion", "ninguna")
                val parametro = json.optString("parametro", "")

                handler.post {
                    hudView.establecerEstado(emocion, forma)
                    ejecutarAccionNativa(accion, parametro)
                    hablarNativo(respuestaVoz)
                }
            } catch (e: Exception) {
                handler.post {
                    hudView.establecerEstado("alerta", "nucleo_hud")
                    txtEstado.text = "ESTADO: DESPERTANDO SERVIDOR"
                    hablarNativo("El servidor en la nube está despertando, señor. Intente en unos segundos.")
                }
            }
        }
    }

    private fun ejecutarAccionNativa(accion: String, parametro: String) {
        when (accion) {
            "abrir_spotify" -> abrirSpotifyNativo(parametro)
            "leer_notificaciones" -> {
                if (!NeythanNotificationListener.tienePermisoNotificaciones(this)) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        }
    }

    private fun abrirSpotifyNativo(busqueda: String) {
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
        } catch (_: Exception) {
            try {
                val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launch != null) startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }

    private fun activarBurbujaFlotante() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            hablarNativo("Autorice el permiso de superposición en pantalla para activar la burbuja, señor.")
            return
        }
        val svc = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
        hablarNativo("Núcleo flotante desplegado sobre el sistema, señor.")
    }

    override fun onDestroy() {
        detenerEscuchaNativa()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

class NeythanHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var angulo = 0f
    private var colorPrincipal = Color.parseColor("#00E5FF")
    private var estadoActual = "activo"
    private var formaActual = "nucleo_hud"
    private var nivelVoz = 0f

    private val paintAnillo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val paintNucleo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80DEEA")
        textSize = 24f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val nodos = listOf("TEL", "ONDA", "CAM", "GPS", "USER", "DOC", "WEB", "BUSC")

    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angulo = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun establecerEstado(estado: String, forma: String) {
        estadoActual = estado.lowercase(Locale.getDefault())
        formaActual = forma.lowercase(Locale.getDefault())
        colorPrincipal = when (estadoActual) {
            "escuchando" -> Color.parseColor("#2979FF")
            "procesando" -> Color.parseColor("#FFAB00")
            "hablando" -> Color.parseColor("#00E676")
            "alerta" -> Color.parseColor("#FF1744")
            "feliz" -> Color.parseColor("#1DE9B6")
            else -> Color.parseColor("#00E5FF")
        }
        invalidate()
    }

    fun actualizarNivelVoz(rms: Float) {
        nivelVoz = rms.coerceIn(0f, 12f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radioBase = (minOf(width, height) * 0.26f) + (nivelVoz * 2.5f)

        paintAnillo.color = colorPrincipal
        paintAnillo.alpha = 180
        paintAnillo.strokeWidth = 5f
        canvas.drawCircle(cx, cy, radioBase, paintAnillo)

        paintAnillo.alpha = 110
        paintAnillo.strokeWidth = 3f
        val rectExterno = RectF(cx - radioBase * 1.28f, cy - radioBase * 1.28f, cx + radioBase * 1.28f, cy + radioBase * 1.28f)
        canvas.drawArc(rectExterno, angulo, 110f, false, paintAnillo)
        canvas.drawArc(rectExterno, angulo + 180f, 110f, false, paintAnillo)

        paintNucleo.color = colorPrincipal
        paintNucleo.alpha = if (estadoActual == "hablando") 230 else 150
        canvas.drawCircle(cx, cy, radioBase * 0.58f, paintNucleo)

        // Boca animada unicamente cuando habla
        if (estadoActual == "hablando") {
            val paintBoca = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            val apertura = 12f + absSin(angulo * 8f) * 26f
            canvas.drawRoundRect(
                RectF(cx - 55f, cy + 25f - apertura / 2f, cx + 55f, cy + 25f + apertura / 2f),
                12f, 12f, paintBoca
            )
        }

        // 8 Nodos satelitales HUD
        val radioOrbita = radioBase * 1.52f
        for (i in nodos.indices) {
            val rad = Math.toRadians(((i * 45.0) + (angulo * 0.35)))
            val nx = cx + (cos(rad) * radioOrbita).toFloat()
            val ny = cy + (sin(rad) * radioOrbita).toFloat()
            paintAnillo.alpha = 160
            canvas.drawCircle(nx, ny, 34f, paintAnillo)
            paintTexto.color = colorPrincipal
            canvas.drawText(nodos[i], nx, ny + 8f, paintTexto)
        }
    }

    private fun absSin(deg: Float): Float {
        return kotlin.math.abs(sin(Math.toRadians(deg.toDouble()))).toFloat()
    }
}
