package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * Custom live response curve preview thumbnail for EQ filters.
 * Pre-calculates 100 logarithmic points between 20 Hz and 20 kHz with zero allocations in onDraw.
 */
class FilterPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var filterFrequency = 1000f
    private var filterGain = 0f
    private var filterQ = 1.0f
    private var filterType = AudioEngine.FilterType.PEAKING

    private val numPoints = 100
    private val testFrequencies = FloatArray(numPoints)
    private val responseMagDb = FloatArray(numPoints)

    private val curvePaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Cyan response curve
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#2000E5FF")
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1AFFFFFF")
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#4400E5FF")
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        isAntiAlias = true
    }

    private val curvePath = Path()
    private val fillPath = Path()

    init {
        // Pre-compute 100 test frequencies on a log scale from 20 Hz to 20,000 Hz
        val minLog = ln(20.0)
        val maxLog = ln(20000.0)
        for (i in 0 until numPoints) {
            val logVal = minLog + (i.toDouble() / (numPoints - 1)) * (maxLog - minLog)
            testFrequencies[i] = exp(logVal).toFloat()
        }
        computeResponse()
    }

    private fun computeResponse() {
        AudioEngine.calculateMagnitudeResponse(
            frequency = filterFrequency,
            gain = filterGain,
            q = filterQ,
            filterType = filterType,
            sampleRate = 48000,
            testFrequencies = testFrequencies,
            outResponse = responseMagDb
        )
    }

    fun setFilter(frequency: Float, gain: Float, q: Float, type: AudioEngine.FilterType) {
        filterFrequency = frequency
        filterGain = gain
        filterQ = q
        filterType = type
        computeResponse()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val centerY = height / 2f
        val maxDbRange = 24f // +/- 24 dB visual scale

        // Draw horizontal 0 dB center line
        canvas.drawLine(0f, centerY, width, centerY, centerLinePaint)

        // Draw vertical center grid line
        canvas.drawLine(width / 2f, 0f, width / 2f, height, gridPaint)

        curvePath.reset()
        fillPath.reset()
        fillPath.moveTo(0f, centerY)

        for (i in 0 until numPoints) {
            val x = (i.toFloat() / (numPoints - 1)) * width
            val magDb = responseMagDb[i]
            val y = centerY - (magDb / maxDbRange) * (height / 2f)
            val clampedY = y.coerceIn(2f, height - 2f)

            if (i == 0) {
                curvePath.moveTo(x, clampedY)
                fillPath.lineTo(x, clampedY)
            } else {
                curvePath.lineTo(x, clampedY)
                fillPath.lineTo(x, clampedY)
            }
        }

        fillPath.lineTo(width, centerY)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(curvePath, curvePaint)
    }
}
