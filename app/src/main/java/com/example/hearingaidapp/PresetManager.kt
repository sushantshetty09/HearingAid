package com.example.hearingaidapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException

/**
 * Manages saving, loading, and deleting EQ presets
 * Uses SharedPreferences for persistent storage, offloaded to Dispatchers.IO
 */
class PresetManager(context: Context) {
    companion object {
        private const val TAG = "PresetManager"
        private const val PREFS_NAME = "eq_presets"
        private const val KEY_PRESET_LIST = "preset_list"
        private const val KEY_CURRENT_PRESET = "current_preset"
    }

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save a preset to storage
     * @return true if save was successful
     */
    fun savePreset(preset: EQPreset): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val presets = getAllPresetsInternal().toMutableList()
            
            // Remove existing preset with the same name
            presets.removeAll { it.name == preset.name }
            
            // Add the new/updated preset
            presets.add(preset)
            
            // Save to SharedPreferences
            saveAllPresetsInternal(presets)
            
            Log.d(TAG, "Preset '${preset.name}' saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving preset: ${e.message}", e)
            false
        }
    }

    /**
     * Load a preset by name
     * @return the preset or null if not found
     */
    fun loadPreset(presetName: String): EQPreset? = runBlocking(Dispatchers.IO) {
        try {
            getAllPresetsInternal().firstOrNull { it.name == presetName }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preset: ${e.message}", e)
            null
        }
    }

    /**
     * Delete a preset by name
     * @return true if deletion was successful
     */
    fun deletePreset(presetName: String): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val presets = getAllPresetsInternal().toMutableList()
            val removed = presets.removeAll { it.name == presetName }
            
            if (removed) {
                saveAllPresetsInternal(presets)
                Log.d(TAG, "Preset '$presetName' deleted successfully")
            }
            
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting preset: ${e.message}", e)
            false
        }
    }

    /**
     * Get all saved presets
     */
    fun getAllPresets(): List<EQPreset> = runBlocking(Dispatchers.IO) {
        getAllPresetsInternal()
    }

    private fun getAllPresetsInternal(): List<EQPreset> {
        return try {
            val presetsJson = sharedPreferences.getString(KEY_PRESET_LIST, null)
            
            if (presetsJson.isNullOrEmpty()) {
                return emptyList()
            }

            val jsonArray = JSONArray(presetsJson)
            val presets = mutableListOf<EQPreset>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val presetJson = jsonArray.getString(i)
                    presets.add(EQPreset.fromJSON(presetJson))
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing preset at index $i: ${e.message}")
                }
            }
            
            presets
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all presets: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get preset names sorted alphabetically
     */
    fun getPresetNames(): List<String> = runBlocking(Dispatchers.IO) {
        getAllPresetsInternal().map { it.name }.sorted()
    }

    /**
     * Check if a preset with the given name exists
     */
    fun presetExists(presetName: String): Boolean = runBlocking(Dispatchers.IO) {
        getAllPresetsInternal().any { it.name == presetName }
    }

    /**
     * Rename a preset
     * @return true if rename was successful
     */
    fun renamePreset(oldName: String, newName: String): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val preset = getAllPresetsInternal().firstOrNull { it.name == oldName } ?: return@runBlocking false
            
            if (getAllPresetsInternal().any { it.name == newName } && oldName != newName) {
                Log.w(TAG, "Preset with name '$newName' already exists")
                return@runBlocking false
            }
            
            val presets = getAllPresetsInternal().toMutableList()
            presets.removeAll { it.name == oldName }
            saveAllPresetsInternal(presets)
            
            val renamedPreset = preset.copy(name = newName)
            
            // Re-save under new name
            val updatedPresets = getAllPresetsInternal().toMutableList()
            updatedPresets.removeAll { it.name == renamedPreset.name }
            updatedPresets.add(renamedPreset)
            saveAllPresetsInternal(updatedPresets)
            
            // Update current preset if it was renamed
            if (getCurrentPresetNameInternal() == oldName) {
                setCurrentPresetInternal(newName)
            }
            
            Log.d(TAG, "Preset renamed from '$oldName' to '$newName'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming preset: ${e.message}", e)
            false
        }
    }

    /**
     * Set the currently active preset name
     */
    fun setCurrentPreset(presetName: String?) = runBlocking(Dispatchers.IO) {
        setCurrentPresetInternal(presetName)
    }

    private fun setCurrentPresetInternal(presetName: String?) {
        sharedPreferences.edit()
            .putString(KEY_CURRENT_PRESET, presetName)
            .apply()
    }

    /**
     * Get the currently active preset name
     */
    fun getCurrentPresetName(): String? = runBlocking(Dispatchers.IO) {
        getCurrentPresetNameInternal()
    }

    private fun getCurrentPresetNameInternal(): String? {
        return sharedPreferences.getString(KEY_CURRENT_PRESET, null)
    }

    /**
     * Get the currently active preset
     */
    fun getCurrentPreset(): EQPreset? = runBlocking(Dispatchers.IO) {
        val presetName = getCurrentPresetNameInternal() ?: return@runBlocking null
        getAllPresetsInternal().firstOrNull { it.name == presetName }
    }

    /**
     * Initialize with default presets if no presets exist
     */
    fun initializeDefaultPresets() = runBlocking(Dispatchers.IO) {
        if (getAllPresetsInternal().isEmpty()) {
            Log.d(TAG, "No presets found, initializing with defaults")
            val defaults = EQPreset.getDefaultPresets()
            val presets = getAllPresetsInternal().toMutableList()
            defaults.forEach { preset ->
                presets.removeAll { it.name == preset.name }
                presets.add(preset)
            }
            saveAllPresetsInternal(presets)
        }
    }

    /**
     * Clear all presets (for testing or reset functionality)
     */
    fun clearAllPresets() = runBlocking(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(KEY_PRESET_LIST)
            .remove(KEY_CURRENT_PRESET)
            .apply()
        Log.d(TAG, "All presets cleared")
    }

    /**
     * Export all presets as a JSON string (for backup)
     */
    fun exportPresets(): String? = runBlocking(Dispatchers.IO) {
        sharedPreferences.getString(KEY_PRESET_LIST, null)
    }

    /**
     * Import presets from a JSON string (for restore)
     * @return number of presets imported
     */
    fun importPresets(presetsJson: String, merge: Boolean = false): Int = runBlocking(Dispatchers.IO) {
        return@runBlocking try {
            val jsonArray = JSONArray(presetsJson)
            val newPresets = mutableListOf<EQPreset>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val presetJson = jsonArray.getString(i)
                    newPresets.add(EQPreset.fromJSON(presetJson))
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing preset at index $i during import: ${e.message}")
                }
            }

            val finalPresets = if (merge) {
                val existing = getAllPresetsInternal().toMutableList()
                // Remove duplicates by name
                existing.removeAll { existingPreset ->
                    newPresets.any { it.name == existingPreset.name }
                }
                existing + newPresets
            } else {
                newPresets
            }

            saveAllPresetsInternal(finalPresets)
            Log.d(TAG, "Imported ${newPresets.size} presets (merge: $merge)")
            
            newPresets.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing presets: ${e.message}", e)
            0
        }
    }

    /**
     * Save all presets to SharedPreferences
     */
    private fun saveAllPresetsInternal(presets: List<EQPreset>) {
        val jsonArray = JSONArray()
        presets.forEach { preset ->
            jsonArray.put(preset.toJSON())
        }
        
        sharedPreferences.edit()
            .putString(KEY_PRESET_LIST, jsonArray.toString())
            .apply()
    }
}
