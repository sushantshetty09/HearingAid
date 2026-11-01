package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numBars = 48
    private val magnitudes = FloatArray(numBars)
    private val smoothedMagnitudes = FloatArray(numBars)
    private val peakMagnitudes = FloatArray(numBars)
    private val barBinIndices = IntArray(numBars)

    // Colors loaded from colors.xml
    private val colorLow = ContextCompat.getColor(context, R.color.spectrum_low)     // Violet #7C4DFF
    private val colorMid = ContextCompat.getColor(context, R.color.spectrum_mid)     // Teal #00BFA5
    private val colorHigh = ContextCompat.getColor(context, R.color.spectrum_high)   // Amber #FFAB00
    private val colorMidHighlight = ContextCompat.getColor(context, R.color.spectrum_mid_highlight)
    private val colorPeak = ContextCompat.getColor(context, R.color.spectrum_peak)   // White 80% alpha

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1A00BFA5")
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val midHighlightPaint = Paint().apply {
        color = colorMidHighlight
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val peakPaint = Paint().apply {
        color = colorPeak
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val zoneLabelPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        textSize = 18f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Pre-allocated Paints & Shaders for zero allocation in onDraw
    private val barPaints = Array(numBars) { Paint().apply { isAntiAlias = true; style = Paint.Style.FILL } }
    private val barRects = Array(numBars) { RectF() }

    // Logarithmic frequency labels for the bottom axis
    private val targetFreqs = floatArrayOf(60f, 250f, 1000f, 4000f, 16000f)
    private val freqLabels = arrayOf("60Hz", "250Hz", "1kHz", "4kHz", "16kHz")
    private val freqFractions = FloatArray(targetFreqs.size)

    // Zone X boundaries
    private var midZoneLeft = 0f
    private var midZoneRight = 0f
    private var lowZoneCenterX = 0f
    private var midZoneCenterX = 0f
    private var highZoneCenterX = 0f
    private val midHighlightRect = RectF()

    init {
        // Calculate exact log fractions for axis labels from 50 Hz to 16000 Hz
        val minLog = ln(50.0)
        val maxLog = ln(16000.0)
        val logSpan = maxLog - minLog

        for (i in targetFreqs.indices) {
            val freqLog = ln(targetFreqs[i].toDouble())
            val frac = ((freqLog - minLog) / logSpan).coerceIn(0.0, 1.0)
            freqFractions[i] = frac.toFloat()
        }

        // Pre-calculate FFT bin index for each of the 48 log-spaced bars (assuming 256 positive bins for 512-pt FFT at 48kHz)
        val nyquist = 24000f
        val fftBins = 256
        for (i in 0 until numBars) {
            val centerFreq = FFTProcessor.getCenterFrequencyForBand(i, numBars)
            val bin = ((centerFreq / nyquist) * (fftBins - 1)).roundToInt().coerceIn(0, fftBins - 1)
            barBinIndices[i] = bin
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val width = w.toFloat()
        val height = h.toFloat()
        val bottomMargin = 28f
        val usableHeight = height - bottomMargin

        // Calculate zone boundaries corresponding to <250 Hz, 250 Hz - 4000 Hz, >4000 Hz
        val minLog = ln(50.0)
        val maxLog = ln(16000.0)
        val logSpan = maxLog - minLog

        val lowFrac = ((ln(250.0) - minLog) / logSpan).toFloat()
        val highFrac = ((ln(4000.0) - minLog) / logSpan).toFloat()

        midZoneLeft = lowFrac * width
        midZoneRight = highFrac * width
        midHighlightRect.set(midZoneLeft, 0f, midZoneRight, usableHeight)

        lowZoneCenterX = midZoneLeft / 2f
        midZoneCenterX = (midZoneLeft + midZoneRight) / 2f
        highZoneCenterX = (midZoneRight + width) / 2f

        // Create cached vertical LinearGradient shaders for each bar:
        // brighter at top of bar, dimmer at the base (never allocated in onDraw)
        for (i in 0 until numBars) {
            val freq = FFTProcessor.getCenterFrequencyForBand(i, numBars)
            val baseColor = when {
                freq < 250f -> interpolateColor(colorLow, colorMid, freq / 250f)
                freq <= 4000f -> interpolateColor(colorMid, colorHigh, (freq - 250f) / (4000f - 250f))
                else -> colorHigh
            }

            // Top is full bright color, bottom is 25% alpha base color
            val topColor = baseColor
            val bottomColor = Color.argb(
                64,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )

            val shader = LinearGradient(
                0f, 0f, 0f, usableHeight,
                intArrayOf(topColor, bottomColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            barPaints[i].shader = shader
        }
    }

    private fun interpolateColor(c1: Int, c2: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(c1) + f * (Color.red(c2) - Color.red(c1))).toInt()
        val g = (Color.green(c1) + f * (Color.green(c2) - Color.green(c1))).toInt()
        val b = (Color.blue(c1) + f * (Color.blue(c2) - Color.blue(c1))).toInt()
        return Color.rgb(r, g, b)
    }

    fun updateSpectrum(fftMagnitudes: FloatArray) {
        if (fftMagnitudes.isEmpty()) return

        val maxBin = fftMagnitudes.size - 1

        for (i in 0 until numBars) {
            val bin = barBinIndices[i].coerceIn(0, maxBin)
            val rawMag = fftMagnitudes[bin]

            val dbNormalized = if (rawMag > 0.0001f) {
                (20 * log10(rawMag.toDouble()) + 60.0) / 60.0
            } else 0.0

            val clamped = dbNormalized.toFloat().coerceIn(0.05f, 1.0f)
            magnitudes[i] = clamped

            smoothedMagnitudes[i] = smoothedMagnitudes[i] * 0.65f + magnitudes[i] * 0.35f

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

        // 1. Draw Mid-frequency strip background highlight (250 Hz - 4 kHz)
        canvas.drawRect(midHighlightRect, midHighlightPaint)

        // 2. Draw horizontal grid lines
        val gridRows = 4
        for (i in 1 until gridRows) {
            val y = usableHeight - (usableHeight / gridRows) * i
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        // 3. Draw zone labels: "LOW", "MID", "HIGH"
        zoneLabelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("LOW", lowZoneCenterX, 20f, zoneLabelPaint)
        canvas.drawText("MID", midZoneCenterX, 20f, zoneLabelPaint)
        canvas.drawText("HIGH", highZoneCenterX, 20f, zoneLabelPaint)

        // 4. Draw spectrum bars and peak-hold dots
        val barWidth = width / numBars
        val padding = barWidth * 0.15f

        for (i in 0 until numBars) {
            val barHeight = smoothedMagnitudes[i] * usableHeight * 0.88f
            val x = i * barWidth + padding
            val y = usableHeight - barHeight

            barRects[i].set(x, y, x + barWidth - padding * 2, usableHeight)
            canvas.drawRoundRect(barRects[i], 4f, 4f, barPaints[i])

            // Draw Peak hold dots (white 80% alpha)
            val peakY = usableHeight - (peakMagnitudes[i] * usableHeight * 0.88f) - 4f
            canvas.drawCircle(x + (barWidth - padding * 2) / 2, peakY.coerceAtLeast(4f), 3.5f, peakPaint)
        }

        // 5. Draw Bottom Log-Mapped Frequency Axis Labels
        for (idx in freqLabels.indices) {
            val frac = freqFractions[idx]
            val barCenterForFrac = (frac * (numBars - 1) + 0.5f) * barWidth
            val labelX = barCenterForFrac.coerceIn(24f, width - 24f)
            canvas.drawText(freqLabels[idx], labelX, height - 6f, labelPaint)
        }
    }
}
