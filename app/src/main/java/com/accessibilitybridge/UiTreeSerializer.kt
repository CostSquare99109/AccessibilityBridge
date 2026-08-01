package com.accessibilitybridge

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

object UiTreeSerializer {

    private const val TAG = "UiTreeSerializer"

    fun serialize(root: AccessibilityNodeInfo): String {
        try {
            val json = serializeNode(root, 0)
            return json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Serialization error", e)
            return JSONObject().put("error", e.message).toString()
        }
    }

    private fun serializeNode(node: AccessibilityNodeInfo, depth: Int): JSONObject {
        val json = JSONObject()

        try {
            json.put("id", node.hashCode())
            json.put("className", node.className?.toString() ?: "")
            json.put("packageName", node.packageName?.toString() ?: "")
            json.put("text", node.text?.toString() ?: "")
            json.put("contentDescription", node.contentDescription?.toString() ?: "")
            json.put("viewIdResourceName", node.viewIdResourceName?.toString() ?: "")
            json.put("clickable", node.isClickable)
            json.put("enabled", node.isEnabled)
            json.put("focusable", node.isFocusable)
            json.put("editable", node.isEditable)
            json.put("selected", node.isSelected)
            json.put("checked", node.isChecked)
            json.put("scrollable", node.isScrollable)
            json.put("longClickable", node.isLongClickable)
            json.put("focused", node.isFocused)
            json.put("visibleToUser", node.isVisibleToUser)

            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            json.put("bounds", JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom))
            json.put("center", JSONArray().put((bounds.left + bounds.right) / 2).put((bounds.top + bounds.bottom) / 2))

            val actions = JSONArray()
            // getActionList() returns List<AccessibilityAction> in newer API
            val actionList = node.actionList ?: emptyList()
            for (action in actionList) {
                actions.put(getActionName(action.id))
            }
            json.put("actions", actions)

            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    children.put(serializeNode(child, depth + 1))
                    child.recycle()
                }
            }
            json.put("children", children)

        } catch (e: Exception) {
            Log.e(TAG, "Node serialization error", e)
        }

        return json
    }

    private fun getActionName(action: Int): String {
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> "click"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "long_click"
            AccessibilityNodeInfo.ACTION_FOCUS -> "focus"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "scroll_forward"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "scroll_backward"
            AccessibilityNodeInfo.ACTION_COPY -> "copy"
            AccessibilityNodeInfo.ACTION_PASTE -> "paste"
            AccessibilityNodeInfo.ACTION_CUT -> "cut"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "set_text"
            AccessibilityNodeInfo.ACTION_SELECT -> "select"
            AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "clear_selection"
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "next_granularity"
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "previous_granularity"
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> "next_html_element"
            AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT -> "previous_html_element"
            // These constants may not exist in older API levels, use fallback
            else -> {
                // Try to match by known constant values
                when (action) {
                    0x00010000 -> "show_on_screen"     // ACTION_SHOW_ON_SCREEN
                    0x00020000 -> "context_click"       // ACTION_CONTEXT_CLICK
                    0x00040000 -> "set_progress"        // ACTION_SET_PROGRESS
                    0x00080000 -> "move_window"         // ACTION_MOVE_WINDOW
                    0x00100000 -> "dismiss"             // ACTION_DISMISS
                    else -> "action_$action"
                }
            }
        }
    }
}