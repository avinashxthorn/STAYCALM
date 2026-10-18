package com.staycalm.focus

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Single-purpose, user-enabled AccessibilityService: watches window changes from three
 * user-requested distracting apps and navigates Home during an unfinished focus session.
 * No UI nodes, window text or user data are accessed.
 */
class FocusAppBlocker : AccessibilityService() {
    private var lastRedirectElapsed = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val appName = FocusBlockPolicy.appName(packageName) ?: return
        val store = FocusStore(this)
        // Finish overdue sessions before deciding; exact alarms may be delayed by Android.
        store.completeIfDue()
        if (!FocusBlockPolicy.shouldBlock(store.state(), store.blockDistractingApps(), packageName)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectElapsed < 750L) return
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            lastRedirectElapsed = now
            Toast.makeText(applicationContext, "$appName is blocked until your focus session ends.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        fun isEnabled(context: Context): Boolean {
            val ours = ComponentName(context, FocusAppBlocker::class.java)
            return runCatching {
                val enabled = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return@runCatching false
                enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
            }.getOrDefault(false)
        }
    }
}

/** Pure rules used by the service and tested without a device. */
internal object FocusBlockPolicy {
    private val blockedApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite",
        "com.google.android.youtube" to "YouTube"
    )

    fun appName(packageName: String): String? = blockedApps[packageName]

    fun shouldBlock(state: FocusState, enabled: Boolean, packageName: String): Boolean =
        enabled && state.mode == "focus" && state.sessionId.isNotEmpty() && appName(packageName) != null
}
