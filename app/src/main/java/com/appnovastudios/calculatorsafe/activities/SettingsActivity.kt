package com.example.calculatorsafe.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.example.calculatorsafe.BuildConfig
import com.example.calculatorsafe.R
import com.appnovastudios.calculatorsafe.helpers.PreferenceHelper

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        val appVersionTextView = findViewById<TextView>(R.id.app_version_tv)
        val deleteOriginalFilesSwitch = findViewById<SwitchCompat>(R.id.setting_option_1_toggle)
        val appVersionName = BuildConfig.VERSION_NAME
        appVersionTextView.text = "Version: $appVersionName"

        deleteOriginalFilesSwitch.isChecked = PreferenceHelper.getDeleteOriginal(this)
        deleteOriginalFilesSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setDeleteOriginal(this, isChecked)
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}