package com.example.hearingaidapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ActivityFragment : Fragment() {

    private lateinit var presetsHistoryContainer: LinearLayout
    private lateinit var tvNoHistory: TextView
    private lateinit var noiseGraphContainer: LinearLayout
    private lateinit var tvNoiseDbLabel: TextView

    private val mainActivity: MainActivity
        get() = activity as MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mirror the Waveform and Spectrum views
        val waveform = view.findViewById<WaveformView>(R.id.activityWaveformView)
        val spectrum = view.findViewById<SpectrumView>(R.id.activitySpectrumView)
        mainActivity.registerFragmentVisualizers(waveform, spectrum)

        presetsHistoryContainer = view.findViewById(R.id.presetsHistoryContainer)
        tvNoHistory = view.findViewById(R.id.tvNoHistory)
        noiseGraphContainer = view.findViewById(R.id.noiseGraphContainer)
        tvNoiseDbLabel = view.findViewById(R.id.tvNoiseDbLabel)

        refreshAllData(view)
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the Activity tab is revisited
        view?.let { refreshAllData(it) }
    }

    private fun refreshAllData(view: View) {
        loadWeeklyUsageStats(view)
        loadEnvironmentHistory()
        loadNoiseLevelSeries()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainActivity.restoreHomeVisualizers()
    }

    private fun loadWeeklyUsageStats(view: View) {
        val prefs = requireContext().getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val usageJson = prefs.getString("usage_days", "{}")
        
        try {
            val usageObj = JSONObject(usageJson)
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            // Monday = index 0, ..., Sunday = index 6
            val days = arrayOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
            )

            val dayIds = arrayOf(
                R.id.barMon, R.id.barTue, R.id.barWed, R.id.barThu, R.id.barFri, R.id.barSat, R.id.barSun
            )
            val textIds = arrayOf(
                R.id.tvMonHours, R.id.tvTueHours, R.id.tvWedHours, R.id.tvThuHours, R.id.tvFriHours, R.id.tvSatHours, R.id.tvSunHours
            )
            val labelIds = arrayOf(
                R.id.tvMonLabel, R.id.tvTueLabel, R.id.tvWedLabel, R.id.tvThuLabel, R.id.tvFriLabel, R.id.tvSatLabel, R.id.tvSunLabel
            )

            val currentDay = calendar.get(Calendar.DAY_OF_WEEK)

            // Loop and draw actual hours used
            for (i in days.indices) {
                val dayOfWeek = days[i]
                
                // Get date string for this day in the current week
                val targetCal = Calendar.getInstance()
                targetCal.firstDayOfWeek = Calendar.MONDAY
                targetCal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                val dateStr = dateFormat.format(targetCal.time)
                
                val seconds = usageObj.optLong(dateStr, 0L)
                val liveSec = if (dayOfWeek == currentDay) mainActivity.getLiveSessionSeconds() else 0L
                val totalSec = seconds + liveSec
                val hours = totalSec / 3600f
                
                // Maximum scale: 12 hours
                val maxHours = 12f
                val heightPercent = (hours / maxHours).coerceIn(0.05f, 1.0f) // minimum 5% height for visual
                
                val barView = view.findViewById<View>(dayIds[i])
                val textHours = view.findViewById<TextView>(textIds[i])
                val labelDay = view.findViewById<TextView>(labelIds[i])

                // Set hours text
                textHours.text = "%.1fh".format(hours)
                
                // Update bar height dynamically
                val params = barView.layoutParams as LinearLayout.LayoutParams
                params.height = (100 * heightPercent * resources.displayMetrics.density).toInt()
                barView.layoutParams = params

                // Style active today differently
                if (dayOfWeek == currentDay) {
                    barView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_active)
                    labelDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    labelDay.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    barView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_inactive)
                }
            }
        } catch (e: Exception) {
            Log.e("ActivityFragment", "Error loading weekly usage stats: ${e.message}")
        }
    }

    private fun loadEnvironmentHistory() {
        presetsHistoryContainer.removeAllViews()
        val prefs = requireContext().getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("environment_history", "[]")

        try {
            val arr = JSONArray(historyJson)
            if (arr.length() == 0) {
                tvNoHistory.visibility = View.VISIBLE
                return
            }
            tvNoHistory.visibility = View.GONE
            val ctx = context ?: return

            // Calculate "Most Frequently Used" preset
            val counts = mutableMapOf<String, Int>()
            for (i in 0 until arr.length()) {
                val pName = arr.getJSONObject(i).optString("preset", "Standard")
                counts[pName] = (counts[pName] ?: 0) + 1
            }
            val topPreset = counts.maxByOrNull { it.value }

            if (topPreset != null) {
                val summaryCard = TextView(ctx).apply {
                    text = "Most Used: ${topPreset.key} (${topPreset.value} times)"
                    setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    textSize = 13f
                    setPadding(0, 0, 0, 16)
                }
                presetsHistoryContainer.addView(summaryCard)
            }
            
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                val presetName = entry.getString("preset")
                val timestamp = entry.getLong("timestamp")

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 16, 0, 16)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val icon = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 40)
                    setImageResource(
                        when (presetName.lowercase()) {
                            "restaurant" -> R.drawable.ic_restaurant
                            "outdoor" -> R.drawable.ic_nature_people
                            "home" -> R.drawable.ic_home
                            else -> R.drawable.ic_equalizer
                        }
                    )
                    setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
                }
                row.addView(icon)

                val details = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 24
                    }
                }

                val title = TextView(ctx).apply {
                    text = "$presetName Mode Loaded"
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    textSize = 14f
                }

                val subtitle = TextView(ctx).apply {
                    val date = Date(timestamp)
                    val sdf = SimpleDateFormat("hh:mm a - d MMM", Locale.getDefault())
                    text = sdf.format(date)
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
                    textSize = 12f
                }

                details.addView(title)
                details.addView(subtitle)
                row.addView(details)
                presetsHistoryContainer.addView(row)

                // Divider line
                if (i < arr.length() - 1) {
                    val line = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                            setMargins(0, 0, 0, 0)
                        }
                        setBackgroundColor(ContextCompat.getColor(ctx, R.color.outline_variant))
                    }
                    presetsHistoryContainer.addView(line)
                }
            }
        } catch (e: Exception) {
            Log.e("ActivityFragment", "Error loading environment history: ${e.message}")
        }
    }

    private fun loadNoiseLevelSeries() {
        noiseGraphContainer.removeAllViews()
        val prefs = requireContext().getSharedPreferences("activity_stats", Context.MODE_PRIVATE)
        val seriesJson = prefs.getString("noise_level_series", "[]")

        try {
            val arr = JSONArray(seriesJson)
            val ctx = context ?: return
            
            if (arr.length() == 0) {
                // Populate default flat timeline if empty
                tvNoiseDbLabel.text = "Now (30dB)"
                for (i in 0 until 10) {
                    addNoiseBar(30f, i == 9, ctx)
                }
                return
            }

            val lastVal = arr.optDouble(arr.length() - 1, 30.0).toFloat()
            tvNoiseDbLabel.text = "Now (${lastVal.toInt()}dB)"

            // Show last 10 points
            val displayCount = 10
            val startIdx = (arr.length() - displayCount).coerceAtLeast(0)
            
            // Draw empty filler bars if we have fewer than 10 points
            if (arr.length() < displayCount) {
                val padding = displayCount - arr.length()
                for (i in 0 until padding) {
                    addNoiseBar(30f, false, ctx)
                }
            }

            for (i in startIdx until arr.length()) {
                val db = arr.getDouble(i).toFloat()
                addNoiseBar(db, i == arr.length() - 1, ctx)
            }
        } catch (e: Exception) {
            Log.e("ActivityFragment", "Error loading noise level series: ${e.message}")
        }
    }

    private fun addNoiseBar(db: Float, isActive: Boolean, ctx: Context) {
        // Map 30dB - 100dB to heights from 20dp to 100dp
        val minDb = 30f
        val maxDb = 100f
        val factor = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val barHeightDp = 20 + factor * 80

        val bar = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, (barHeightDp * resources.displayMetrics.density).toInt(), 1f).apply {
                setMargins(4, 0, 4, 0)
            }
            background = if (isActive) {
                ContextCompat.getDrawable(ctx, R.drawable.bg_chip_active)
            } else {
                ContextCompat.getDrawable(ctx, R.drawable.bg_chip_inactive)
            }
        }
        noiseGraphContainer.addView(bar)
    }

    private var TextView.textSize: Float
        get() = paint.textSize
        set(value) {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, value)
        }
}
