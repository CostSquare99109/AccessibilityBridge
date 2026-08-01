package com.accessibilitybridge

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityGestureEvent

object GestureExecutor {

    private const val TAG = "GestureExecutor"

    fun performTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        Path().apply { moveTo(x, y) }, 0, 50
                    ))
                    .build()
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.d(TAG, "Tap gesture completed at ($x, $y)")
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.w(TAG, "Tap gesture cancelled at ($x, $y)")
                    }
                }, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tap gesture error", e)
            false
        }
    }

    fun performSwipe(
        service: AccessibilityService,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Int = 300
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.d(TAG, "Swipe gesture completed ($startX,$startY) -> ($endX,$endY)")
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.w(TAG, "Swipe gesture cancelled")
                    }
                }, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Swipe gesture error", e)
            false
        }
    }

    fun performLongPress(service: AccessibilityService, x: Float, y: Float, duration: Int = 500): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        Path().apply { moveTo(x, y) }, 0, duration
                    ))
                    .build()
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.d(TAG, "Long press gesture completed at ($x, $y)")
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                        Log.w(TAG, "Long press gesture cancelled")
                    }
                }, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Long press gesture error", e)
            false
        }
    }
}