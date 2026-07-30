package com.example.hearingaidapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
    private lateinit var btnResetNoiseProfile: Button

    // Advanced Features
    private lateinit var seekBarNoiseGate: SeekBar
    private lateinit var seekBarCompression: SeekBar
    private lateinit var tvNoiseGate: TextView
    private lateinit var tvCompression: TextView

    // Visualization views
    private lateinit var waveformView: WaveformView
    private lateinit var spectrumView: SpectrumView

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
        if (learned) {
            tvNoiseStatus.text = "Active"
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
        } else {
            tvNoiseStatus.text = "Learning noise..."
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
        }
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
        btnResetNoiseProfile = view.findViewById(R.id.btnResetNoiseProfile)

        // Advanced features
        seekBarNoiseGate = view.findViewById(R.id.seekBarNoiseGate)
        seekBarCompression = view.findViewById(R.id.seekBarCompression)
        tvNoiseGate = view.findViewById(R.id.tvNoiseGate)
        tvCompression = view.findViewById(R.id.tvCompression)

        waveformView = view.findViewById(R.id.waveformView)
        spectrumView = view.findViewById(R.id.spectrumView)

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

        val chipRestaurant = view.findViewById<View>(R.id.chipRestaurant)
        val chipHome = view.findViewById<View>(R.id.chipHome)
        val chipOutdoor = view.findViewById<View>(R.id.chipOutdoor)
        chipRestaurant?.setOnClickListener { loadPresetByName("Restaurant") }
        chipHome?.setOnClickListener { loadPresetByName("Home") }
        chipOutdoor?.setOnClickListener { loadPresetByName("Outdoor") }

        // Preset management buttons
        btnLoadPreset.setOnClickListener {
            showLoadPresetDialog()
        }

        btnSavePreset.setOnClickListener {
            showSavePresetDialog()
        }

        btnAddFilter.setOnClickListener {
            showAddFilterDialog()
        }

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
            if (isChecked) {
                tvNoiseStatus.visibility = View.VISIBLE
                tvNoiseStatus.text = "Learning noise..."
                checkNoiseProfileStatus()
            } else {
                tvNoiseStatus.visibility = View.GONE
            }
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
            tvNoiseStatus.text = "Learning noise..."
            tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
            Toast.makeText(requireContext(), "Noise profile reset. Stay quiet...", Toast.LENGTH_SHORT).show()
            checkNoiseProfileStatus()
        }
    }

    private fun checkNoiseProfileStatus() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isAdded && switchNoiseCanceling.isChecked) {
                    if (audioEngine.isNoiseProfileLearned()) {
                        tvNoiseStatus.text = "Active"
                        tvNoiseStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                    } else {
                        handler.postDelayed(this, 500)
                    }
                }
            }
        }, 500)
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
        
        // Query noise canceling state directly from AudioEngine (not part of EQPreset)
        switchNoiseCanceling.isChecked = audioEngine.isNoiseCancelingEnabled()
        seekBarNoiseStrength.progress = (audioEngine.getNoiseCancelingStrength() * 100).toInt()
        tvNoiseCancelingStatus.text = if (audioEngine.isNoiseCancelingEnabled()) "Active" else "Off"

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

    private fun addFilterView(bandIndex: Int, band: AudioEngine.EQBand) {
        val filterView = LayoutInflater.from(requireContext()).inflate(R.layout.item_filter, filterContainer, false)
        
        val tvFilterTitle = filterView.findViewById<TextView>(R.id.tvFilterTitle)
        val tvFilterType = filterView.findViewById<TextView>(R.id.tvFilterType)
        val seekBarGain = filterView.findViewById<SeekBar>(R.id.seekBarFilterGain)
        val tvFilterGain = filterView.findViewById<TextView>(R.id.tvFilterGain)
        val seekBarQ = filterView.findViewById<SeekBar>(R.id.seekBarFilterQ)
        val tvFilterQ = filterView.findViewById<TextView>(R.id.tvFilterQ)
        val btnEdit = filterView.findViewById<Button>(R.id.btnEditFilter)
        val btnRemove = filterView.findViewById<Button>(R.id.btnRemoveFilter)

        tvFilterTitle.text = "Filter ${bandIndex + 1}: ${band.frequency.toInt()} Hz"
        tvFilterType.text = band.filterType.getDisplayName()
        
        val gainProgress = (band.gain + 20).toInt().coerceIn(0, 40)
        seekBarGain.progress = gainProgress
        tvFilterGain.text = "${(gainProgress - 20)} dB"
        
        val qProgress = ((band.q - 0.1f) * 10).toInt().coerceIn(0, 99)
        seekBarQ.progress = qProgress
        tvFilterQ.text = "%.1f".format(band.q)

        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gainDb = progress - 20
                    tvFilterGain.text = "$gainDb dB"
                    audioEngine.setEQBand(bandIndex, band.frequency, gainDb.toFloat(), band.q, band.filterType)
                    markAsCustomPreset()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val q = (progress + 1) / 10.0f
                    tvFilterQ.text = "%.1f".format(q)
                    audioEngine.setEQBand(bandIndex, band.frequency, band.gain, q, band.filterType)
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

    private fun showAddFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_filter, null)
        val etFrequency = dialogView.findViewById<TextInputEditText>(R.id.etFrequency)
        val seekBarGain = dialogView.findViewById<SeekBar>(R.id.seekBarGain)
        val seekBarQ = dialogView.findViewById<SeekBar>(R.id.seekBarQ)
        val tvGainLabel = dialogView.findViewById<TextView>(R.id.tvGainLabel)
        val tvQLabel = dialogView.findViewById<TextView>(R.id.tvQLabel)
        val spinnerFilterType = dialogView.findViewById<Spinner>(R.id.spinnerFilterType)

        val filterTypes = AudioEngine.FilterType.values().map { it.getDisplayName() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterType.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Add Custom Filter")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val frequency = etFrequency.text.toString().toFloatOrNull()
                if (frequency == null || frequency < 20f || frequency > 20000f) {
                    Toast.makeText(requireContext(), "Invalid frequency (20-20000 Hz)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val gain = (seekBarGain.progress - 20).toFloat()
                val q = (seekBarQ.progress + 1) / 10.0f
                val filterType = AudioEngine.FilterType.values()[spinnerFilterType.selectedItemPosition]

                audioEngine.addEQBand(frequency, gain, q, filterType)
                updateFilterDisplay()
                markAsCustomPreset()
            }
            .setNegativeButton("Cancel", null)
            .create()

        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvGainLabel.text = "Gain (dB): ${progress - 20}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val q = (progress + 1) / 10.0f
                tvQLabel.text = "Q Factor: %.1f".format(q)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.show()
    }

    private fun showEditFilterDialog(bandIndex: Int, band: AudioEngine.EQBand) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_filter, null)
        val etFrequency = dialogView.findViewById<TextInputEditText>(R.id.etFrequency)
        val seekBarGain = dialogView.findViewById<SeekBar>(R.id.seekBarGain)
        val seekBarQ = dialogView.findViewById<SeekBar>(R.id.seekBarQ)
        val tvGainLabel = dialogView.findViewById<TextView>(R.id.tvGainLabel)
        val tvQLabel = dialogView.findViewById<TextView>(R.id.tvQLabel)
        val spinnerFilterType = dialogView.findViewById<Spinner>(R.id.spinnerFilterType)

        etFrequency.setText(band.frequency.toInt().toString())
        seekBarGain.progress = (band.gain + 20).toInt()
        seekBarQ.progress = (band.q * 10 - 1).toInt()
        tvGainLabel.text = "Gain (dB): ${band.gain.toInt()}"
        tvQLabel.text = "Q Factor: %.1f".format(band.q)

        val filterTypes = AudioEngine.FilterType.values().map { it.getDisplayName() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterType.adapter = adapter
        spinnerFilterType.setSelection(band.filterType.ordinal)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Filter")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val frequency = etFrequency.text.toString().toFloatOrNull()
                if (frequency == null || frequency < 20f || frequency > 20000f) {
                    Toast.makeText(requireContext(), "Invalid frequency (20-20000 Hz)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val gain = (seekBarGain.progress - 20).toFloat()
                val q = (seekBarQ.progress + 1) / 10.0f
                val filterType = AudioEngine.FilterType.values()[spinnerFilterType.selectedItemPosition]

                audioEngine.setEQBand(bandIndex, frequency, gain, q, filterType)
                updateFilterDisplay()
                markAsCustomPreset()
            }
            .setNegativeButton("Cancel", null)
            .create()

        seekBarGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvGainLabel.text = "Gain (dB): ${progress - 20}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val q = (progress + 1) / 10.0f
                tvQLabel.text = "Q Factor: %.1f".format(q)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.show()
    }
}
