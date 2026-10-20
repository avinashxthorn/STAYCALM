package com.staycalm.focus

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** User-granted, legacy Device Admin protection. System Settings remain the recovery path. */
object SessionUninstallGuard {
    private const val PENDING = "session_admin_pending_until"
    private const val RELEASING = "session_admin_releasing"
    private fun prefs(context: Context) = AccountData.preferences(context)
    fun component(context: Context) = ComponentName(context, SessionAdminReceiver::class.java)
    fun active(context: Context): Boolean = runCatching {
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))
    }.getOrDefault(false)
    fun releasing(context: Context): Boolean = prefs(context).getBoolean(RELEASING, false) && active(context)
    fun ready(context: Context): Boolean = active(context) && !releasing(context)
    fun unfinished(state: FocusState): Boolean = state.running || state.sessionId.isNotEmpty()
    fun keepTimerAlive(context: Context): Boolean = active(context) && FocusStore(context).uninstallProtectionEnabled()

    fun beginActivation(context: Context): Boolean {
        if (releasing(context)) return false
        return prefs(context).edit().remove(RELEASING).putLong(PENDING, System.currentTimeMillis() + 10 * 60_000L).commit()
    }
    fun activationPending(context: Context): Boolean = prefs(context).getLong(PENDING, 0L) > System.currentTimeMillis()
    fun clearPending(context: Context) { prefs(context).edit().remove(PENDING).commit() }
    fun activationIntent(context: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Protect this session from direct uninstall, including while paused. Protection is released when the session completes or you use an allowed End session. Reward points alone do not unlock it. You can always deactivate Device Admin in Android Settings.")

    fun releaseIfIdle(context: Context) {
        if (unfinished(FocusStore(context).state()) || activationPending(context)) return
        if (active(context)) {
            // Removal is asynchronous. Refuse a new protected start until it has finished.
            prefs(context).edit().putBoolean(RELEASING, true).commit()
            runCatching {
                context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
            }.onFailure { prefs(context).edit().remove(RELEASING).commit() }
        } else prefs(context).edit().remove(RELEASING).apply()
    }
    fun released(context: Context) { prefs(context).edit().remove(RELEASING).commit() }
}

class SessionAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        val store = FocusStore(context)
        // Only a Start/Resume action followed by Android's explicit consent can start a timer.
        if (store.uninstallProtectionEnabled() && SessionUninstallGuard.activationPending(context)) {
            store.start()
        }
        SessionUninstallGuard.clearPending(context)
        SessionUninstallGuard.releaseIfIdle(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return if (SessionUninstallGuard.unfinished(FocusStore(context).state()))
            "Your session is unfinished. Deactivating this removes uninstall protection. You can finish the session or use an allowed End session in STAYCALM first."
        else null
    }

    override fun onDisabled(context: Context, intent: Intent) {
        SessionUninstallGuard.released(context)
        SessionUninstallGuard.clearPending(context)
        // Respect system-level revocation; never interfere with Settings or re-enable silently.
        TimerService.ensureRunning(context)
    }
}
