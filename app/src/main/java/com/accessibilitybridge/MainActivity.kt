package com.accessibilitybridge

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        settingsButton = findViewById(R.id.settingsButton)

        updateStatus()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val service = AccessibilityBridgeService.instance
        if (service != null) {
            statusText.text = getString(R.string.service_running)
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            enableButton.isEnabled = false
            enableButton.text = "Servicio activo"
        } else {
            statusText.text = getString(R.string.service_stopped)
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            enableButton.isEnabled = true
            enableButton.text = getString(R.string.enable_accessibility)
        }
    }

    private fun setupButtons() {
        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }
        settingsButton.setOnClickListener {
            openAppSettings()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening accessibility settings", e)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening app settings", e)
        }
    }
}