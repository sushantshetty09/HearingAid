package com.example.hearingaidapp

import android.content.Context
import android.media.*
import android.os.Process
import android.util.Log
import kotlin.math.*


class AudioEngine(private val context: Context) {
    companion object {
        private const val TAG = "AudioEngine"

        /**
         * Convert slider progress (0 to 1000) to logarithmic Q factor (0.1 to 10.0).
         * Log scale provides finer control for lower Q values (0.3 - 2.0).
         */
        fun sliderToQ(progress: Int): Float {
            val minLog = ln(0.1f)
            val maxLog = ln(10.0f)
            val fraction = progress.coerceIn(0, 1000) / 1000.0f
            val logValue = minLog + fraction * (maxLog - minLog)
            return exp(logValue)
        }

        /**
         * Convert Q factor (0.1 to 10.0) to logarithmic slider progress (0 to 1000).
         */
        fun qToSlider(q: Float): Int {
            val clampedQ = q.coerceIn(0.1f, 10.0f)
            val minLog = ln(0.1f)
            val maxLog = ln(10.0f)
            val fraction = (ln(clampedQ) - minLog) / (maxLog - minLog)
            return (fraction * 1000).toInt().coerceIn(0, 1000)
        }

        /**
         * Calculate bandwidth in octaves for a given Q factor:
         * BW = 2 * asinh(1 / (2*Q)) / ln(2)
         */
        fun qToBandwidthOctaves(q: Float): Float {
            val clampedQ = q.coerceAtLeast(0.01f)
            val arg = 1.0f / (2.0f * clampedQ)
            val asinhVal = ln(arg + sqrt(arg * arg + 1.0f))
            return (2.0f * asinhVal / ln(2.0f)).toFloat()
        }

        /**
         * Compute biquad filter magnitude response in dB across target frequencies.
         * H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2) evaluated at z = e^(j*w)
         */
        fun calculateMagnitudeResponse(
            frequency: Float,
            gain: Float,
            q: Float,
            filterType: FilterType,
            sampleRate: Int = 48000,
            testFrequencies: FloatArray,
            outResponse: FloatArray? = null
        ): FloatArray {
            val omega0 = 2 * PI * frequency / sampleRate
            val cosW0 = cos(omega0).toFloat()
            val sinW0 = sin(omega0).toFloat()
            val alpha = sinW0 / (2 * q.coerceAtLeast(0.1f))
            val A = 10.0.pow(gain / 40.0).toFloat()

            var b0 = 1f; var b1 = 0f; var b2 = 0f
            var a0 = 1f; var a1 = 0f; var a2 = 0f

            when (filterType) {
                FilterType.PEAKING -> {
                    b0 = 1 + alpha * A
                    b1 = -2 * cosW0
                    b2 = 1 - alpha * A
                    a0 = 1 + alpha / A
                    a1 = -2 * cosW0
                    a2 = 1 - alpha / A
                }
                FilterType.LOW_PASS -> {
                    b0 = (1 - cosW0) / 2
                    b1 = 1 - cosW0
                    b2 = (1 - cosW0) / 2
                    a0 = 1 + alpha
                    a1 = -2 * cosW0
                    a2 = 1 - alpha
                }
                FilterType.HIGH_PASS -> {
                    b0 = (1 + cosW0) / 2
                    b1 = -(1 + cosW0)
                    b2 = (1 + cosW0) / 2
                    a0 = 1 + alpha
                    a1 = -2 * cosW0
                    a2 = 1 - alpha
                }
                FilterType.BAND_PASS -> {
                    b0 = alpha
                    b1 = 0f
                    b2 = -alpha
                    a0 = 1 + alpha
                    a1 = -2 * cosW0
                    a2 = 1 - alpha
                }
                FilterType.NOTCH -> {
                    b0 = 1f
                    b1 = -2 * cosW0
                    b2 = 1f
                    a0 = 1 + alpha
                    a1 = -2 * cosW0
                    a2 = 1 - alpha
                }
                FilterType.LOW_SHELF -> {
                    val sqrtA = sqrt(A)
                    b0 = A * ((A + 1) - (A - 1) * cosW0 + 2 * sqrtA * alpha)
                    b1 = 2 * A * ((A - 1) - (A + 1) * cosW0)
                    b2 = A * ((A + 1) - (A - 1) * cosW0 - 2 * sqrtA * alpha)
                    a0 = (A + 1) + (A - 1) * cosW0 + 2 * sqrtA * alpha
                    a1 = -2 * ((A - 1) + (A + 1) * cosW0)
                    a2 = (A + 1) + (A - 1) * cosW0 - 2 * sqrtA * alpha
                }
                FilterType.HIGH_SHELF -> {
                    val sqrtA = sqrt(A)
                    b0 = A * ((A + 1) + (A - 1) * cosW0 + 2 * sqrtA * alpha)
                    b1 = -2 * A * ((A - 1) + (A + 1) * cosW0)
                    b2 = A * ((A + 1) + (A - 1) * cosW0 - 2 * sqrtA * alpha)
                    a0 = (A + 1) - (A - 1) * cosW0 + 2 * sqrtA * alpha
                    a1 = 2 * ((A - 1) - (A + 1) * cosW0)
                    a2 = (A + 1) - (A - 1) * cosW0 - 2 * sqrtA * alpha
                }
            }

            b0 /= a0; b1 /= a0; b2 /= a0
            a1 /= a0; a2 /= a0

            val response = outResponse ?: FloatArray(testFrequencies.size)
            for (i in testFrequencies.indices) {
                val f = testFrequencies[i]
                val w = 2.0 * PI * f / sampleRate
                val cosW = cos(w)
                val sinW = sin(w)
                val cos2W = cos(2 * w)
                val sin2W = sin(2 * w)

                val numRe = b0 + b1 * cosW + b2 * cos2W
                val numIm = -b1 * sinW - b2 * sin2W

                val denRe = 1.0 + a1 * cosW + a2 * cos2W
                val denIm = -a1 * sinW - a2 * sin2W

                val numMagSq = numRe * numRe + numIm * numIm
                val denMagSq = denRe * denRe + denIm * denIm

                val magSq = if (denMagSq > 1e-12) numMagSq / denMagSq else 1.0
                val magDb = (10.0 * log10(magSq)).toFloat()
                response[i] = magDb.coerceIn(-30f, 30f)
            }
            return response
        }
    }

    enum class FilterType {
        PEAKING,      // Parametric EQ - boost or cut at specific frequency
        LOW_PASS,     // Allows frequencies below cutoff
        HIGH_PASS,    // Allows frequencies above cutoff
        BAND_PASS,    // Allows frequencies around center frequency
        NOTCH,        // Removes frequencies around center frequency
        LOW_SHELF,    // Boost or cut all frequencies below cutoff
        HIGH_SHELF;   // Boost or cut all frequencies above cutoff

        fun getDisplayName(): String {
            return when (this) {
                PEAKING -> "Peaking"
                LOW_PASS -> "Low-Pass"
                HIGH_PASS -> "High-Pass"
                BAND_PASS -> "Band-Pass"
                NOTCH -> "Notch"
                LOW_SHELF -> "Low-Shelf"
                HIGH_SHELF -> "High-Shelf"
            }
        }

        companion object {
            fun fromString(name: String): FilterType {
                return values().find { it.name == name } ?: PEAKING
            }
        }
    }

    // Feedback listener interface
    interface FeedbackEventListener {
        fun onFeedbackDetected(frequencyHz: Float)
    }

    private var feedbackEventListener: FeedbackEventListener? = null

    fun setFeedbackEventListener(listener: FeedbackEventListener?) {
        feedbackEventListener = listener
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var isRecording = false

    // Ultra-low-latency optimized settings
    private var sampleRate: Int = 48000
    private var recordBufferSize: Int = 0
    private var playBufferSize: Int = 0
    private var targetAmplificationGain = 1.5f
    private var currentAmplificationGain = 1.5f
    private var autoHeadroomScale = 1.0f
    private var nativeFramesPerBuffer = 128

    // Smooth fade-in / fade-out control
    private var fadeFactor = 0.0f
    private var isStopping = false

    // Feedback howling detection & auto-ducking
    private var feedbackDuckingGain = 1.0f
    private var consecutiveFeedbackFrames = 0
    private var lastFeedbackTime = 0L

    // Use extremely small processing chunks for minimal latency
    private var processingChunkSize = 128  // Reduced from 256 to 128 for lower latency
    private var lowLatencyMode = true

    // Dynamic EQ - supports adding/removing bands
    private var eqBands = mutableListOf<EQBand>()
    private var filters = mutableListOf<BiquadFilter>()

    // Advanced dynamics
    private var noiseGateThreshold = 0.02f
    private var compressionRatio = 0.7f
    private var compressionThreshold = 0.6f

    // ========================================================================
    // NOISE CANCELING SYSTEM — STFT-based spectral subtraction
    // ========================================================================
    private var noiseCancelingEnabled = false
    private var noiseCancelingStrength = 0.7f // 0.0 to 1.0

    // STFT parameters
    private val stftSize = 512         // FFT frame length (power of 2)
    private val stftHop = 256          // 50% overlap for COLA reconstruction
    private val stftBins = stftSize / 2 + 1  // Number of unique frequency bins (257)

    // Noise profile (stored as average magnitude per FFT bin)
    private var noiseProfile = FloatArray(stftBins) { 0f }
    private var noiseProfileSamples = 0
    private val maxNoiseProfileSamples = 50
    private var noiseProfileLearned = false
    private val spectralFloor = 0.01f         // Minimum gain to prevent complete silence
    private var noiseEstimateSmoothing = 0.9f // Smoothing factor for Wiener gain updates
    private var overSubtractionFactor = 2.0f  // 1.5–3.0x on noise power for aggressive suppression
    private val adaptRate = 0.01f             // Slow adaptation rate for voice-activity-aware updates

    // Wiener filter gains & decision-directed a priori SNR buffers (Ephraim-Malah style)
    private var wienerGains = FloatArray(stftBins) { 1f }
    private var prevCleanMag = FloatArray(stftBins) { 0f }
    private var aPrioriSnr = FloatArray(stftBins) { 1f }
    private val alphaDD = 0.92f // Decision-directed temporal smoothing factor

    // Pre-allocated Hann window (computed once at init)
    private val hannWindow = FloatArray(stftSize)

    // Pre-allocated STFT work buffers (ZERO per-callback allocations on audio thread)
    private val stftInputBuf = FloatArray(stftSize)  // Input frame accumulator
    private var stftFilled = 0                        // Samples currently in accumulator
    private val stftWorkReal = DoubleArray(stftSize)  // FFT real part
    private val stftWorkImag = DoubleArray(stftSize)  // FFT imaginary part
    private val stftMag = FloatArray(stftBins)        // Magnitude spectrum
    private val stftPhase = FloatArray(stftBins)      // Phase spectrum
    private val synthFrame = FloatArray(stftSize)     // IFFT output
    private val olaBuffer = FloatArray(stftHop)       // Overlap-add tail from previous frame

    // Output FIFO for denoised samples (circular buffer)
    private val stftOutBuf = FloatArray(4096)
    private var stftOutRead = 0
    private var stftOutWrite = 0

    // Pre-allocated buffer for short↔float conversion
    private var ncFloatBuf = FloatArray(1024)

    // Visualization
    private var waveformView: WaveformView? = null
    private var spectrumView: SpectrumView? = null
    private var visualUpdateCounter = 0
    private val visualUpdateFrequency = 12 // ~30 fps throttling (12 chunks * 128 / 48000 = 32 ms)
    private val visDecimatedColumns = 64
    private val visDecimatedBuffer = FloatArray(visDecimatedColumns * 2)
    private val visAccumBuffer = ShortArray(2048)
    private var visAccumLength = 0
    private val fftProcessor = FFTProcessor(512)  

    data class EQBand(
        var frequency: Float = 1000f,
        var gain: Float = 0f,
        var q: Float = 1f,
        var filterType: FilterType = FilterType.PEAKING,
        var enabled: Boolean = true
    )

    private data class BiquadFilter(
        var b0: Float = 1f, var b1: Float = 0f, var b2: Float = 0f,
        var a1: Float = 0f, var a2: Float = 0f,
        var x1: Float = 0f, var x2: Float = 0f,
        var y1: Float = 0f, var y2: Float = 0f,
        var isStable: Boolean = true
    )

    init {
        // Pre-compute Hann window coefficients
        for (i in 0 until stftSize) {
            hannWindow[i] = (0.5 * (1.0 - cos(2.0 * PI * i / stftSize))).toFloat()
        }

        optimizeForLowLatency()
        initializeDefaultEQ()
    }

    private fun optimizeForLowLatency() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val nativeSampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        sampleRate = nativeSampleRate?.toIntOrNull() ?: 48000  

        val nativeBufferSize = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        nativeFramesPerBuffer = nativeBufferSize?.toIntOrNull() ?: 128

        val minRecordBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        val minPlayBuffer = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        recordBufferSize = max(minRecordBuffer, nativeFramesPerBuffer * 2)
        playBufferSize = max(minPlayBuffer, nativeFramesPerBuffer * 2)

        processingChunkSize = if (lowLatencyMode) {
            nativeFramesPerBuffer.coerceAtMost(128)
        } else {
            nativeFramesPerBuffer.coerceAtMost(512)
        }

        if (ncFloatBuf.size < processingChunkSize) {
            ncFloatBuf = FloatArray(processingChunkSize)
        }

        Log.d(TAG, "Ultra-low-latency: ${sampleRate}Hz, record: ${recordBufferSize}, play: ${playBufferSize}, chunk: $processingChunkSize frames")
    }

    private fun initializeDefaultEQ() {
        val defaultFrequencies = floatArrayOf(60f, 250f, 500f, 800f, 1000f, 1500f, 2000f, 3000f, 4000f, 8000f)
        for (freq in defaultFrequencies) {
            addEQBand(freq, 0f, 1.0f, FilterType.PEAKING)
        }
        recalculateHeadroomCompensation()
    }

    @Throws(SecurityException::class)
    fun startAudioProcessing() {
        if (isRecording) return

        try {
            audioRecord = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(recordBufferSize)
                    .build()
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            audioTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)  
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)  
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(playBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }

                builder.build()
            } else {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    playBufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("AudioTrack failed to initialize")
            }

            audioRecord?.startRecording()
            audioTrack?.play()
            isRecording = true
            isStopping = false
            fadeFactor = 0.0f

            audioThread = Thread { processLowLatencyAudio() }
            audioThread?.start()

            Log.d(TAG, "Low-latency audio processing started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting low-latency audio: ${e.message}", e)
            cleanup()
            throw e
        }
    }

    private fun updateRealTimeVisualization(buffer: ShortArray, length: Int) {
        if (visAccumLength > 0) {
            val samplesPerCol = visAccumLength.toFloat() / visDecimatedColumns
            val shortInv = 1.0f / Short.MAX_VALUE
            for (col in 0 until visDecimatedColumns) {
                val start = (col * samplesPerCol).toInt()
                val end = kotlin.math.min(((col + 1) * samplesPerCol).toInt(), visAccumLength)
                var minVal = 1.0f
                var maxVal = -1.0f
                if (start < end) {
                    for (s in start until end) {
                        val norm = visAccumBuffer[s] * shortInv
                        if (norm < minVal) minVal = norm
                        if (norm > maxVal) maxVal = norm
                    }
                } else if (start < visAccumLength) {
                    val norm = visAccumBuffer[start] * shortInv
                    minVal = norm
                    maxVal = norm
                } else {
                    minVal = 0f
                    maxVal = 0f
                }
                visDecimatedBuffer[col * 2] = minVal
                visDecimatedBuffer[col * 2 + 1] = maxVal
            }
            waveformView?.updateWaveform(visDecimatedBuffer, visDecimatedColumns * 2)
            visAccumLength = 0
        }

        if (fftProcessor.isReady()) {
            val fftMagnitudes = fftProcessor.computeFFT()
            spectrumView?.updateSpectrum(fftMagnitudes)
        }
    }

    private fun processLowLatencyAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val buffer = ShortArray(processingChunkSize)
        val originalBuffer = ShortArray(processingChunkSize)
        val shortMaxInv = 1.0f / Short.MAX_VALUE

        Log.d(TAG, "Ultra-low-latency processing loop active")

        while (isRecording) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, processingChunkSize) ?: 0

                if (bytesRead > 0) {
                    if (visAccumLength + bytesRead <= visAccumBuffer.size) {
                        System.arraycopy(buffer, 0, originalBuffer, 0, bytesRead)
                        System.arraycopy(originalBuffer, 0, visAccumBuffer, visAccumLength, bytesRead)
                        visAccumLength += bytesRead
                    } else {
                        System.arraycopy(buffer, 0, originalBuffer, 0, bytesRead)
                    }
                    fftProcessor.addSamples(originalBuffer, bytesRead)

                    visualUpdateCounter++
                    val shouldUpdateVis = visualUpdateCounter >= visualUpdateFrequency
                    if (shouldUpdateVis) {
                        updateRealTimeVisualization(originalBuffer, bytesRead)
                        visualUpdateCounter = 0
                    }

                    if (noiseCancelingEnabled) {
                        applyNoiseCanceling(buffer, bytesRead)
                    }

                    // Smooth gain parameter interpolation
                    val gainStep = 0.002f
                    
                    // Smooth fade-in / fade-out ramping (10 ms)
                    val fadeStep = 1.0f / (sampleRate * 0.010f)

                    for (i in 0 until bytesRead) {
                        // Smoothly move current gain toward target
                        if (currentAmplificationGain < targetAmplificationGain) {
                            currentAmplificationGain = (currentAmplificationGain + gainStep).coerceAtMost(targetAmplificationGain)
                        } else if (currentAmplificationGain > targetAmplificationGain) {
                            currentAmplificationGain = (currentAmplificationGain - gainStep).coerceAtLeast(targetAmplificationGain)
                        }

                        // Ramp fade factor
                        if (isStopping) {
                            fadeFactor = (fadeFactor - fadeStep).coerceAtLeast(0.0f)
                        } else if (fadeFactor < 1.0f) {
                            fadeFactor = (fadeFactor + fadeStep).coerceAtMost(1.0f)
                        }

                        var sample = buffer[i] * shortMaxInv

                        // Apply noise gate
                        if (abs(sample) < noiseGateThreshold) {
                            sample *= 0.1f
                        }

                        // Apply EQ filters with automatic headroom scaling
                        sample = applyStreamlinedEQ(sample) * autoHeadroomScale

                        // Apply compression
                        if (abs(sample) > compressionThreshold) {
                            val compressedAbs = compressionThreshold +
                                    (abs(sample) - compressionThreshold) * compressionRatio
                            sample = if (sample >= 0) compressedAbs else -compressedAbs
                        }

                        // Apply amplification gain with acoustic feedback ducking & smooth fade
                        sample *= (currentAmplificationGain * feedbackDuckingGain * fadeFactor)

                        // Apply soft-knee limiter & safety output ceiling (prevents harsh linear clipping)
                        sample = applySoftLimiter(sample)

                        buffer[i] = (sample * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    }

                    audioTrack?.write(buffer, 0, bytesRead)

                    if (isStopping && fadeFactor <= 0.0f) {
                        break
                    }


                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in low-latency processing: ${e.message}", e)
                break
            }
        }
    }

    /**
     * Soft-knee limiter with tanh saturation curve.
     * Prevents harsh square-wave clipping at full volume.
     * Enforces an absolute safety output ceiling at 0.92f (-0.7 dBFS).
     */
    private fun applySoftLimiter(sample: Float): Float {
        val kneeThreshold = 0.70f // -3 dBFS threshold
        val absSample = abs(sample)
        if (absSample <= kneeThreshold) {
            return sample
        }
        val over = absSample - kneeThreshold
        val compressed = kneeThreshold + (1.0f - kneeThreshold) * tanh(over / (1.0f - kneeThreshold))
        val limited = compressed.coerceIn(0f, 0.92f)
        return if (sample >= 0f) limited else -limited
    }

    private fun applyStreamlinedEQ(input: Float): Float {
        var output = input
        for (i in eqBands.indices) {
            if (eqBands[i].enabled) {
                output = processBiquadFilter(output, filters[i])
            }
        }
        return output
    }

    /**
     * Process biquad filter with stability guard and denormal number protection.
     */
    private fun processBiquadFilter(input: Float, filter: BiquadFilter): Float {
        if (!filter.isStable) return input

        var output = filter.b0 * input + filter.b1 * filter.x1 + filter.b2 * filter.x2 -
                filter.a1 * filter.y1 - filter.a2 * filter.y2

        // Denormal guard: flush tiny sub-normal values to zero to prevent CPU slowdown/noise
        if (abs(output) < 1e-15f) output = 0f
        if (abs(filter.y1) < 1e-15f) filter.y1 = 0f
        if (abs(filter.y2) < 1e-15f) filter.y2 = 0f

        filter.x2 = filter.x1
        filter.x1 = input
        filter.y2 = filter.y1
        filter.y1 = output

        return output
    }

    private fun applyNoiseCanceling(buffer: ShortArray, length: Int) {
        if (!noiseCancelingEnabled || length < 2) return

        for (i in 0 until length) {
            ncFloatBuf[i] = buffer[i].toFloat() / Short.MAX_VALUE
        }

        for (i in 0 until length) {
            stftInputBuf[stftFilled] = ncFloatBuf[i]
            stftFilled++

            if (stftFilled >= stftSize) {
                processSTFTFrame()
                System.arraycopy(stftInputBuf, stftHop, stftInputBuf, 0, stftSize - stftHop)
                stftFilled = stftSize - stftHop
            }
        }

        val available = stftFifoAvailable()
        if (available >= length) {
            for (i in 0 until length) {
                val sample = stftOutBuf[stftOutRead]
                stftOutRead = (stftOutRead + 1) % stftOutBuf.size
                buffer[i] = (sample * Short.MAX_VALUE)
                    .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                    .toInt().toShort()
            }
        }
    }

    private fun stftFifoAvailable(): Int {
        return (stftOutWrite - stftOutRead + stftOutBuf.size) % stftOutBuf.size
    }

    private fun stftFifoPush(sample: Float) {
        stftOutBuf[stftOutWrite] = sample
        stftOutWrite = (stftOutWrite + 1) % stftOutBuf.size
    }

    private fun processSTFTFrame() {
        for (i in 0 until stftSize) {
            stftWorkReal[i] = (stftInputBuf[i] * hannWindow[i]).toDouble()
            stftWorkImag[i] = 0.0
        }

        fftInPlace(stftWorkReal, stftWorkImag, inverse = false)

        var totalMag = 0f
        var maxBinMag = 0f
        var maxBinIdx = 0

        for (i in 0 until stftBins) {
            val re = stftWorkReal[i]
            val im = stftWorkImag[i]
            val mag = sqrt((re * re + im * im).toFloat())
            stftMag[i] = mag
            stftPhase[i] = atan2(im.toFloat(), re.toFloat())

            totalMag += mag
            if (mag > maxBinMag) {
                maxBinMag = mag
                maxBinIdx = i
            }
        }

        // Acoustic Feedback (Howling) Detector:
        // If a single bin holds > 35% of total frame energy persistently over 8 frames (>40ms)
        if (totalMag > 1.0f && (maxBinMag / totalMag) > 0.35f) {
            consecutiveFeedbackFrames++
            if (consecutiveFeedbackFrames >= 8) {
                feedbackDuckingGain = 0.25f // Duck master gain by -12 dB
                val freqHz = maxBinIdx * (sampleRate / 2.0f) / stftBins
                val now = System.currentTimeMillis()
                if (now - lastFeedbackTime > 3000) { // Limit notifications to once per 3s
                    lastFeedbackTime = now
                    feedbackEventListener?.onFeedbackDetected(freqHz)
                }
            }
        } else {
            consecutiveFeedbackFrames = max(0, consecutiveFeedbackFrames - 1)
            if (consecutiveFeedbackFrames == 0 && feedbackDuckingGain < 1.0f) {
                feedbackDuckingGain = min(1.0f, feedbackDuckingGain + 0.05f) // Smoothly recover gain
            }
        }

        val frameEnergy = computeFrameEnergy()

        if (!noiseProfileLearned) {
            // Voice Activity Detection (VAD) guard: skip learning loud speech frames
            val isSpeechDetected = frameEnergy > (noiseGateThreshold * 8f).coerceAtLeast(0.08f)
            if (!isSpeechDetected) {
                for (i in 0 until stftBins) {
                    noiseProfile[i] += stftMag[i]
                }
                noiseProfileSamples++

                if (noiseProfileSamples >= maxNoiseProfileSamples) {
                    for (i in 0 until stftBins) {
                        noiseProfile[i] /= maxNoiseProfileSamples
                    }
                    noiseProfileLearned = true
                    Log.d(TAG, "Noise profile learned via STFT")
                }
            }
        } else {
            if (frameEnergy < (noiseGateThreshold * 5f).coerceAtLeast(0.04f)) {
                for (i in 0 until stftBins) {
                    noiseProfile[i] = (1f - adaptRate) * noiseProfile[i] + adaptRate * stftMag[i]
                }
            }
        }

        // 5. Decision-Directed A Priori SNR Estimation & Spectral Suppression (Ephraim-Malah style)
        if (noiseProfileLearned) {
            for (i in 0 until stftBins) {
                val noisyPower = stftMag[i] * stftMag[i]
                val noisePower = (noiseProfile[i] * noiseProfile[i] * overSubtractionFactor).coerceAtLeast(1e-10f)

                // A posteriori SNR: gamma_k = noisyPower / noisePower
                val aPosterioriSnr = noisyPower / noisePower

                // Decision-directed a priori SNR: xi_k = alpha * (prevCleanPower / noisePower) + (1-alpha) * max(aPosterioriSnr - 1, 0)
                val prevCleanPower = prevCleanMag[i] * prevCleanMag[i]
                val xi = alphaDD * (prevCleanPower / noisePower) + (1f - alphaDD) * (aPosterioriSnr - 1f).coerceAtLeast(0f)
                aPrioriSnr[i] = xi

                // Wiener gain based on a priori SNR: G_k = xi / (xi + 1)
                val targetGain = (xi / (xi + 1.0f)).coerceAtLeast(spectralFloor)

                // Apply noise canceling strength scaling
                val finalGain = targetGain.pow(noiseCancelingStrength)

                // Temporal gain smoothing
                wienerGains[i] = noiseEstimateSmoothing * wienerGains[i] + (1f - noiseEstimateSmoothing) * finalGain

                // Apply gain to magnitude
                stftMag[i] *= wienerGains[i]

                // Store clean magnitude for next frame's decision-directed SNR estimate
                prevCleanMag[i] = stftMag[i]
            }
        }

        for (i in 0 until stftBins) {
            stftWorkReal[i] = (stftMag[i] * cos(stftPhase[i])).toDouble()
            stftWorkImag[i] = (stftMag[i] * sin(stftPhase[i])).toDouble()
        }
        for (i in 1 until stftSize / 2) {
            stftWorkReal[stftSize - i] = stftWorkReal[i]
            stftWorkImag[stftSize - i] = -stftWorkImag[i]
        }

        fftInPlace(stftWorkReal, stftWorkImag, inverse = true)

        for (i in 0 until stftHop) {
            val outputSample = stftWorkReal[i].toFloat() + olaBuffer[i]
            stftFifoPush(outputSample)
        }
        for (i in 0 until stftHop) {
            olaBuffer[i] = stftWorkReal[i + stftHop].toFloat()
        }
    }

    /**
     * Sourced from AudioEngine STFT noise profile data.
     * Computes the current background noise profile average magnitude in dB.
     */
    fun getAverageNoiseLevelDb(): Float {
        var sumMag = 0f
        for (mag in noiseProfile) {
            sumMag += mag
        }
        val avgMag = sumMag / stftBins
        val db = 30f + (avgMag * 140f).coerceIn(0f, 65f)
        return db
    }

    private fun computeFrameEnergy(): Float {
        var energy = 0f
        for (i in 0 until stftBins) {
            energy += stftMag[i] * stftMag[i]
        }
        return sqrt(energy / stftBins)
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray, inverse: Boolean) {
        val n = real.size

        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        var mLen = 2
        while (mLen <= n) {
            val halfM = mLen / 2
            val angle = (if (inverse) 2.0 else -2.0) * PI / mLen
            val wReal = cos(angle)
            val wImag = sin(angle)

            var k = 0
            while (k < n) {
                var wrReal = 1.0
                var wrImag = 0.0
                for (jj in 0 until halfM) {
                    val idx = k + jj + halfM
                    val tReal = wrReal * real[idx] - wrImag * imag[idx]
                    val tImag = wrReal * imag[idx] + wrImag * real[idx]
                    real[idx] = real[k + jj] - tReal
                    imag[idx] = imag[k + jj] - tImag
                    real[k + jj] += tReal
                    imag[k + jj] += tImag
                    val newWr = wrReal * wReal - wrImag * wImag
                    wrImag = wrReal * wImag + wrImag * wReal
                    wrReal = newWr
                }
                k += mLen
            }
            mLen *= 2
        }

        if (inverse) {
            val invN = 1.0 / n
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }

    private fun resetNoiseProfile() {
        noiseProfile.fill(0f)
        wienerGains.fill(1f)
        prevCleanMag.fill(0f)
        aPrioriSnr.fill(1f)
        noiseProfileSamples = 0
        noiseProfileLearned = false

        stftInputBuf.fill(0f)
        stftFilled = 0
        olaBuffer.fill(0f)
        stftOutRead = 0
        stftOutWrite = 0
    }

    private fun updateAllFilters() {
        for (i in eqBands.indices) {
            updateBiquadCoefficients(i)
        }
        recalculateHeadroomCompensation()
    }

    private fun recalculateHeadroomCompensation() {
        var positiveGainSum = 0f
        for (band in eqBands) {
            if (band.enabled && band.gain > 0f) {
                positiveGainSum += band.gain
            }
        }
        autoHeadroomScale = 1.0f / (1.0f + 0.08f * positiveGainSum)
    }

    private fun updateBiquadCoefficients(bandIndex: Int) {
        val band = eqBands[bandIndex]
        val filter = filters[bandIndex]

        val omega = 2 * PI * band.frequency / sampleRate
        val cosOmega = cos(omega).toFloat()
        val sinOmega = sin(omega).toFloat()
        // Q factor mapping per filter type (Robert Bristow-Johnson RBJ Audio EQ Cookbook):
        // PEAKING, BAND_PASS, NOTCH: Q controls filter bandwidth BW = f0 / Q.
        // LOW_PASS, HIGH_PASS: Q controls resonance / Butterworth Q factor (Q=0.707 is maximally flat).
        // LOW_SHELF, HIGH_SHELF: Q controls shelf transition steepness / slope S (alpha = sin(w0)/(2*Q)).
        val alpha = sinOmega / (2 * band.q.coerceAtLeast(0.1f))
        val A = 10.0.pow(band.gain / 40.0).toFloat()

        when (band.filterType) {
            FilterType.PEAKING -> {
                filter.b0 = 1 + alpha * A
                filter.b1 = -2 * cosOmega
                filter.b2 = 1 - alpha * A
                val a0 = 1 + alpha / A
                filter.a1 = -2 * cosOmega / a0
                filter.a2 = (1 - alpha / A) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.LOW_PASS -> {
                filter.b0 = (1 - cosOmega) / 2
                filter.b1 = 1 - cosOmega
                filter.b2 = (1 - cosOmega) / 2
                val a0 = 1 + alpha
                filter.a1 = -2 * cosOmega / a0
                filter.a2 = (1 - alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.HIGH_PASS -> {
                filter.b0 = (1 + cosOmega) / 2
                filter.b1 = -(1 + cosOmega)
                filter.b2 = (1 + cosOmega) / 2
                val a0 = 1 + alpha
                filter.a1 = -2 * cosOmega / a0
                filter.a2 = (1 - alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.BAND_PASS -> {
                filter.b0 = alpha
                filter.b1 = 0f
                filter.b2 = -alpha
                val a0 = 1 + alpha
                filter.a1 = -2 * cosOmega / a0
                filter.a2 = (1 - alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.NOTCH -> {
                filter.b0 = 1f
                filter.b1 = -2 * cosOmega
                filter.b2 = 1f
                val a0 = 1 + alpha
                filter.a1 = -2 * cosOmega / a0
                filter.a2 = (1 - alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                filter.b0 = A * ((A + 1) - (A - 1) * cosOmega + 2 * sqrtA * alpha)
                filter.b1 = 2 * A * ((A - 1) - (A + 1) * cosOmega)
                filter.b2 = A * ((A + 1) - (A - 1) * cosOmega - 2 * sqrtA * alpha)
                val a0 = (A + 1) + (A - 1) * cosOmega + 2 * sqrtA * alpha
                filter.a1 = -2 * ((A - 1) + (A + 1) * cosOmega) / a0
                filter.a2 = ((A + 1) + (A - 1) * cosOmega - 2 * sqrtA * alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                filter.b0 = A * ((A + 1) + (A - 1) * cosOmega + 2 * sqrtA * alpha)
                filter.b1 = -2 * A * ((A - 1) + (A + 1) * cosOmega)
                filter.b2 = A * ((A + 1) + (A - 1) * cosOmega - 2 * sqrtA * alpha)
                val a0 = (A + 1) - (A - 1) * cosOmega + 2 * sqrtA * alpha
                filter.a1 = 2 * ((A - 1) - (A + 1) * cosOmega) / a0
                filter.a2 = ((A + 1) - (A - 1) * cosOmega - 2 * sqrtA * alpha) / a0
                filter.b0 /= a0
                filter.b1 /= a0
                filter.b2 /= a0
            }
        }

        // Biquad Stability Guard: Poles must lie inside unit circle (|a2| < 1.0 and |a1| < 1.0 + a2)
        filter.isStable = abs(filter.a2) < 0.999f && abs(filter.a1) < (1.0f + filter.a2)
        if (!filter.isStable) {
            Log.w(TAG, "Unstable biquad coefficients at band $bandIndex (${band.frequency}Hz). Resetting to pass-through.")
            filter.b0 = 1f; filter.b1 = 0f; filter.b2 = 0f
            filter.a1 = 0f; filter.a2 = 0f
            filter.isStable = true
        }
    }

    fun setEQBand(bandIndex: Int, frequency: Float, gain: Float, q: Float, filterType: FilterType = FilterType.PEAKING) {
        if (bandIndex in eqBands.indices) {
            eqBands[bandIndex].frequency = frequency.coerceIn(20f, 20000f)
            eqBands[bandIndex].gain = gain.coerceIn(-20f, 20f)
            eqBands[bandIndex].q = q.coerceIn(0.1f, 10f)
            eqBands[bandIndex].filterType = filterType
            updateBiquadCoefficients(bandIndex)
            recalculateHeadroomCompensation()
        }
    }

    fun addEQBand(frequency: Float, gain: Float, q: Float, filterType: FilterType = FilterType.PEAKING): Int {
        val newBand = EQBand(
            frequency = frequency.coerceIn(20f, 20000f),
            gain = gain.coerceIn(-20f, 20f),
            q = q.coerceIn(0.1f, 10f),
            filterType = filterType,
            enabled = true
        )
        eqBands.add(newBand)
        
        val newFilter = BiquadFilter()
        filters.add(newFilter)
        
        val newIndex = eqBands.size - 1
        updateBiquadCoefficients(newIndex)
        recalculateHeadroomCompensation()
        
        return newIndex
    }

    fun removeEQBand(bandIndex: Int): Boolean {
        if (bandIndex in eqBands.indices) {
            eqBands.removeAt(bandIndex)
            filters.removeAt(bandIndex)
            recalculateHeadroomCompensation()
            return true
        }
        return false
    }

    fun getEQBandCount(): Int = eqBands.size

    fun getEQBand(bandIndex: Int): EQBand? {
        return if (bandIndex in eqBands.indices) {
            eqBands[bandIndex].copy()
        } else null
    }

    fun getAllEQBands(): List<EQBand> {
        return eqBands.map { it.copy() }
    }

    fun clearAllEQBands() {
        eqBands.clear()
        filters.clear()
        recalculateHeadroomCompensation()
    }

    fun applyPreset(preset: EQPreset) {
        clearAllEQBands()
        preset.filters.forEach { filter ->
            addEQBand(filter.frequency, filter.gain, filter.q, filter.filterType)
        }
        setAmplification(preset.masterVolume)
        setNoiseGate(preset.noiseGateThreshold)
        setCompression(preset.compressionRatio, 0.6f)
        recalculateHeadroomCompensation()
    }

    fun getCurrentState(presetName: String): EQPreset {
        val filterBands = eqBands.map { band ->
            EQPreset.FilterBand(
                frequency = band.frequency,
                gain = band.gain,
                q = band.q,
                filterType = band.filterType,
                enabled = band.enabled
            )
        }
        
        return EQPreset(
            name = presetName,
            filters = filterBands,
            masterVolume = targetAmplificationGain,
            noiseGateThreshold = noiseGateThreshold,
            compressionRatio = compressionRatio
        )
    }

    fun setAmplification(gain: Float) {
        targetAmplificationGain = max(0.5f, min(gain, 4.0f))
    }

    fun setNoiseGate(threshold: Float) {
        noiseGateThreshold = max(0.001f, min(threshold, 0.2f))
    }

    fun setCompression(ratio: Float, threshold: Float) {
        compressionRatio = ratio.coerceIn(0.1f, 1f)
        compressionThreshold = threshold.coerceIn(0.1f, 1f)
    }

    fun setNoiseCanceling(enabled: Boolean) {
        if (enabled && !noiseCancelingEnabled) {
            resetNoiseProfile()
        }
        noiseCancelingEnabled = enabled
    }

    fun setNoiseCancelingStrength(strength: Float) {
        noiseCancelingStrength = strength.coerceIn(0.0f, 1.0f)
    }

    fun getNoiseCancelingStrength(): Float = noiseCancelingStrength

    fun isNoiseCancelingEnabled(): Boolean = noiseCancelingEnabled

    fun isNoiseProfileLearned(): Boolean = noiseProfileLearned

    fun getNoiseLearningProgress(): Float {
        return if (noiseProfileLearned) 1.0f else (noiseProfileSamples.toFloat() / maxNoiseProfileSamples).coerceIn(0f, 1f)
    }

    fun resetNoiseCancelingProfile() {
        resetNoiseProfile()
    }

    fun isLowLatencyMode(): Boolean = lowLatencyMode

    fun setLowLatencyMode(enabled: Boolean) {
        lowLatencyMode = enabled
        processingChunkSize = if (enabled) {
            nativeFramesPerBuffer.coerceAtMost(128)
        } else {
            nativeFramesPerBuffer.coerceAtMost(512)
        }
        if (ncFloatBuf.size < processingChunkSize) {
            ncFloatBuf = FloatArray(processingChunkSize)
        }
    }

    fun setVisualizerViews(waveform: WaveformView?, spectrum: SpectrumView?) {
        waveformView = waveform
        spectrumView = spectrum
    }

    fun stopAudioProcessing() {
        if (!isRecording) return

        isStopping = true
        // Wait up to 100ms for smooth fade-out
        var attempts = 0
        while (fadeFactor > 0.0f && attempts < 10) {
            try { Thread.sleep(10) } catch (e: Exception) {}
            attempts++
        }

        isRecording = false

        audioThread?.let { thread ->
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Thread interruption", e)
            }
        }
        audioThread = null

        cleanup()
        Log.d(TAG, "Audio processing stopped with fade-out")
    }

    private fun cleanup() {
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up AudioRecord", e)
            }
        }
        audioRecord = null

        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up AudioTrack", e)
            }
        }
        audioTrack = null
    }

    fun isProcessing(): Boolean = isRecording
}
