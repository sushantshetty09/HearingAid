package com.example.hearingaidapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * Custom live frequency spectrum view for the Noise Reduction feature.
 * Shows:
 *  - Input (pre-reduction) magnitude spectrum in dB
 *  - Output (post-reduction) magnitude spectrum in dB
 *  - Learned Noise Profile (dashed line) in dB
 *  - Optional "Show reduction" shaded area between before and after
 *  - Logarithmic frequency axis (50 Hz to 16 kHz) with ticks at 100, 500, 1k, 5k, 10k
 *  - dB vertical axis (-90 dBFS to 0 dBFS) with 20 dB grid lines
 *
 * Guarantees zero memory allocation in onDraw.
 */
class NoiseSpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_FREQ = 50f
        const val MAX_FREQ = 16000f
        const val MIN_DB = -90f
        const val MAX_DB = 0f
        const val DEFAULT_STFT_SIZE = 512
        const val DEFAULT_SAMPLE_RATE = 48000

        /**
         * Calculate physical center frequency for an STFT bin index.
         */
        fun binToFrequency(binIndex: Int, sampleRate: Int = DEFAULT_SAMPLE_RATE, stftSize: Int = DEFAULT_STFT_SIZE): Float {
            return (binIndex.toFloat() * sampleRate) / stftSize
        }

        /**
         * Map frequency to log-scale fraction [0.0, 1.0] across minFreq to maxFreq.
         */
        fun frequencyToXFraction(
            freq: Float,
            minFreq: Float = MIN_FREQ,
            maxFreq: Float = MAX_FREQ
        ): Float {
            val clamped = freq.coerceIn(minFreq, maxFreq)
            val minLog = ln(minFreq.toDouble())
            val maxLog = ln(maxFreq.toDouble())
            return ((ln(clamped.toDouble()) - minLog) / (maxLog - minLog)).toFloat()
        }

        /**
         * Map frequency to pixel X position given view bounds and horizontal padding.
         */
        fun frequencyToX(
            freq: Float,
            width: Float,
            paddingLeft: Float,
            paddingRight: Float,
            minFreq: Float = MIN_FREQ,
            maxFreq: Float = MAX_FREQ
        ): Float {
            val graphWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0f)
            val fraction = frequencyToXFraction(freq, minFreq, maxFreq)
            return paddingLeft + fraction * graphWidth
        }

        /**
         * Map dBFS level (-90 to 0) to pixel Y position.
         */
        fun dbToY(
            db: Float,
            height: Float,
            paddingTop: Float,
            paddingBottom: Float,
            minDb: Float = MIN_DB,
            maxDb: Float = MAX_DB
        ): Float {
            val graphHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0f)
            val clampedDb = db.coerceIn(minDb, maxDb)
            val fraction = (maxDb - clampedDb) / (maxDb - minDb)
            return paddingTop + fraction * graphHeight
        }
    }

    private var sampleRate: Int = DEFAULT_SAMPLE_RATE
    private val stftSize: Int = DEFAULT_STFT_SIZE
    private val numBins: Int = stftSize / 2 + 1 // 257

    // Display curve resolution (64 log-spaced evaluation points)
    private val numCurvePoints = 64
    private val curveFreqs = FloatArray(numCurvePoints)
    private val binLow = IntArray(numCurvePoints)
    private val binHigh = IntArray(numCurvePoints)

    // Pre-allocated dB values and smoothed curves
    private val rawBeforeDb = FloatArray(numCurvePoints) { MIN_DB }
    private val rawAfterDb = FloatArray(numCurvePoints) { MIN_DB }
    private val rawNoiseDb = FloatArray(numCurvePoints) { MIN_DB }
    private val rawGainDb = FloatArray(numCurvePoints) { 0f }

    private val smoothBeforeDb = FloatArray(numCurvePoints) { MIN_DB }
    private val smoothAfterDb = FloatArray(numCurvePoints) { MIN_DB }
    private val smoothNoiseDb = FloatArray(numCurvePoints) { MIN_DB }

    // Calibration & display state
    private var isNoiseLearned = false
    private var learningProgress = 0f
    private var showReduction = true

    // Paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#121815")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1FFFFFFF")
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val axisLabelPaint = Paint().apply {
        color = Color.parseColor("#9AA497")
        textSize = 22f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val beforePaint = Paint().apply {
        color = Color.parseColor("#9E9EB4") // Muted grey/violet
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val afterPaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Primary cyan/teal
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val noisePaint = Paint().apply {
        color = Color.parseColor("#FFAB00") // Amber
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        isAntiAlias = true
    }

    private val reductionFillPaint = Paint().apply {
        color = Color.argb(45, 0, 229, 255) // Semi-transparent cyan fill
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val legendTextPaint = Paint().apply {
        color = Color.parseColor("#D5E0D3")
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val legendChipPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val calibrationOverlayPaint = Paint().apply {
        color = Color.argb(190, 18, 24, 21)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val calibrationTextPaint = Paint().apply {
        color = Color.parseColor("#FFD54F")
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val calibrationSubtextPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        textSize = 20f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val calibrationPbBgPaint = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val calibrationPbFillPaint = Paint().apply {
        color = Color.parseColor("#FFAB00")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Pre-allocated paths & rects for zero allocations in onDraw
    private val beforeCurvePath = Path()
    private val afterCurvePath = Path()
    private val noiseCurvePath = Path()
    private val reductionFillPath = Path()
    private val bgRect = RectF()
    private val calibPbRect = RectF()
    private val calibPbFillRect = RectF()

    // Pre-computed tick frequencies
    private val xTickFreqs = floatArrayOf(100f, 500f, 1000f, 5000f, 10000f)
    private val xTickLabels = arrayOf("100", "500", "1k", "5k", "10k")

    // Y ticks every 20 dB: 0, -20, -40, -60, -80 dB
    private val yTickDbs = floatArrayOf(0f, -20f, -40f, -60f, -80f)
    private val yTickLabels = arrayOf("0", "-20", "-40", "-60", "-80")

    init {
        setupLogBinMapping()
    }

    fun setSampleRate(sr: Int) {
        if (sr > 0 && sr != sampleRate) {
            sampleRate = sr
            setupLogBinMapping()
            invalidate()
        }
    }

    fun setShowReduction(show: Boolean) {
        showReduction = show
        invalidate()
    }

    fun isShowReduction(): Boolean = showReduction

    fun clearNoiseProfile() {
        isNoiseLearned = false
        learningProgress = 0f
        smoothNoiseDb.fill(MIN_DB)
        invalidate()
    }

    private fun setupLogBinMapping() {
        val minLog = ln(MIN_FREQ.toDouble())
        val maxLog = ln(MAX_FREQ.toDouble())
        val logSpan = maxLog - minLog

        for (i in 0 until numCurvePoints) {
            val f = exp(minLog + (i.toDouble() / (numCurvePoints - 1)) * logSpan).toFloat()
            curveFreqs[i] = f

            // Half-bin bounds in log space
            val fLow = exp(minLog + ((i - 0.5).coerceAtLeast(0.0) / (numCurvePoints - 1)) * logSpan).toFloat()
            val fHigh = exp(minLog + ((i + 0.5).coerceAtMost((numCurvePoints - 1).toDouble()) / (numCurvePoints - 1)) * logSpan).toFloat()

            val kLow = ((fLow * stftSize) / sampleRate).toInt().coerceIn(0, numBins - 1)
            val kHigh = ((fHigh * stftSize) / sampleRate).toInt().coerceIn(kLow, numBins - 1)

            binLow[i] = kLow
            binHigh[i] = kHigh
        }
    }

    /**
     * Update spectrum data with fresh STFT frame snapshots.
     * Audio-thread safe (called on UI thread with pre-copied snapshots).
     */
    fun updateSpectrumData(
        before: FloatArray,
        after: FloatArray,
        noise: FloatArray,
        gain: FloatArray,
        noiseLearned: Boolean,
        progress: Float
    ) {
        this.isNoiseLearned = noiseLearned
        this.learningProgress = progress

        // Map 257 linear STFT bins to 64 log-spaced curve points
        for (i in 0 until numCurvePoints) {
            val k1 = binLow[i]
            val k2 = binHigh[i]

            var sumBefore = 0f
            var sumAfter = 0f
            var sumNoise = 0f
            var sumGain = 0f
            val count = (k2 - k1 + 1).coerceAtLeast(1)

            if (k1 == k2) {
                sumBefore = before[k1]
                sumAfter = after[k1]
                sumNoise = noise[k1]
                sumGain = gain[k1]
            } else {
                for (k in k1..k2) {
                    sumBefore += before[k]
                    sumAfter += after[k]
                    sumNoise += noise[k]
                    sumGain += gain[k]
                }
                sumBefore /= count
                sumAfter /= count
                sumNoise /= count
                sumGain /= count
            }

            // Convert to dB with 20 * log10(mag + 1e-9)
            val dbB = (20f * log10(sumBefore + 1e-9f)).coerceIn(MIN_DB, MAX_DB)
            val dbA = (20f * log10(sumAfter + 1e-9f)).coerceIn(MIN_DB, MAX_DB)
            val dbN = if (noiseLearned) (20f * log10(sumNoise + 1e-9f)).coerceIn(MIN_DB, MAX_DB) else MIN_DB
            val dbG = (20f * log10(sumGain + 1e-9f)).coerceIn(-30f, 0f)

            rawBeforeDb[i] = dbB
            rawAfterDb[i] = dbA
            rawNoiseDb[i] = dbN
            rawGainDb[i] = dbG

            // Light exponential smoothing
            smoothBeforeDb[i] = smoothBeforeDb[i] * 0.65f + dbB * 0.35f
            smoothAfterDb[i] = smoothAfterDb[i] * 0.65f + dbA * 0.35f
            if (noiseLearned) {
                smoothNoiseDb[i] = smoothNoiseDb[i] * 0.85f + dbN * 0.15f
            } else {
                smoothNoiseDb[i] = MIN_DB
            }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val paddingLeft = 60f
        val paddingRight = 24f
        val paddingTop = 44f
        val paddingBottom = 34f

        val graphWidth = (w - paddingLeft - paddingRight).coerceAtLeast(10f)
        val graphHeight = (h - paddingTop - paddingBottom).coerceAtLeast(10f)

        // Draw dark rounded background
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)

        // Draw horizontal Y grid lines and labels
        for (i in yTickDbs.indices) {
            val db = yTickDbs[i]
            val y = dbToY(db, h, paddingTop, paddingBottom)
            canvas.drawLine(paddingLeft, y, paddingLeft + graphWidth, y, gridPaint)
            axisLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(yTickLabels[i], paddingLeft - 8f, y + 7f, axisLabelPaint)
        }

        // Draw vertical X grid lines and labels
        axisLabelPaint.textAlign = Paint.Align.CENTER
        for (i in xTickFreqs.indices) {
            val freq = xTickFreqs[i]
            val x = frequencyToX(freq, w, paddingLeft, paddingRight)
            canvas.drawLine(x, paddingTop, x, paddingTop + graphHeight, gridPaint)
            canvas.drawText(xTickLabels[i], x, h - 8f, axisLabelPaint)
        }

        // Draw dB and Hz corner units
        axisLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("dBFS", 8f, paddingTop - 12f, axisLabelPaint)
        axisLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Hz", w - 6f, h - 8f, axisLabelPaint)

        // Construct Before, After, and Reduction fill paths
        beforeCurvePath.reset()
        afterCurvePath.reset()
        reductionFillPath.reset()

        var firstPoint = true
        for (i in 0 until numCurvePoints) {
            val x = paddingLeft + (i.toFloat() / (numCurvePoints - 1)) * graphWidth
            val yBefore = dbToY(smoothBeforeDb[i], h, paddingTop, paddingBottom)
            val yAfter = dbToY(smoothAfterDb[i], h, paddingTop, paddingBottom)

            if (firstPoint) {
                beforeCurvePath.moveTo(x, yBefore)
                afterCurvePath.moveTo(x, yAfter)
                firstPoint = false
            } else {
                beforeCurvePath.lineTo(x, yBefore)
                afterCurvePath.lineTo(x, yAfter)
            }
        }

        // Shaded reduction region between Before and After curves
        if (showReduction) {
            reductionFillPath.set(beforeCurvePath)
            // Trace backward along After curve
            for (i in (numCurvePoints - 1) downTo 0) {
                val x = paddingLeft + (i.toFloat() / (numCurvePoints - 1)) * graphWidth
                val yAfter = dbToY(smoothAfterDb[i], h, paddingTop, paddingBottom)
                reductionFillPath.lineTo(x, yAfter)
            }
            reductionFillPath.close()
            canvas.drawPath(reductionFillPath, reductionFillPaint)
        }

        // Draw Before curve
        canvas.drawPath(beforeCurvePath, beforePaint)

        // Draw After curve
        canvas.drawPath(afterCurvePath, afterPaint)

        // Draw Noise Profile (if learned)
        if (isNoiseLearned) {
            noiseCurvePath.reset()
            firstPoint = true
            for (i in 0 until numCurvePoints) {
                val x = paddingLeft + (i.toFloat() / (numCurvePoints - 1)) * graphWidth
                val yNoise = dbToY(smoothNoiseDb[i], h, paddingTop, paddingBottom)
                if (firstPoint) {
                    noiseCurvePath.moveTo(x, yNoise)
                    firstPoint = false
                } else {
                    noiseCurvePath.lineTo(x, yNoise)
                }
            }
            canvas.drawPath(noiseCurvePath, noisePaint)
        }

        // Draw Top Legend
        drawLegend(canvas, paddingLeft, paddingTop - 14f, graphWidth)

        // If calibrating, draw clean non-blocking banner/overlay
        if (!isNoiseLearned && learningProgress < 1.0f) {
            drawCalibrationOverlay(canvas, paddingLeft, paddingTop, graphWidth, graphHeight)
        }
    }

    private fun drawLegend(canvas: Canvas, left: Float, baselineY: Float, width: Float) {
        var curX = left + 40f

        // 1. Input (Before) chip
        legendChipPaint.color = beforePaint.color
        canvas.drawCircle(curX, baselineY - 6f, 6f, legendChipPaint)
        curX += 12f
        canvas.drawText("Input", curX, baselineY, legendTextPaint)
        curX += 68f

        // 2. Output (After) chip
        legendChipPaint.color = afterPaint.color
        canvas.drawCircle(curX, baselineY - 6f, 6f, legendChipPaint)
        curX += 12f
        canvas.drawText("Output", curX, baselineY, legendTextPaint)
        curX += 78f

        // 3. Noise Profile chip
        if (isNoiseLearned) {
            legendChipPaint.color = noisePaint.color
            canvas.drawCircle(curX, baselineY - 6f, 6f, legendChipPaint)
            curX += 12f
            canvas.drawText("Noise Profile", curX, baselineY, legendTextPaint)
        }
    }

    private fun drawCalibrationOverlay(
        canvas: Canvas,
        graphLeft: Float,
        graphTop: Float,
        graphWidth: Float,
        graphHeight: Float
    ) {
        val bannerHeight = 84f
        val bannerTop = graphTop + (graphHeight - bannerHeight) / 2f
        val bannerRect = RectF(graphLeft + 16f, bannerTop, graphLeft + graphWidth - 16f, bannerTop + bannerHeight)

        canvas.drawRoundRect(bannerRect, 12f, 12f, calibrationOverlayPaint)

        val pct = (learningProgress * 100).toInt().coerceIn(0, 100)
        val textY = bannerTop + 32f
        canvas.drawText("Learning your environment... $pct%", bannerRect.centerX(), textY, calibrationTextPaint)
        canvas.drawText("Please stay quiet for ~2 seconds", bannerRect.centerX(), textY + 24f, calibrationSubtextPaint)

        // Mini progress bar at bottom of banner
        val pbWidth = bannerRect.width() * 0.7f
        val pbLeft = bannerRect.centerX() - pbWidth / 2f
        val pbTop = textY + 32f
        calibPbRect.set(pbLeft, pbTop, pbLeft + pbWidth, pbTop + 6f)
        canvas.drawRoundRect(calibPbRect, 3f, 3f, calibrationPbBgPaint)

        val fillWidth = pbWidth * (learningProgress.coerceIn(0f, 1f))
        calibPbFillRect.set(pbLeft, pbTop, pbLeft + fillWidth, pbTop + 6f)
        canvas.drawRoundRect(calibPbFillRect, 3f, 3f, calibrationPbFillPaint)
    }
}
