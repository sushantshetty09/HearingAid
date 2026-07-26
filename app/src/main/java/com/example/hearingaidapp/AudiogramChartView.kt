package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10

/**
 * Custom View that renders an audiogram chart with left and right ear threshold data.
 * Uses a log-scaled frequency axis (250 Hz - 8000 Hz) and linear dB HL axis (0 - 100 dB).
 * Standard audiogram convention: higher dB (worse hearing) is plotted downward.
 */
class AudiogramChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 6-frequency audiogram data: 250, 500, 1000, 2000, 4000, 8000 Hz
    private val frequencies = floatArrayOf(250f, 500f, 1000f, 2000f, 4000f, 8000f)
    private var leftEarValues = floatArrayOf(20f, 25f, 30f, 45f, 50f, 60f)
    private var rightEarValues = floatArrayOf(15f, 20f, 25f, 35f, 45f, 55f)

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val leftEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val leftEarPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val rightEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val rightEarPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }

    // Chart area margins
    private val marginLeft = 80f
    private val marginRight = 30f
    private val marginTop = 30f
    private val marginBottom = 50f

    // dB range: 0 to 100 dB HL (inverted: 0 at top, 100 at bottom)
    private val minDb = 0f
    private val maxDb = 100f
    private val dbGridStep = 20f

    private val leftEarPath = Path()
    private val rightEarPath = Path()

    fun setData(left: FloatArray, right: FloatArray) {
        if (left.size == 6) leftEarValues = left.copyOf()
        if (right.size == 6) rightEarValues = right.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val chartWidth = width - marginLeft - marginRight
        val chartHeight = height - marginTop - marginBottom

        // Background
        canvas.drawRect(marginLeft, marginTop, marginLeft + chartWidth, marginTop + chartHeight, backgroundPaint)

        // Horizontal grid lines (dB levels)
        var db = minDb
        while (db <= maxDb) {
            val y = marginTop + (db / maxDb) * chartHeight
            canvas.drawLine(marginLeft, y, marginLeft + chartWidth, y, gridPaint)
            canvas.drawText("${db.toInt()}", marginLeft - 30f, y + 8f, axisLabelPaint)
            db += dbGridStep
        }

        // Vertical grid lines (frequencies, log-scaled)
        val logMin = log10(frequencies.first().toDouble()).toFloat()
        val logMax = log10(frequencies.last().toDouble()).toFloat()
        val logRange = logMax - logMin

        for (i in frequencies.indices) {
            val logFreq = log10(frequencies[i].toDouble()).toFloat()
            val x = marginLeft + ((logFreq - logMin) / logRange) * chartWidth
            canvas.drawLine(x, marginTop, x, marginTop + chartHeight, gridPaint)

            val freqLabel = if (frequencies[i] >= 1000) "${(frequencies[i] / 1000).toInt()}k" else "${frequencies[i].toInt()}"
            canvas.drawText(freqLabel, x, marginTop + chartHeight + 35f, axisLabelPaint)
        }

        // Plot left ear (X markers)
        leftEarPath.reset()
        for (i in frequencies.indices) {
            val logFreq = log10(frequencies[i].toDouble()).toFloat()
            val x = marginLeft + ((logFreq - logMin) / logRange) * chartWidth
            val y = marginTop + (leftEarValues[i] / maxDb) * chartHeight

            if (i == 0) leftEarPath.moveTo(x, y) else leftEarPath.lineTo(x, y)

            // Draw X marker
            val markerSize = 12f
            canvas.drawLine(x - markerSize, y - markerSize, x + markerSize, y + markerSize, leftEarPointPaint)
            canvas.drawLine(x - markerSize, y + markerSize, x + markerSize, y - markerSize, leftEarPointPaint)
        }
        canvas.drawPath(leftEarPath, leftEarPaint)

        // Plot right ear (O markers)
        rightEarPath.reset()
        for (i in frequencies.indices) {
            val logFreq = log10(frequencies[i].toDouble()).toFloat()
            val x = marginLeft + ((logFreq - logMin) / logRange) * chartWidth
            val y = marginTop + (rightEarValues[i] / maxDb) * chartHeight

            if (i == 0) rightEarPath.moveTo(x, y) else rightEarPath.lineTo(x, y)

            // Draw O marker
            canvas.drawCircle(x, y, 12f, rightEarPointPaint)
        }
        canvas.drawPath(rightEarPath, rightEarPaint)
    }
}
