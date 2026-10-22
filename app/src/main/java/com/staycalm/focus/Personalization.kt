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
    private val preferences = AccountData.preferences(context)
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
        AiCoachWork.schedule(context)
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
    private const val ACTION="com.staycalm.focus.MOTIVATION"
    private const val REQUEST_CODE=5011
    private fun alarm(context:Context)=context.getSystemService(AlarmManager::class.java)
    private fun pending(context:Context)=PendingIntent.getBroadcast(context,REQUEST_CODE,
        Intent(context,QuoteReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(context:Context){alarm(context).cancel(pending(context))}
    fun schedule(context:Context){
        val pi=pending(context);alarm(context).cancel(pi)
        val profile=Personalization(context);if(!profile.isReady())return
        val ai=StaycalmAi(context);val interval=if(ai.notifications())ai.notificationInterval() else profile.interval()
        if(interval==0)return
        try{alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+interval*60000L,pi)}catch(_:SecurityException){}
    }
    fun deliver(context:Context,hour:Int=LocalTime.now().hour){
        val profile=Personalization(context);if(!profile.isReady())return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val ai=StaycalmAi(context);val aiMode=ai.notifications()
        val content:String
        if(aiMode){
            if(!ai.notificationDue())return
            val cached=ai.notification()
            if(cached==null){AiCoachWork.refreshSoon(context);return}
            content=cached
        }else{
            if(profile.interval()==0||hour !in 8..21||FocusStore(context).state().running)return
            content=StudyQuotes.next(profile.name(),profile.subject())
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        val channel=if(aiMode)"staycalm_ai_coach" else "study_motivation"
        manager.createNotificationChannel(NotificationChannel(channel,if(aiMode)"AI personalized notifications" else "Gentle study motivation",NotificationManager.IMPORTANCE_DEFAULT))
        val click=PendingIntent.getActivity(context,REQUEST_CODE,Intent(context,MainActivity::class.java).putExtra("open_ai_insights",aiMode),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if(aiMode)"Staycalm AI · Your focus coach" else "A little encouragement, ${profile.name()} 🌷")
            .setContentText(content).setStyle(Notification.BigTextStyle().bigText(content)).setAutoCancel(true).setContentIntent(click).build()
        try{manager.notify(REQUEST_CODE,notification);if(aiMode)ai.markNotified()}catch(_:SecurityException){}
    }
}

class QuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.staycalm.focus.MOTIVATION") return
        try { MotivationAlarms.deliver(context) }
        finally { MotivationAlarms.schedule(context) }
    }
}
