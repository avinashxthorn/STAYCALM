package com.staycalm.focus

import android.accessibilityservice.AccessibilityServiceInfo
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
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Focus blocking is opt-in. Usage Access and an application overlay provide a fallback.
 * The separately consented Accessibility service supplies immediate package-only window
 * changes, including returning to an already-open task through Android Recents.
 *
 * No window content, text, screen captures, gestures, or activity history is collected.
 * Package names are processed only in memory. Android system settings remain accessible.
 */
object FocusAppBlocker {
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun hasOverlayAccess(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasAccessibilityAccess(context: Context): Boolean = runCatching {
        val name = FocusAccessibilityService::class.java.name
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == name }
    }.getOrDefault(false)

    // Every permission must be user granted before starting a NEW blocked session.
    fun isEnabled(context: Context): Boolean =
        hasUsageAccess(context) && hasOverlayAccess(context) && hasAccessibilityAccess(context)

    // Existing sessions keep their usage/overlay fallback if Accessibility is revoked.
    fun keepAlive(context: Context, state: FocusState): Boolean =
        state.mode == "focus" && state.sessionId.isNotEmpty() &&
            FocusStore(context).blockDistractingApps() &&
            hasUsageAccess(context) && hasOverlayAccess(context)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

/** Blocking applies ONLY to the three specifically chosen apps, never Android Settings. */
internal object FocusBlockPolicy {
    private val blockedApps = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite",
        "com.google.android.youtube" to "YouTube"
    )

    fun appName(packageName: String): String? = blockedApps[packageName]

    // SystemUI/Recents are transient: a system window is not evidence the underlying
    // blocked app stopped being visible. Do not take down the shield for these events.
    fun isSystemTransition(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
        packageName == "android" ||
        packageName.contains("recents", ignoreCase = true)

    fun isLauncher(packageName: String): Boolean =
        packageName.contains("launcher", ignoreCase = true)

    fun shouldBlock(state: FocusState, enabled: Boolean, packageName: String): Boolean =
        enabled && state.mode == "focus" && state.sessionId.isNotEmpty() &&
            appName(packageName) != null
}

/** Foreground timer service owns the UI; Accessibility only reports app package names. */
internal class FocusAppWatcher(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    private var foregroundPackage: String? = null
    private var lastScanWall = 0L
    private var latestUsageEventWall = 0L
    private var lastAccessibilityElapsed = 0L
    private var pendingLauncher: String? = null
    private var pendingLauncherSince = 0L
    private var overlay: LinearLayout? = null
    private var shownApp: String? = null

    private fun eligible(state: FocusState, enabled: Boolean): Boolean =
        enabled && state.mode == "focus" && state.sessionId.isNotEmpty() &&
            FocusAppBlocker.hasOverlayAccess(context)

    /** Authoritative, instant event from the opt-in Accessibility service. */
    fun onWindowChanged(packageName: String, state: FocusState, enabled: Boolean) {
        if (!eligible(state, enabled)) {
            hide()
            return
        }
        if (packageName == context.packageName ||
            FocusBlockPolicy.isSystemTransition(packageName)) return

        lastAccessibilityElapsed = SystemClock.elapsedRealtime()
        pendingLauncher = null
        // A confirmed safe app, including Settings or STAYCALM's own Activity,
        // removes the cover. Recents and SystemUI events above never do.
        foregroundPackage = packageName
        render(state, enabled)
    }

    fun tick(state: FocusState, enabled: Boolean) {
        if (!eligible(state, enabled)) {
            hide()
            foregroundPackage = null
            return
        }
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val from = if (lastScanWall == 0L || lastScanWall > nowWall)
            nowWall - 15_000L else lastScanWall - 1_000L
        lastScanWall = nowWall
        try {
            val events = usage.queryEvents(from.coerceAtLeast(0L), nowWall)
            val event = UsageEvents.Event()
            var newestTime = latestUsageEventWall
            var newestPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if ((event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) &&
                    event.timeStamp > newestTime && pkg != context.packageName &&
                    !FocusBlockPolicy.isSystemTransition(pkg)) {
                    newestTime = event.timeStamp
                    newestPackage = pkg
                }
            }
            if (newestPackage != null) {
                latestUsageEventWall = newestTime
                // Accessibility events are more current than UsageStats around Recents.
                if (!FocusAppBlocker.hasAccessibilityAccess(context) ||
                    nowElapsed - lastAccessibilityElapsed > 2_000L) {
                    val pkg = newestPackage
                    if (FocusBlockPolicy.isLauncher(pkg) &&
                        foregroundPackage?.let{ FocusBlockPolicy.appName(it) } != null) {
                        // Do not expose the blocked task during a launcher/Recents swipe.
                        // Wait for a stable launcher or a confirmed app window.
                        pendingLauncher = pkg
                        pendingLauncherSince = nowElapsed
                    } else {
                        pendingLauncher = null
                        foregroundPackage = pkg
                    }
                }
            }
        } catch (_: SecurityException) {
            // Keep the last blocked window covered if usage access is temporarily denied.
        } catch (_: RuntimeException) {
            // Retain the previous known state until the next event/poll.
        }
        if (pendingLauncher != null &&
            nowElapsed - pendingLauncherSince >= 1_500L &&
            (!FocusAppBlocker.hasAccessibilityAccess(context) ||
                nowElapsed - lastAccessibilityElapsed > 2_000L)) {
            foregroundPackage = pendingLauncher
            pendingLauncher = null
        }
        render(state, enabled)
    }

    private fun render(state: FocusState, enabled: Boolean) {
        val name = if(eligible(state, enabled))
            foregroundPackage?.let{ FocusBlockPolicy.appName(it) } else null
        if (name == null) hide() else show(name)
    }

    private fun show(name: String) {
        if (overlay != null && shownApp == name && overlay?.isAttachedToWindow == true) return
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
