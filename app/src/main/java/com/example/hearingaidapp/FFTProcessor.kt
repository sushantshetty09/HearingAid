package com.example.hearingaidapp

import kotlin.math.*

/**
 * High-performance, zero-allocation Fast Fourier Transform (FFT) processor.
 * Uses an iterative in-place Cooley-Tukey radix-2 algorithm with a pre-computed
 * Hann window and pre-allocated buffers to prevent GC pauses on the audio thread.
 */
class FFTProcessor(private val fftSize: Int = 512) {

    private val inputBuffer = FloatArray(fftSize)
    private var bufferIndex = 0

    // Pre-computed Hann window
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
    }

    // Pre-allocated in-place FFT work arrays
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2)
    private val smoothedMagnitudes = FloatArray(fftSize / 2)

    fun addSamples(samples: ShortArray, length: Int = samples.size) {
        val count = min(length, samples.size)
        val shortInv = 1.0f / Short.MAX_VALUE
        for (i in 0 until count) {
            if (bufferIndex < fftSize) {
                inputBuffer[bufferIndex] = samples[i] * shortInv
                bufferIndex++
            } else {
                break
            }
        }
    }

    fun isReady(): Boolean = bufferIndex >= fftSize

    /**
     * Compute FFT magnitudes with zero allocations.
     * Applies Hann windowing, iterative in-place FFT, and exponential temporal smoothing.
     */
    fun computeFFT(outMagnitudes: FloatArray? = null): FloatArray {
        val dest = outMagnitudes ?: magnitudes
        val outputSize = min(dest.size, fftSize / 2)

        if (!isReady()) {
            return dest
        }

        // 1. Copy windowed input into real work buffer, zero imag buffer
        for (i in 0 until fftSize) {
            real[i] = (inputBuffer[i] * hannWindow[i]).toDouble()
            imag[i] = 0.0
        }

        // 2. Iterative in-place Bit-Reversal Permutation
        var j = 0
        for (i in 0 until fftSize) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = fftSize / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // 3. Cooley-Tukey Butterfly Stages
        var mLen = 2
        while (mLen <= fftSize) {
            val halfM = mLen / 2
            val angle = -2.0 * PI / mLen
            val wReal = cos(angle)
            val wImag = sin(angle)

            var k = 0
            while (k < fftSize) {
                var wr = 1.0
                var wi = 0.0
                for (jj in 0 until halfM) {
                    val idx = k + jj + halfM
                    val tr = wr * real[idx] - wi * imag[idx]
                    val ti = wr * imag[idx] + wi * real[idx]

                    real[idx] = real[k + jj] - tr
                    imag[idx] = imag[k + jj] - ti
                    real[k + jj] += tr
                    imag[k + jj] += ti

                    val nextWr = wr * wReal - wi * wImag
                    wi = wr * wImag + wi * wReal
                    wr = nextWr
                }
                k += mLen
            }
            mLen *= 2
        }

        // 4. Calculate magnitudes and apply temporal smoothing
        for (i in 0 until outputSize) {
            val r = real[i]
            val im = imag[i]
            val mag = sqrt(r * r + im * im).toFloat()
            smoothedMagnitudes[i] = smoothedMagnitudes[i] * 0.7f + mag * 0.3f
            dest[i] = smoothedMagnitudes[i]
        }

        // Reset buffer index for next chunk
        bufferIndex = 0

        return dest
    }

    fun getFrequencyForBin(binIndex: Int, sampleRate: Int = 48000): Float {
        return (binIndex.toFloat() * sampleRate) / fftSize
    }

    companion object {
        /**
         * Calculate logarithmic center frequency for a bar index in a 48-band visualizer.
         * Maps from minFreq (50 Hz) to maxFreq (16000 Hz).
         */
        fun getCenterFrequencyForBand(
            bandIndex: Int,
            numBands: Int = 48,
            minFreq: Float = 50f,
            maxFreq: Float = 16000f
        ): Float {
            val clampedIdx = bandIndex.coerceIn(0, numBands - 1)
            val logMin = ln(minFreq.toDouble())
            val logMax = ln(maxFreq.toDouble())
            val logFreq = logMin + (clampedIdx.toDouble() / (numBands - 1)) * (logMax - logMin)
            return exp(logFreq).toFloat()
        }

        /**
         * Inverse mapping: find the closest band index for a given frequency.
         */
        fun getBandForFrequency(
            frequency: Float,
            numBands: Int = 48,
            minFreq: Float = 50f,
            maxFreq: Float = 16000f
        ): Int {
            val clampedFreq = frequency.coerceIn(minFreq, maxFreq)
            val logMin = ln(minFreq.toDouble())
            val logMax = ln(maxFreq.toDouble())
            val fraction = (ln(clampedFreq.toDouble()) - logMin) / (logMax - logMin)
            return (fraction * (numBands - 1)).roundToInt().coerceIn(0, numBands - 1)
        }
    }
}
