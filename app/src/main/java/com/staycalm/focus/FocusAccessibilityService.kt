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
        if (pkg.isBlank()) return
        // Skip all package processing outside user-started unfinished focus sessions.
        val store = FocusStore(this)
        val state = store.state()
        if (state.mode != "focus" || state.sessionId.isEmpty() ||
            !store.blockDistractingApps()) return
        // Our shield is itself a LinearLayout. Only a real MainActivity event
        // may clear it; never treat a shield event as a safe destination.
        if (pkg == packageName && e.className?.toString() != MainActivity::class.java.name)
            return
        // SystemUI transitions inform the watcher to keep/rebuild the shield.
        TimerService.reportForeground(this, pkg)
    }

    override fun onInterrupt() = Unit
}
