package com.accessibilitybridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class AccessibilityBridgeService : AccessibilityService() {

    companion object {
        const val TAG = "AccessibilityBridgeService"
        var instance: AccessibilityBridgeService? = null
            private set
        private const val PORT = 8080
    }

    private var httpServer: LocalHttpServer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var latestUiTree: String = "{}"
    private var lastTreeUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Service created")
        startHttpServer()
    }

    override fun onDestroy() {
        instance = null
        httpServer?.stop()
        httpServer = null
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            refreshUiTree()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        httpServer?.stop()
        httpServer = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.capabilities = info.capabilities or AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION
        }
        setServiceInfo(info)
        refreshUiTree()
    }

    fun refreshUiTree() {
        handler.post {
            val root = rootInActiveWindow
            if (root != null) {
                latestUiTree = UiTreeSerializer.serialize(root)
                lastTreeUpdate = System.currentTimeMillis()
                root.recycle()
            }
        }
    }

    fun getLatestUiTree(): String = latestUiTree
    fun getLastTreeUpdate(): Long = lastTreeUpdate

    fun performTap(nodeId: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val result = findAndPerformAction(root, nodeId, AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()
        return result
    }

    fun performTapByCoordinates(x: Float, y: Float): Boolean {
        return GestureExecutor.performTap(this, x, y)
    }

    fun performText(nodeId: Int, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, nodeId)
        var result = false
        if (node != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text) })
            } else {
                val args = Bundle()
                args.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text)
                result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            node.recycle()
        }
        root.recycle()
        return result
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Int = 300): Boolean {
        return GestureExecutor.performSwipe(this, startX, startY, endX, endY, duration)
    }

    private fun findAndPerformAction(root: AccessibilityNodeInfo, targetId: Int, action: Int): Boolean {
        if (root.hashCode() == targetId) {
            return root.performAction(action)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                if (findAndPerformAction(child, targetId, action)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    private fun findNodeById(root: AccessibilityNodeInfo, targetId: Int): AccessibilityNodeInfo? {
        if (root.hashCode() == targetId) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                val found = findNodeById(child, targetId)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    private fun startHttpServer() {
        httpServer = LocalHttpServer(PORT, this)
        httpServer?.start()
    }
}

