package com.accessibilitybridge

import android.app.Application
import android.util.Log

class AccessibilityBridgeApplication : Application() {

    companion object {
        const val TAG = "AccessibilityBridge"
        const val DEFAULT_PORT = 8080
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created")
    }
}