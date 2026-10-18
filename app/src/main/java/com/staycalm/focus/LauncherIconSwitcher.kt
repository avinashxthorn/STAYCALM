package com.staycalm.focus

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Android launchers only support runtime icon switching through enabled activity aliases. */
object LauncherIconSwitcher {
    private const val LIGHT = "com.staycalm.focus.LightLauncher"
    private const val DARK = "com.staycalm.focus.DarkLauncher"

    fun apply(context: Context, darkMode: Boolean) {
        val pm = context.packageManager
        val selected = ComponentName(context, if (darkMode) DARK else LIGHT)
        val inactive = ComponentName(context, if (darkMode) LIGHT else DARK)
        // Enable the destination before disabling the previous alias to avoid a launcher gap.
        if (pm.getComponentEnabledSetting(selected) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(
                selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        }
        if (pm.getComponentEnabledSetting(inactive) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(
                inactive, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }
}
