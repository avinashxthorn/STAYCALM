package com.staycalm.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Optional, simple Instagram-only overlay. Strict Accessibility and Recents
 * interception were removed. YouTube remains accessible during focus sessions.
 * No screen content is read or stored, and all permissions are user-controlled.
 */
object FocusAppBlocker {
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun hasOverlayAccess(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isEnabled(context: Context): Boolean = hasUsageAccess(context) && hasOverlayAccess(context)

    fun keepAlive(context: Context, state: FocusState): Boolean =
        state.mode == "focus" && state.sessionId.isNotEmpty() &&
            FocusStore(context).blockDistractingApps() && isEnabled(context)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
}

/** YouTube is intentionally allowed. Instagram blocking is entirely optional. */
internal object FocusBlockPolicy {
    private val blockedApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite"
    )

    fun appName(packageName: String): String? = blockedApps[packageName]

    fun shouldBlock(state: FocusState, enabled: Boolean, packageName: String): Boolean =
        enabled && state.mode == "focus" && state.sessionId.isNotEmpty() &&
            appName(packageName) != null
}

/**
 * Standard Usage Access polling, without Accessibility or gesture interception.
 * Android may temporarily hide ordinary overlays in the Recents system UI.
 */
internal class FocusAppWatcher(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    private var foregroundPackage: String? = null
    private var lastScanWall = 0L
    private var overlay: LinearLayout? = null
    private var shownApp: String? = null

    fun tick(state: FocusState, enabled: Boolean) {
        if (!enabled || !FocusAppBlocker.isEnabled(context) ||
            state.mode != "focus" || state.sessionId.isEmpty()) {
            hide()
            foregroundPackage = null
            return
        }
        val now = System.currentTimeMillis()
        val from = if (lastScanWall == 0L || lastScanWall > now) now - 15_000L else lastScanWall - 1_000L
        lastScanWall = now
        runCatching {
            val events = usage.queryEvents(from.coerceAtLeast(0L), now)
            val event = UsageEvents.Event()
            var chosenTime = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if ((event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) &&
                    event.timeStamp >= chosenTime) {
                    chosenTime = event.timeStamp
                    foregroundPackage = event.packageName
                }
            }
        }
        val name = foregroundPackage?.let { FocusBlockPolicy.appName(it) }
        if (name == null) hide() else show(name)
    }

    private fun show(name: String) {
        if (overlay != null && shownApp == name) return
        hide()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(52, 40, 52, 40)
            setBackgroundColor(Color.rgb(255, 246, 250))
        }
        val flower = TextView(context).apply {
            text = "✿"
            textSize = 56f
            setTextColor(Color.rgb(172, 52, 95))
            gravity = Gravity.CENTER
        }
        val title = TextView(context).apply {
            text = "Stay with your focus"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(84, 37, 54))
        }
        val info = TextView(context).apply {
            text = "$name is unavailable during your unfinished focus session. Return to STAYCALM to finish, pause or use an allowed End session."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(136, 97, 111))
            setPadding(0, 30, 0, 40)
        }
        val button = Button(context).apply {
            text = "Open STAYCALM"
            setOnClickListener {
                foregroundPackage = context.packageName
                hide()
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }
            }
        }
        layout.addView(flower)
        layout.addView(title)
        layout.addView(info)
        layout.addView(button)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }
        try {
            windows.addView(layout, params)
            overlay = layout
            shownApp = name
        } catch (_: Exception) {
            shownApp = null
        }
    }

    fun hide() {
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
        shownApp = null
    }
}
