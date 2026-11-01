package com.example.hearingaidapp

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileOutputStream

class ProfilesFragment : Fragment() {

    private lateinit var ivProfilePic: ImageView
    private lateinit var btnProfilePic: View
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileAge: TextView
    private lateinit var btnEditProfile: ImageButton
    private lateinit var presetsListContainer: LinearLayout
    private lateinit var tvNoSavedPresets: TextView

    private lateinit var prefs: SharedPreferences

    private val mainActivity: MainActivity
        get() = activity as MainActivity

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
        setupListeners()
        loadPresetsList()
    }

    override fun onResume() {
        super.onResume()
        loadPresetsList()
    }

    private fun initializeViews(view: View) {
        ivProfilePic = view.findViewById(R.id.ivProfilePic)
        btnProfilePic = view.findViewById(R.id.btnProfilePic)
        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvProfileAge = view.findViewById(R.id.tvProfileAge)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        presetsListContainer = view.findViewById(R.id.presetsListContainer)
        tvNoSavedPresets = view.findViewById(R.id.tvNoSavedPresets)
    }

    private fun loadProfileData() {
        val name = prefs.getString("profile_name", "Your Name") ?: "Your Name"
        val age = prefs.getString("profile_age", "Age: —") ?: "Age: —"
        val imgPath = prefs.getString("profile_image_path", "")

        tvProfileName.text = name
        tvProfileAge.text = age

        if (!imgPath.isNullOrEmpty()) {
            val file = File(imgPath)
            if (file.exists()) {
                ivProfilePic.setImageURI(Uri.fromFile(file))
                ivProfilePic.clearColorFilter()
            }
        }
    }

    private fun setupListeners() {
        btnProfilePic.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
    }

    private fun showEditProfileDialog() {
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileName)
        val etAge = dialogView.findViewById<TextInputEditText>(R.id.etEditProfileAge)

        etName?.setText(prefs.getString("profile_name", ""))
        etAge?.setText(prefs.getString("profile_age_raw", ""))

        AlertDialog.Builder(ctx)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName?.text?.toString()?.trim() ?: ""
                val newAgeRaw = etAge?.text?.toString()?.trim() ?: ""

                if (newName.isNotEmpty()) {
                    prefs.edit()
                        .putString("profile_name", newName)
                        .putString("profile_age", if (newAgeRaw.isNotEmpty()) "Age: $newAgeRaw" else "Age: —")
                        .putString("profile_age_raw", newAgeRaw)
                        .apply()
                    loadProfileData()
                    Toast.makeText(ctx, "Profile updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleProfileImagePicked(uri: Uri) {
        try {
            val ctx = requireContext()
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val file = File(ctx.filesDir, "profile_pic.jpg")
            FileOutputStream(file).use { out ->
                inputStream.copyTo(out)
            }
            prefs.edit().putString("profile_image_path", file.absolutePath).apply()
            ivProfilePic.setImageURI(Uri.fromFile(file))
            ivProfilePic.clearColorFilter()
            Toast.makeText(ctx, "Profile picture updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to update profile image", Toast.LENGTH_SHORT).show()
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
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                }

                val detailText = TextView(ctx).apply {
                    text = "${preset.filters.size} active filters"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
                }

                infoLayout.addView(nameText)
                infoLayout.addView(detailText)
                card.addView(infoLayout)

                val btnLoad = Button(ctx).apply {
                    text = "Load"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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
}
