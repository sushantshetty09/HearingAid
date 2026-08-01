package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveformPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Vibrant neon green
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#4400E676")
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1A00E676")
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#B3FFFFFF")
        textSize = 22f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val maxDisplaySamples = 512
    private var currentIndex = 0
    private val displayBuffer = FloatArray(maxDisplaySamples)
    private var currentRms = 0f
    private var visualGainScale = 1.0f

    fun updateWaveform(buffer: ShortArray) {
        if (buffer.isEmpty()) return

        var sumSquares = 0.0
        var maxAbs = 0f

        for (sample in buffer) {
            val normalized = sample.toFloat() / Short.MAX_VALUE
            displayBuffer[currentIndex] = normalized
            currentIndex = (currentIndex + 1) % maxDisplaySamples

            val absVal = abs(normalized)
            if (absVal > maxAbs) maxAbs = absVal
            sumSquares += (normalized * normalized)
        }

        val rms = sqrt(sumSquares / buffer.size).toFloat()
        currentRms = currentRms * 0.7f + rms * 0.3f

        // Dynamic Visual Auto-Gain Control (AGC) so quiet mic input is visibly scaled
        val targetScale = if (maxAbs > 0.001f) {
            (0.75f / maxAbs).coerceIn(1.0f, 20.0f)
        } else {
            1.0f
        }
        visualGainScale = visualGainScale * 0.80f + targetScale * 0.20f

        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        // Draw horizontal grid lines
        val gridRows = 4
        for (i in 1 until gridRows) {
            val y = (height / gridRows) * i
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // Draw zero-axis center line
        canvas.drawLine(0f, centerY, width, centerY, centerLinePaint)

        if (displayBuffer.isNotEmpty()) {
            val path = Path()
            val fillPath = Path()

            fillPath.moveTo(0f, centerY)
            var started = false

            for (i in 0 until maxDisplaySamples) {
                val bufferIndex = (currentIndex + i) % maxDisplaySamples
                val rawSample = displayBuffer[bufferIndex]

                // Apply dynamic visual gain scale so waveform is vibrant and visible
                val scaledSample = (rawSample * visualGainScale).coerceIn(-0.95f, 0.95f)

                val x = (i.toFloat() / (maxDisplaySamples - 1)) * width
                val y = centerY - (scaledSample * centerY * 0.88f)

                if (!started) {
                    path.moveTo(x, y)
                    fillPath.lineTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, centerY)
            fillPath.close()

            // Gradient fill under waveform curve
            val fillShader = LinearGradient(
                0f, 0f, 0f, height,
                intArrayOf(
                    Color.parseColor("#4D00E676"),
                    Color.parseColor("#1A00E5FF"),
                    Color.parseColor("#4D00E676")
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = fillShader
            canvas.drawPath(fillPath, fillPaint)

            // Draw glowing waveform line
            canvas.drawPath(path, waveformPaint)

            // Draw RMS amplitude reading in upper right corner
            val dbVal = if (currentRms > 0.0001f) (20 * log10(currentRms.toDouble())).toInt().coerceIn(-60, 0) else -60
            val statsText = "RMS: ${dbVal} dB"
            canvas.drawText(statsText, width - 140f, 30f, textPaint)
        }
    }
}
