package com.jonathan.neythan

import android.animation.ValueAnimator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.random.Random

/**
 * Controlador de fluctuación reactiva al hablar para el holograma central de N.E.Y.T.H.A.N.
 * Se acopla sobre cualquier View/Canvas central sin alterar el diseño ni las funciones existentes.
 */
class NeythanHologramaVozController(
    private val vistaHolograma: View,
    private val onNivelEnergiaCambiado: ((Float) -> Unit)? = null
) {
    private var pulsoAnimator: ValueAnimator? = null
    private var hablando = false

    fun iniciarFluctuacionVoz() {
        if (hablando) return
        hablando = true
        pulsoAnimator?.cancel()
        pulsoAnimator = ValueAnimator.ofFloat(0.96f, 1.12f).apply {
            duration = 140L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val factor = (anim.animatedValue as Float) + Random.nextFloat() * 0.04f
                vistaHolograma.scaleX = factor
                vistaHolograma.scaleY = factor
                vistaHolograma.alpha = (0.82f + (factor - 0.96f)).coerceIn(0.75f, 1.0f)
                onNivelEnergiaCambiado?.invoke(factor)
            }
            start()
        }
    }

    fun modularPorSilaba() {
        if (!hablando) return
        val pico = 1.05f + Random.nextFloat() * 0.14f
        vistaHolograma.animate()
            .scaleX(pico)
            .scaleY(pico)
            .setDuration(75L)
            .start()
        onNivelEnergiaCambiado?.invoke(pico)
    }

    fun detenerFluctuacionVoz() {
        hablando = false
        pulsoAnimator?.cancel()
        pulsoAnimator = null
        vistaHolograma.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(220L)
            .start()
        onNivelEnergiaCambiado?.invoke(1.0f)
    }

    fun vincularATextToSpeech(
        tts: TextToSpeech?,
        onInicioHabla: (() -> Unit)? = null,
        onFinHabla: (() -> Unit)? = null
    ) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                vistaHolograma.post {
                    iniciarFluctuacionVoz()
                    onInicioHabla?.invoke()
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                vistaHolograma.post {
                    modularPorSilaba()
                }
            }

            override fun onDone(utteranceId: String?) {
                vistaHolograma.post {
                    detenerFluctuacionVoz()
                    onFinHabla?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                vistaHolograma.post {
                    detenerFluctuacionVoz()
                    onFinHabla?.invoke()
                }
            }
        })
    }
}
