package com.staycalm.focus

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.ContextCompat
import java.time.LocalTime

/** User-owned preferences; cloud backup is opt-in from Account & backup. */
val LocalStaycalmDark = compositionLocalOf { false }

class Personalization(private val context: Context) {
    private val preferences = context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
    fun isReady(): Boolean = preferences.getBoolean("profile_ready", false)
    fun name(): String = preferences.getString("profile_name", "")?.trim().orEmpty()
    fun subject(): String = preferences.getString("profile_subject", "my studies")?.trim().orEmpty().ifBlank { "my studies" }
    fun darkMode(): Boolean = preferences.getBoolean("dark_theme", false)
    fun interval(): Int = preferences.getInt("quote_interval", 120).takeIf { it in listOf(0, 60, 120, 180) } ?: 120
    fun setDarkMode(value: Boolean) { preferences.edit().putBoolean("dark_theme", value).apply() }
    fun saveProfile(name: String, subject: String) {
        require(name.trim().isNotEmpty()) { "Enter a name to personalize STAYCALM." }
        check(preferences.edit()
            .putBoolean("profile_ready", true)
            .putString("profile_name", name.trim().take(40))
            .putString("profile_subject", subject.trim().ifBlank { "my studies" }.take(65))
            .commit()) { "Unable to save your profile." }
        MotivationAlarms.schedule(context)
    }
    fun setInterval(minutes: Int) {
        require(minutes in listOf(0, 60, 120, 180))
        preferences.edit().putInt("quote_interval", minutes).apply()
        MotivationAlarms.schedule(context)
    }
}

object StudyQuotes {
    private val messages = listOf(
        "One page at a time, you're building something meaningful.",
        "Consistency grows when you show up, even for ten minutes.",
        "A difficult chapter is still progress. Keep going gently.",
        "Your future self will thank you for this focused moment.",
        "Small, repeated efforts become remarkable knowledge.",
        "You do not have to finish everything today. Just begin.",
        "Take a sip of water, reset your posture, and return to your goal.",
        "Understanding one new idea is a win worth celebrating."
    )
    fun message(name: String, subject: String, index: Long): String {
        val quote = messages[Math.floorMod(index, messages.size.toLong()).toInt()]
        return "${name.ifBlank { "Friend" }}, ${subject.ifBlank { "your studies" }} matters. $quote"
    }
    fun next(name: String, subject: String): String =
        message(name, subject, System.currentTimeMillis() / 3_600_000L)
}

object MotivationAlarms {
    private const val ACTION = "com.staycalm.focus.MOTIVATION"
    private const val CHANNEL = "study_motivation"
    private const val REQUEST_CODE = 5011
    private fun alarm(context: Context) = context.getSystemService(AlarmManager::class.java)
    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, QuoteReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    /** Inexact by design: Android may defer delivery while the phone is idle. */
    fun schedule(context: Context) {
        val pi = pending(context)
        alarm(context).cancel(pi)
        val prefs = Personalization(context)
        if (!prefs.isReady() || prefs.interval() == 0) return
        val next = System.currentTimeMillis() + prefs.interval() * 60_000L
        try { alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
        catch (_: SecurityException) { /* Device restrictions can prohibit background alarms. */ }
    }
    fun deliver(context: Context) {
        val prefs = Personalization(context)
        if (!prefs.isReady() || prefs.interval() == 0) return
        val hour = LocalTime.now().hour
        if (hour !in 8..21 || FocusStore(context).state().running) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Gentle study motivation", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(context, REQUEST_CODE,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = StudyQuotes.next(prefs.name(), prefs.subject())
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("A little encouragement, ${prefs.name()} 🌷")
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        try { manager.notify(REQUEST_CODE, notification) }
        catch (_: SecurityException) { /* Permission can be revoked at any time. */ }
    }
}

class QuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.staycalm.focus.MOTIVATION") return
        try { MotivationAlarms.deliver(context) }
        finally { MotivationAlarms.schedule(context) }
    }
}
