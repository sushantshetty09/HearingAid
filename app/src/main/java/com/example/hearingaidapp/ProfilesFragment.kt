package com.example.hearingaidapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

class ProfilesFragment : Fragment() {

    private lateinit var ivProfilePic: ImageView
    private lateinit var btnProfilePic: View
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileAge: TextView
    private lateinit var tvProfileDescription: TextView
    private lateinit var tvCalibrationStatus: TextView
    private lateinit var btnRunCalibration: Button
    private lateinit var btnSuggestEQ: Button
    private lateinit var audiogramChartView: AudiogramChartView
    private lateinit var presetsListContainer: LinearLayout
    private lateinit var tvNoSavedPresets: TextView

    private lateinit var prefs: SharedPreferences

    // 6 Frequencies: 250, 500, 1000, 2000, 4000, 8000 Hz
    private val frequencies = floatArrayOf(250f, 500f, 1000f, 2000f, 4000f, 8000f)
    private var leftEarValues = floatArrayOf(20f, 25f, 30f, 45f, 50f, 60f)
    private var rightEarValues = floatArrayOf(15f, 20f, 25f, 35f, 45f, 55f)

    private val mainActivity: MainActivity
        get() = activity as MainActivity

    private val audioEngine: AudioEngine
        get() = mainActivity.appAudioEngine

    private val presetManager: PresetManager
        get() = mainActivity.appPresetManager

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleProfileImagePicked(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profiles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)

        initializeViews(view)
        loadProfileData()
        setupListeners(view)
        loadPresetsList()
    }

    override fun onPause() {
        super.onPause()
        // Ensure no lingering tone playback when fragment is paused or backgrounded
        stopActiveHearingCheckAudio()
    }

    private var activeAudioTrack: AudioTrack? = null
    private var isCheckAudioActive = false

    private fun stopActiveHearingCheckAudio() {
        isCheckAudioActive = false
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (e: Exception) {}
        activeAudioTrack = null
    }

    private fun initializeViews(view: View) {
        ivProfilePic = view.findViewById(R.id.ivProfilePic)
        btnProfilePic = view.findViewById(R.id.btnProfilePic)
        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvProfileAge = view.findViewById(R.id.tvProfileAge)
        tvProfileDescription = view.findViewById(R.id.tvProfileDescription)
        tvCalibrationStatus = view.findViewById(R.id.tvCalibrationStatus)
        btnRunCalibration = view.findViewById(R.id.btnRunCalibration)
        btnSuggestEQ = view.findViewById(R.id.btnSuggestEQ)
        audiogramChartView = view.findViewById(R.id.audiogramChartView)
        presetsListContainer = view.findViewById(R.id.presetsListContainer)
        tvNoSavedPresets = view.findViewById(R.id.tvNoSavedPresets)
    }

    private fun loadProfileData() {
        val name = prefs.getString("profile_name", "John Doe")
        val age = prefs.getString("profile_age", "Age: 68")
        val desc = prefs.getString("profile_desc", "Condition: Mild High-Frequency Loss")
        val calibStatus = prefs.getString("calibration_status", "Status: Calibrated (Standard Mode)")
        val imgPath = prefs.getString("profile_image_path", "")

        tvProfileName.text = name
        tvProfileAge.text = age
        tvProfileDescription.text = desc
        tvCalibrationStatus.text = calibStatus

        if (!imgPath.isNullOrEmpty()) {
            val file = File(imgPath)
            if (file.exists()) {
                ivProfilePic.setImageURI(Uri.fromFile(file))
                ivProfilePic.imageTintList = null // Clear primary tint
            }
        }

        // Load Left / Right Ear values from SharedPreferences
        val leftJson = prefs.getString("audiogram_left", null)
        val rightJson = prefs.getString("audiogram_right", null)

        if (!leftJson.isNullOrEmpty()) {
            try {
                val arr = JSONArray(leftJson)
                for (i in 0 until arr.length()) {
                    leftEarValues[i] = arr.getDouble(i).toFloat()
                }
            } catch (e: Exception) {
                Log.e("ProfilesFragment", "Error loading left audiogram data: ${e.message}")
            }
        }

        if (!rightJson.isNullOrEmpty()) {
            try {
                val arr = JSONArray(rightJson)
                for (i in 0 until arr.length()) {
                    rightEarValues[i] = arr.getDouble(i).toFloat()
                }
            } catch (e: Exception) {
                Log.e("ProfilesFragment", "Error loading right audiogram data: ${e.message}")
            }
        }

        audiogramChartView.setData(leftEarValues, rightEarValues)
        updateSeekBarProgress()
    }

    private fun updateSeekBarProgress() {
        val root = view ?: return
        val leftIds = intArrayOf(R.id.sbLeft250, R.id.sbLeft500, R.id.sbLeft1000, R.id.sbLeft2000, R.id.sbLeft4000, R.id.sbLeft8000)
        val rightIds = intArrayOf(R.id.sbRight250, R.id.sbRight500, R.id.sbRight1000, R.id.sbRight2000, R.id.sbRight4000, R.id.sbRight8000)
        for (i in leftIds.indices) {
            root.findViewById<SeekBar>(leftIds[i])?.progress = leftEarValues[i].toInt()
        }
        for (i in rightIds.indices) {
            root.findViewById<SeekBar>(rightIds[i])?.progress = rightEarValues[i].toInt()
        }
    }

    private fun saveAudiogramData() {
        val leftArr = JSONArray()
        leftEarValues.forEach { leftArr.put(it) }
        val rightArr = JSONArray()
        rightEarValues.forEach { rightArr.put(it) }

        prefs.edit()
            .putString("audiogram_left", leftArr.toString())
            .putString("audiogram_right", rightArr.toString())
            .apply()
    }

    private fun setupListeners(view: View) {
        // Edit Profile Details dialog
        val infoLayout = view.findViewById<View>(R.id.tvProfileName).parent as View
        infoLayout.setOnClickListener {
            showEditProfileDialog()
        }

        btnProfilePic.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSuggestEQ.setOnClickListener {
            suggestEQFromAudiogram()
        }

        btnRunCalibration.setOnClickListener {
            runCalibrationWizard()
        }

        // Connect SeekBars to variables
        val leftSeekBars = arrayOf(
            view.findViewById<SeekBar>(R.id.sbLeft250),
            view.findViewById<SeekBar>(R.id.sbLeft500),
            view.findViewById<SeekBar>(R.id.sbLeft1000),
            view.findViewById<SeekBar>(R.id.sbLeft2000),
            view.findViewById<SeekBar>(R.id.sbLeft4000),
            view.findViewById<SeekBar>(R.id.sbLeft8000)
        )

        val rightSeekBars = arrayOf(
            view.findViewById<SeekBar>(R.id.sbRight250),
            view.findViewById<SeekBar>(R.id.sbRight500),
            view.findViewById<SeekBar>(R.id.sbRight1000),
            view.findViewById<SeekBar>(R.id.sbRight2000),
            view.findViewById<SeekBar>(R.id.sbRight4000),
            view.findViewById<SeekBar>(R.id.sbRight8000)
        )

        for (i in leftSeekBars.indices) {
            leftSeekBars[i].progress = leftEarValues[i].toInt()
            val finalIndex = i
            leftSeekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        leftEarValues[finalIndex] = progress.toFloat()
                        audiogramChartView.setData(leftEarValues, rightEarValues)
                        saveAudiogramData()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        for (i in rightSeekBars.indices) {
            rightSeekBars[i].progress = rightEarValues[i].toInt()
            val finalIndex = i
            rightSeekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        rightEarValues[finalIndex] = progress.toFloat()
                        audiogramChartView.setData(leftEarValues, rightEarValues)
                        saveAudiogramData()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileName)
        val etAge = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileAge)

        etName?.setText(tvProfileName.text)

        // Extract numeric digits from tvProfileAge (e.g. "Age: 68" -> "68")
        val currentAgeDigits = tvProfileAge.text.toString().filter { it.isDigit() }
        etAge?.setText(currentAgeDigits)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile Information")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName?.text.toString().trim()
                val ageStr = etAge?.text.toString().trim()

                if (name.isNotEmpty()) {
                    tvProfileName.text = name
                    prefs.edit().putString("profile_name", name).apply()
                }

                val ageInt = ageStr.toIntOrNull()
                if (ageInt != null && ageInt in 0..120) {
                    val formattedAge = "Age: $ageInt"
                    tvProfileAge.text = formattedAge
                    prefs.edit().putString("profile_age", formattedAge).apply()
                } else if (ageStr.isNotEmpty()) {
                    Toast.makeText(context, "Invalid age. Enter a number between 0 and 120.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleProfileImagePicked(uri: Uri) {
        try {
            val ctx = context ?: return
            val inputStream = ctx.contentResolver.openInputStream(uri)
            val file = File(ctx.filesDir, "profile_pic.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putString("profile_image_path", file.absolutePath).apply()
            ivProfilePic.setImageURI(Uri.fromFile(file))
            ivProfilePic.imageTintList = null // Clear primary tint
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun suggestEQFromAudiogram() {
        // Suggest EQ based on audiogram values (0-100 dB HL).
        // Standard rule: Boost frequencies by 25% of their loss threshold to prevent feedback.
        // Frequencies map: 250, 500, 1k, 2k, 4k, 8k.
        val suggestedBands = listOf(
            EQPreset.FilterBand(250f, leftEarValues[0].coerceAtLeast(rightEarValues[0]) * 0.25f, 1.0f),
            EQPreset.FilterBand(500f, leftEarValues[1].coerceAtLeast(rightEarValues[1]) * 0.25f, 1.0f),
            EQPreset.FilterBand(1000f, leftEarValues[2].coerceAtLeast(rightEarValues[2]) * 0.25f, 1.0f),
            EQPreset.FilterBand(2000f, leftEarValues[3].coerceAtLeast(rightEarValues[3]) * 0.25f, 1.0f),
            EQPreset.FilterBand(4000f, leftEarValues[4].coerceAtLeast(rightEarValues[4]) * 0.25f, 1.0f),
            EQPreset.FilterBand(8000f, leftEarValues[5].coerceAtLeast(rightEarValues[5]) * 0.25f, 1.0f)
        )

        val preset = EQPreset(
            name = "Audiogram Suggested",
            filters = suggestedBands,
            masterVolume = 1.2f,
            noiseGateThreshold = 0.01f,
            compressionRatio = 0.4f
        )

        audioEngine.applyPreset(preset)
        presetManager.setCurrentPreset(preset.name)
        Toast.makeText(context, "Suggested EQ applied live on Home screen!", Toast.LENGTH_SHORT).show()
    }

    private fun runCalibrationWizard() {
        val ctx = context ?: return

        // Show Pre-Test Instructions & Disclaimer Prompt
        AlertDialog.Builder(ctx)
            .setTitle("Baseline Hearing Check")
            .setMessage(
                "DISCLAIMER: This is a basic check, not a substitute for a professional hearing test.\n\n" +
                "Instructions:\n" +
                "1. Connect headphones or earbuds for left/right ear channel separation.\n" +
                "2. Set your device volume to a comfortable 50% level.\n" +
                "3. Test tones start quiet and increase gradually. Tap 'I CAN HEAR IT' as soon as you notice the tone."
            )
            .setPositiveButton("Start Check") { _, _ ->
                startHearingCheckWizard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startHearingCheckWizard() {
        val ctx = context ?: return

        var currentStep = 0
        val tempLeftValues = leftEarValues.copyOf()
        val tempRightValues = rightEarValues.copyOf()

        var audioTrack: AudioTrack? = null
        var isTonePlaying = false
        var toneThread: Thread? = null

        // Conservative audio safety parameters
        val sampleRate = 44100
        val minAmplitude = 0.02f // Quiet starting level (~15 dB HL)
        val maxAmplitudeCeiling = 0.35f // Hard max volume ceiling (far below 1.0f for safety)
        val stepInc = 0.03f // Small volume increment
        var currentAmplitude = minAmplitude

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_hearing_check, null)
        val tvStepProgress = dialogView.findViewById<TextView>(R.id.tvStepProgress)
        val pbCheckProgress = dialogView.findViewById<ProgressBar>(R.id.pbCheckProgress)
        val tvVolumeStatus = dialogView.findViewById<TextView>(R.id.tvVolumeStatus)
        val btnHearIt = dialogView.findViewById<Button>(R.id.btnHearIt)
        val btnSkipStep = dialogView.findViewById<Button>(R.id.btnSkipStep)
        val btnStopCheck = dialogView.findViewById<Button>(R.id.btnStopCheck)

        val checkDialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        fun stopTonePlayback() {
            isTonePlaying = false
            stopActiveHearingCheckAudio()
        }

        fun playToneStep(step: Int) {
            stopTonePlayback()

            if (step >= 12) {
                checkDialog.dismiss()
                showHearingCheckSummary(tempLeftValues, tempRightValues)
                return
            }

            val isLeftEar = step < 6
            val freqIndex = step % 6
            val freq = frequencies[freqIndex]
            val earName = if (isLeftEar) "Left Ear" else "Right Ear"

            tvStepProgress.text = "Step ${step + 1} of 12 — $earName (${freq.toInt()} Hz)"
            pbCheckProgress.progress = step + 1

            currentAmplitude = minAmplitude
            tvVolumeStatus.text = "Testing $earName at ${freq.toInt()} Hz...\n(Stepping volume up gradually)"

            isTonePlaying = true
            isCheckAudioActive = true

            // Generate stereo AudioTrack with channel isolation & 10ms fade-in
            toneThread = Thread {
                val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
                val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)

                try {
                    val track = AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                    activeAudioTrack = track
                    track.play()

                    val buffer = ShortArray(1024)
                    var sampleCount = 0L

                    while (isTonePlaying && isCheckAudioActive && currentStep == step) {
                        for (i in 0 until buffer.size step 2) {
                            // 10ms linear fade-in ramp (441 samples)
                            val fadeFactor = (sampleCount / 441.0f).coerceAtMost(1.0f)
                            val valSin = sin(2.0 * Math.PI * sampleCount * freq / sampleRate).toFloat()
                            val sampleVal = (valSin * currentAmplitude * fadeFactor * Short.MAX_VALUE).toInt().toShort()

                            if (isLeftEar) {
                                buffer[i] = sampleVal     // Left channel
                                buffer[i + 1] = 0         // Right channel (silence)
                            } else {
                                buffer[i] = 0             // Left channel (silence)
                                buffer[i + 1] = sampleVal // Right channel
                            }
                            sampleCount++
                        }

                        track.write(buffer, 0, buffer.size)

                        // Step volume up gradually every 750 ms until hard ceiling
                        if (sampleCount % (sampleRate * 0.75f).toLong() < 1024) {
                            if (currentAmplitude < maxAmplitudeCeiling) {
                                currentAmplitude = (currentAmplitude + stepInc).coerceAtMost(maxAmplitudeCeiling)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfilesFragment", "Error playing test tone: ${e.message}")
                }
            }
            toneThread?.start()
        }

        btnHearIt.setOnClickListener {
            val measuredDb = ((currentAmplitude / maxAmplitudeCeiling) * 100f).coerceIn(0f, 100f)
            val isLeftEar = currentStep < 6
            val freqIndex = currentStep % 6

            if (isLeftEar) {
                tempLeftValues[freqIndex] = measuredDb
            } else {
                tempRightValues[freqIndex] = measuredDb
            }

            stopTonePlayback()
            currentStep++
            playToneStep(currentStep)
        }

        btnSkipStep.setOnClickListener {
            stopTonePlayback()
            currentStep++
            playToneStep(currentStep)
        }

        btnStopCheck.setOnClickListener {
            stopTonePlayback()
            checkDialog.dismiss()
            Toast.makeText(ctx, "Hearing check cancelled.", Toast.LENGTH_SHORT).show()
        }

        checkDialog.setOnDismissListener {
            stopTonePlayback()
        }

        checkDialog.show()
        playToneStep(0)
    }

    private fun showHearingCheckSummary(left: FloatArray, right: FloatArray) {
        val ctx = context ?: return

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_hearing_check_summary, null)
        val tvSummaryDetails = dialogView.findViewById<TextView>(R.id.tvSummaryDetails)
        val btnConfirmSave = dialogView.findViewById<Button>(R.id.btnConfirmSave)
        val btnDiscardCheck = dialogView.findViewById<Button>(R.id.btnDiscardCheck)

        val sb = StringBuilder()
        sb.append("FREQUENCY | LEFT EAR  | RIGHT EAR\n")
        sb.append("---------------------------------\n")
        for (i in frequencies.indices) {
            val freqLabel = if (frequencies[i] >= 1000) "${(frequencies[i] / 1000).toInt()} kHz" else "${frequencies[i].toInt()} Hz"
            sb.append(String.format("%-9s | %3d dB HL | %3d dB HL\n", freqLabel, left[i].toInt(), right[i].toInt()))
        }
        tvSummaryDetails.text = sb.toString()

        val summaryDialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnConfirmSave.setOnClickListener {
            summaryDialog.dismiss()

            leftEarValues = left.copyOf()
            rightEarValues = right.copyOf()

            saveAudiogramData()
            audiogramChartView.setData(leftEarValues, rightEarValues)
            updateSeekBarProgress()

            val statusText = "Status: Calibrated (Baseline Hearing Check)"
            tvCalibrationStatus.text = statusText
            tvCalibrationStatus.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
            prefs.edit().putString("calibration_status", statusText).apply()

            Toast.makeText(ctx, "Hearing check saved to Audiogram!", Toast.LENGTH_SHORT).show()
        }

        btnDiscardCheck.setOnClickListener {
            summaryDialog.dismiss()
            Toast.makeText(ctx, "Hearing check results discarded.", Toast.LENGTH_SHORT).show()
        }

        summaryDialog.show()
    }

    private fun setupAudiogramSlidersFromData() {
        val root = view ?: return
        val leftSeekBars = arrayOf(
            root.findViewById<SeekBar>(R.id.sbLeft250),
            root.findViewById<SeekBar>(R.id.sbLeft500),
            root.findViewById<SeekBar>(R.id.sbLeft1000),
            root.findViewById<SeekBar>(R.id.sbLeft2000),
            root.findViewById<SeekBar>(R.id.sbLeft4000),
            root.findViewById<SeekBar>(R.id.sbLeft8000)
        )

        val rightSeekBars = arrayOf(
            root.findViewById<SeekBar>(R.id.sbRight250),
            root.findViewById<SeekBar>(R.id.sbRight500),
            root.findViewById<SeekBar>(R.id.sbRight1000),
            root.findViewById<SeekBar>(R.id.sbRight2000),
            root.findViewById<SeekBar>(R.id.sbRight4000),
            root.findViewById<SeekBar>(R.id.sbRight8000)
        )

        for (i in leftSeekBars.indices) {
            leftSeekBars[i].progress = leftEarValues[i].toInt()
        }
        for (i in rightSeekBars.indices) {
            rightSeekBars[i].progress = rightEarValues[i].toInt()
        }
    }

    private fun loadPresetsList() {
        presetsListContainer.removeAllViews()
        val presets = presetManager.getAllPresets()

        if (presets.isEmpty()) {
            tvNoSavedPresets.visibility = View.VISIBLE
        } else {
            tvNoSavedPresets.visibility = View.GONE
            val ctx = context ?: return
            
            for (preset in presets) {
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 16)
                    }
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_info_card)
                    setPadding(32, 32, 32, 32)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val avatar = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                        marginEnd = 24
                    }
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_preset_avatar_primary)
                }
                
                val icon = ImageView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    setImageResource(R.drawable.ic_equalizer)
                }
                icon.setColorFilter(ContextCompat.getColor(ctx, R.color.on_primary))
                avatar.addView(icon)
                card.addView(avatar)

                val infoLayout = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val nameText = TextView(ctx).apply {
                    text = preset.name
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                }
                
                val detailText = TextView(ctx).apply {
                    text = "${preset.filters.size} active filters"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
                }

                infoLayout.addView(nameText)
                infoLayout.addView(detailText)
                card.addView(infoLayout)

                val btnLoad = Button(ctx).apply {
                    text = "Load"
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        mainActivity.loadPresetAndNavigateToHome(preset.name)
                    }
                }
                card.addView(btnLoad)
                presetsListContainer.addView(card)
            }
        }
    }

    private var TextView.textSize: Float
        get() = paint.textSize
        set(value) {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, value)
        }
}
