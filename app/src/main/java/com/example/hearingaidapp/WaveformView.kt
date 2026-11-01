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
        strokeWidth = 5f
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

    private val badgeBgPaint = Paint().apply {
        color = Color.parseColor("#CC0D1B1E")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val badgeBorderPaint = Paint().apply {
        color = Color.parseColor("#3300E676")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#E0FFFFFF")
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val maxDisplaySamples = 512
    private var currentIndex = 0
    private val displayBuffer = FloatArray(maxDisplaySamples)
    private var currentRms = 0f
    private var visualGainScale = 1.0f
    private var userZoomMultiplier = 1.0f

    // Pre-allocated paths & rects for zero allocation in onDraw
    private val linePath = Path()
    private val fillPath = Path()
    private val badgeRect = RectF()

    fun setZoomMultiplier(zoom: Float) {
        userZoomMultiplier = zoom.coerceIn(1.0f, 8.0f)
        post { invalidate() }
    }

    fun getZoomMultiplier(): Float = userZoomMultiplier

    /**
     * Update waveform from peak-preserving decimated float buffer (min/max pairs).
     */
    fun updateWaveform(decimatedSamples: FloatArray, length: Int = decimatedSamples.size) {
        if (length <= 0) return

        var sumSquares = 0.0
        var maxAbs = 0f

        val count = min(length, maxDisplaySamples)
        for (i in 0 until count) {
            val sample = decimatedSamples[i]
            displayBuffer[currentIndex] = sample
            currentIndex = (currentIndex + 1) % maxDisplaySamples

            val absVal = abs(sample)
            if (absVal > maxAbs) maxAbs = absVal
            sumSquares += (sample * sample)
        }

        val rms = sqrt(sumSquares / count).toFloat()
        currentRms = currentRms * 0.7f + rms * 0.3f

        // Dynamic Visual Auto-Gain Control (AGC) with 40x ceiling, fast attack (0.35) and slower release (0.05)
        val targetScale = if (maxAbs > 0.001f) {
            (0.85f / maxAbs).coerceIn(1.0f, 40.0f)
        } else {
            1.0f
        }

        if (targetScale > visualGainScale) {
            // Fast attack: quickly expand quiet speech
            visualGainScale = visualGainScale * 0.65f + targetScale * 0.35f
        } else {
            // Slower release: keep speech waveform tall without abrupt shrinking
            visualGainScale = visualGainScale * 0.95f + targetScale * 0.05f
        }

        post { invalidate() }
    }

    /**
     * Backward-compatible overload for raw ShortArray input.
     */
    fun updateWaveform(buffer: ShortArray) {
        if (buffer.isEmpty()) return

        var sumSquares = 0.0
        var maxAbs = 0f

        val step = max(1, buffer.size / maxDisplaySamples)
        val shortInv = 1.0f / Short.MAX_VALUE
        for (i in buffer.indices step step) {
            val normalized = buffer[i] * shortInv
            displayBuffer[currentIndex] = normalized
            currentIndex = (currentIndex + 1) % maxDisplaySamples

            val absVal = abs(normalized)
            if (absVal > maxAbs) maxAbs = absVal
            sumSquares += (normalized * normalized)
        }

        val rms = sqrt(sumSquares / (buffer.size / step)).toFloat()
        currentRms = currentRms * 0.7f + rms * 0.3f

        val targetScale = if (maxAbs > 0.001f) {
            (0.85f / maxAbs).coerceIn(1.0f, 40.0f)
        } else {
            1.0f
        }

        if (targetScale > visualGainScale) {
            visualGainScale = visualGainScale * 0.65f + targetScale * 0.35f
        } else {
            visualGainScale = visualGainScale * 0.95f + targetScale * 0.05f
        }

        post { invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val height = h.toFloat()
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
            linePath.reset()
            fillPath.reset()

            fillPath.moveTo(0f, centerY)
            var started = false

            val totalEffectiveGain = visualGainScale * userZoomMultiplier

            for (i in 0 until maxDisplaySamples) {
                val bufferIndex = (currentIndex + i) % maxDisplaySamples
                val rawSample = displayBuffer[bufferIndex]

                // Fill ~92-95% of view height with smooth saturation curve clamped at +/-0.95
                val scaledSample = (rawSample * totalEffectiveGain).coerceIn(-0.95f, 0.95f)

                val x = (i.toFloat() / (maxDisplaySamples - 1)) * width
                val y = centerY - (scaledSample * centerY * 0.98f)

                if (!started) {
                    linePath.moveTo(x, y)
                    fillPath.lineTo(x, y)
                    started = true
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, centerY)
            fillPath.close()

            // Draw gradient fill under waveform curve
            canvas.drawPath(fillPath, fillPaint)

            // Draw glowing waveform line (5f stroke width)
            canvas.drawPath(linePath, waveformPaint)

            // Draw non-overlapping RMS amplitude pill badge in top-right corner
            val dbVal = if (currentRms > 0.0001f) (20 * log10(currentRms.toDouble())).toInt().coerceIn(-60, 0) else -60
            val statsText = "RMS ${dbVal} dB"

            val badgeWidth = 110f
            val badgeHeight = 28f
            val margin = 8f
            val badgeRight = width - margin
            val badgeLeft = badgeRight - badgeWidth
            val badgeTop = margin
            val badgeBottom = badgeTop + badgeHeight

            badgeRect.set(badgeLeft, badgeTop, badgeRight, badgeBottom)
            canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBorderPaint)

            canvas.drawText(statsText, badgeRect.centerX(), badgeRect.centerY() + 6f, textPaint)
        }
    }
}
