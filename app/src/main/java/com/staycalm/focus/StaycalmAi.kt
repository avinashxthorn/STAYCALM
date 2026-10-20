package com.staycalm.focus

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** No provider credentials exist in the app. Requests use the learner's existing sign-in token. */
class StaycalmAi(private val context:Context) {
    private val owner=AccountData.uid(context)
    private val prefs=AccountData.preferencesFor(context,owner)
    fun enabled()=prefs.getBoolean("ai_enabled",false)
    fun notifications()=enabled()&&prefs.getBoolean("ai_notifications",false)
    fun notificationInterval()=prefs.getInt("ai_notification_interval",120).coerceIn(60,360)
    fun goal()=prefs.getString("ai_goal","").orEmpty()
    fun weakTopics()=prefs.getString("ai_weak_topics","").orEmpty()
    fun examDate()=prefs.getString("ai_exam_date","").orEmpty()
    fun focusArea()=prefs.getString("ai_focus_area","Study").orEmpty()
    fun preferredTime()=prefs.getString("ai_preferred_time","18:00").orEmpty()
    private fun token()=prefs.getString("ai_config_token","").orEmpty()
    fun status()=prefs.getString("ai_status","").orEmpty()
    fun busy()=prefs.getBoolean("ai_busy",false)&&System.currentTimeMillis()-prefs.getLong("ai_busy_started",0) in 0..90000
    private fun invalidate()=prefs.edit().remove("ai_report").putString("ai_config_token",UUID.randomUUID().toString())
    fun ensureInitialized() {
        if(prefs.getInt("ai_setup_version",0)>=2||!Personalization(context).isReady())return
        // Reuse saved focus details on upgrade. Explicit earlier opt-outs are preserved.
        val on=prefs.getBoolean("ai_enabled",true)
        check(prefs.edit().putInt("ai_setup_version",2).putBoolean("ai_enabled",on)
            .putBoolean("ai_notifications",prefs.getBoolean("ai_notifications",on))
            .putString("ai_preferred_time",preferredTime()).putString("ai_focus_area",focusArea()).commit())
        removeLegacyPersonalKey()
    }
    private fun removeLegacyPersonalKey() {
        val id=hash(owner?:"signed_out")
        java.io.File(context.noBackupFilesDir,"ai-key-$id").delete()
        runCatching{java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("staycalm-ai-$id")}}
    }
    fun saveDetails(goal:String,weak:String,exam:String,area:String,time:String,on:Boolean,nudges:Boolean) {
        require(owner==AccountData.uid(context)){"Account changed. Reopen Insights."}
        if(exam.isNotBlank() && exam.trim()!=examDate())require(!LocalDate.parse(exam.trim()).isBefore(LocalDate.now())){"Choose today or a future target date."}
        require(AiCoachFormat.validTime(time)){"Choose a focus time in HH:mm format."}
        check(invalidate().putString("ai_goal",goal.trim().take(300)).putString("ai_weak_topics",weak.trim().take(300))
            .putString("ai_exam_date",exam.trim()).putString("ai_focus_area",area.take(30)).putString("ai_preferred_time",time)
            .putBoolean("ai_enabled",on).putBoolean("ai_notifications",on&&nudges).putInt("ai_setup_version",2)
            .putString("ai_status",if(on)"Staycalm AI will update your suggestions automatically." else "Staycalm AI is off.").commit())
        removeLegacyPersonalKey();AiCoachWork.schedule(context);MotivationAlarms.schedule(context)
    }
    fun setNotifications(value:Boolean) {
        prefs.edit().apply{if(value)putBoolean("ai_enabled",true)}.putBoolean("ai_notifications",value).apply()
        if(!value)context.getSystemService(android.app.NotificationManager::class.java).cancel(5011)
        MotivationAlarms.schedule(context);if(value){AiCoachWork.schedule(context);if(notificationDue())MotivationAlarms.deliver(context)}
    }
    fun setNotificationInterval(minutes:Int){
        require(minutes in listOf(60,120,180,360));prefs.edit().putInt("ai_notification_interval",minutes).apply();MotivationAlarms.schedule(context)
    }
    fun report():AiStudyReport?=runCatching{AiStudyReport.decode(prefs.getString("ai_report",null)?:return null)}.getOrNull()
    private fun hash(data:String)=MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun studyContext():JSONObject {
        check(owner==AccountData.uid(context)){"Account changed."}
        val store=FocusStore(context);val profile=Personalization(context);val today=LocalDate.now()
        val recent=store.logs().filter{it.at>=System.currentTimeMillis()-TimeUnit.DAYS.toMillis(14)}
        return JSONObject().put("today",today.toString()).put("name",profile.name()).put("subject",profile.subject())
            .put("focusTopic",store.intention()).put("futureGoal",goal()).put("weakTopics",weakTopics()).put("examDate",examDate())
            .put("focusArea",focusArea()).put("preferredTime",preferredTime()).put("dailyGoalMinutes",store.dailyGoal())
            .put("maximumSessionMinutes",store.dailyGoal().coerceAtMost(180))
            .put("recentSessions",JSONArray().apply{recent.takeLast(40).forEach{put(JSONObject().put("date",Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate().toString()).put("minutes",it.minutes).put("topic",it.intention.take(120)))}})
            .put("planner",JSONArray().apply{store.tasks().filter{runCatching{LocalDate.parse(it.date) in today.minusDays(7)..today.plusDays(7)}.getOrDefault(false)}.take(30).forEach{put(JSONObject().put("topic",it.title.take(450)).put("date",it.date).put("time",it.time).put("minutes",it.duration).put("completed",it.done).put("kind",it.kind))}})
    }
    internal fun contextFingerprint()=hash(studyContext().toString())
    fun currentReport():AiStudyReport?=report()?.takeIf{enabled()&&it.contextHash==contextFingerprint()}
    fun needsRefresh():Boolean {
        if(!enabled())return false
        val saved=currentReport()?:return true
        return System.currentTimeMillis()-saved.generatedAt !in 0..TimeUnit.HOURS.toMillis(6)
    }
    suspend fun refresh():AiStudyReport=mutex.withLock {
        check(enabled()&&owner==AccountData.uid(context)){"Staycalm AI is off."}
        if(!needsRefresh())return@withLock report()!!
        val user=if(CloudBackup.ready())FirebaseAuth.getInstance().currentUser else null
        check(user?.uid==owner&&user?.isEmailVerified==true){"Sign in to your verified Staycalm account to use Staycalm AI."}
        val configuration=token();val data=studyContext().toString();val fingerprint=hash(data);val now=System.currentTimeMillis()
        require(now-prefs.getLong("ai_last_request",0)>=60000){"Staycalm AI is reviewing recent changes. Your saved suggestions are kept."}
        prefs.edit().putLong("ai_last_request",now).putBoolean("ai_busy",true).putLong("ai_busy_started",now).putString("ai_status","Staycalm AI is preparing your next steps…").apply()
        try {
            val identityToken=user!!.getIdToken(false).await().token?:error("Please sign in again to reconnect Staycalm AI.")
            val result=withContext(Dispatchers.IO){
                check(enabled()&&owner==AccountData.uid(context)&&token()==configuration){"Focus details changed. Staycalm AI will update again."}
                request(identityToken,data,fingerprint)
            }
            check(enabled()&&owner==AccountData.uid(context)&&token()==configuration){"Focus details changed. Staycalm AI will update again."}
            check(prefs.edit().putString("ai_report",result.encode()).putString("ai_status","Your next steps are ready.").commit())
            result
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){
            val message=when(e){is java.io.IOException->"Staycalm AI will reconnect when your internet is available. Your saved suggestions are kept.";is org.json.JSONException->"Staycalm AI could not finish these suggestions. It will try again later.";else->e.message?:"Staycalm AI will try again later."}
            if(owner==AccountData.uid(context))prefs.edit().putString("ai_status",message).apply()
            throw e
        }finally{prefs.edit().putBoolean("ai_busy",false).apply()}
    }
    private fun request(identityToken:String,data:String,fingerprint:String):AiStudyReport {
        val connection=URL(ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=55000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            connection.setRequestProperty("Authorization","Bearer $identityToken")
            connection.outputStream.use{it.write(JSONObject().put("context",data).toString().toByteArray())}
            when(connection.responseCode){200->Unit;401->error("Please sign in again to reconnect Staycalm AI.");429->error("Staycalm AI is taking a short pause. Your saved suggestions are still available.");else->error("Staycalm AI is temporarily unavailable. Your saved suggestions are kept.")}
            val raw=connection.inputStream.use{input->val bytes=ByteArray(100001);var count=0;while(count<bytes.size){val n=input.read(bytes,count,bytes.size-count);if(n<0)break;count+=n};require(count<=100000){"Staycalm AI response was too large."};String(bytes,0,count,Charsets.UTF_8)}
            val envelope=JSONObject(raw);require(envelope.getString("contextHash")==fingerprint){"Staycalm AI received mismatched focus details."}
            val received=envelope.getLong("generatedAt");require(kotlin.math.abs(received-System.currentTimeMillis())<=TimeUnit.DAYS.toMillis(1)){"Staycalm AI returned an outdated suggestion."}
            return AiCoachFormat.parse(envelope.getJSONObject("report").toString(),LocalDate.parse(JSONObject(data).getString("today")),JSONObject(data).getInt("dailyGoalMinutes"),received,fingerprint)
        }finally{connection.disconnect()}
    }
    fun notification():String? {
        if(!notifications())return null
        val report=currentReport()?:return null
        if(System.currentTimeMillis()-report.generatedAt !in 0..TimeUnit.HOURS.toMillis(24))return null
        val state=FocusStore(context).state()
        if(state.running)return "After this ${if(state.mode=="break")"break" else "session"}: ${report.breakAdvice}".take(240)
        val index=prefs.getInt("ai_nudge_index",0).mod(report.nudges.size)
        prefs.edit().putInt("ai_nudge_index",(index+1).mod(report.nudges.size)).apply();return report.nudges[index]
    }
    fun notificationDue()=notifications()&&System.currentTimeMillis()-prefs.getLong("ai_last_notification",0)>=notificationInterval()*60000L
    fun markNotified(){prefs.edit().putLong("ai_last_notification",System.currentTimeMillis()).apply()}
    companion object {
        const val ENDPOINT="https://staycalm-ai-service.flowymouse.chatgpt.site/api/coach"
        private val mutex=Mutex()
    }
}

object AiCoachWork {
    private const val NAME="staycalm-ai-coach"
    private var observed:SharedPreferences?=null
    private var listener:SharedPreferences.OnSharedPreferenceChangeListener?=null
    private val inputKeys=setOf("plans","logs","intention","profile_name","profile_subject","daily_goal","ai_goal","ai_weak_topics","ai_exam_date","ai_focus_area","ai_preferred_time")
    fun observe(context:Context) {
        val app=context.applicationContext
        observed?.let{prefs->listener?.let{prefs.unregisterOnSharedPreferenceChangeListener(it)}}
        val prefs=AccountData.preferences(app)
        listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key in inputKeys)request(app,false)}
        observed=prefs;prefs.registerOnSharedPreferenceChangeListener(listener!!)
    }
    fun cancel(context:Context){WorkManager.getInstance(context).cancelUniqueWork(NAME);WorkManager.getInstance(context).cancelUniqueWork("$NAME-refresh")}
    fun request(context:Context,immediate:Boolean=false) {
        val ai=StaycalmAi(context)
        if(AccountData.sessionUid(context)==null||!ai.enabled()||!Personalization(context).isReady()||!ai.needsRefresh())return
        val work=OneTimeWorkRequestBuilder<AiCoachWorker>().setInputData(workDataOf("owner" to AccountData.uid(context)))
            .setInitialDelay(if(immediate)0 else 20,TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$NAME-refresh",if(immediate)ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,work)
    }
    fun refreshSoon(context:Context)=request(context,true)
    fun schedule(context:Context) {
        val ai=StaycalmAi(context);ai.ensureInitialized()
        if(AccountData.sessionUid(context)==null||!ai.enabled()||!Personalization(context).isReady()){cancel(context);return}
        val work=PeriodicWorkRequestBuilder<AiCoachWorker>(6,TimeUnit.HOURS).setInputData(workDataOf("owner" to AccountData.uid(context)))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,work)
        request(context,true)
    }
}
class AiCoachWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        if(AccountData.sessionUid(applicationContext)==null||inputData.getString("owner")!=AccountData.uid(applicationContext))return Result.success()
        val ai=StaycalmAi(applicationContext);if(!ai.enabled())return Result.success()
        return try{ai.refresh();if(ai.notificationDue())MotivationAlarms.deliver(applicationContext);MotivationAlarms.schedule(applicationContext);Result.success()}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){Result.success()}
    }
}
