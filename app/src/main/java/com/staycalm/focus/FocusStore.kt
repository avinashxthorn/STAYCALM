package com.staycalm.focus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID
import kotlin.math.roundToInt

data class PlanItem(val id: String, val title: String, val date: String, val time: String, val duration: Int, val done: Boolean, val remind: Boolean)
data class FocusLog(val id: String, val at: Long, val minutes: Int, val taskId: String, val intention: String = "")
data class FocusState(val running: Boolean = false, val remainingMs: Long = 25*60_000L, val durationMinutes: Int = 25,
    val endElapsed: Long = 0L, val endWall: Long = 0L, val bootOrigin: Long = 0L, val taskId: String = "", val mode: String = "focus", val bootCount: Int = -1)

object Evolution {
    private val thresholds = intArrayOf(0,30,90,180,360,720)
    fun stage(minutes: Int): Int = thresholds.indexOfLast { minutes >= it }.coerceIn(0,5)
    fun next(minutes: Int): Int? = thresholds.firstOrNull { it > minutes }
    fun title(stage: Int): String = listOf("A new beginning","Hairline fractures","Almost there","Hello, little one","Finding your feet","Fully grown")[stage.coerceIn(0,5)]
}

class FocusStore(private val context: Context) {
    private val prefs=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
    private fun parsePlan(o:JSONObject) = PlanItem(o.optString("id"),o.optString("title"),o.optString("date"),o.optString("time"),o.optInt("duration",25),o.optBoolean("done"),o.optBoolean("remind"))
    private fun encode(p:PlanItem)=JSONObject().put("id",p.id).put("title",p.title).put("date",p.date).put("time",p.time).put("duration",p.duration).put("done",p.done).put("remind",p.remind)
    fun tasks():List<PlanItem> = runCatching{val a=JSONArray(prefs.getString("plans","[]")?:"[]");(0 until a.length()).map{parsePlan(a.getJSONObject(it))}}.getOrDefault(emptyList())
    fun savePlans(items:List<PlanItem>) {
        prefs.edit().putString("plans",JSONArray().apply{items.forEach{put(encode(it))}}.toString()).apply()
        items.forEach{if(it.remind && !it.done) FocusAlarms.scheduleTask(context,it) else FocusAlarms.cancelTask(context,it.id)}
    }
    fun addTask(title:String,date:String,time:String,duration:Int,remind:Boolean):PlanItem {
        val task=PlanItem(UUID.randomUUID().toString(),title,date,time,duration,false,remind)
        savePlans(tasks()+task);return task
    }
    fun replaceTask(item:PlanItem) {savePlans(tasks().map{if(it.id==item.id)item else it})}
    fun deleteTask(item:PlanItem) {FocusAlarms.cancelTask(context,item.id);savePlans(tasks().filterNot{it.id==item.id})}
    fun logs():List<FocusLog> = runCatching{
        val a=JSONArray(prefs.getString("logs","[]")?:"[]")
        (0 until a.length()).map{val o=a.getJSONObject(it);FocusLog(o.getString("id"),o.getLong("at"),o.getInt("minutes"),o.optString("taskId"),o.optString("intention"))}
    }.getOrDefault(emptyList())
    fun intention():String=prefs.getString("intention","")?:""
    fun setIntention(value:String){prefs.edit().putString("intention",value.take(120)).apply()}
    fun completion():String?=prefs.getString("completion",null)
    fun dismissCompletion(){prefs.edit().remove("completion").apply()}
    fun completedMinutes():Int=logs().sumOf{it.minutes}
    fun dailyGoal():Int=prefs.getInt("daily_goal",120)
    fun setDailyGoal(value:Int){prefs.edit().putInt("daily_goal",value.coerceIn(30,360)).apply()}
    fun enableDnd():Boolean=prefs.getBoolean("focus_dnd",false)
    fun setDnd(value:Boolean){prefs.edit().putBoolean("focus_dnd",value).apply()}
    fun soundVolume():Float=prefs.getFloat("volume",.66f)
    fun setVolume(v:Float){prefs.edit().putFloat("volume",v).apply()}
    fun lastSound():String=AudioLibrary(context).find(prefs.getString("sound",Ambients.all.first().id)?:Ambients.all.first().id).id
    fun setSound(s:String){prefs.edit().putString("sound",s).apply()}
    @Synchronized fun state():FocusState=FocusState(
        prefs.getBoolean("running",false),prefs.getLong("remaining",25*60_000L),
        prefs.getInt("duration",25),prefs.getLong("end_elapsed",0),
        prefs.getLong("end_wall",0),prefs.getLong("boot_origin",0),prefs.getString("current_task","")?:"",prefs.getString("mode","focus")?:"focus",prefs.getInt("boot_count",-1))
    private fun stateEditor(s:FocusState): android.content.SharedPreferences.Editor = prefs.edit().putBoolean("running",s.running).putLong("remaining",s.remainingMs)
            .putInt("duration",s.durationMinutes).putLong("end_elapsed",s.endElapsed)
            .putLong("end_wall",s.endWall).putLong("boot_origin",s.bootOrigin)
            .putString("current_task",s.taskId).putString("mode",s.mode).putInt("boot_count",s.bootCount)
    private fun save(s:FocusState){stateEditor(s).commit()}
    private fun sameBoot(s:FocusState):Boolean = kotlin.math.abs((System.currentTimeMillis()-SystemClock.elapsedRealtime())-s.bootOrigin)<120_000
    fun remaining(s:FocusState=state()):Long=FocusClock.remainingMs(
        running=s.running,pausedMs=s.remainingMs,endElapsed=s.endElapsed,
        endWall=s.endWall,bootOrigin=s.bootOrigin,
        nowElapsed=SystemClock.elapsedRealtime(),nowWall=System.currentTimeMillis(),
        sameBootOverride=if(s.bootCount<0)null else s.bootCount==Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-2)
    )
    @Synchronized fun configure(minutes:Int,taskId:String="",mode:String="focus"){
        val s=state();if(s.running)return
        save(s.copy(durationMinutes=minutes.coerceIn(5,180),remainingMs=minutes.coerceIn(5,180)*60_000L,taskId=taskId,mode=mode))
    }
    @Synchronized fun start(){
        val s=state();if(s.running)return
        val ms=s.remainingMs.coerceAtLeast(1000L)
        val next=s.copy(running=true,endElapsed=SystemClock.elapsedRealtime()+ms,
            endWall=System.currentTimeMillis()+ms,bootOrigin=System.currentTimeMillis()-SystemClock.elapsedRealtime(),
            bootCount=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1))
        save(next); FocusAlarms.scheduleFocus(context,ms); Notice.sessionStarted(context,next.endWall,next.mode)
        if(enableDnd() && next.mode=="focus") FocusDnd.enter(context)
    }
    @Synchronized fun pause(){
        val s=state();if(!s.running)return
        if(remaining(s)<=0){completeIfDue();return}
        save(s.copy(running=false,remainingMs=remaining(s),endElapsed=0L,endWall=0L))
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
    }
    @Synchronized fun abandon(){
        val s=state()
        save(s.copy(running=false,remainingMs=s.durationMinutes*60_000L,endWall=0L,endElapsed=0L))
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
        context.stopService(Intent(context,SoundService::class.java))
    }
    @Synchronized fun completeIfDue():Boolean {
        val s=state();if(!s.running||remaining(s)>0)return false
        // Persist the completion and reset together so a restarted process cannot credit it twice.
        val editor=stateEditor(s.copy(running=false,remainingMs=s.durationMinutes*60_000L,endWall=0L,endElapsed=0L))
        if(s.mode=="focus"){
            val log=FocusLog(UUID.randomUUID().toString(),s.endWall,s.durationMinutes,s.taskId,intention())
            val all=logs()+log
            editor.putString("logs",JSONArray().apply{all.forEach{put(JSONObject().put("id",it.id).put("at",it.at).put("minutes",it.minutes).put("taskId",it.taskId).put("intention",it.intention))}}.toString())
        }
        editor.putString("completion",if(s.mode=="focus")"${s.durationMinutes} minutes, just for your focus." else "Your break is complete. Ready for a fresh start?").commit()
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
        context.stopService(Intent(context,SoundService::class.java))
        Notice.finished(context,s.durationMinutes,s.mode)
        return true
    }
}

object FocusDnd {
    fun allowed(context:Context):Boolean=context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    fun enter(context:Context){
        if(!allowed(context))return
        val n=context.getSystemService(NotificationManager::class.java)
        val p=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
        try{
            if(Build.VERSION.SDK_INT<35) p.edit().putInt("dnd_previous",n.currentInterruptionFilter).commit()
            n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            p.edit().putBoolean("dnd_activated",true).apply()
        }catch (_:Exception){}
    }
    fun exit(context:Context){
        val p=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
        if(!p.getBoolean("dnd_activated",false))return
        try{
            if(allowed(context)){
                val n=context.getSystemService(NotificationManager::class.java)
                // Android 15+ ALL deactivates our implicit AutomaticZenRule, not the user's other rules.
                n.setInterruptionFilter(if(Build.VERSION.SDK_INT>=35)NotificationManager.INTERRUPTION_FILTER_ALL
                    else p.getInt("dnd_previous",NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }catch (_:Exception){}finally{p.edit().remove("dnd_activated").remove("dnd_previous").apply()}
    }
}

object Notice {
    private const val FINISH="staycalm_finish"
    private const val REMIND="staycalm_plan"
    private const val ACTIVE="staycalm_active"
    fun channels(context:Context){
        val n=context.getSystemService(NotificationManager::class.java)
        n.createNotificationChannel(NotificationChannel(FINISH,"Completed focus sessions",NotificationManager.IMPORTANCE_HIGH))
        n.createNotificationChannel(NotificationChannel(REMIND,"Plan reminders",NotificationManager.IMPORTANCE_DEFAULT))
        n.createNotificationChannel(NotificationChannel(ACTIVE,"Ongoing focus",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})
    }
    private fun post(context:Context,channel:String,id:Int,title:String,body:String){
        channels(context)
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(open)
        try{context.getSystemService(NotificationManager::class.java).notify(id,builder.build())}catch (_:SecurityException){}
    }
    fun sessionStarted(context:Context,finishAt:Long,mode:String="focus"){
        channels(context)
        val pending=PendingIntent.getActivity(context,1002,Intent(context,MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(context,ACTIVE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if(mode=="focus")"STAYCALM · In focus" else "STAYCALM · On a break")
            .setContentText("Your session is in progress")
            .setWhen(finishAt).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pending)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"Pause",
                PendingIntent.getBroadcast(context,1003,Intent(context,AlarmReceiver::class.java).setAction("pause"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()).build()
        try{context.getSystemService(NotificationManager::class.java).notify(1002,notification)}catch (_:SecurityException){}
    }
    fun sessionStopped(context:Context){context.getSystemService(NotificationManager::class.java).cancel(1002)}
    fun finished(context:Context,minutes:Int,mode:String)=post(context,FINISH,1001,if(mode=="focus")"Focus completed" else "Break completed",if(mode=="focus")"Your ${minutes}-minute session is complete. Your turtle grew." else "Time for a fresh start.")
    fun reminder(context:Context,item:PlanItem)=post(context,REMIND,item.id.hashCode(),"Up next · ${item.title}","Scheduled for ${item.time} · ${item.duration} minutes")
}

object FocusAlarms {
    private fun manager(c:Context)=c.getSystemService(AlarmManager::class.java)
    private fun pi(c:Context,action:String,id:Int):PendingIntent=PendingIntent.getBroadcast(c,id,
        Intent(c,AlarmReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun preciseAllowed(c:Context):Boolean=Build.VERSION.SDK_INT<31 || manager(c).canScheduleExactAlarms()
    fun scheduleFocus(c:Context,remainingMs:Long){
        val at=SystemClock.elapsedRealtime()+remainingMs.coerceAtLeast(1L)
        val alarms=manager(c)
        val alarm=pi(c,"focus",1000)
        if(preciseAllowed(c)) {
            try{alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,alarm)}
            catch (_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,alarm)}
        } else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,at,alarm)
    }
    fun cancelFocus(c:Context){manager(c).cancel(pi(c,"focus",1000))}
    fun scheduleTask(c:Context,item:PlanItem){
        cancelTask(c,item.id)
        val dateTime=runCatching{LocalDateTime.of(LocalDate.parse(item.date),LocalTime.parse(item.time))}.getOrNull()?:return
        val whenMs=dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(whenMs<=System.currentTimeMillis())return
        val pending=PendingIntent.getBroadcast(c,item.id.hashCode(),Intent(c,AlarmReceiver::class.java).setAction("task").putExtra("id",item.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMs,pending)
    }
    fun cancelTask(c:Context,id:String){manager(c).cancel(pi(c,"task",id.hashCode()))}
}

class AlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action=="pause"){FocusStore(context).pause()}
        if(intent.action=="focus"){
            val store=FocusStore(context)
            if(!store.completeIfDue() && store.state().running)FocusAlarms.scheduleFocus(context,store.remaining())
        }
        if(intent.action=="task"){
            val item=FocusStore(context).tasks().firstOrNull{it.id==intent.getStringExtra("id")}
            if(item!=null && item.remind && !item.done)Notice.reminder(context,item)
        }
    }
}

class BootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED && intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return
        val store=FocusStore(context)
        store.completeIfDue()
        val state=store.state()
        if(state.running)FocusAlarms.scheduleFocus(context,store.remaining(state))
        store.tasks().filter{it.remind&&!it.done}.forEach{FocusAlarms.scheduleTask(context,it)}
    }
}
