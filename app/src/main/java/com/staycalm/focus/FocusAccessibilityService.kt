package com.staycalm.focus

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional, explicitly consented window-change signal for Focus mode.
 * Deliberately does NOT retrieve window nodes/content, text, screenshots or gestures.
 * The foreground package name is used transiently on-device, never persisted/uploaded.
 */
class FocusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        TimerService.ensureRunning(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = e.packageName?.toString()?.trim().orEmpty()
        if (pkg.isBlank() || FocusBlockPolicy.isSystemTransition(pkg)) return
        // Our own cover is a LinearLayout. Only a real MainActivity window event
        // is allowed to clear the cover; ignore UI events caused by the overlay.
        if (pkg == packageName && e.className?.toString() != MainActivity::class.java.name)
            return
        TimerService.reportForeground(this, pkg)
    }

    override fun onInterrupt() = Unit
}
