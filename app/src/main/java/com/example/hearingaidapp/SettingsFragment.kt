package com.example.hearingaidapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var switchDarkMode: Switch
    private lateinit var switchLowLatency: Switch
    private lateinit var btnPermissionShortcut: Button
    private lateinit var btnExportData: Button
    private lateinit var btnResetData: Button

    private lateinit var prefs: SharedPreferences

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
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        initializeViews(view)
        setupThemeToggle()
        setupAudioToggle()
        setupPermissionShortcut()
        setupDataManagement()
    }

    private fun initializeViews(view: View) {
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        switchLowLatency = view.findViewById(R.id.switchLowLatency)
        btnPermissionShortcut = view.findViewById(R.id.btnPermissionShortcut)
        btnExportData = view.findViewById(R.id.btnExportData)
        btnResetData = view.findViewById(R.id.btnResetData)
    }

    private fun setupThemeToggle() {
        // Load persistent preference
        val storedTheme = prefs.getInt("dark_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (storedTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            switchDarkMode.isChecked = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        } else {
            switchDarkMode.isChecked = storedTheme == AppCompatDelegate.MODE_NIGHT_YES
        }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
            prefs.edit().putInt("dark_mode", mode).apply()
        }
    }

    private fun setupAudioToggle() {
        switchLowLatency.isChecked = audioEngine.isLowLatencyMode()
        
        switchLowLatency.setOnCheckedChangeListener { _, isChecked ->
            audioEngine.setLowLatencyMode(isChecked)
            
            val message = if (isChecked) {
                "Ultra-low latency processing enabled (recommended)."
            } else {
                "Standard latency mode engaged (reduces CPU load)."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPermissionShortcut() {
        btnPermissionShortcut.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun setupDataManagement() {
        btnExportData.setOnClickListener {
            val presetsJson = presetManager.exportPresets()
            if (!presetsJson.isNullOrEmpty()) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, presetsJson)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Export Presets Backup")
                startActivity(shareIntent)
            } else {
                Toast.makeText(context, "No custom presets found to export.", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetData.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            AlertDialog.Builder(ctx)
                .setTitle("Reset All Settings")
                .setMessage("Are you sure you want to delete all profiles, logs, presets and settings back to factory defaults?")
                .setPositiveButton("Reset") { _, _ ->
                    // Reset all SharedPreferences files
                    ctx.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                    ctx.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                    ctx.getSharedPreferences("activity_stats", Context.MODE_PRIVATE).edit().clear().apply()
                    
                    // Reset preset manager
                    presetManager.clearAllPresets()
                    presetManager.initializeDefaultPresets()
                    
                    // Reset theme
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    
                    // Restart Activity to apply all changes cleanly
                    val intent = Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    ctx.startActivity(intent)
                    Toast.makeText(ctx, "App reset completed successfully", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
