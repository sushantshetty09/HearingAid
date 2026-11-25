package com.example.hearingaidapp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioEngineUnitTest {

    @Test
    fun testLogarithmicQConversions() {
        // Test min and max bounds
        assertEquals(0.1f, AudioEngine.sliderToQ(0), 0.001f)
        assertEquals(10.0f, AudioEngine.sliderToQ(1000), 0.001f)

        // Test mid-point Q = 1.0
        val sliderForQ1 = AudioEngine.qToSlider(1.0f)
        val qFromSlider = AudioEngine.sliderToQ(sliderForQ1)
        assertEquals(1.0f, qFromSlider, 0.02f)

        // Test round-trip conversion accuracy
        val testProgressValues = listOf(0, 100, 250, 500, 750, 900, 1000)
        for (p in testProgressValues) {
            val q = AudioEngine.sliderToQ(p)
            val p2 = AudioEngine.qToSlider(q)
            assertEquals("Progress roundtrip failed for p=$p", p, p2)
        }
    }

    @Test
    fun testBandwidthOctaves() {
        // Q = 1.0 -> approx 1.388 octaves
        val bwQ1 = AudioEngine.qToBandwidthOctaves(1.0f)
        assertEquals(1.388f, bwQ1, 0.05f)

        // Q = 2.0 -> approx 0.705 octaves
        val bwQ2 = AudioEngine.qToBandwidthOctaves(2.0f)
        assertEquals(0.705f, bwQ2, 0.05f)

        // Higher Q -> narrower bandwidth
        val bwQ4 = AudioEngine.qToBandwidthOctaves(4.0f)
        assertTrue("Higher Q must yield narrower octave bandwidth", bwQ4 < bwQ2)
        assertEquals(0.355f, bwQ4, 0.05f)
    }

    @Test
    fun testPeakingFilterGainAtCenterFrequency() {
        val centerFreq = 1000f
        val gainDb = 6.0f
        val q = 1.0f
        val testFreqs = floatArrayOf(centerFreq)

        val response = AudioEngine.calculateMagnitudeResponse(
            frequency = centerFreq,
            gain = gainDb,
            q = q,
            filterType = AudioEngine.FilterType.PEAKING,
            sampleRate = 48000,
            testFrequencies = testFreqs
        )

        assertEquals("Peaking filter gain at center frequency must equal configured dB", gainDb, response[0], 0.2f)
    }

    @Test
    fun testPeakingFilterCutGainAtCenterFrequency() {
        val centerFreq = 2000f
        val cutGainDb = -6.0f
        val q = 1.5f
        val testFreqs = floatArrayOf(centerFreq)

        val response = AudioEngine.calculateMagnitudeResponse(
            frequency = centerFreq,
            gain = cutGainDb,
            q = q,
            filterType = AudioEngine.FilterType.PEAKING,
            sampleRate = 48000,
            testFrequencies = testFreqs
        )

        assertEquals("Peaking filter cut gain at center frequency must equal configured dB", cutGainDb, response[0], 0.2f)
    }

    @Test
    fun testPeakingFilterBandwidthMinus3dBPointsForQ1_2_4() {
        val f0 = 1000f
        val gainDb = 6.0f
        val qValues = floatArrayOf(1.0f, 2.0f, 4.0f)

        for (q in qValues) {
            val bwOct = AudioEngine.qToBandwidthOctaves(q)
            val fLow = (f0 * 2.0.pow(-bwOct / 2.0)).toFloat()
            val fHigh = (f0 * 2.0.pow(bwOct / 2.0)).toFloat()

            val testFreqs = floatArrayOf(fLow, f0, fHigh)
            val out = FloatArray(3)

            AudioEngine.calculateMagnitudeResponse(
                frequency = f0,
                gain = gainDb,
                q = q,
                filterType = AudioEngine.FilterType.PEAKING,
                sampleRate = 48000,
                testFrequencies = testFreqs,
                outResponse = out
            )

            val lowGain = out[0]
            val peakGain = out[1]
            val highGain = out[2]

            assertEquals("Peak gain must be +6 dB for Q=$q", 6.0f, peakGain, 0.2f)
            // In a +6 dB peaking filter, the half-power bandwidth is at +3 dB (3 dB below peak)
            assertEquals("fLow gain should be half-power (+3 dB) for Q=$q", 3.0f, lowGain, 0.5f)
            assertEquals("fHigh gain should be half-power (+3 dB) for Q=$q", 3.0f, highGain, 0.5f)
        }
    }

    @Test
    fun testLowShelfAndHighShelfResponse() {
        val fCutoff = 1000f
        val gainDb = 6.0f
        val q = 0.707f // standard Butterworth shelf slope
        val testFreqs = floatArrayOf(100f, 1000f, 10000f)

        // Low Shelf: low frequencies boosted by +6 dB, high frequencies untouched (~0 dB)
        val lowShelfOut = AudioEngine.calculateMagnitudeResponse(
            frequency = fCutoff,
            gain = gainDb,
            q = q,
            filterType = AudioEngine.FilterType.LOW_SHELF,
            sampleRate = 48000,
            testFrequencies = testFreqs
        )
        assertEquals("Low shelf at 100Hz should have ~6 dB boost", 6.0f, lowShelfOut[0], 0.3f)
        assertEquals("Low shelf at 10000Hz should have ~0 dB gain", 0.0f, lowShelfOut[2], 0.3f)

        // High Shelf: high frequencies boosted by +6 dB, low frequencies untouched (~0 dB)
        val highShelfOut = AudioEngine.calculateMagnitudeResponse(
            frequency = fCutoff,
            gain = gainDb,
            q = q,
            filterType = AudioEngine.FilterType.HIGH_SHELF,
            sampleRate = 48000,
            testFrequencies = testFreqs
        )
        assertEquals("High shelf at 100Hz should have ~0 dB gain", 0.0f, highShelfOut[0], 0.3f)
        assertEquals("High shelf at 10000Hz should have ~6 dB boost", 6.0f, highShelfOut[2], 0.3f)
    }

    @Test
    fun testHeadroomCompensationPreventsClippingAt12dB() {
        // Test automatic headroom compensation:
        // When multiple bands have +12 dB gain boost, autoHeadroomScale reduces gain so total amplitude stays <= 1.0
        val gainPerBand = 12.0f
        val numBoostedBands = 4
        val totalPositiveGain = gainPerBand * numBoostedBands // 48 dB

        val autoHeadroomScale = 1.0f / (1.0f + 0.08f * totalPositiveGain)
        val linearBoostPerBand = 10.0.pow(gainPerBand / 20.0).toFloat() // ~3.98x
        val scaledBoost = linearBoostPerBand * autoHeadroomScale

        // autoHeadroomScale must be significantly attenuated (scale < 0.25)
        assertTrue("Headroom scaling factor must compress extreme boosts", autoHeadroomScale < 0.25f)
        assertTrue("Scaled output for unity input must prevent clipping", scaledBoost < 1.0f)
    }

    @Test
    fun testFFTProcessorLogBandMapping() {
        // First band index 0 must be close to 50 Hz
        val f0 = FFTProcessor.getCenterFrequencyForBand(0, 48, 50f, 16000f)
        assertEquals(50f, f0, 1.0f)

        // Last band index 47 must be close to 16000 Hz
        val fLast = FFTProcessor.getCenterFrequencyForBand(47, 48, 50f, 16000f)
        assertEquals(16000f, fLast, 10.0f)

        // Center band index ~24 must be near 900 Hz
        val fMid = FFTProcessor.getCenterFrequencyForBand(24, 48, 50f, 16000f)
        assertTrue("Mid band frequency must fall in speech presence range", fMid in 800f..1100f)

        // Test inverse mapping
        val band1k = FFTProcessor.getBandForFrequency(1000f, 48, 50f, 16000f)
        val centerFor1k = FFTProcessor.getCenterFrequencyForBand(band1k, 48, 50f, 16000f)
        assertEquals(1000f, centerFor1k, 150f)
    }

    @Test
    fun testPresetSerializationAndBackwardCompatibility() {
        val speechPreset = EQPreset.getDefaultPresets().first { it.name == "Speech Clarity" }

        // Test serialization to JSON
        val jsonStr = speechPreset.toJSON()
        assertNotNull(jsonStr)
        assertTrue(jsonStr.contains("Speech Clarity"))

        // Test deserialization from JSON
        val restoredPreset = EQPreset.fromJSON(jsonStr)
        assertEquals(speechPreset.name, restoredPreset.name)
        assertEquals(speechPreset.masterVolume, restoredPreset.masterVolume, 0.001f)
        assertEquals(speechPreset.filters.size, restoredPreset.filters.size)

        // Verify mid-frequency bands are preserved
        val freq500 = restoredPreset.filters.find { it.frequency == 500f }
        assertNotNull("500 Hz speech band must exist", freq500)
        assertEquals(-1f, freq500!!.gain, 0.01f)

        val freq800 = restoredPreset.filters.find { it.frequency == 800f }
        assertNotNull("800 Hz speech band must exist", freq800)
        assertEquals(2f, freq800!!.gain, 0.01f)

        val freq1500 = restoredPreset.filters.find { it.frequency == 1500f }
        assertNotNull("1500 Hz speech band must exist", freq1500)
        assertEquals(3f, freq1500!!.gain, 0.01f)

        val freq2000 = restoredPreset.filters.find { it.frequency == 2000f }
        assertNotNull("2000 Hz speech band must exist", freq2000)
        assertEquals(3f, freq2000!!.gain, 0.01f)

        // Test Legacy JSON deserialization without optional filterType or timestamp
        val legacyJson = """
            {
                "name": "Legacy Preset",
                "masterVolume": 1.0,
                "noiseGateThreshold": 0.01,
                "compressionRatio": 0.5,
                "filters": [
                    {"frequency": 250.0, "gain": 0.0, "q": 1.0},
                    {"frequency": 1000.0, "gain": 3.0, "q": 1.2}
                ]
            }
        """.trimIndent()

        val legacyPreset = EQPreset.fromJSON(legacyJson)
        assertEquals("Legacy Preset", legacyPreset.name)
        assertEquals(2, legacyPreset.filters.size)
        assertEquals(AudioEngine.FilterType.PEAKING, legacyPreset.filters[0].filterType)
        assertTrue(legacyPreset.filters[0].enabled)
    }

    @Test
    fun testPeakingFilterGainAt30dB() {
        val centerFreq = 1000f
        val gainDb = 30.0f
        val q = 1.0f
        val testFreqs = floatArrayOf(centerFreq)

        val response = AudioEngine.calculateMagnitudeResponse(
            frequency = centerFreq,
            gain = gainDb,
            q = q,
            filterType = AudioEngine.FilterType.PEAKING,
            sampleRate = 48000,
            testFrequencies = testFreqs
        )

        assertEquals("Peaking filter gain at center frequency must equal configured +30 dB", gainDb, response[0], 0.2f)
    }

    @Test
    fun testPeakingFilterGainAt30dBNarrowQStability() {
        val centerFreq = 1000f
        val gainDb = 30.0f
        val testFreqs = floatArrayOf(centerFreq)

        for (q in floatArrayOf(2.0f, 5.0f, 10.0f)) {
            val response = AudioEngine.calculateMagnitudeResponse(
                frequency = centerFreq,
                gain = gainDb,
                q = q,
                filterType = AudioEngine.FilterType.PEAKING,
                sampleRate = 48000,
                testFrequencies = testFreqs
            )
            assertEquals("High Q ($q) peaking filter gain at center frequency must reach +30 dB without instability", gainDb, response[0], 0.3f)
        }
    }

    @Test
    fun testSetEQBandClampsToGainMinMax() {
        val engine = AudioEngine(null)
        val bandIndex = engine.addEQBand(1000f, 0f, 1.0f)

        // Test clamping to GAIN_MAX_DB (+30 dB)
        engine.setEQBand(bandIndex, 1000f, 45f, 1.0f)
        var band = engine.getEQBand(bandIndex)
        assertNotNull(band)
        assertEquals(AudioEngine.GAIN_MAX_DB, band!!.gain, 0.001f)

        // Test clamping to GAIN_MIN_DB (-20 dB)
        engine.setEQBand(bandIndex, 1000f, -35f, 1.0f)
        band = engine.getEQBand(bandIndex)
        assertNotNull(band)
        assertEquals(AudioEngine.GAIN_MIN_DB, band!!.gain, 0.001f)

        // Test addEQBand clamping
        val highBandIndex = engine.addEQBand(2000f, 50f, 1.0f)
        val highBand = engine.getEQBand(highBandIndex)
        assertNotNull(highBand)
        assertEquals(AudioEngine.GAIN_MAX_DB, highBand!!.gain, 0.001f)

        val lowBandIndex = engine.addEQBand(500f, -40f, 1.0f)
        val lowBand = engine.getEQBand(lowBandIndex)
        assertNotNull(lowBand)
        assertEquals(AudioEngine.GAIN_MIN_DB, lowBand!!.gain, 0.001f)
    }

    @Test
    fun testGainAndSliderConversions() {
        // -20 dB -> slider 0
        assertEquals(0, AudioEngine.gainToSlider(AudioEngine.GAIN_MIN_DB))
        assertEquals(AudioEngine.GAIN_MIN_DB, AudioEngine.sliderToGain(0), 0.001f)

        // 0 dB -> slider 20
        assertEquals(20, AudioEngine.gainToSlider(0f))
        assertEquals(0f, AudioEngine.sliderToGain(20), 0.001f)

        // +30 dB -> slider 50
        assertEquals(50, AudioEngine.gainToSlider(AudioEngine.GAIN_MAX_DB))
        assertEquals(AudioEngine.GAIN_MAX_DB, AudioEngine.sliderToGain(50), 0.001f)

        // Roundtrip across all integer gains from -20 to +30
        for (g in -20..30) {
            val slider = AudioEngine.gainToSlider(g.toFloat())
            val restored = AudioEngine.sliderToGain(slider)
            assertEquals("Roundtrip failed for gain $g", g.toFloat(), restored, 0.01f)
        }
    }

    @Test
    fun testNoiseSpectrumViewBinAndFrequencyMapping() {
        val sampleRate = 48000
        val stftSize = 512

        // Bin 0 -> 0 Hz
        assertEquals(0f, NoiseSpectrumView.binToFrequency(0, sampleRate, stftSize), 0.01f)

        // Bin 1 -> 48000 / 512 = 93.75 Hz
        assertEquals(93.75f, NoiseSpectrumView.binToFrequency(1, sampleRate, stftSize), 0.01f)

        // Bin 256 (Nyquist) -> 24000 Hz
        assertEquals(24000f, NoiseSpectrumView.binToFrequency(256, sampleRate, stftSize), 0.01f)

        // Frequency to X fraction mapping (50 Hz to 16000 Hz)
        assertEquals(0.0f, NoiseSpectrumView.frequencyToXFraction(50f), 0.001f)
        assertEquals(1.0f, NoiseSpectrumView.frequencyToXFraction(16000f), 0.001f)

        // Geometric midpoint: sqrt(50 * 16000) ~ 894.4 Hz -> fraction 0.5
        val geomMid = sqrt(50f * 16000f)
        assertEquals(0.5f, NoiseSpectrumView.frequencyToXFraction(geomMid), 0.01f)

        // Frequency to pixel X mapping
        val width = 500f
        val paddingLeft = 50f
        val paddingRight = 50f
        val graphWidth = 400f

        val x50 = NoiseSpectrumView.frequencyToX(50f, width, paddingLeft, paddingRight)
        assertEquals(paddingLeft, x50, 0.01f)

        val x16k = NoiseSpectrumView.frequencyToX(16000f, width, paddingLeft, paddingRight)
        assertEquals(paddingLeft + graphWidth, x16k, 0.01f)

        val xMid = NoiseSpectrumView.frequencyToX(geomMid, width, paddingLeft, paddingRight)
        assertEquals(paddingLeft + graphWidth * 0.5f, xMid, 1.0f)

        // dB to pixel Y mapping (0 dBFS at top, -90 dBFS at bottom)
        val height = 200f
        val paddingTop = 20f
        val paddingBottom = 20f
        val graphHeight = 160f

        val y0 = NoiseSpectrumView.dbToY(0f, height, paddingTop, paddingBottom)
        assertEquals(paddingTop, y0, 0.01f)

        val yMinus90 = NoiseSpectrumView.dbToY(-90f, height, paddingTop, paddingBottom)
        assertEquals(paddingTop + graphHeight, yMinus90, 0.01f)

        val yMinus45 = NoiseSpectrumView.dbToY(-45f, height, paddingTop, paddingBottom)
        assertEquals(paddingTop + graphHeight * 0.5f, yMinus45, 0.01f)
    }

    @Test
    fun testOldPresetsWithPlusMinus20dBStillLoadAndNew30dBLoads() {
        // Preset with -20 dB, 0 dB, +20 dB
        val oldPresetJson = """
            {
                "name": "Old Saved Preset",
                "masterVolume": 1.0,
                "noiseGateThreshold": 0.0,
                "compressionRatio": 0.0,
                "filters": [
                    {"frequency": 100.0, "gain": -20.0, "q": 1.0},
                    {"frequency": 1000.0, "gain": 0.0, "q": 1.0},
                    {"frequency": 8000.0, "gain": 20.0, "q": 1.0}
                ]
            }
        """.trimIndent()

        val oldPreset = EQPreset.fromJSON(oldPresetJson)
        assertEquals("Old Saved Preset", oldPreset.name)
        assertEquals(3, oldPreset.filters.size)
        assertEquals(-20f, oldPreset.filters[0].gain, 0.001f)
        assertEquals(0f, oldPreset.filters[1].gain, 0.001f)
        assertEquals(20f, oldPreset.filters[2].gain, 0.001f)

        // Applying old preset to AudioEngine preserves +/-20 dB
        val engine = AudioEngine(null)
        engine.applyPreset(oldPreset)
        assertEquals(3, engine.getEQBandCount())
        assertEquals(-20f, engine.getEQBand(0)!!.gain, 0.001f)
        assertEquals(0f, engine.getEQBand(1)!!.gain, 0.001f)
        assertEquals(20f, engine.getEQBand(2)!!.gain, 0.001f)

        // Preset with new +30 dB gain loads and preserves +30 dB
        val newPresetJson = """
            {
                "name": "High Gain Preset",
                "masterVolume": 1.0,
                "noiseGateThreshold": 0.0,
                "compressionRatio": 0.0,
                "filters": [
                    {"frequency": 2000.0, "gain": 30.0, "q": 1.5}
                ]
            }
        """.trimIndent()

        val newPreset = EQPreset.fromJSON(newPresetJson)
        assertEquals(30f, newPreset.filters[0].gain, 0.001f)
        engine.applyPreset(newPreset)
        assertEquals(1, engine.getEQBandCount())
        assertEquals(30f, engine.getEQBand(0)!!.gain, 0.001f)
    }
}
