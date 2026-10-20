package com.staycalm.focus

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest

/** Each Firebase UID has its own private preferences, files and sync bookkeeping. */
object AccountData {
    fun uid(context: Context): String? = (context.applicationContext as StaycalmApplication).accountUid()
    private fun key(uid: String?): String = if (uid == null) "signed_out" else
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    fun preferences(context: Context) = preferencesFor(context, uid(context))
    fun preferencesFor(context: Context, uid: String?): SharedPreferences =
        context.getSharedPreferences("account_${key(uid)}", Context.MODE_PRIVATE)
    fun metadata(context: Context) = context.getSharedPreferences("cloud_${key(uid(context))}", Context.MODE_PRIVATE)
    fun root(context: Context) = rootFor(context, uid(context))
    fun rootFor(context: Context, uid: String?) = File(context.filesDir, "accounts/${key(uid)}")
    fun navigation(context: Context) = context.getSharedPreferences("account_navigation", Context.MODE_PRIVATE)
    fun beginLogin(context: Context) { check(navigation(context).edit().putBoolean("login_pending", true).commit()) }
    fun finishLogin(context: Context) { check(navigation(context).edit().remove("login_pending").commit()) }
    fun sessionUid(context: Context): String? = if (navigation(context).getBoolean("login_pending", false)) null
        else (context.applicationContext as StaycalmApplication).verifiedAccountUid()

    /** Bind pre-4.0 data only to the account already signed in during upgrade. Never to a later login. */
    @Synchronized fun migrateLegacy(context: Context) {
        val nav = navigation(context)
        if (nav.getBoolean("legacy_checked", false)) return
        val owner = nav.getString("legacy_owner", null) ?: uid(context)
        if (owner == null) {
            // Unclaimed old files are preserved privately, but never shown to a different account.
            check(nav.edit().putBoolean("legacy_checked", true).commit())
            return
        }
        check(nav.edit().putString("legacy_owner", owner).commit())
        val old = context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
        val target = preferencesFor(context, owner)
        val root = rootFor(context, owner).apply { mkdirs() }
        for (folder in listOf("journal", "audio")) {
            val source = File(context.filesDir, folder)
            val destination = File(root, folder)
            if (source.exists()) {
                require(!destination.exists()) { "Your earlier files need recovery before upgrading. Keep your full backup." }
                check(source.renameTo(destination)) { "Could not preserve your earlier files. Keep your full backup." }
            }
        }
        if (target.all.isEmpty()) {
            val editor = target.edit()
            copyValues(editor, old.all)
            check(editor.commit())
        }
        val oldMeta = context.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)
        if (oldMeta.getString("owner", null) == owner) {
            val editor = context.getSharedPreferences("cloud_${key(owner)}", Context.MODE_PRIVATE).edit()
            copyValues(editor, oldMeta.all)
            check(editor.commit())
        }
        check(nav.edit().putBoolean("legacy_checked", true)
            .putBoolean("intro_done", old.getBoolean("intro_done", false) || old.getBoolean("profile_ready", false)).commit())
        // Commit scoped copies before dropping the old shared preferences.
        check(old.edit().clear().commit())
        check(oldMeta.edit().clear().commit())
    }

    private fun copyValues(editor: SharedPreferences.Editor, values: Map<String, *>) {
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
    }
}
