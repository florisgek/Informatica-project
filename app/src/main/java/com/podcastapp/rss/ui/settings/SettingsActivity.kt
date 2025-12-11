package com.podcastapp.rss.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.podcastapp.rss.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        setupSettings()
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // Dark mode toggle
        binding.switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Auto-play next episode
        binding.switchAutoPlay.isChecked = prefs.getBoolean("auto_play", true)
        binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_play", isChecked).apply()
        }

        // Skip silence
        binding.switchSkipSilence.isChecked = prefs.getBoolean("skip_silence", false)
        binding.switchSkipSilence.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("skip_silence", isChecked).apply()
        }

        // Stream on WiFi only
        binding.switchWifiOnly.isChecked = prefs.getBoolean("wifi_only", false)
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_only", isChecked).apply()
        }

        // Default playback speed
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val currentSpeed = prefs.getFloat("playback_speed", 1.0f)
        val speedIndex = when (currentSpeed) {
            0.5f -> 0
            0.75f -> 1
            1.25f -> 3
            1.5f -> 4
            1.75f -> 5
            2.0f -> 6
            else -> 2
        }
        binding.tvSpeedValue.text = speeds[speedIndex]

        binding.layoutSpeed.setOnClickListener {
            val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Default Playback Speed")
                .setItems(speeds) { _, which ->
                    prefs.edit().putFloat("playback_speed", speedValues[which]).apply()
                    binding.tvSpeedValue.text = speeds[which]
                }
                .show()
        }

        // Skip intro duration
        val skipIntro = prefs.getInt("skip_intro", 0)
        binding.tvSkipIntroValue.text = if (skipIntro > 0) "${skipIntro}s" else "Off"

        binding.layoutSkipIntro.setOnClickListener {
            val options = arrayOf("Off", "10s", "15s", "30s", "45s", "60s")
            val values = intArrayOf(0, 10, 15, 30, 45, 60)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Skip Intro")
                .setItems(options) { _, which ->
                    prefs.edit().putInt("skip_intro", values[which]).apply()
                    binding.tvSkipIntroValue.text = if (values[which] > 0) "${values[which]}s" else "Off"
                }
                .show()
        }

        // About section
        binding.tvVersion.text = "Version 1.0.0"

        binding.layoutClearCache.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("This will clear all cached podcast data. Your subscriptions will be kept.")
                .setPositiveButton("Clear") { _, _ ->
                    // Clear cache logic here
                    android.widget.Toast.makeText(this, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
