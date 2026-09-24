package com.bsd.remotecontrol.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RemoteAccessibilityService? = null
        var isEnabled = false
    }

    private val main = Handler(Looper.getMainLooper())

    /** Gestures must be dispatched from the main thread; commands arrive on a background thread. */
    private fun dispatchOnMain(gesture: GestureDescription) {
        main.post { try { dispatchGesture(gesture, null, null) } catch (_: Exception) {} }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isEnabled = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        isEnabled = false
        super.onDestroy()
    }

    // -------- TAP --------
    fun performTap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        dispatchOnMain(GestureDescription.Builder().addStroke(stroke).build())
    }

    // -------- SWIPE --------
    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 300) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchOnMain(GestureDescription.Builder().addStroke(stroke).build())
    }

    // -------- GLOBAL ACTIONS --------
    fun performBack()    { main.post { try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) {} } }
    fun performHome()    { main.post { try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Exception) {} } }
    fun performRecents() { main.post { try { performGlobalAction(GLOBAL_ACTION_RECENTS) } catch (_: Exception) {} } }

    // -------- LONG PRESS --------
    fun performLongPress(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 800)
        dispatchOnMain(GestureDescription.Builder().addStroke(stroke).build())
    }

    // -------- KEY EVENT (root less approach via AccessibilityService) --------
    fun injectKeyEvent(keyCode: Int) {
        // Accessibility does not directly inject key events, but global actions cover most cases
        when (keyCode) {
            KeyEvent.KEYCODE_BACK   -> performBack()
            KeyEvent.KEYCODE_HOME   -> performHome()
            KeyEvent.KEYCODE_APP_SWITCH -> performRecents()
            else -> {
                // Try finding focused node and setting text for text input
                val root = rootInActiveWindow ?: return
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null) {
                    val args = Bundle()
                    args.putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        focused.text?.toString() ?: "")
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }
        }
    }
}
