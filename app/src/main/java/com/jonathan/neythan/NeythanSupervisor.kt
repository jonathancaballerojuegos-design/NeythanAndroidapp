package com.jonathan.neythan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object NeythanSupervisor {

    private const val GITHUB_REPO = "NeythanAndroidapp"
    private const val PREFS_NAME = "neythan_supervisor_prefs"
    private const val KEY_ULTIMO_RUN_ID = "ultimo_run_id_reportado"
    private const val KEY_ERROR_INTERNO = "ultimo_error_interno"

    @Volatile
    var anomaliaPendienteDescripcion: String? = null

    @Volatile
    var anomaliaPendienteDetalleTecnico: String? = null

    @Volatile
    var esperandoAutorizacionUsuario: Boolean = false

    fun registrarErrorInterno(context: Context, modulo: String, detalle: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val huella = "$modulo: ${detalle.take(120)}"
            val anterior = prefs.getString(KEY_ERROR_INTERNO, "")
            if (huella != anterior) {
                prefs.edit().putString(KEY_ERROR_INTERNO, huella).apply()
                anomaliaPendienteDescripcion = "una incidencia interna en el módulo $modulo"
                anomaliaPendienteDetalleTecnico = "Módulo afectado: $modulo\nDetalle técnico: $detalle"
                esperandoAutorizacionUsuario = true
            }
        } catch (_: Exception) {
        }
    }

    fun auditarSistemaSilencioso(
        context: Context,
        githubOwner: String,
        onAnomaliaDetectada: (mensajeAviso: String) -> Unit
    ) {
        thread {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (esperandoAutorizacionUsuario && !anomaliaPendienteDescripcion.isNullOrEmpty()) {
                val aviso = "Señor, detecté ${anomaliaPendienteDescripcion}. ¿Me autoriza a analizar el código y preparar la corrección en sus notas?"
                Handler(Looper.getMainLooper()).post { onAnomaliaDetectada(aviso) }
                return@thread
            }

            if (githubOwner.isBlank()) return@thread

            try {
                val apiUrl = "https://api.github.com/repos/$githubOwner/$GITHUB_REPO/actions/runs?per_page=1"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NeythanAndroidApp")
                    connectTimeout = 8000
                    readTimeout = 10000
                }

                if (conn.responseCode in 200..299) {
                    val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val json = JSONObject(raw)
                    val runs = json.optJSONArray("workflow_runs")
                    if (runs != null && runs.length() > 0) {
                        val ultimoRun = runs.getJSONObject(0)
                        val runId = ultimoRun.optLong("id", 0L)
                        val status = ultimoRun.optString("status", "")
                        val conclusion = ultimoRun.optString("conclusion", "")
                        val commitMsg = ultimoRun.optJSONObject("head_commit")?.optString("message", "") ?: ""
                        val runNumber = ultimoRun.optInt("run_number", 0)

                        val ultimoReportado = prefs.getLong(KEY_ULTIMO_RUN_ID, 0L)
                        if (status == "completed" && conclusion == "failure" && runId != ultimoReportado) {
                            prefs.edit().putLong(KEY_ULTIMO_RUN_ID, runId).apply()
                            anomaliaPendienteDescripcion = "un fallo en la compilación número $runNumber de GitHub Actions"
                            anomaliaPendienteDetalleTecnico = "Compilación #$runNumber (ID: $runId) fallida.\nCommit: $commitMsg\nURL: ${ultimoRun.optString("html_url", "")}"
                            esperandoAutorizacionUsuario = true

                            val aviso = "Señor, detecté ${anomaliaPendienteDescripcion}. ¿Me autoriza a analizar el código y guardar la propuesta de corrección en sus notas?"
                            Handler(Looper.getMainLooper()).post {
                                onAnomaliaDetectada(aviso)
                            }
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun esRespuestaDeAutorizacion(textoUsuario: String): Boolean {
        if (!esperandoAutorizacionUsuario) return false
        val l = textoUsuario.lowercase(Locale.getDefault()).trim()
        val afirmaciones = listOf(
            "sí", "si", "autorizo", "adelante", "procede", "hazlo", "analiza",
            "revisa", "aprueba", "aprobado", "dale", "ok", "de acuerdo", "claro"
        )
        return afirmaciones.any { l == it || l.startsWith("$it ") || l.contains("autorizo") || l.contains("adelante") }
    }

    fun ejecutarAnalisisYGuardarEnNotas(
        context: Context,
        githubOwner: String,
        onInicio: (String) -> Unit,
        onFinalizado: (String) -> Unit
    ) {
        val problema = anomaliaPendienteDetalleTecnico ?: "Incidencia detectada en el núcleo operativo."
        esperandoAutorizacionUsuario = false
        anomaliaPendienteDescripcion = null
        anomaliaPendienteDetalleTecnico = null

        Handler(Looper.getMainLooper()).post {
            onInicio("Autorización recibida, señor. Auditando el código fuente y preparando la corrección. Le avisaré en cuanto culmine.")
        }

        thread {
            val apiKey = try {
                BuildConfig.GEMINI_API_KEY.trim()
            } catch (_: Exception) {
                ""
            }

            if (apiKey.isEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    onFinalizado("No encontré la clave de Gemini configurada para completar la auditoría, señor.")
                }
                return@thread
            }

            val codigoContexto = obtenerCodigoRemotoResumido(githubOwner)
            val promptAuditoria = """
                Eres el núcleo T5 Supervisor de N.E.Y.T.H.A.N.
                Se ha detectado la siguiente anomalía en el proyecto Android (Kotlin):
                $problema

                Fragmentos actuales del repositorio:
                $codigoContexto

                Tarea:
                1. Explica en 2 líneas claras cuál es la causa raíz exacta del fallo.
                2. Entrega el bloque de código corregido listo para copiar y pegar.
                Mantén un tono técnico, claro y ordenado para guardarse directamente en una nota de revisión.
            """.trimIndent()

            val modelos = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite")
            var diagnosticoCompleto: String? = null

            for (modelo in modelos) {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$apiKey"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        connectTimeout = 15000
                        readTimeout = 25000
                        doOutput = true
                    }

                    val body = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", promptAuditoria)))
                        }))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.3)
                            put("maxOutputTokens", 1800)
                        })
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use {
                        it.write(body.toString())
                        it.flush()
                    }

                    if (conn.responseCode in 200..299) {
                        val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                        val json = JSONObject(raw)
                        val parts = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) {
                                sb.append(parts.getJSONObject(i).optString("text", ""))
                            }
                            if (sb.isNotBlank()) {
                                diagnosticoCompleto = sb.toString().trim()
                                conn.disconnect()
                                break
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

            if (diagnosticoCompleto.isNullOrEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    onFinalizado("No pude completar el análisis remoto por una intermitencia de red, señor.")
                }
                return@thread
            }

            val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val contenidoNota = "=== REPORTE T5 SUPERVISOR NEYTHAN ($fecha) ===\n\nPROBLEMA DETECTADO:\n$problema\n\nDIAGNÓSTICO Y CORRECCIÓN:\n$diagnosticoCompleto"

            guardarRespaldoLocalYPortapapeles(context, contenidoNota)

            Handler(Looper.getMainLooper()).post {
                abrirEnAppDeNotas(context, "Corrección Neythan ($fecha)", contenidoNota)
                onFinalizado("Análisis culminado, señor. He guardado el diagnóstico y la corrección lista en sus notas y en el portapapeles.")
            }
        }
    }

    private fun obtenerCodigoRemotoResumido(githubOwner: String): String {
        if (githubOwner.isBlank()) return "Sin usuario de GitHub configurado."
        val archivos = listOf(
            "app/src/main/java/com/jonathan/neythan/MainActivity.kt",
            "app/src/main/java/com/jonathan/neythan/NeythanBackgroundService.kt",
            "app/src/main/AndroidManifest.xml"
        )
        val sb = StringBuilder()
        for (ruta in archivos) {
            try {
                val rawUrl = "https://raw.githubusercontent.com/$githubOwner/$GITHUB_REPO/main/$ruta"
                val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                }
                if (conn.responseCode in 200..299) {
                    val contenido = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    sb.append("--- $ruta ---\n")
                    sb.append(contenido.take(3500))
                    sb.append("\n\n")
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
        return if (sb.isEmpty()) "No se pudo descargar el código fuente público." else sb.toString()
    }

    private fun guardarRespaldoLocalYPortapapeles(context: Context, texto: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Corrección Neythan", texto))
        } catch (_: Exception) {
        }
        try {
            val dir = File(context.getExternalFilesDir(null), "ParchesNeythan")
            if (!dir.exists()) dir.mkdirs()
            val archivo = File(dir, "correccion_${System.currentTimeMillis()}.txt")
            archivo.writeText(texto, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun abrirEnAppDeNotas(context: Context, titulo: String, cuerpo: String) {
        try {
            val noteIntent = Intent("com.google.android.gms.actions.CREATE_NOTE").apply {
                putExtra(Intent.EXTRA_SUBJECT, titulo)
                putExtra(Intent.EXTRA_TEXT, cuerpo)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (noteIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(noteIntent)
                return
            }
        } catch (_: Exception) {
        }

        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, titulo)
                putExtra(Intent.EXTRA_TEXT, cuerpo)
            }
            val chooser = Intent.createChooser(sendIntent, "Guardar corrección de Neythan en Notas").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }
}
