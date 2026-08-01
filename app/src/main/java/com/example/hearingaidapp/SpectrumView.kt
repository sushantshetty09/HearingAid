package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val peakPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1A00E5FF")
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#B3FFFFFF")
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val numBars = 48
    private val magnitudes = FloatArray(numBars)
    private val smoothedMagnitudes = FloatArray(numBars)
    private val peakMagnitudes = FloatArray(numBars)

    // Logarithmic frequency labels for the bottom axis
    private val freqLabels = arrayOf("60Hz", "250Hz", "1kHz", "4kHz", "16kHz")
    private val freqPositions = floatArrayOf(0.08f, 0.28f, 0.50f, 0.72f, 0.92f)

    // Color gradient for spectrum bars across frequency spectrum
    private val barColors = intArrayOf(
        Color.parseColor("#0288D1"), // Deep Blue (Sub-bass)
        Color.parseColor("#00ACC1"), // Cyan (Bass)
        Color.parseColor("#00E676"), // Emerald Green (Low Mid)
        Color.parseColor("#76FF03"), // Lime Green (Mid)
        Color.parseColor("#FFD600"), // Gold (High Mid)
        Color.parseColor("#FF1744")  // Crimson Red (Treble)
    )

    fun updateSpectrum(fftMagnitudes: FloatArray) {
        if (fftMagnitudes.isEmpty()) return

        for (i in 0 until numBars) {
            val logIndex = (exp(i.toDouble() / numBars * ln(fftMagnitudes.size.toDouble())) - 1).toInt()
                .coerceIn(0, fftMagnitudes.size - 1)

            val rawMag = fftMagnitudes[logIndex]
            val dbNormalized = if (rawMag > 0.0001f) {
                (20 * log10(rawMag.toDouble()) + 60.0) / 60.0
            } else 0.0

            val clamped = dbNormalized.toFloat().coerceIn(0.05f, 1.0f)
            magnitudes[i] = clamped

            smoothedMagnitudes[i] = smoothedMagnitudes[i] * 0.65f + magnitudes[i] * 0.35f

            // Peak hold decay calculation
            if (smoothedMagnitudes[i] > peakMagnitudes[i]) {
                peakMagnitudes[i] = smoothedMagnitudes[i]
            } else {
                peakMagnitudes[i] = (peakMagnitudes[i] - 0.02f).coerceAtLeast(smoothedMagnitudes[i])
            }
        }

        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val bottomMargin = 28f
        val usableHeight = height - bottomMargin

        // Draw horizontal grid lines
        val gridRows = 4
        for (i in 1 until gridRows) {
            val y = usableHeight - (usableHeight / gridRows) * i
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // Draw spectrum bars
        val barWidth = width / numBars
        val padding = barWidth * 0.15f

        for (i in 0 until numBars) {
            val barHeight = smoothedMagnitudes[i] * usableHeight * 0.9f
            val x = i * barWidth + padding
            val y = usableHeight - barHeight

            val colorFraction = i.toFloat() / (numBars - 1)
            val colorIdx = (colorFraction * (barColors.size - 1)).toInt()
            barPaint.color = barColors[colorIdx]

            val rect = RectF(x, y, x + barWidth - padding * 2, usableHeight)
            canvas.drawRoundRect(rect, 4f, 4f, barPaint)

            // Draw Peak hold dots
            val peakY = usableHeight - (peakMagnitudes[i] * usableHeight * 0.9f) - 4f
            canvas.drawCircle(x + (barWidth - padding * 2) / 2, peakY.coerceAtLeast(4f), 3f, peakPaint)
        }

        // Draw Bottom Frequency Axis Labels
        for (idx in freqLabels.indices) {
            val labelX = width * freqPositions[idx]
            canvas.drawText(freqLabels[idx], labelX, height - 6f, labelPaint)
        }
    }
}
