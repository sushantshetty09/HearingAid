package com.example.hearingaidapp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.net.Uri
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
    }

    private lateinit var audioEngine: AudioEngine
    private lateinit var presetManager: PresetManager

    val appPresetManager: PresetManager
        get() = presetManager

    val appAudioEngine: AudioEngine
        get() = audioEngine

    private var isRunning = false
    private var pendingStart = false
    private var sessionStartTime: Long = 0L

    interface AudioStateListener {
        fun onAudioStateChanged(isRunning: Boolean)
        fun onTimerUpdated(formattedTime: String)
        fun onNoiseProfileLearnedChanged(learned: Boolean)
    }

    private val audioStateListeners = mutableListOf<AudioStateListener>()

    fun addAudioStateListener(listener: AudioStateListener) {
        audioStateListeners.add(listener)
        listener.onAudioStateChanged(isRunning)
        if (::audioEngine.isInitialized) {
            listener.onNoiseProfileLearnedChanged(audioEngine.isNoiseProfileLearned())
        }
    }

    fun removeAudioStateListener(listener: AudioStateListener) {
        audioStateListeners.remove(listener)
    }

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val elapsed = System.currentTimeMillis() - sessionStartTime
                val hours = (elapsed / 3600000).toInt()
                val minutes = ((elapsed % 3600000) / 60000).toInt()
                val seconds = ((elapsed % 60000) / 1000).toInt()
                val formatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                audioStateListeners.forEach { it.onTimerUpdated(formatted) }
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    // Noise level sampling handler (once per minute while NC is active)
    private val noiseSampleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val noiseSampleRunnable = object : Runnable {
        override fun run() {
            if (isRunning && ::audioEngine.isInitialized && audioEngine.isNoiseCancelingEnabled()) {
                val db = audioEngine.getAverageNoiseLevelDb()
                appendNoiseLevelSample(db)
            }
            noiseSampleHandler.postDelayed(this, 60_000) // every 60 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore saved theme BEFORE setContentView to avoid flash of wrong theme
        val settingsPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val savedTheme = settingsPrefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pass context to AudioEngine for low-latency optimization
        audioEngine = AudioEngine(this)
        audioEngine.setFeedbackEventListener(object : AudioEngine.FeedbackEventListener {
            override fun onFeedbackDetected(frequencyHz: Float) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Feedback detected (${frequencyHz.toInt()} Hz) — try repositioning your earbuds or lowering volume.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        // Initialize preset manager
        presetManager = PresetManager(this)
        presetManager.initializeDefaultPresets()

        // Restore user's personal hearing profile if saved
        val profilePrefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        if (profilePrefs.getBoolean("has_saved_hearing_profile", false)) {
            val personalPresetName = profilePrefs.getString("personal_profile_preset_name", null)
            if (personalPresetName != null) {
                val personalPreset = presetManager.loadPreset(personalPresetName)
                if (personalPreset != null) {
                    audioEngine.applyPreset(personalPreset)
                    presetManager.setCurrentPreset(personalPreset.name)
                }
            }
        }

        setupNavigation()
        setupWindowInsets()
        checkNoiseProfileStatus()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setupWithNavController(navController)
    }

    private fun setupWindowInsets() {
        val topAppBarContainer = findViewById<View>(R.id.topAppBarContainer)
        ViewCompat.setOnApplyWindowInsetsListener(topAppBarContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
    }

    private fun checkNoiseProfileStatus() {
        val handler = android.os.Handler(mainLooper)
        handler.post(object : Runnable {
            override fun run() {
                if (::audioEngine.isInitialized) {
                    val learned = audioEngine.isNoiseProfileLearned()
                    audioStateListeners.forEach { it.onNoiseProfileLearnedChanged(learned) }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    fun isHearingAidRunning(): Boolean = isRunning

    fun toggleHearingAid() {
        try {
            if (hasPermissions()) {
                if (!isRunning) {
                    startHearingAid()
                } else {
                    stopHearingAid()
                }
            } else {
                requestPermissionsWithRationale()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHearingAid() {
        try {
            audioEngine.startAudioProcessing()
            isRunning = true
            sessionStartTime = System.currentTimeMillis()
            timerHandler.post(timerRunnable)
            noiseSampleHandler.postDelayed(noiseSampleRunnable, 60_000) // start noise sampling
            audioStateListeners.forEach { it.onAudioStateChanged(true) }
            Toast.makeText(this, "Hearing aid started", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied. Cannot start.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopHearingAid() {
        try {
            // Accumulate session usage before stopping
            accumulateSessionUsage()
            audioEngine.stopAudioProcessing()
            isRunning = false
            timerHandler.removeCallbacks(timerRunnable)
            noiseSampleHandler.removeCallbacks(noiseSampleRunnable)
            audioStateListeners.forEach { it.onAudioStateChanged(false) }
            Toast.makeText(this, "Hearing aid stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================================================
    // Activity Tab Data Sources — Session Tracking, Noise Sampling, Preset Logging
    // ========================================================================

    private fun accumulateSessionUsage() {
        if (sessionStartTime <= 0L) return
        val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000L // seconds
        if (elapsed <= 0) return

        val prefs = getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val usageJson = prefs.getString("usage_days", "{}")
        val usageObj = try { JSONObject(usageJson) } catch (e: Exception) { JSONObject() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prev = usageObj.optLong(dateStr, 0L)
        usageObj.put(dateStr, prev + elapsed)
        prefs.edit().putString("usage_days", usageObj.toString()).apply()

        sessionStartTime = System.currentTimeMillis() // reset for next accumulation
    }

    /** Returns the live seconds for the current session (not yet saved). */
    fun getLiveSessionSeconds(): Long {
        return if (isRunning && sessionStartTime > 0L) {
            (System.currentTimeMillis() - sessionStartTime) / 1000L
        } else 0L
    }

    private fun appendNoiseLevelSample(db: Float) {
        val prefs = getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val seriesJson = prefs.getString("noise_level_series", "[]")
        val arr = try { JSONArray(seriesJson) } catch (e: Exception) { JSONArray() }
        arr.put(db.toDouble())

        // Cap at 500 most recent points
        while (arr.length() > 500) arr.remove(0)
        prefs.edit().putString("noise_level_series", arr.toString()).apply()
    }

    fun logPresetLoadEvent(presetName: String) {
        val prefs = getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("environment_history", "[]")
        val arr = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }

        val entry = JSONObject()
        entry.put("preset", presetName)
        entry.put("timestamp", System.currentTimeMillis())
        arr.put(entry)

        // Cap at 200 most recent entries
        while (arr.length() > 200) arr.remove(0)
        prefs.edit().putString("environment_history", arr.toString()).apply()
    }

    fun registerFragmentVisualizers(waveform: WaveformView?, spectrum: SpectrumView?) {
        if (::audioEngine.isInitialized) {
            audioEngine.setVisualizerViews(waveform, spectrum)
        }
    }

    fun restoreHomeVisualizers() {
        // Visualizers will be restored by the active fragment in its onResume
    }

    fun loadPresetAndNavigateToHome(presetName: String) {
        val preset = presetManager.loadPreset(presetName)
        if (preset != null) {
            audioEngine.applyPreset(preset)
            presetManager.setCurrentPreset(preset.name)

            // Log preset load event for Activity tab environment history
            logPresetLoadEvent(preset.name)
            
            // Switch navigation to Home tab
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
            bottomNav.selectedItemId = R.id.nav_control
            
            Toast.makeText(this, "Loaded: ${preset.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsWithRationale() {
        when {
            hasPermissions() -> {
                toggleHearingAid()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestPermissions()
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Microphone Permission Required")
            .setMessage("This hearing aid app needs microphone access to amplify sounds for you. Please grant permission to continue.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permission required for functionality", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        pendingStart = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                if (pendingStart) {
                    pendingStart = false
                    toggleHearingAid()
                }
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_LONG).show()
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    showSettingsDialog()
                }
            }
        }
        pendingStart = false
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Microphone permission is required for the hearing aid to work. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun isBluetoothAudioConnected(): Boolean {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return false
            if (!adapter.isEnabled) return false
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        } catch (e: Exception) { false }
    }

    override fun onDestroy() {
        // Save partial session before destroying
        if (isRunning) {
            accumulateSessionUsage()
        }
        noiseSampleHandler.removeCallbacks(noiseSampleRunnable)
        super.onDestroy()
        if (::audioEngine.isInitialized && isRunning) {
            audioEngine.stopAudioProcessing()
        }
    }
}
