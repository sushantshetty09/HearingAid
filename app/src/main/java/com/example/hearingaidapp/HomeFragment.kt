package com.example.hearingaidapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    // Preset Management
    private lateinit var btnLoadPreset: Button
    private lateinit var btnSavePreset: Button
    private lateinit var btnAddFilter: ImageButton
    private lateinit var tvCurrentPresetName: TextView

    // Dynamic Filter Management
    private lateinit var filterContainer: LinearLayout
    private lateinit var tvNoFilters: TextView

    // Noise Canceling
    private lateinit var switchNoiseCanceling: Switch
    private lateinit var seekBarNoiseStrength: SeekBar
    private lateinit var tvNoiseStrength: TextView
    private lateinit var tvNoiseStatus: TextView
    private lateinit var pbNoiseLearning: ProgressBar
    private lateinit var btnResetNoiseProfile: Button
    private lateinit var btnNcInfo: ImageButton

    // Advanced Features
    private lateinit var seekBarNoiseGate: SeekBar
    private lateinit var seekBarCompression: SeekBar
    private lateinit var tvNoiseGate: TextView
    private lateinit var tvCompression: TextView

    // Visualization views & controls
    private lateinit var waveformView: WaveformView
    private lateinit var spectrumView: SpectrumView
    private lateinit var cgWaveformZoom: ChipGroup
    private lateinit var chipZoom1x: Chip
    private lateinit var chipZoom2x: Chip
    private lateinit var chipZoom4x: Chip

    private lateinit var tvNoiseCancelingStatus: TextView
    private lateinit var tvGainValue: TextView

    private lateinit var advancedSection: View
    private lateinit var advancedHeader: View

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
    }

    override fun onResume() {
        super.onResume()
        mainActivity.addAudioStateListener(this)
        registerVisualizers()
        updateUIFromAudioEngine()
        updateCurrentPresetDisplay()
    }

    override fun onPause() {
        super.onPause()
        mainActivity.removeAudioStateListener(this)
        unregisterVisualizers()
    }

    private fun registerVisualizers() {
        if (::waveformView.isInitialized && ::spectrumView.isInitialized) {
            mainActivity.registerFragmentVisualizers(waveformView, spectrumView)
        }
    }

    private fun unregisterVisualizers() {
        mainActivity.restoreHomeVisualizers()
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
        updateNoiseCancelingStatusUI()
    }

    private fun initializeViews(view: View) {
        btnToggle = view.findViewById(R.id.btnToggle)
        tvToggleText = view.findViewById(R.id.tvToggleText)
        seekBarVolume = view.findViewById(R.id.seekBarVolume)
        tvVolumeLevel = view.findViewById(R.id.tvVolumeLevel)

        // Preset management
        btnLoadPreset = view.findViewById(R.id.btnLoadPreset)
        btnSavePreset = view.findViewById(R.id.btnSavePreset)
        btnAddFilter = view.findViewById(R.id.btnAddFilter)
        tvCurrentPresetName = view.findViewById(R.id.tvCurrentPreset)

        // Dynamic filter container
        filterContainer = view.findViewById(R.id.filterContainer)
        tvNoFilters = view.findViewById(R.id.tvNoFilters)

        // Noise Canceling controls
        switchNoiseCanceling = view.findViewById(R.id.switchNoiseCanceling)
        seekBarNoiseStrength = view.findViewById(R.id.seekBarNoiseStrength)
        tvNoiseStrength = view.findViewById(R.id.tvNoiseStrength)
        tvNoiseStatus = view.findViewById(R.id.tvNoiseStatus)
        pbNoiseLearning = view.findViewById(R.id.pbNoiseLearning)
        btnResetNoiseProfile = view.findViewById(R.id.btnResetNoiseProfile)
        btnNcInfo = view.findViewById(R.id.btnNcInfo)

        // Advanced features
        seekBarNoiseGate = view.findViewById(R.id.seekBarNoiseGate)
        seekBarCompression = view.findViewById(R.id.seekBarCompression)
        tvNoiseGate = view.findViewById(R.id.tvNoiseGate)
        tvCompression = view.findViewById(R.id.tvCompression)

        waveformView = view.findViewById(R.id.waveformView)
        spectrumView = view.findViewById(R.id.spectrumView)
        cgWaveformZoom = view.findViewById(R.id.cgWaveformZoom)
        chipZoom1x = view.findViewById(R.id.chipZoom1x)
        chipZoom2x = view.findViewById(R.id.chipZoom2x)
        chipZoom4x = view.findViewById(R.id.chipZoom4x)

        tvSessionTimer = view.findViewById(R.id.tvSessionTimer)
        statusIndicator = view.findViewById(R.id.statusIndicator)
        tvNoiseCancelingStatus = view.findViewById(R.id.tvNoiseCancelingStatus)
        tvGainValue = view.findViewById(R.id.tvGainValue)

        advancedSection = view.findViewById(R.id.advancedSection)
        advancedHeader = view.findViewById(R.id.advancedHeader)
    }

    private fun setupListeners(view: View) {
        btnToggle.setOnClickListener {
            mainActivity.toggleHearingAid()
        }

        advancedHeader.setOnClickListener {
            val content = view.findViewById<View>(R.id.advancedContent)
            if (content != null) {
                content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        // Waveform Zoom Chip Selector
        cgWaveformZoom.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipZoom1x
            val zoom = when (checkedId) {
                R.id.chipZoom2x -> 2.0f
                R.id.chipZoom4x -> 4.0f
                else -> 1.0f
            }
            waveformView.setZoomMultiplier(zoom)
        }

        val chipRestaurant = view.findViewById<View>(R.id.chipRestaurant)
        val chipHome = view.findViewById<View>(R.id.chipHome)
        val chipOutdoor = view.findViewById<View>(R.id.chipOutdoor)
        chipRestaurant?.setOnClickListener { loadPresetByName("Restaurant") }
        chipHome?.setOnClickListener { loadPresetByName("Home") }
        chipOutdoor?.setOnClickListener { loadPresetByName("Outdoor") }

        btnLoadPreset.setOnClickListener { showLoadPresetDialog() }
        btnSavePreset.setOnClickListener { showSavePresetDialog() }
        btnAddFilter.setOnClickListener { showAddFilterDialog() }

        // Noise Canceling Info Bottom Sheet Button
        btnNcInfo.setOnClickListener { showNcInfoBottomSheet() }

        // Master Volume
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gain = 0.5f + (progress / 100.0f) * 3.5f
                    audioEngine.setAmplification(gain)
                    tvVolumeLevel.text = "Volume: ${(gain * 100).toInt()}%"
                    tvGainValue.text = "+${(gain * 10).toInt() - 5}dB"
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Noise Gate
        seekBarNoiseGate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val threshold = progress / 1000.0f
                    audioEngine.setNoiseGate(threshold)
                    tvNoiseGate.text = "Threshold: ${progress}%"
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Compression
        seekBarCompression.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val ratio = progress / 100.0f
                    audioEngine.setCompression(ratio, 0.6f)
                    tvCompression.text = "Ratio: ${progress}%"
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Noise Canceling Switch
        switchNoiseCanceling.setOnCheckedChangeListener { _, isChecked ->
            audioEngine.setNoiseCanceling(isChecked)
            btnResetNoiseProfile.isEnabled = isChecked
            tvNoiseCancelingStatus.text = if (isChecked) "Active" else "Off"
            updateNoiseCancelingStatusUI()
            markAsCustomPreset()
        }

        // Noise Canceling Strength
        seekBarNoiseStrength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val strength = progress / 100.0f
                    audioEngine.setNoiseCancelingStrength(strength)
                    tvNoiseStrength.text = "Strength: ${progress}%"
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Reset Noise Profile Button
        btnResetNoiseProfile.setOnClickListener {
            audioEngine.resetNoiseCancelingProfile()
            updateNoiseCancelingStatusUI()

            Snackbar.make(view, "Noise profile reset. Please remain quiet for 2 seconds...", Snackbar.LENGTH_LONG)
                .setAction("OK") {}
                .show()
        }
    }

    private fun showNcInfoBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_nc_info, null)
        sheetView.findViewById<Button>(R.id.btnCloseNcInfo).setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheet.setContentView(sheetView)
        bottomSheet.show()
    }

    private fun showQInfoBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_q_info, null)
        sheetView.findViewById<Button>(R.id.btnCloseQInfo).setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheet.setContentView(sheetView)
        bottomSheet.show()
    }

    private fun updateNoiseCancelingStatusUI() {
        if (!isAdded) return
        val isEnabled = audioEngine.isNoiseCancelingEnabled()
        if (!isEnabled) {
            tvNoiseStatus.text = "Status: Off"
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            pbNoiseLearning.visibility = View.GONE
            return
        }

        val learned = audioEngine.isNoiseProfileLearned()
        val progress = audioEngine.getNoiseLearningProgress()

        if (learned) {
            tvNoiseStatus.text = "Status: Active (Wiener Filter)"
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
            pbNoiseLearning.visibility = View.GONE
        } else {
            tvNoiseStatus.text = "Status: Learning background noise (${(progress * 100).toInt()}%)..."
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
            pbNoiseLearning.visibility = View.VISIBLE
            pbNoiseLearning.progress = (progress * 100).toInt()

            // Poll while learning
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                if (isAdded && audioEngine.isNoiseCancelingEnabled() && !audioEngine.isNoiseProfileLearned()) {
                    updateNoiseCancelingStatusUI()
                }
            }, 100)
        }
    }

    private fun updateAllSliderDisplays() {
        val masterGain = 0.5f + (seekBarVolume.progress / 100.0f) * 3.5f
        tvVolumeLevel.text = "Volume: ${(masterGain * 100).toInt()}%"
        tvGainValue.text = "+${(masterGain * 10).toInt() - 5}dB"
        tvNoiseStrength.text = "Strength: ${seekBarNoiseStrength.progress}%"
        tvNoiseGate.text = "Threshold: ${seekBarNoiseGate.progress}%"
        tvCompression.text = "Ratio: ${seekBarCompression.progress}%"
        updateFilterDisplay()
    }

    private fun updateUIFromAudioEngine() {
        val tempState = audioEngine.getCurrentState("temp")
        val volumeProgress = ((tempState.masterVolume - 0.5f) / 3.5f * 100).toInt()
        seekBarVolume.progress = volumeProgress
        seekBarNoiseGate.progress = (tempState.noiseGateThreshold * 1000).toInt()
        seekBarCompression.progress = (tempState.compressionRatio * 100).toInt()

        switchNoiseCanceling.isChecked = audioEngine.isNoiseCancelingEnabled()
        seekBarNoiseStrength.progress = (audioEngine.getNoiseCancelingStrength() * 100).toInt()
        tvNoiseCancelingStatus.text = if (audioEngine.isNoiseCancelingEnabled()) "Active" else "Off"
        updateNoiseCancelingStatusUI()

        updateFilterDisplay()
        updateAllSliderDisplays()
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
    }

    private fun getQTextLabel(q: Float): String {
        return when {
            q < 0.8f -> "Wide"
            q < 2.0f -> "Medium"
            q < 4.0f -> "Narrow"
            else -> "Sharp"
        }
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

        val qLabel = getQTextLabel(band.q)
        val bwOct = AudioEngine.qToBandwidthOctaves(band.q)
        tvFilterType.text = "${band.filterType.getDisplayName()}, ${if (band.gain >= 0) "+${band.gain.toInt()}" else "${band.gain.toInt()}"} dB"

        filterPreviewItem.setFilter(band.frequency, band.gain, band.q, band.filterType)

        val gainProgress = (band.gain + 20).toInt().coerceIn(0, 40)
        seekBarGain.progress = gainProgress
        tvFilterGain.text = "${(gainProgress - 20)} dB"

        // Logarithmic Q slider progress
        seekBarQ.progress = AudioEngine.qToSlider(band.q)
        tvFilterQ.text = "Q %.1f (%s) ~%.1f oct".format(band.q, qLabel, bwOct)

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
                    val label = getQTextLabel(q)
                    val bw = AudioEngine.qToBandwidthOctaves(q)
                    tvFilterQ.text = "Q %.1f (%s) ~%.1f oct".format(q, label, bw)

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
        presetManager.setCurrentPreset(null)
        updateCurrentPresetDisplay()
    }

    private fun updateCurrentPresetDisplay() {
        val currentPreset = presetManager.getCurrentPresetName()
        tvCurrentPresetName.text = "Current: ${currentPreset ?: "Custom"}"
    }

    private fun loadPresetByName(name: String) {
        val preset = presetManager.loadPreset(name)
        if (preset != null) {
            audioEngine.applyPreset(preset)
            presetManager.setCurrentPreset(preset.name)
            updateUIFromAudioEngine()
            updateCurrentPresetDisplay()
            Toast.makeText(requireContext(), "Loaded: ${preset.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoadPresetDialog() {
        val presets = presetManager.getAllPresets()
        if (presets.isEmpty()) {
            Toast.makeText(requireContext(), "No presets available. Save one first!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_preset_list, null)
        val listView = dialogView.findViewById<ListView>(R.id.lvPresets)
        val tvCurrentPreset = dialogView.findViewById<TextView>(R.id.tvCurrentPreset)

        val currentPresetName = presetManager.getCurrentPresetName()
        tvCurrentPreset.text = "Current: ${currentPresetName ?: "None"}"

        val presetNames = presets.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, presetNames)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Load Preset")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedPreset = presets[position]
            audioEngine.applyPreset(selectedPreset)
            presetManager.setCurrentPreset(selectedPreset.name)
            updateUIFromAudioEngine()
            updateCurrentPresetDisplay()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showSavePresetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_preset, null)
        val etPresetName = dialogView.findViewById<TextInputEditText>(R.id.etPresetName)

        val currentName = presetManager.getCurrentPresetName()
        etPresetName.setText(currentName ?: "My Preset")
        etPresetName.selectAll()

        AlertDialog.Builder(requireContext())
            .setTitle("Save Preset")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val presetName = etPresetName.text.toString().trim()
                if (presetName.isNotEmpty()) {
                    val preset = audioEngine.getCurrentState(presetName)
                    if (presetManager.savePreset(preset)) {
                        presetManager.setCurrentPreset(presetName)
                        updateCurrentPresetDisplay()
                        Toast.makeText(requireContext(), "Preset saved: $presetName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupFilterDialogView(
        dialogView: View,
        initialFreq: Float = 1000f,
        initialGain: Float = 0f,
        initialQ: Float = 1.0f,
        initialType: AudioEngine.FilterType = AudioEngine.FilterType.PEAKING
    ): FilterDialogControls {
        val etFrequency = dialogView.findViewById<TextInputEditText>(R.id.etFrequency)
        val filterPreview = dialogView.findViewById<FilterPreviewView>(R.id.filterPreview)
        val seekBarGain = dialogView.findViewById<SeekBar>(R.id.seekBarGain)
        val seekBarQ = dialogView.findViewById<SeekBar>(R.id.seekBarQ)
        val tvGainLabel = dialogView.findViewById<TextView>(R.id.tvGainLabel)
        val tvQLabel = dialogView.findViewById<TextView>(R.id.tvQLabel)
        val tvQHint = dialogView.findViewById<TextView>(R.id.tvQHint)
        val spinnerFilterType = dialogView.findViewById<Spinner>(R.id.spinnerFilterType)
        val btnQInfo = dialogView.findViewById<ImageButton>(R.id.btnQInfo)

        // Initialize values
        etFrequency.setText(initialFreq.toInt().toString())
        seekBarGain.progress = (initialGain + 20).toInt().coerceIn(0, 40)
        seekBarQ.progress = AudioEngine.qToSlider(initialQ)

        tvGainLabel.text = "${initialGain.toInt()} dB"

        val initialQLabel = getQTextLabel(initialQ)
        val initialBw = AudioEngine.qToBandwidthOctaves(initialQ)
        tvQLabel.text = "Q %.1f (%s)".format(initialQ, initialQLabel)
        tvQHint.text = "Bandwidth ~%.1f octaves".format(initialBw)

        filterPreview.setFilter(initialFreq, initialGain, initialQ, initialType)

        val filterTypes = AudioEngine.FilterType.values().map { it.getDisplayName() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterType.adapter = adapter
        spinnerFilterType.setSelection(initialType.ordinal)

        fun updatePreview() {
            val freq = etFrequency.text.toString().toFloatOrNull() ?: 1000f
            val gain = (seekBarGain.progress - 20).toFloat()
            val q = AudioEngine.sliderToQ(seekBarQ.progress)
            val type = AudioEngine.FilterType.values()[spinnerFilterType.selectedItemPosition]

            val qL = getQTextLabel(q)
            val bw = AudioEngine.qToBandwidthOctaves(q)
            tvQLabel.text = "Q %.1f (%s)".format(q, qL)
            tvQHint.text = "Bandwidth ~%.1f octaves".format(bw)

            filterPreview.setFilter(freq, gain, q, type)
        }

        btnQInfo?.setOnClickListener { showQInfoBottomSheet() }

        // Wire quick-pick chips
        val chipMap = mapOf(
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
        for ((chipId, freqVal) in chipMap) {
            dialogView.findViewById<Chip>(chipId)?.setOnClickListener {
                etFrequency.setText(freqVal)
                updatePreview()
            }
        }

        // Wire Q preset chips
        dialogView.findViewById<Chip>(R.id.chipQ05)?.setOnClickListener {
            seekBarQ.progress = AudioEngine.qToSlider(0.5f)
            updatePreview()
        }
        dialogView.findViewById<Chip>(R.id.chipQ10)?.setOnClickListener {
            seekBarQ.progress = AudioEngine.qToSlider(1.0f)
            updatePreview()
        }
        dialogView.findViewById<Chip>(R.id.chipQ20)?.setOnClickListener {
            seekBarQ.progress = AudioEngine.qToSlider(2.0f)
            updatePreview()
        }
        dialogView.findViewById<Chip>(R.id.chipQ40)?.setOnClickListener {
            seekBarQ.progress = AudioEngine.qToSlider(4.0f)
            updatePreview()
        }

        etFrequency.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })

        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvGainLabel.text = "${progress - 20} dB"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        spinnerFilterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updatePreview() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return FilterDialogControls(etFrequency, seekBarGain, seekBarQ, spinnerFilterType)
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
