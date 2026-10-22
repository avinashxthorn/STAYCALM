package com.staycalm.focus

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun AiStudyDay.planDraft()=AiPlanDraft("$topic: $action".take(450),date,preferredTime,minutes,
    sourceId="focus|$date|${topic.trim().lowercase()}")

@Composable
fun StaycalmAiCard(onStartBreak:(Int)->String?={"Start a break from the Focus screen when your current session is finished."},onPlanAdded:()->Unit) {
    val context=LocalContext.current
    val ai=remember{StaycalmAi(context)}
    var revision by remember{mutableIntStateOf(0)}
    var settings by remember{mutableStateOf(false)}
    var reviewing by remember{mutableStateOf<AiPlanDraft?>(null)}
    var message by remember{mutableStateOf<String?>(null)}
    val prefs=remember{AccountData.preferences(context)}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key?.startsWith("ai_")==true||key=="plans")revision++}
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(Unit){AiCoachWork.schedule(context)}
    val report=remember(revision){ai.report()}
    val enabled=remember(revision){ai.enabled()}
    val busy=remember(revision){ai.busy()}
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Staycalm AI",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("Your personal focus coach",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            Text("Insights, next steps and useful breaks, shaped around your focus and future goals.")
            Text(if(enabled)"Updates automatically as your focus and planner change." else "AI coaching is off. You can enable it in AI settings.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={settings=true}){Text("AI settings")}
            if(busy){LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("Staycalm AI is preparing your next steps…",style=MaterialTheme.typography.bodySmall)}
            if(!busy&&enabled)ai.status().takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            report?.takeIf{enabled}?.let{saved->
                Text("Updated ${Instant.ofEpochMilli(saved.generatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",style=MaterialTheme.typography.labelSmall)
                Text(saved.summary,fontWeight=FontWeight.SemiBold)
                Text("Your insights",fontWeight=FontWeight.Bold)
                saved.insights.forEach{Text("• $it")}
                HorizontalDivider()
                Text("A helpful pause",fontWeight=FontWeight.Bold,fontSize=18.sp)
                Text(saved.breakAdvice)
                Button(onClick={val result=onStartBreak(saved.breakMinutes);message=result?:"Your ${saved.breakMinutes}-minute break has started."}){Text("Take a ${saved.breakMinutes}-minute break")}
                TextButton(onClick={
                    val state=FocusStore(context).state()
                    val start=if(state.running)Instant.ofEpochMilli(state.endWall).atZone(ZoneId.systemDefault()).toLocalDateTime().plusMinutes(2) else LocalDateTime.now().plusMinutes(2)
                    reviewing=AiPlanDraft("Break: ${saved.breakAdvice}".take(450),start.toLocalDate().toString(),start.toLocalTime().withSecond(0).withNano(0).toString(),saved.breakMinutes,"break")
                }){Text("Plan this break")}
                Text("Suggested next steps",fontWeight=FontWeight.Bold,fontSize=18.sp)
                Text("Review the date, time and reminder before adding a task. Existing planner commitments are checked when you save.",style=MaterialTheme.typography.bodySmall)
                saved.days.forEach{day->
                    val draft=day.planDraft()
                    val added=FocusStore(context).tasks().any{it.id==AiPlannerPolicy.id(draft)}
                    HorizontalDivider()
                    Text("${LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM"))} · ${day.preferredTime} · ${day.minutes} min",style=MaterialTheme.typography.labelMedium)
                    Text(day.topic,fontWeight=FontWeight.SemiBold);Text(day.action)
                    TextButton(enabled=!added,onClick={reviewing=draft}){Text(if(added)"Added to planner" else "Review & add")}
                }
                Text("Adjust suggestions to your syllabus, priorities and energy.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(settings)AiCoachSettings(ai,onDismiss={settings=false},onSaved={settings=false;revision++})
    reviewing?.let{draft->AiPlanReview(draft,onDismiss={reviewing=null},onSaved={
        reviewing=null;revision++;onPlanAdded();message="Added ${it.title} to Planner."
    })}
}

@Composable
private fun AiPlanReview(initial:AiPlanDraft,onDismiss:()->Unit,onSaved:(PlanItem)->Unit){
    val context=LocalContext.current
    var title by remember{mutableStateOf(initial.title)}
    var date by remember{mutableStateOf(initial.date)}
    var time by remember{mutableStateOf(initial.time)}
    var minutes by remember{mutableStateOf(initial.minutes.toString())}
    var remind by remember{mutableStateOf(initial.remind)}
    var error by remember{mutableStateOf<String?>(null)}
    fun draft()=initial.copy(title=title,date=date,time=time,minutes=minutes.toIntOrNull()?:0,remind=remind)
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial.kind=="break")"Plan your break" else "Review focus task")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(title,{title=it.take(450)},label={Text("Task title")},modifier=Modifier.testTag("ai-plan-title"))
            OutlinedTextField(date,{date=it.take(10)},label={Text("Date · YYYY-MM-DD")},singleLine=true,modifier=Modifier.testTag("ai-plan-date"))
            OutlinedTextField(time,{time=it.take(5)},label={Text("Time · HH:mm")},singleLine=true,modifier=Modifier.testTag("ai-plan-time"))
            OutlinedTextField(minutes,{minutes=it.filter{c->c.isDigit()}.take(3)},label={Text("Minutes")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.testTag("ai-plan-minutes"))
            Row{Checkbox(remind,{remind=it});Text("Remind me when this task starts",modifier=Modifier.padding(top=10.dp))}
            TextButton(onClick={try{val d=draft();require(d.minutes in 5..180){"Enter a duration from 5 to 180 minutes."};val free=AiPlannerPolicy.freeTime(date,time,d.minutes,FocusStore(context).tasks(),LocalDateTime.now());date=free.toLocalDate().toString();time=free.toLocalTime().toString();error=null}catch(e:Exception){error=e.message}}){Text("Find free time")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick={try{val task=FocusStore(context).addAiTask(draft());onSaved(task)}catch(e:Exception){error=e.message?:"This task could not be added. Please check the details."}}){Text("Add task")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
fun AiCoachSettings(ai:StaycalmAi,onDismiss:()->Unit,onSaved:()->Unit){
    var goal by remember{mutableStateOf(ai.goal())};var weak by remember{mutableStateOf(ai.weakTopics())}
    var exam by remember{mutableStateOf(ai.examDate())};var area by remember{mutableStateOf(ai.focusArea())}
    var time by remember{mutableStateOf(ai.preferredTime())};var consent by remember{mutableStateOf(ai.enabled())}
    var nudges by remember{mutableStateOf(ai.notifications())};var interval by remember{mutableIntStateOf(ai.notificationInterval())}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Staycalm AI settings")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row{Checkbox(consent,{consent=it});Text("Automatic AI coaching",modifier=Modifier.padding(top=10.dp))}
            Row{Checkbox(nudges,{nudges=it},enabled=consent);Text("AI personalized notifications",modifier=Modifier.padding(top=10.dp))}
            Text("When enabled, AI notifications can arrive at any hour, including during focus. Your phone's notification permission and Do Not Disturb settings still apply.",style=MaterialTheme.typography.bodySmall)
            Text("Notification interval",fontWeight=FontWeight.SemiBold)
            listOf(60,120,180,360).forEach{value->Row{RadioButton(interval==value,{interval=value});Text("Every ${value/60} ${if(value==60)"hour" else "hours"}",modifier=Modifier.padding(top=13.dp))}}
            Text("Focus details (optional to edit)",fontWeight=FontWeight.SemiBold)
            OutlinedTextField(area,{area=it.take(30)},label={Text("Study, work or another focus")})
            OutlinedTextField(goal,{goal=it.take(300)},label={Text("Future goal")})
            OutlinedTextField(weak,{weak=it.take(300)},label={Text("Topics or skills to improve")})
            OutlinedTextField(exam,{exam=it.take(10)},label={Text("Target date · YYYY-MM-DD (optional)")},singleLine=true)
            OutlinedTextField(time,{time=it.take(5)},label={Text("Preferred focus time · HH:mm")},singleLine=true)
            Text("Staycalm AI processes your focus profile, recent sessions and planner details using a cloud AI service. Files, audio, passwords and chats are not sent. Suggestions update automatically and can be imperfect.",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick={try{ai.saveDetails(goal,weak,exam,area,time,consent,nudges);ai.setNotificationInterval(interval);onSaved()}catch(_:java.time.format.DateTimeParseException){error="Use a target date in YYYY-MM-DD format."}catch(e:Exception){error=e.message?:"Could not save AI settings."}}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
