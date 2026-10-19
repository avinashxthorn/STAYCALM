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
    val endElapsed: Long = 0L, val endWall: Long = 0L, val bootOrigin: Long = 0L, val taskId: String = "", val mode: String = "focus", val bootCount: Int = -1, val sessionId: String = "")

object Evolution {
    private val thresholds = intArrayOf(0,30,90,180,360,720)
    fun stage(minutes: Int): Int = thresholds.indexOfLast { minutes >= it }.coerceIn(0,5)
    fun next(minutes: Int): Int? = thresholds.firstOrNull { it > minutes }
    fun title(stage: Int): String = listOf("A new beginning","Hairline fractures","Almost there","Hello, little one","Finding your feet","Fully grown")[stage.coerceIn(0,5)]
}

class FocusStore(private val context: Context) {
    // All Activity, service and receiver instances share one transaction lock.
    companion object { private val timerLock = Any() }
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
    private fun detachAutoIntention(id:String){
        if(prefs.getString("auto_intention_plan_id",null)==id)
            prefs.edit().remove("auto_intention_plan_id").remove("auto_intention_plan_date").apply()
    }
    fun replaceTask(item:PlanItem) {detachAutoIntention(item.id);savePlans(tasks().map{if(it.id==item.id)item else it})}
    fun deleteTask(item:PlanItem) {detachAutoIntention(item.id);FocusAlarms.cancelTask(context,item.id);savePlans(tasks().filterNot{it.id==item.id})}
    fun logs():List<FocusLog> = runCatching{
        val a=JSONArray(prefs.getString("logs","[]")?:"[]")
        (0 until a.length()).map{val o=a.getJSONObject(it);FocusLog(o.getString("id"),o.getLong("at"),o.getInt("minutes"),o.optString("taskId"),o.optString("intention"))}
    }.getOrDefault(emptyList())
    fun intention():String=prefs.getString("intention","")?:""
    fun setIntention(value:String){prefs.edit().putString("intention",value.take(120)).apply()}
    // Maintain one Planner draft for the home-page intention; do not touch other alarms.
    fun saveIntentionToPlanner(value:String) = synchronized(timerLock) {
        val title=value.trim().take(120)
        val today=LocalDate.now().toString()
        val linkedId=prefs.getString("auto_intention_plan_id",null)
        val linkedDate=prefs.getString("auto_intention_plan_date",null)
        val all=tasks()
        val index=if(linkedDate==today)all.indexOfFirst{it.id==linkedId && !it.done} else -1
        if(title.isBlank()){
            if(index>=0){
                prefs.edit().putString("plans",JSONArray().apply{
                    all.forEachIndexed{position,item->if(position!=index)put(encode(item))}
                }.toString()).remove("auto_intention_plan_id").remove("auto_intention_plan_date").apply()
            }else prefs.edit().remove("auto_intention_plan_id").remove("auto_intention_plan_date").apply()
        }else{
            val task=if(index>=0)all[index].copy(title=title) else PlanItem(
                UUID.randomUUID().toString(),title,today,
                LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                state().durationMinutes,false,false)
            val next=if(index>=0)all.toMutableList().apply{set(index,task)} else all+task
            prefs.edit().putString("plans",JSONArray().apply{next.forEach{put(encode(it))}}.toString())
                .putString("auto_intention_plan_id",task.id)
                .putString("auto_intention_plan_date",today).apply()
        }
    }
    fun completion():String?=prefs.getString("completion",null)
    fun dismissCompletion(){prefs.edit().remove("completion").apply()}
    fun completedMinutes():Int=logs().sumOf{it.minutes}
    fun dailyGoal():Int=prefs.getInt("daily_goal",120)
    fun setDailyGoal(value:Int){prefs.edit().putInt("daily_goal",value.coerceIn(30,360)).apply()}
    fun uninstallProtectionEnabled():Boolean=prefs.getBoolean("session_uninstall_protection",false)
    fun setUninstallProtectionEnabled(value:Boolean):Boolean = synchronized(timerLock) {
        if(SessionUninstallGuard.unfinished(state()))return false
        val saved=prefs.edit().putBoolean("session_uninstall_protection",value).commit()
        if(saved && !value){SessionUninstallGuard.clearPending(context);SessionUninstallGuard.releaseIfIdle(context)}
        saved
    }
    fun blockDistractingApps():Boolean=prefs.getBoolean("block_distracting_apps",false)
    fun setBlockDistractingApps(value:Boolean):Boolean = synchronized(timerLock) {
        // Prevent stale UI/background callers from switching off the blocker mid-session.
        val s=state()
        if(!value && blockDistractingApps() && s.mode=="focus" && SessionUninstallGuard.unfinished(s))
            return@synchronized false
        if(!prefs.edit().putBoolean("block_distracting_apps",value).commit())return@synchronized false
        TimerService.ensureRunning(context)
        if(!value)TimerService.stop(context)
        true
    }
    fun lockTimerNotification():Boolean=prefs.getBoolean("lock_timer_notification",true)
    fun setLockTimerNotification(value:Boolean){
        prefs.edit().putBoolean("lock_timer_notification",value).apply()
        val s=state()
        if(s.running) { Notice.sessionStarted(context,s.endWall,s.mode); TimerService.ensureRunning(context) }
    }
    fun enableDnd():Boolean=prefs.getBoolean("focus_dnd",false)
    fun setDnd(value:Boolean){prefs.edit().putBoolean("focus_dnd",value).apply()}
    fun soundVolume():Float=prefs.getFloat("volume",.66f)
    fun setVolume(v:Float){prefs.edit().putFloat("volume",v).apply()}
    fun lastSound():String=AudioLibrary(context).find(prefs.getString("sound",Ambients.all.first().id)?:Ambients.all.first().id).id
    fun setSound(s:String){prefs.edit().putString("sound",s).apply()}
    fun rewardBalance():Long = synchronized(timerLock) { prefs.getLong("reward_balance",0L).coerceAtLeast(0L) }
    fun freeCancellationsRemaining():Int = synchronized(timerLock) { RewardPolicy.freeRemaining(prefs.getInt("free_cancellations_used",0)) }
    fun cancellationCost():Long = synchronized(timerLock) { RewardPolicy.cancellationCost(prefs.getInt("free_cancellations_used",0),state().mode) }
    fun state():FocusState = synchronized(timerLock) {
        val running=prefs.getBoolean("running",false)
        val duration=prefs.getInt("duration",25)
        val remaining=prefs.getLong("remaining",25*60_000L)
        // Preserve an existing running/paused session when upgrading from v3.4.
        val id=prefs.getString("session_id",null) ?: if(running || remaining<duration*60_000L) "legacy-session" else ""
        FocusState(running,remaining,duration,prefs.getLong("end_elapsed",0),
            prefs.getLong("end_wall",0),prefs.getLong("boot_origin",0),prefs.getString("current_task","")?:"",
            prefs.getString("mode","focus")?:"focus",prefs.getInt("boot_count",-1),id)
    }
    private fun stateEditor(s:FocusState): android.content.SharedPreferences.Editor = prefs.edit().putBoolean("running",s.running).putLong("remaining",s.remainingMs)
            .putInt("duration",s.durationMinutes).putLong("end_elapsed",s.endElapsed)
            .putLong("end_wall",s.endWall).putLong("boot_origin",s.bootOrigin)
            .putString("current_task",s.taskId).putString("mode",s.mode).putInt("boot_count",s.bootCount).putString("session_id",s.sessionId)
    private fun save(s:FocusState):Boolean = stateEditor(s).commit()
    private fun sameBoot(s:FocusState):Boolean = kotlin.math.abs((System.currentTimeMillis()-SystemClock.elapsedRealtime())-s.bootOrigin)<120_000
    fun remaining(s:FocusState=state()):Long=FocusClock.remainingMs(
        running=s.running,pausedMs=s.remainingMs,endElapsed=s.endElapsed,
        endWall=s.endWall,bootOrigin=s.bootOrigin,
        nowElapsed=SystemClock.elapsedRealtime(),nowWall=System.currentTimeMillis(),
        sameBootOverride=if(s.bootCount<0)null else s.bootCount==Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-2)
    )
    fun configure(minutes:Int,taskId:String="",mode:String="focus") { synchronized(timerLock) {
        val s=state();if(s.running || s.sessionId.isNotEmpty())return
        save(s.copy(durationMinutes=minutes.coerceIn(5,180),remainingMs=minutes.coerceIn(5,180)*60_000L,taskId=taskId,mode=if(mode=="break")"break" else "focus"))
    } }
    fun start():Boolean = synchronized(timerLock) {
        val s=state();if(s.running)return true
        // Never silently start a protected focus session when its requested blocker is unavailable.
        if(s.mode=="focus" && blockDistractingApps() && !FocusAppBlocker.isEnabled(context))return false
        if(uninstallProtectionEnabled() && !SessionUninstallGuard.ready(context))return false
        val ms=s.remainingMs.coerceAtLeast(1L)
        val next=s.copy(running=true,endElapsed=SystemClock.elapsedRealtime()+ms,
            endWall=System.currentTimeMillis()+ms,bootOrigin=System.currentTimeMillis()-SystemClock.elapsedRealtime(),
            bootCount=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1),
            sessionId=s.sessionId.ifEmpty{UUID.randomUUID().toString()})
        if(!save(next))return false
        SessionUninstallGuard.clearPending(context)
        FocusAlarms.scheduleFocus(context,ms)
        TimerService.ensureRunning(context)
        if(enableDnd() && next.mode=="focus") FocusDnd.enter(context)
        true
    }
    fun pause() { synchronized(timerLock) {
        val s=state();if(!s.running)return
        val left=remaining(s)
        if(left<=0){completeIfDue();return}
        if(!save(s.copy(running=false,remainingMs=left.coerceAtMost(s.durationMinutes*60_000L-1),endElapsed=0L,endWall=0L)))return
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
        if(FocusAppBlocker.keepAlive(context,state()))TimerService.ensureRunning(context)
    } }
    fun abandon():CancellationResult = synchronized(timerLock) {
        val s=state()
        if(s.running && remaining(s)<=0) {
            return if(completeIfDue()) CancellationResult.COMPLETED else CancellationResult.SAVE_FAILED
        }
        if(s.sessionId.isEmpty()){SessionUninstallGuard.releaseIfIdle(context);return CancellationResult.NO_SESSION}
        val used=prefs.getInt("free_cancellations_used",0)
        val cost=RewardPolicy.cancellationCost(used,s.mode)
        val balance=rewardBalance()
        if(balance<cost)return CancellationResult.INSUFFICIENT_POINTS
        val editor=stateEditor(s.copy(running=false,remainingMs=s.durationMinutes*60_000L,endWall=0L,endElapsed=0L,sessionId=""))
        if(s.mode=="focus") {
            if(cost==0L)editor.putInt("free_cancellations_used",(used+1).coerceAtMost(RewardPolicy.FREE_CANCELLATIONS))
            else editor.putLong("reward_balance",balance-cost)
        }
        // The charge, allowance and session reset are one durable transaction.
        if(!editor.commit())return CancellationResult.SAVE_FAILED
        SessionUninstallGuard.releaseIfIdle(context)
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
        context.stopService(Intent(context,SoundService::class.java))
        if(cost>0) CancellationResult.CHARGED else CancellationResult.FREE
    }
    fun completeIfDue():Boolean = synchronized(timerLock) {
        val s=state();if(!s.running||remaining(s)>0)return false
        // Credit and reset together, under the same lock across all store instances.
        val editor=stateEditor(s.copy(running=false,remainingMs=s.durationMinutes*60_000L,endWall=0L,endElapsed=0L,sessionId=""))
        val earned=RewardPolicy.earnedPoints(s.durationMinutes,s.mode)
        if(s.mode=="focus"){
            val log=FocusLog(s.sessionId.ifEmpty{UUID.randomUUID().toString()},s.endWall,s.durationMinutes,s.taskId,intention())
            val all=logs()+log
            editor.putString("logs",JSONArray().apply{all.forEach{put(JSONObject().put("id",it.id).put("at",it.at).put("minutes",it.minutes).put("taskId",it.taskId).put("intention",it.intention))}}.toString())
            editor.putLong("reward_balance",rewardBalance()+earned)
        }
        val message=if(s.mode=="focus") {
            "${s.durationMinutes} minutes, just for your focus." +
                (if(earned>0) " +$earned reward ${if(earned==1L)"point" else "points"}!" else "")
        } else "Your break is complete. Ready for a fresh start?"
        if(!editor.putString("completion",message).commit())return false
        SessionUninstallGuard.releaseIfIdle(context)
        FocusAlarms.cancelFocus(context);Notice.sessionStopped(context);FocusDnd.exit(context)
        context.stopService(Intent(context,SoundService::class.java))
        Notice.finished(context,s.durationMinutes,s.mode)
        true
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
    // New channel because Android doesn't allow raising an existing channel's importance.
    private const val REMIND="staycalm_plan_on_time_v2"
    private const val ACTIVE="staycalm_active"
    fun channels(context:Context){
        val n=context.getSystemService(NotificationManager::class.java)
        n.createNotificationChannel(NotificationChannel(FINISH,"Completed focus sessions",NotificationManager.IMPORTANCE_HIGH))
        n.createNotificationChannel(NotificationChannel(REMIND,"Plan reminders",NotificationManager.IMPORTANCE_HIGH))
        n.createNotificationChannel(NotificationChannel(ACTIVE,"Ongoing focus",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})
    }
    private fun post(context:Context,channel:String,id:Int,title:String,body:String){
        channels(context)
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(open)
        if(channel==REMIND)builder.setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_HIGH)
        try{context.getSystemService(NotificationManager::class.java).notify(id,builder.build())}catch (_:SecurityException){}
    }
    fun sessionNotification(context:Context,finishAt:Long,mode:String="focus"):Notification {
        channels(context)
        val locked=FocusStore(context).lockTimerNotification()
        val pending=PendingIntent.getActivity(context,1002,Intent(context,MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val state=FocusStore(context).state()
        val blockerActive=FocusAppBlocker.keepAlive(context,state)
        val builder=Notification.Builder(context,ACTIVE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if(!state.running) "STAYCALM · Focus paused" else if(mode=="focus")"STAYCALM · In focus" else "STAYCALM · On a break")
            .setContentText(if(!state.running && blockerActive) "Instagram and YouTube remain blocked · tap to open" else if(locked) "Timer notification locked · tap to open" else "Your session is in progress")
            .setOngoing(locked || blockerActive).setAutoCancel(false).setOnlyAlertOnce(true).setContentIntent(pending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if(state.running)builder.setWhen(finishAt).setUsesChronometer(true).setChronometerCountDown(true)
        if(locked || blockerActive)builder.setDeleteIntent(PendingIntent.getBroadcast(context,1004,
            Intent(context,AlarmReceiver::class.java).setAction("timer_notification_dismissed"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if(state.running)builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"Pause",
            PendingIntent.getBroadcast(context,1003,Intent(context,AlarmReceiver::class.java).setAction("pause"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
        return builder.build()
    }
    fun sessionStarted(context:Context,finishAt:Long,mode:String="focus"){
        try{context.getSystemService(NotificationManager::class.java).notify(1002,sessionNotification(context,finishAt,mode))}catch (_:SecurityException){}
    }
    fun sessionStopped(context:Context){
        TimerService.stop(context)
        context.getSystemService(NotificationManager::class.java).cancel(1002)
    }
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
    // Intent extras are not part of PendingIntent equality; encode each task id in its URI.
    private fun taskPi(c:Context,id:String):PendingIntent=PendingIntent.getBroadcast(c,0,
        Intent(c,AlarmReceiver::class.java).setAction("task")
            .setData(android.net.Uri.parse("staycalm://plan/"+android.net.Uri.encode(id)))
            .putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun scheduleTask(c:Context,item:PlanItem){
        cancelTask(c,item.id)
        if(!item.remind || item.done)return
        val dateTime=runCatching{LocalDateTime.of(LocalDate.parse(item.date),LocalTime.parse(item.time))}.getOrNull()?:return
        val whenMs=dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(whenMs<=System.currentTimeMillis())return
        val alarms=manager(c)
        val pending=taskPi(c,item.id)
        if(preciseAllowed(c)){
            try{alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMs,pending)}
            catch (_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMs,pending)}
        }else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMs,pending)
    }
    fun cancelTask(c:Context,id:String){
        manager(c).cancel(taskPi(c,id))
        // Cancel legacy-version reminders on upgrade, including old pending intent IDs.
        manager(c).cancel(pi(c,"task",id.hashCode()))
    }
}

class AlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action=="pause"){FocusStore(context).pause()}
        if(intent.action=="timer_notification_dismissed"){
            val store=FocusStore(context)
            if(store.lockTimerNotification() && !store.completeIfDue()){
                val state=store.state()
                if(state.running)Notice.sessionStarted(context,state.endWall,state.mode)
            }
        }
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
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED &&
            intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action!=Intent.ACTION_TIME_CHANGED &&
            intent.action!=Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action!=AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)return
        if(intent.action==AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            !FocusAlarms.preciseAllowed(context))return
        val store=FocusStore(context)
        store.completeIfDue()
        SessionUninstallGuard.releaseIfIdle(context)
        val state=store.state()
        if(state.running){
            FocusAlarms.scheduleFocus(context,store.remaining(state))
            TimerService.ensureRunning(context)
        }
        store.tasks().filter{it.remind&&!it.done}.forEach{FocusAlarms.scheduleTask(context,it)}
        MotivationAlarms.schedule(context)
    }
}
