package com.example.hearingaidapp

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class HomeFragment : Fragment(), MainActivity.AudioStateListener {

    private lateinit var btnToggle: View
    private lateinit var tvToggleText: TextView
    private lateinit var tvSessionTimer: TextView
    private lateinit var statusIndicator: View

    // Master Volume
    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvVolumeLevel: TextView

    // Visuals
    private lateinit var waveformView: WaveformView
    private lateinit var spectrumView: SpectrumView
    private lateinit var cgWaveformZoom: ChipGroup
    private lateinit var chipZoom1x: Chip
    private lateinit var chipZoom2x: Chip
    private lateinit var chipZoom4x: Chip

    // Meeting Recorder
    private lateinit var btnToggleRecording: MaterialButton
    private lateinit var tvRecordingStatus: TextView
    private var recordingManager: RecordingManager? = null

    // Equalizer: Simplified 3 Tone Sliders
    private lateinit var tvCurrentPresetName: TextView
    private lateinit var seekBarVoiceClarity: SeekBar
    private lateinit var tvVoiceClarityVal: TextView
    private lateinit var seekBarBass: SeekBar
    private lateinit var tvBassVal: TextView
    private lateinit var seekBarTreble: SeekBar
    private lateinit var tvTrebleVal: TextView

    // Preset Buttons
    private lateinit var btnPresetSpeech: Button
    private lateinit var btnPresetRestaurant: Button
    private lateinit var btnPresetHome: Button
    private lateinit var btnPresetMusic: Button
    private lateinit var btnPresetFlat: Button
    private lateinit var btnSaveToProfile: MaterialButton
    private lateinit var btnLoadPreset: Button
    private lateinit var btnSavePreset: Button

    // Expandable Expert EQ
    private lateinit var btnToggleExpertEQ: View
    private lateinit var ivExpertToggleIcon: ImageView
    private lateinit var filterContainer: LinearLayout
    private lateinit var tvNoFilters: TextView
    private lateinit var btnAddFilter: ImageButton

    // Noise Reduction & Comfort
    private lateinit var switchNoiseCanceling: Switch
    private lateinit var btnNcInfo: ImageButton
    private lateinit var tvNoiseStatus: TextView
    private lateinit var pbNoiseLearning: ProgressBar
    private lateinit var seekBarNoiseStrength: SeekBar
    private lateinit var tvNoiseStrength: TextView
    private lateinit var btnResetNoiseProfile: Button

    // Comfort Tools (Gate & Limiter)
    private lateinit var advancedHeader: View
    private lateinit var ivComfortToggleIcon: ImageView
    private lateinit var advancedContent: View
    private lateinit var seekBarNoiseGate: SeekBar
    private lateinit var tvNoiseGate: TextView
    private lateinit var seekBarCompression: SeekBar
    private lateinit var tvCompression: TextView

    private var isUpdatingToneSliders = false

    private val mainActivity: MainActivity
        get() = activity as MainActivity

    private val audioEngine: AudioEngine
        get() = mainActivity.appAudioEngine

    private val presetManager: PresetManager
        get() = mainActivity.appPresetManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupListeners(view)
        setupToneSliders()
        updateUIFromAudioEngine()

        audioEngine.setVisualizerViews(waveformView, spectrumView)

        mainActivity.addAudioStateListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainActivity.removeAudioStateListener(this)
        audioEngine.setVisualizerViews(null, null)
        recordingManager?.stopRecording()
    }

    override fun onAudioStateChanged(isRunning: Boolean) {
        if (!isAdded) return
        if (isRunning) {
            tvToggleText.text = "STOP HEARING AID"
            btnToggle.setBackgroundResource(R.drawable.bg_emergency_button)
            statusIndicator.visibility = View.VISIBLE
        } else {
            tvToggleText.text = "START HEARING AID"
            btnToggle.setBackgroundResource(R.drawable.bg_start_button)
            statusIndicator.visibility = View.GONE
        }
    }

    override fun onTimerUpdated(formattedTime: String) {
        if (!isAdded) return
        tvSessionTimer.text = formattedTime
    }

    override fun onNoiseProfileLearnedChanged(learned: Boolean) {
        if (!isAdded) return
        activity?.runOnUiThread {
            updateNoiseCancelingStatusUI()
        }
    }

    private fun initializeViews(view: View) {
        btnToggle = view.findViewById(R.id.btnToggle)
        tvToggleText = view.findViewById(R.id.tvToggleText)
        tvSessionTimer = view.findViewById(R.id.tvSessionTimer)
        statusIndicator = view.findViewById(R.id.statusIndicator)

        seekBarVolume = view.findViewById(R.id.seekBarVolume)
        tvVolumeLevel = view.findViewById(R.id.tvVolumeLevel)

        waveformView = view.findViewById(R.id.waveformView)
        spectrumView = view.findViewById(R.id.spectrumView)
        cgWaveformZoom = view.findViewById(R.id.cgWaveformZoom)
        chipZoom1x = view.findViewById(R.id.chipZoom1x)
        chipZoom2x = view.findViewById(R.id.chipZoom2x)
        chipZoom4x = view.findViewById(R.id.chipZoom4x)

        btnToggleRecording = view.findViewById(R.id.btnToggleRecording)
        tvRecordingStatus = view.findViewById(R.id.tvRecordingStatus)

        tvCurrentPresetName = view.findViewById(R.id.tvCurrentPreset)
        seekBarVoiceClarity = view.findViewById(R.id.seekBarVoiceClarity)
        tvVoiceClarityVal = view.findViewById(R.id.tvVoiceClarityVal)
        seekBarBass = view.findViewById(R.id.seekBarBass)
        tvBassVal = view.findViewById(R.id.tvBassVal)
        seekBarTreble = view.findViewById(R.id.seekBarTreble)
        tvTrebleVal = view.findViewById(R.id.tvTrebleVal)

        btnPresetSpeech = view.findViewById(R.id.btnPresetSpeech)
        btnPresetRestaurant = view.findViewById(R.id.btnPresetRestaurant)
        btnPresetHome = view.findViewById(R.id.btnPresetHome)
        btnPresetMusic = view.findViewById(R.id.btnPresetMusic)
        btnPresetFlat = view.findViewById(R.id.btnPresetFlat)
        btnSaveToProfile = view.findViewById(R.id.btnSaveToProfile)
        btnLoadPreset = view.findViewById(R.id.btnLoadPreset)
        btnSavePreset = view.findViewById(R.id.btnSavePreset)

        btnToggleExpertEQ = view.findViewById(R.id.btnToggleExpertEQ)
        ivExpertToggleIcon = view.findViewById(R.id.ivExpertToggleIcon)
        filterContainer = view.findViewById(R.id.filterContainer)
        tvNoFilters = view.findViewById(R.id.tvNoFilters)
        btnAddFilter = view.findViewById(R.id.btnAddFilter)

        switchNoiseCanceling = view.findViewById(R.id.switchNoiseCanceling)
        btnNcInfo = view.findViewById(R.id.btnNcInfo)
        tvNoiseStatus = view.findViewById(R.id.tvNoiseStatus)
        pbNoiseLearning = view.findViewById(R.id.pbNoiseLearning)
        seekBarNoiseStrength = view.findViewById(R.id.seekBarNoiseStrength)
        tvNoiseStrength = view.findViewById(R.id.tvNoiseStrength)
        btnResetNoiseProfile = view.findViewById(R.id.btnResetNoiseProfile)

        advancedHeader = view.findViewById(R.id.advancedHeader)
        ivComfortToggleIcon = view.findViewById(R.id.ivComfortToggleIcon)
        advancedContent = view.findViewById(R.id.advancedContent)
        seekBarNoiseGate = view.findViewById(R.id.seekBarNoiseGate)
        tvNoiseGate = view.findViewById(R.id.tvNoiseGate)
        seekBarCompression = view.findViewById(R.id.seekBarCompression)
        tvCompression = view.findViewById(R.id.tvCompression)
    }

    private fun setupListeners(view: View) {
        btnToggle.setOnClickListener {
            if (!mainActivity.isHearingAidRunning() && !mainActivity.isBluetoothAudioConnected()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Connect Bluetooth Headphones")
                    .setMessage("For optimal hearing and to prevent acoustic feedback, please connect your Bluetooth headphones or earbuds.\n\nNo Bluetooth audio device is currently connected.")
                    .setPositiveButton("Start Anyway") { _, _ ->
                        mainActivity.toggleHearingAid()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                mainActivity.toggleHearingAid()
            }
        }

        // Meeting Recorder
        btnToggleRecording.setOnClickListener {
            val rm = recordingManager ?: RecordingManager(requireContext()).also { recordingManager = it }
            if (!rm.isActive) {
                rm.setListener(object : RecordingManager.RecordingListener {
                    override fun onRecordingStarted() {
                        activity?.runOnUiThread {
                            btnToggleRecording.text = "Stop"
                            btnToggleRecording.setIconResource(R.drawable.ic_equalizer)
                            tvRecordingStatus.text = "Recording meeting... tap Stop to save"
                        }
                    }

                    override fun onRecordingStopped(file: java.io.File) {
                        activity?.runOnUiThread {
                            btnToggleRecording.text = "Record"
                            btnToggleRecording.setIconResource(R.drawable.ic_mic)
                            tvRecordingStatus.text = "Saved: ${file.name}"
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    requireContext(),
                                    "${requireContext().packageName}.provider",
                                    file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/wav"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(android.content.Intent.createChooser(shareIntent, "Share or Review Recording"))
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onRecordingError(message: String) {
                        activity?.runOnUiThread {
                            btnToggleRecording.text = "Record"
                            btnToggleRecording.setIconResource(R.drawable.ic_mic)
                            tvRecordingStatus.text = message
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                rm.startRecording()
            } else {
                rm.stopRecording()
            }
        }

        // Waveform Zoom
        cgWaveformZoom.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipZoom1x
            val zoom = when (checkedId) {
                R.id.chipZoom2x -> 2.0f
                R.id.chipZoom4x -> 4.0f
                else -> 1.0f
            }
            waveformView.setZoomMultiplier(zoom)
        }

        // Master Volume
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gain = 0.5f + (progress / 100.0f) * 3.5f
                    audioEngine.setAmplification(gain)
                    tvVolumeLevel.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick Presets
        btnPresetSpeech.setOnClickListener { loadPresetByName("Speech Clarity") }
        btnPresetRestaurant.setOnClickListener { loadPresetByName("Restaurant") }
        btnPresetHome.setOnClickListener { loadPresetByName("Home") }
        btnPresetMusic.setOnClickListener { loadPresetByName("Music") }
        btnPresetFlat.setOnClickListener { loadPresetByName("Flat") }

        // Save to User Profile
        btnSaveToProfile.setOnClickListener { saveToMyProfile() }

        // Presets dialog
        btnLoadPreset.setOnClickListener { showLoadPresetDialog() }
        btnAddFilter.setOnClickListener { showAddFilterDialog() }

        // Expandable Expert EQ
        btnToggleExpertEQ.setOnClickListener {
            if (filterContainer.visibility == View.VISIBLE) {
                filterContainer.visibility = View.GONE
                ivExpertToggleIcon.setImageResource(R.drawable.ic_expand_more)
            } else {
                filterContainer.visibility = View.VISIBLE
                ivExpertToggleIcon.setImageResource(R.drawable.ic_expand_less)
            }
        }

        // Noise Canceling Switch
        switchNoiseCanceling.setOnCheckedChangeListener { _, isChecked ->
            audioEngine.setNoiseCanceling(isChecked)
            updateNoiseCancelingStatusUI()
        }

        btnNcInfo.setOnClickListener { showNcInfoBottomSheet() }

        seekBarNoiseStrength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val strength = progress / 100.0f
                    audioEngine.setNoiseCancelingStrength(strength)
                    tvNoiseStrength.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnResetNoiseProfile.setOnClickListener {
            audioEngine.resetNoiseCancelingProfile()
            updateNoiseCancelingStatusUI()
            Snackbar.make(requireView(), "Noise profile reset. Learning room background...", Snackbar.LENGTH_SHORT).show()
        }

        // Comfort Tools Expandable Header
        advancedHeader.setOnClickListener {
            if (advancedContent.visibility == View.VISIBLE) {
                advancedContent.visibility = View.GONE
                ivComfortToggleIcon.setImageResource(R.drawable.ic_expand_more)
            } else {
                advancedContent.visibility = View.VISIBLE
                ivComfortToggleIcon.setImageResource(R.drawable.ic_expand_less)
            }
        }

        seekBarNoiseGate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val threshold = progress / 1000.0f
                    audioEngine.setNoiseGate(threshold)
                    tvNoiseGate.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarCompression.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val ratio = 1.0f + (progress / 100.0f) * 7.0f
                    audioEngine.setCompression(ratio, 0.6f)
                    tvCompression.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupToneSliders() {
        seekBarVoiceClarity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 12
                tvVoiceClarityVal.text = if (db >= 0) "+$db dB" else "$db dB"
                if (fromUser && !isUpdatingToneSliders) {
                    applyToneBandGain(500f..3000f, db.toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 12
                tvBassVal.text = if (db >= 0) "+$db dB" else "$db dB"
                if (fromUser && !isUpdatingToneSliders) {
                    applyToneBandGain(20f..499f, db.toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarTreble.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 12
                tvTrebleVal.text = if (db >= 0) "+$db dB" else "$db dB"
                if (fromUser && !isUpdatingToneSliders) {
                    applyToneBandGain(3001f..20000f, db.toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun applyToneBandGain(freqRange: ClosedFloatingPointRange<Float>, targetGain: Float) {
        val bands = audioEngine.getAllEQBands()
        bands.forEachIndexed { index, band ->
            if (band.frequency in freqRange) {
                audioEngine.setEQBand(index, band.frequency, targetGain, band.q, band.filterType)
            }
        }
        updateFilterDisplay()
        markAsCustomPreset()
    }

    private fun updateToneSlidersFromBands() {
        isUpdatingToneSliders = true
        val bands = audioEngine.getAllEQBands()

        val bassBands = bands.filter { it.frequency < 500f }
        val midBands = bands.filter { it.frequency in 500f..3000f }
        val trebleBands = bands.filter { it.frequency > 3000f }

        if (bassBands.isNotEmpty()) {
            val avgBass = bassBands.map { it.gain }.average().toInt()
            val progress = (avgBass + 12).coerceIn(0, 24)
            seekBarBass.progress = progress
            tvBassVal.text = if (avgBass >= 0) "+$avgBass dB" else "$avgBass dB"
        }

        if (midBands.isNotEmpty()) {
            val avgMid = midBands.map { it.gain }.average().toInt()
            val progress = (avgMid + 12).coerceIn(0, 24)
            seekBarVoiceClarity.progress = progress
            tvVoiceClarityVal.text = if (avgMid >= 0) "+$avgMid dB" else "$avgMid dB"
        }

        if (trebleBands.isNotEmpty()) {
            val avgTreble = trebleBands.map { it.gain }.average().toInt()
            val progress = (avgTreble + 12).coerceIn(0, 24)
            seekBarTreble.progress = progress
            tvTrebleVal.text = if (avgTreble >= 0) "+$avgTreble dB" else "$avgTreble dB"
        }
        isUpdatingToneSliders = false
    }

    private fun saveToMyProfile() {
        val prefs = requireContext().getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("profile_name", "My") ?: "My"
        val profilePresetName = if (userName == "Your Name" || userName == "My") "My Personal Profile" else "$userName's Profile"

        val bands = audioEngine.getAllEQBands()
        val filters = bands.map { band ->
            EQPreset.FilterBand(
                frequency = band.frequency,
                gain = band.gain,
                q = band.q,
                filterType = band.filterType
            )
        }
        val preset = EQPreset(name = profilePresetName, filters = filters)
        presetManager.savePreset(preset)

        prefs.edit()
            .putBoolean("has_saved_hearing_profile", true)
            .putString("personal_profile_preset_name", profilePresetName)
            .putInt("personal_volume", seekBarVolume.progress)
            .putInt("personal_noise_strength", seekBarNoiseStrength.progress)
            .apply()

        Snackbar.make(requireView(), "✓ Saved as your personal hearing profile!", Snackbar.LENGTH_LONG)
            .setAction("View") {
                mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                    ?.selectedItemId = R.id.nav_profiles
            }
            .show()
    }

    private fun updateUIFromAudioEngine() {
        val currentAmp = audioEngine.getAmplification()
        val volumeProgress = (((currentAmp - 0.5f) / 3.5f) * 100.0f).toInt().coerceIn(0, 100)
        seekBarVolume.progress = volumeProgress
        tvVolumeLevel.text = "$volumeProgress%"

        switchNoiseCanceling.isChecked = audioEngine.isNoiseCancelingEnabled()
        val ncStrength = (audioEngine.getNoiseCancelingStrength() * 100).toInt()
        seekBarNoiseStrength.progress = ncStrength
        tvNoiseStrength.text = "$ncStrength%"

        val noiseGateProgress = (audioEngine.getNoiseGateThreshold() * 1000).toInt().coerceIn(0, 100)
        seekBarNoiseGate.progress = noiseGateProgress
        tvNoiseGate.text = "$noiseGateProgress%"

        val compressionProgress = (((audioEngine.getCompressionRatio() - 1.0f) / 7.0f) * 100).toInt().coerceIn(0, 100)
        seekBarCompression.progress = compressionProgress
        tvCompression.text = "$compressionProgress%"

        updateNoiseCancelingStatusUI()
        updateFilterDisplay()
    }

    private fun updateNoiseCancelingStatusUI() {
        if (!switchNoiseCanceling.isChecked) {
            tvNoiseStatus.text = "Status: Off"
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            pbNoiseLearning.visibility = View.GONE
        } else {
            val isLearned = audioEngine.isNoiseProfileLearned()
            val progress = audioEngine.getNoiseLearningProgress()
            if (!isLearned && progress < 1.0f) {
                tvNoiseStatus.text = "Status: Learning background noise (${(progress * 100).toInt()}%)..."
                tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.spectrum_high))
                pbNoiseLearning.visibility = View.VISIBLE
                pbNoiseLearning.progress = (progress * 100).toInt()
            } else {
                tvNoiseStatus.text = "Status: Active (Speech Protected)"
                tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.spectrum_mid))
                pbNoiseLearning.visibility = View.GONE
            }
        }
    }

    private fun updateFilterDisplay() {
        filterContainer.removeAllViews()
        val bands = audioEngine.getAllEQBands()

        if (bands.isEmpty()) {
            tvNoFilters.visibility = View.VISIBLE
        } else {
            tvNoFilters.visibility = View.GONE
            bands.forEachIndexed { index, band ->
                addFilterView(index, band)
            }
        }
        updateToneSlidersFromBands()
    }

    private fun addFilterView(bandIndex: Int, band: AudioEngine.EQBand) {
        val filterView = LayoutInflater.from(requireContext()).inflate(R.layout.item_filter, filterContainer, false)

        val tvFilterTitle = filterView.findViewById<TextView>(R.id.tvFilterTitle)
        val tvFilterType = filterView.findViewById<TextView>(R.id.tvFilterType)
        val filterPreviewItem = filterView.findViewById<FilterPreviewView>(R.id.filterPreviewItem)
        val seekBarGain = filterView.findViewById<SeekBar>(R.id.seekBarFilterGain)
        val tvFilterGain = filterView.findViewById<TextView>(R.id.tvFilterGain)
        val seekBarQ = filterView.findViewById<SeekBar>(R.id.seekBarFilterQ)
        val tvFilterQ = filterView.findViewById<TextView>(R.id.tvFilterQ)
        val btnEdit = filterView.findViewById<Button>(R.id.btnEditFilter)
        val btnRemove = filterView.findViewById<Button>(R.id.btnRemoveFilter)

        tvFilterTitle.text = "Filter ${bandIndex + 1}: ${band.frequency.toInt()} Hz"
        tvFilterType.text = "${band.filterType.getDisplayName()}, ${if (band.gain >= 0) "+${band.gain.toInt()}" else "${band.gain.toInt()}"} dB"

        filterPreviewItem.setFilter(band.frequency, band.gain, band.q, band.filterType)

        val gainProgress = (band.gain + 20).toInt().coerceIn(0, 40)
        seekBarGain.progress = gainProgress
        tvFilterGain.text = "${(gainProgress - 20)} dB"

        seekBarQ.progress = AudioEngine.qToSlider(band.q)
        val bwOct = AudioEngine.qToBandwidthOctaves(band.q)
        tvFilterQ.text = "Q %.1f ~%.1f oct".format(band.q, bwOct)

        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gainDb = progress - 20
                    tvFilterGain.text = "$gainDb dB"
                    val currentQ = AudioEngine.sliderToQ(seekBarQ.progress)
                    audioEngine.setEQBand(bandIndex, band.frequency, gainDb.toFloat(), currentQ, band.filterType)
                    filterPreviewItem.setFilter(band.frequency, gainDb.toFloat(), currentQ, band.filterType)
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val q = AudioEngine.sliderToQ(progress)
                    val bw = AudioEngine.qToBandwidthOctaves(q)
                    tvFilterQ.text = "Q %.1f ~%.1f oct".format(q, bw)
                    val currentGain = (seekBarGain.progress - 20).toFloat()
                    audioEngine.setEQBand(bandIndex, band.frequency, currentGain, q, band.filterType)
                    filterPreviewItem.setFilter(band.frequency, currentGain, q, band.filterType)
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnEdit.setOnClickListener {
            showEditFilterDialog(bandIndex, band)
        }

        btnRemove.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Remove Filter")
                .setMessage("Remove filter at ${band.frequency.toInt()} Hz?")
                .setPositiveButton("Remove") { _, _ ->
                    audioEngine.removeEQBand(bandIndex)
                    updateFilterDisplay()
                    markAsCustomPreset()
                    Toast.makeText(requireContext(), "Filter removed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        filterContainer.addView(filterView)
    }

    private fun markAsCustomPreset() {
        tvCurrentPresetName.text = "Custom"
        presetManager.setCurrentPreset(null)
    }

    fun loadPresetByName(presetName: String) {
        val preset = presetManager.loadPreset(presetName)
        if (preset != null) {
            audioEngine.applyPreset(preset)
            tvCurrentPresetName.text = preset.name
            presetManager.setCurrentPreset(preset.name)
            updateFilterDisplay()
            Toast.makeText(requireContext(), "Loaded '${preset.name}'", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Preset not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNcInfoBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_nc_info, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.btnCloseNcInfo)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showQInfoBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_q_info, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.btnCloseQInfo)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showLoadPresetDialog() {
        val presets = presetManager.getAllPresets()
        if (presets.isEmpty()) {
            Toast.makeText(requireContext(), "No presets available", Toast.LENGTH_SHORT).show()
            return
        }

        val names = presets.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Load Hearing Mode")
            .setItems(names) { _, which ->
                loadPresetByName(names[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupFilterDialogView(
        dialogView: View,
        initialFrequency: Float,
        initialGain: Float,
        initialQ: Float,
        initialType: AudioEngine.FilterType
    ): FilterDialogControls {
        // Use actual IDs from dialog_add_filter.xml
        val etFrequency = dialogView.findViewById<TextInputEditText>(R.id.etFrequency)
        val seekBarGain = dialogView.findViewById<SeekBar>(R.id.seekBarGain)
        val tvGainLabel = dialogView.findViewById<TextView>(R.id.tvGainLabel)
        val seekBarQ = dialogView.findViewById<SeekBar>(R.id.seekBarQ)
        val tvQLabel = dialogView.findViewById<TextView>(R.id.tvQLabel)
        val tvBandwidthHint = dialogView.findViewById<TextView>(R.id.tvQHint)
        val spinnerFilterType = dialogView.findViewById<Spinner>(R.id.spinnerFilterType)
        val filterPreview = dialogView.findViewById<FilterPreviewView>(R.id.filterPreview)
        val btnQInfo = dialogView.findViewById<ImageButton>(R.id.btnQInfo)

        btnQInfo?.setOnClickListener { showQInfoBottomSheet() }

        val typeNames = AudioEngine.FilterType.values().map { it.getDisplayName() }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterType?.adapter = adapter
        spinnerFilterType?.setSelection(initialType.ordinal)

        etFrequency?.setText(initialFrequency.toInt().toString())
        val gainProg = (initialGain + 20).toInt().coerceIn(0, 40)
        seekBarGain?.progress = gainProg
        tvGainLabel?.text = "${initialGain.toInt()} dB"

        seekBarQ?.progress = AudioEngine.qToSlider(initialQ)

        fun updatePreview() {
            val freq = etFrequency?.text.toString().toFloatOrNull() ?: initialFrequency
            val gain = ((seekBarGain?.progress ?: 20) - 20).toFloat()
            val q = AudioEngine.sliderToQ(seekBarQ?.progress ?: 434)
            val bw = AudioEngine.qToBandwidthOctaves(q)
            val type = AudioEngine.FilterType.values()[spinnerFilterType?.selectedItemPosition ?: 0]

            tvQLabel?.text = "Q %.1f".format(q)
            tvBandwidthHint?.text = "Bandwidth: ~%.1f octaves".format(bw)
            filterPreview?.setFilter(freq, gain, q, type)
        }

        updatePreview()

        // Wire individual frequency chips directly (no ChipGroup wrapper in XML)
        val freqChipMap = mapOf(
            R.id.chip60 to "60",
            R.id.chip250 to "250",
            R.id.chip500 to "500",
            R.id.chip800 to "800",
            R.id.chip1500 to "1500",
            R.id.chip2000 to "2000",
            R.id.chip3000 to "3000",
            R.id.chip4000 to "4000",
            R.id.chip8000 to "8000"
        )
        freqChipMap.forEach { (id, freq) ->
            dialogView.findViewById<Chip>(id)?.setOnClickListener {
                etFrequency?.setText(freq)
                updatePreview()
            }
        }

        // Wire Q preset chips directly
        val qChipMap = mapOf(
            R.id.chipQ05 to 0.5f,
            R.id.chipQ10 to 1.0f,
            R.id.chipQ20 to 2.0f,
            R.id.chipQ40 to 4.0f
        )
        qChipMap.forEach { (id, targetQ) ->
            dialogView.findViewById<Chip>(id)?.setOnClickListener {
                seekBarQ?.progress = AudioEngine.qToSlider(targetQ)
                updatePreview()
            }
        }

        etFrequency?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })

        seekBarGain?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvGainLabel?.text = "${progress - 20} dB"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        spinnerFilterType?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updatePreview() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return FilterDialogControls(etFrequency!!, seekBarGain!!, seekBarQ!!, spinnerFilterType!!)
    }

    private data class FilterDialogControls(
        val etFrequency: TextInputEditText,
        val seekBarGain: SeekBar,
        val seekBarQ: SeekBar,
        val spinnerFilterType: Spinner
    )

    private fun showAddFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_filter, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = "Add Custom Filter"

        val controls = setupFilterDialogView(dialogView, 1000f, 0f, 1.0f, AudioEngine.FilterType.PEAKING)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancelFilter)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnApplyFilter)?.setOnClickListener {
            val frequency = controls.etFrequency.text.toString().toFloatOrNull()
            if (frequency == null || frequency < 20f || frequency > 20000f) {
                Toast.makeText(requireContext(), "Invalid frequency (20-20000 Hz)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gain = (controls.seekBarGain.progress - 20).toFloat()
            val q = AudioEngine.sliderToQ(controls.seekBarQ.progress)
            val filterType = AudioEngine.FilterType.values()[controls.spinnerFilterType.selectedItemPosition]

            audioEngine.addEQBand(frequency, gain, q, filterType)
            updateFilterDisplay()
            markAsCustomPreset()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditFilterDialog(bandIndex: Int, band: AudioEngine.EQBand) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_filter, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = "Edit Filter #${bandIndex + 1}"

        val controls = setupFilterDialogView(dialogView, band.frequency, band.gain, band.q, band.filterType)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancelFilter)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnApplyFilter)?.setOnClickListener {
            val frequency = controls.etFrequency.text.toString().toFloatOrNull()
            if (frequency == null || frequency < 20f || frequency > 20000f) {
                Toast.makeText(requireContext(), "Invalid frequency (20-20000 Hz)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gain = (controls.seekBarGain.progress - 20).toFloat()
            val q = AudioEngine.sliderToQ(controls.seekBarQ.progress)
            val filterType = AudioEngine.FilterType.values()[controls.spinnerFilterType.selectedItemPosition]

            audioEngine.setEQBand(bandIndex, frequency, gain, q, filterType)
            updateFilterDisplay()
            markAsCustomPreset()
            dialog.dismiss()
        }

        dialog.show()
    }
}
