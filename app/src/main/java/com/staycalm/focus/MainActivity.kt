package com.staycalm.focus

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.*

private val Ink=Color(0xFF29212D)
private val Rose=Color(0xFFC45E83)
private val Blush=Color(0xFFF5E4E9)
private val Fog=Color(0xFFF9F5F6)
private val Sub=Color(0xFF897F88)
private val Cream=Color(0xFFFFFCFB)
private val Green=Color(0xFF447B65)
private val DarkGreen=Color(0xFF305C50)
private val Border=Color(0xFFEEE5E8)

private val StaycalmColors=lightColorScheme(primary=Rose,onPrimary=Color.White,background=Fog,onBackground=Ink,surface=Color.White,onSurface=Ink,secondary=Green)

class MainActivity:ComponentActivity(){
    private lateinit var store:FocusStore
    override fun onCreate(savedInstanceState:android.os.Bundle?){super.onCreate(savedInstanceState);store=FocusStore(this);Notice.channels(this)
        setContent {
            var page by remember{mutableIntStateOf(0)}
            var tick by remember{mutableLongStateOf(0L)}
            var revision by remember{mutableIntStateOf(0)}
            var sound by remember{mutableStateOf(store.lastSound())}
            var playing by remember{mutableStateOf(false)}
            var openSound by remember{mutableStateOf(false)}
            var editTask by remember{mutableStateOf<PlanItem?>(null)}
            var adding by remember{mutableStateOf(false)}
            var selectedDate by remember{mutableStateOf(LocalDate.now())}
            var showReset by remember{mutableStateOf(false)}
            var dnd by remember{mutableStateOf(store.enableDnd())}
            var goal by remember{mutableFloatStateOf(store.dailyGoal().toFloat())}
            var volume by remember{mutableFloatStateOf(store.soundVolume())}
            var duration by remember{mutableIntStateOf(store.state().durationMinutes)}
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
            LaunchedEffect(Unit){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
            LaunchedEffect(Unit){while(true){tick=System.currentTimeMillis();if(store.completeIfDue())revision++;delay(300)}}
            val current=remember(revision,tick){store.state()}
            val left=store.remaining(current)
            val logs=remember(revision){store.logs()}
            val plans=remember(revision){store.tasks()}
            val total=logs.sumOf{it.minutes}
            val stage=Evolution.stage(total)
            val today=logs.filter{Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()}.sumOf{it.minutes}
            val titleStyle=FontFamily(Font(com.staycalm.focus.R.font.manrope))
            MaterialTheme(colorScheme=StaycalmColors,typography=Typography(
                displayLarge=TextStyle(fontFamily=titleStyle,fontWeight=FontWeight.Bold),
                headlineLarge=TextStyle(fontFamily=titleStyle,fontWeight=FontWeight.Bold),
                titleLarge=TextStyle(fontFamily=titleStyle,fontWeight=FontWeight.SemiBold),
                bodyMedium=TextStyle(fontFamily=titleStyle),
                labelLarge=TextStyle(fontFamily=titleStyle,fontWeight=FontWeight.SemiBold)
            )){
                Scaffold(containerColor=Fog,bottomBar={
                    NavigationBar(containerColor=Cream,tonalElevation=0.dp){
                        listOf(Triple("Focus",Icons.Rounded.Timer,0),Triple("Planner",Icons.Rounded.CalendarMonth,1),Triple("Insights",Icons.Rounded.BarChart,2),Triple("Settings",Icons.Rounded.Tune,3)).forEach{(name,icon,index)->
                            NavigationBarItem(selected=page==index,onClick={page=index},icon={Icon(icon,name)},label={Text(name,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Rose,selectedTextColor=Rose,indicatorColor=Blush))
                        }
                    }
                }){pad->
                    Column(Modifier.fillMaxSize().padding(pad)){
                        Row(Modifier.fillMaxWidth().padding(start=23.dp,end=23.dp,top=23.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                            Column{Text("STAYCALM",fontWeight=FontWeight.ExtraBold,letterSpacing=3.sp,fontSize=17.sp,color=Ink);Text("THE FOCUS STUDIO",fontSize=9.sp,letterSpacing=2.sp,color=Sub)}
                            Surface(shape=CircleShape,color=Blush){Icon(Icons.Rounded.Spa,"Stay calm",tint=Rose,modifier=Modifier.padding(12.dp).size(20.dp))}
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                            Spacer(Modifier.height(8.dp))
                            when(page){
                                0->{
                                    Header("Your quiet space.","Make today count, gently.")
                                    Surface(shape=RoundedCornerShape(27.dp),color=Color.White){
                                        Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                                                SmallLabel("YOUR COMPANION")
                                                Text("STAGE ${stage+1}/6",fontSize=11.sp,fontWeight=FontWeight.Bold,color=Rose,letterSpacing=1.sp)
                                            }
                                            TurtleScene(stage,Modifier.fillMaxWidth().height(205.dp))
                                            Text(Evolution.title(stage),fontWeight=FontWeight.Bold,fontSize=20.sp,color=Ink)
                                            Spacer(Modifier.height(5.dp))
                                            Text(if(stage==5)"A companion grown through focus." else "Every finished minute helps your turtle grow.",fontSize=12.sp,color=Sub,textAlign=TextAlign.Center)
                                            Spacer(Modifier.height(15.dp))
                                            val prev=listOf(0,30,90,180,360,720)[stage]
                                            val next=Evolution.next(total)
                                            LinearProgressIndicator(progress={if(next==null)1f else ((total-prev).toFloat()/(next-prev)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=Rose,trackColor=Blush)
                                            Spacer(Modifier.height(7.dp))
                                            Text(if(next==null)"Fully evolved · Keep going" else "${next-total} min to next chapter",color=Sub,fontSize=11.sp)
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(27.dp),color=Color.White){
                                        Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                            SmallLabel("FOCUS SESSION")
                                            Spacer(Modifier.height(14.dp))
                                            Box(contentAlignment=Alignment.Center){
                                                CircularProgressIndicator(progress={if(current.running||left<current.durationMinutes*60_000L)1f-left/(current.durationMinutes*60_000f) else 0f},modifier=Modifier.size(207.dp),strokeWidth=8.dp,trackColor=Blush,color=Rose,strokeCap=StrokeCap.Round)
                                                Column(horizontalAlignment=Alignment.CenterHorizontally){Text("%02d:%02d".format(left/60000,(left/1000)%60),fontSize=45.sp,color=Ink,fontWeight=FontWeight.Light);Text(if(current.running)"IN YOUR ELEMENT" else "A MOMENT TO BEGIN",fontSize=10.sp,letterSpacing=1.6.sp,color=Sub)}
                                            }
                                            Spacer(Modifier.height(20.dp))
                                            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                                listOf(15,25,45,60).forEach{n->FilterChip(selected=duration==n,onClick={if(!current.running){duration=n;store.configure(n);revision++}},label={Text("${n}m",fontSize=12.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Blush,selectedLabelColor=Rose))}
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            Button(onClick={
                                                if(current.running){store.pause()}else{store.start()}
                                                revision++
                                            },modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(17.dp)){
                                                Icon(if(current.running)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(9.dp));Text(if(current.running)"Pause focus" else if(left<current.durationMinutes*60_000L)"Resume focus" else "Begin focus",fontWeight=FontWeight.Bold)
                                            }
                                            if(current.running||left<current.durationMinutes*60_000L)TextButton(onClick={store.abandon();revision++}){Text("End session",color=Sub)}
                                        }
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
                                        Text("Soundscapes",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.weight(1f))
                                        TextButton(onClick={openSound=true}){Text("See all",color=Rose)}
                                    }
                                    Surface(shape=RoundedCornerShape(22.dp),color=Color.White){
                                        Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){
                                            Surface(shape=RoundedCornerShape(15.dp),color=Blush){Icon(Icons.Rounded.GraphicEq,null,tint=Rose,modifier=Modifier.padding(14.dp))}
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)){Text(Ambients.all.first{it.id==sound}.label,fontWeight=FontWeight.Bold,color=Ink);Text("Offline ambience",fontSize=12.sp,color=Sub)}
                                            IconButton(onClick={
                                                if(playing){stopService(Intent(this@MainActivity,SoundService::class.java));playing=false}
                                                else {val i=Intent(this@MainActivity,SoundService::class.java).putExtra("sound",sound);try{startForegroundService(i);playing=true}catch (_:Exception){playing=false}}
                                            }){Icon(if(playing)Icons.Rounded.StopCircle else Icons.Rounded.PlayCircle,null,tint=Rose,modifier=Modifier.size(32.dp))}
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                        MiniMetric("${today} min","TODAY",Modifier.weight(1f))
                                        MiniMetric("${store.dailyGoal()} min","DAILY GOAL",Modifier.weight(1f))
                                    }
                                }
                                1->{
                                    Header("Plan with purpose.","A little structure, more room to breathe.")
                                    val month=YearMonth.from(selectedDate)
                                    Surface(shape=RoundedCornerShape(25.dp),color=Color.White){
                                        Column(Modifier.padding(16.dp)){
                                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                                                IconButton(onClick={selectedDate=selectedDate.minusMonths(1).withDayOfMonth(1)}){Icon(Icons.Rounded.ChevronLeft,null)}
                                                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                                                IconButton(onClick={selectedDate=selectedDate.plusMonths(1).withDayOfMonth(1)}){Icon(Icons.Rounded.ChevronRight,null)}
                                            }
                                            Row(Modifier.fillMaxWidth()){listOf("M","T","W","T","F","S","S").forEach{Box(Modifier.weight(1f).height(32.dp),contentAlignment=Alignment.Center){Text(it,fontSize=11.sp,color=Sub)}}}
                                            val first=month.atDay(1).dayOfWeek.value-1
                                            val dates=(0 until (first+month.lengthOfMonth()+6)/7*7).map{if(it<first||it>=first+month.lengthOfMonth())null else month.atDay(it-first+1)}
                                            dates.chunked(7).forEach{week->Row(Modifier.fillMaxWidth()){week.forEach{day->
                                                Box(Modifier.weight(1f).height(44.dp).clickable(enabled=day!=null){if(day!=null)selectedDate=day},contentAlignment=Alignment.Center){
                                                    if(day!=null){
                                                        if(day==selectedDate)Box(Modifier.size(35.dp).background(Rose,CircleShape))
                                                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                                                            Text("${day.dayOfMonth}",fontSize=13.sp,fontWeight=if(day==selectedDate)FontWeight.Bold else FontWeight.Medium,color=if(day==selectedDate)Color.White else Ink)
                                                            if(plans.any{it.date==day.toString()})Box(Modifier.padding(top=2.dp).size(4.dp).background(if(day==selectedDate)Color.White else Rose,CircleShape))
                                                        }
                                                    }
                                                }
                                            }}}
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),fontWeight=FontWeight.Bold,fontSize=18.sp,color=Ink);Text("${plans.count{it.date==selectedDate.toString()}} planned tasks",color=Sub,fontSize=12.sp)}
                                        FilledIconButton(onClick={adding=true},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Rose)){Icon(Icons.Rounded.Add,"New task")}
                                    }
                                    val dayPlans=plans.filter{it.date==selectedDate.toString()}.sortedBy{it.time}
                                    if(dayPlans.isEmpty())EmptyPanel("A fresh page","Add a small intention for this day.")
                                    dayPlans.forEach{task->
                                        Surface(shape=RoundedCornerShape(19.dp),color=Color.White){
                                            Row(Modifier.fillMaxWidth().clickable{editTask=task}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                                Checkbox(checked=task.done,onCheckedChange={store.replaceTask(task.copy(done=it));revision++},colors=CheckboxDefaults.colors(checkedColor=Rose))
                                                Column(Modifier.weight(1f)){Text(task.title,fontWeight=FontWeight.SemiBold,color=if(task.done)Sub else Ink,textDecoration=if(task.done)TextDecoration.LineThrough else null)
                                                    Text("${task.time}  ·  ${task.duration} min${if(task.remind)"  ·  Reminder" else ""}",color=Sub,fontSize=11.sp)}
                                                Icon(Icons.Rounded.Edit,null,tint=Sub,modifier=Modifier.size(17.dp))
                                            }
                                        }
                                    }
                                    Button(onClick={adding=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Plan something")}
                                }
                                2->{
                                    Header("Progress, softly.","Consistency is a practice.")
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                        MiniMetric("${today} min","TODAY",Modifier.weight(1f))
                                        MiniMetric("${total} min","ALL TIME",Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                        MiniMetric("${logs.size}","SESSIONS",Modifier.weight(1f))
                                        MiniMetric("${stage+1} / 6","TURTLE STAGE",Modifier.weight(1f))
                                    }
                                    Surface(shape=RoundedCornerShape(25.dp),color=Color.White){
                                        Column(Modifier.fillMaxWidth().padding(20.dp)){
                                            Text("Last seven days",fontWeight=FontWeight.Bold,fontSize=18.sp,color=Ink)
                                            Spacer(Modifier.height(26.dp))
                                            val days=(6 downTo 0).map{LocalDate.now().minusDays(it.toLong())}
                                            val tallies=days.map{day->logs.filter{Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()==day}.sumOf{it.minutes}}
                                            val maxMinutes=tallies.maxOrNull()?.coerceAtLeast(30)?:30
                                            Row(Modifier.fillMaxWidth().height(140.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom){
                                                days.forEachIndexed{idx,day->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){
                                                    Text(if(tallies[idx]>0)"${tallies[idx]}" else "",fontSize=10.sp,color=Sub)
                                                    Spacer(Modifier.height(4.dp))
                                                    Box(Modifier.fillMaxWidth(.67f).height((max(5f,(tallies[idx].toFloat()/maxMinutes)*90f)).dp).background(if(day==LocalDate.now())Rose else Blush,RoundedCornerShape(topStart=6.dp,topEnd=6.dp)))
                                                    Spacer(Modifier.height(7.dp))
                                                    Text(day.dayOfWeek.name.take(1),fontSize=12.sp,color=Sub)
                                                }}
                                            }
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(23.dp),color=Color.White){
                                        Column(Modifier.padding(20.dp)){
                                            Text("Small steps become chapters",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                                            TurtleScene(stage,Modifier.fillMaxWidth().height(195.dp))
                                            Text("Your companion evolves only after completed focus sessions.",color=Sub,fontSize=13.sp)
                                        }
                                    }
                                }
                                else->{
                                    Header("Make it yours.","A little control goes a long way.")
                                    Surface(shape=RoundedCornerShape(23.dp),color=Color.White){
                                        Column(Modifier.fillMaxWidth().padding(19.dp)){
                                            Text("Daily intention",fontSize=17.sp,fontWeight=FontWeight.Bold)
                                            Text("${goal.toInt()} min per day",color=Rose,fontWeight=FontWeight.SemiBold)
                                            Slider(value=goal,onValueChange={goal=it},onValueChangeFinished={store.setDailyGoal(goal.toInt())},valueRange=30f..360f,steps=10)
                                            HorizontalDivider(color=Border)
                                            Spacer(Modifier.height(10.dp))
                                            Row(verticalAlignment=Alignment.CenterVertically){
                                                Column(Modifier.weight(1f)){Text("Focus Do Not Disturb",fontWeight=FontWeight.Bold);Text("Quiet non-priority notifications while you focus.",fontSize=12.sp,color=Sub)}
                                                Switch(checked=dnd,onCheckedChange={dnd=it;store.setDnd(it);if(it&&!FocusDnd.allowed(this@MainActivity))startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))})
                                            }
                                            Text("Requires system permission. Android may allow priority calls, alarms and user-defined exceptions.",fontSize=11.sp,color=Sub)
                                            TextButton(onClick={startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}){Text("Manage interruption access")}
                                            HorizontalDivider(color=Border)
                                            Spacer(Modifier.height(12.dp))
                                            Text("Sound volume",fontWeight=FontWeight.Bold)
                                            Slider(value=volume,onValueChange={volume=it;store.setVolume(it);if(playing)startService(Intent(this@MainActivity,SoundService::class.java).setAction("volume"))})
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(22.dp),color=Color.White){
                                        Column(Modifier.padding(19.dp)){Text("Private by design",fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(7.dp));Text("Your plans, focus history and turtle live on this device. No accounts, cloud backup, analytics trackers or AI.",color=Sub,fontSize=13.sp)}
                                    }
                                    Text("STAYCALM · VERSION 2.0",fontSize=10.sp,letterSpacing=1.5.sp,color=Sub)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
            if(openSound)AlertDialog(onDismissRequest={openSound=false},title={Text("Find your atmosphere")},text={
                Column(Modifier.heightIn(max=455.dp).verticalScroll(rememberScrollState())){
                    Ambients.all.forEach {ambient->
                        Row(Modifier.fillMaxWidth().clickable{
                            sound=ambient.id;store.setSound(sound);openSound=false
                            if(playing)startService(Intent(this@MainActivity,SoundService::class.java).putExtra("sound",sound))
                        }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Rounded.GraphicEq,null,tint=if(sound==ambient.id)Rose else Sub)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){Text(ambient.label,fontWeight=FontWeight.Bold);Text(ambient.detail,fontSize=12.sp,color=Sub)}
                            if(sound==ambient.id)Icon(Icons.Rounded.Check,null,tint=Rose)
                        }
                        HorizontalDivider(color=Border)
                    }
                }
            },confirmButton={TextButton(onClick={openSound=false}){Text("Done")}})
            if(adding||editTask!=null){
                PlanEditor(editTask,selectedDate,onDismiss={adding=false;editTask=null},onSave={title,date,time,duration,remind->
                    val old=editTask
                    if(old==null)store.addTask(title,date.toString(),time,duration,remind)
                    else store.replaceTask(old.copy(title=title,date=date.toString(),time=time,duration=duration,remind=remind))
                    selectedDate=date;revision++;adding=false;editTask=null
                },onDelete={item->store.deleteTask(item);revision++;editTask=null;adding=false})
            }
        }
    }
}

@Composable private fun Header(title:String,subtitle:String){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Ink,letterSpacing=(-1).sp);Text(subtitle,fontSize=13.sp,color=Sub)}
}
@Composable private fun SmallLabel(s:String){Text(s,fontSize=10.sp,letterSpacing=1.3.sp,fontWeight=FontWeight.Bold,color=Sub)}
@Composable private fun MiniMetric(value:String,label:String,modifier:Modifier=Modifier){
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=Color.White){
        Column(Modifier.padding(19.dp)){Text(value,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(3.dp));SmallLabel(label)}
    }
}
@Composable private fun EmptyPanel(title:String,subtitle:String){
    Surface(shape=RoundedCornerShape(23.dp),color=Color.White){
        Column(Modifier.fillMaxWidth().padding(vertical=34.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Icon(Icons.Rounded.AutoAwesome,null,tint=Rose,modifier=Modifier.size(26.dp));Spacer(Modifier.height(8.dp))
            Text(title,fontWeight=FontWeight.Bold,color=Ink);Text(subtitle,fontSize=12.sp,color=Sub)
        }
    }
}
@Composable private fun PlanEditor(item:PlanItem?,initialDate:LocalDate,onDismiss:()->Unit,onSave:(String,LocalDate,String,Int,Boolean)->Unit,onDelete:(PlanItem)->Unit){
    var title by remember(item){mutableStateOf(item?.title?:"")}
    var date by remember(item){mutableStateOf(runCatching{LocalDate.parse(item?.date?:initialDate.toString())}.getOrDefault(initialDate))}
    var time by remember(item){mutableStateOf(item?.time?:"09:00")}
    var duration by remember(item){mutableIntStateOf(item?.duration?:25)}
    var remind by remember(item){mutableStateOf(item?.remind?:true)}
    val context=androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(item==null)"New intention" else "Edit intention",fontWeight=FontWeight.Bold)},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(title,{title=it},label={Text("What will you work on?")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={
                DatePickerDialog(context,{_,year,month,day->date=LocalDate.of(year,month+1,day)},date.year,date.monthValue-1,date.dayOfMonth).show()
            },modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))}
            OutlinedButton(onClick={
                val parts=time.split(":");TimePickerDialog(context,{_,hour,min->time="%02d:%02d".format(hour,min)},parts[0].toIntOrNull()?:9,parts.getOrNull(1)?.toIntOrNull()?:0,true).show()
            },modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Schedule,null);Spacer(Modifier.width(8.dp));Text(time)}
            Text("Expected time · ${duration} min",fontSize=13.sp,color=Sub)
            Slider(value=duration.toFloat(),onValueChange={duration=(it/5).roundToInt()*5},valueRange=5f..180f)
            Row(verticalAlignment=Alignment.CenterVertically){Text("Remind me",modifier=Modifier.weight(1f));Switch(checked=remind,onCheckedChange={remind=it})}
            if(item!=null)TextButton(onClick={onDelete(item)}){Text("Delete task",color=Rose)}
        }
    },confirmButton={TextButton(enabled=title.isNotBlank(),onClick={onSave(title.trim(),date,time,duration,remind)}){Text("Save plan")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun TurtleScene(stage:Int,modifier:Modifier=Modifier){
    val transition=rememberInfiniteTransition(label="breathing")
    val breathe by transition.animateFloat(initialValue=.96f,targetValue=1.035f,animationSpec=infiniteRepeatable(tween(1800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="float")
    val drift by transition.animateFloat(initialValue=-3.5f,targetValue=3.5f,animationSpec=infiniteRepeatable(tween(2300,easing=EaseInOutSine),RepeatMode.Reverse),label="drift")
    val crack by animateFloatAsState(targetValue=if(stage>=1)1f else 0f,animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy,stiffness=Spring.StiffnessLow),label="crack")
    val open by animateFloatAsState(targetValue=if(stage>=2)1f else 0f,animationSpec=spring(dampingRatio=Spring.DampingRatioLowBouncy,stiffness=Spring.StiffnessVeryLow),label="hatch")
    val sizeScale by animateFloatAsState(targetValue=when(stage){3->.69f;4->.88f;5->1.05f;else->.7f},animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy,stiffness=Spring.StiffnessVeryLow),label="grow")
    Canvas(modifier){
        val k=min(size.width/250f,size.height/220f)
        drawContext.transform.translate(size.width/2f,size.height/2f)
        drawContext.transform.scale(k,k,Offset.Zero)
        drawOval(Color(0xFFEDE4E5),Offset(-87f,82f),Size(174f,15f))
        drawCircle(Blush,98f,Offset.Zero,alpha=.25f)
        if(stage<=2){
            val floating=drift
            if(stage==2){
                drawOval(Green,Offset(-38f,-59f+floating),Size(76f,93f))
                drawCircle(Ink,4.3f,Offset(-16f,-31f+floating));drawCircle(Ink,4.3f,Offset(16f,-31f+floating))
                drawArc(Ink,15f,150f,false,Offset(-10f,-28f+floating),Size(20f,15f),style=Stroke(width=2.2f))
            }
            drawOval(Color(0xFFE6CCCF),Offset(-63f,-75f+floating),Size(126f,160f))
            drawOval(Color(0xFFFFF7F1),Offset(-57f,-71f+floating),Size(114f,150f))
            val jagged=Path().apply{
                moveTo(-55f,-8f+floating);lineTo(-35f,-19f+floating);lineTo(-24f,-8f+floating)
                lineTo(-9f,-27f+floating);lineTo(9f,-11f+floating);lineTo(25f,-26f+floating)
                lineTo(39f,-14f+floating);lineTo(56f,-23f+floating)
            }
            if(stage>=1)drawPath(jagged,Rose,alpha=crack,style=Stroke(3.1f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            if(stage==2){
                val topY=-open*40f
                drawArc(Color(0xFFFFF7F1),180f,180f,true,Offset(-57f,-75f+floating+topY),Size(114f,80f))
                drawPath(jagged,Rose,style=Stroke(2.5f))
                drawArc(Color(0xFFE3CED0),0f,180f,false,Offset(-60f,7f+floating),Size(120f,79f),style=Stroke(4f))
            }
        }else{
            drawContext.transform.scale(sizeScale*breathe,sizeScale*breathe,Offset.Zero)
            val y=drift
            drawOval(Green,Offset(-72f,24f+y),Size(52f,45f))
            drawOval(Green,Offset(22f,24f+y),Size(52f,45f))
            drawOval(Green,Offset(-74f,-23f+y),Size(38f,41f))
            drawOval(Green,Offset(37f,-23f+y),Size(38f,41f))
            val tail=Path().apply{moveTo(3f,69f+y);lineTo(-9f,87f+y);lineTo(-19f,65f+y);close()}
            drawPath(tail,Green)
            drawOval(DarkGreen,Offset(-63f,-46f+y),Size(126f,120f))
            drawOval(Color(0xFF578B76),Offset(-52f,-36f+y),Size(104f,100f))
            val seam=Color(0xFF9CC4AA)
            drawLine(seam,Offset(0f,-30f+y),Offset(0f,62f+y),3f)
            drawLine(seam,Offset(-48f,17f+y),Offset(48f,17f+y),3f)
            drawLine(seam,Offset(-42f,-11f+y),Offset(0f,17f+y),3f)
            drawLine(seam,Offset(42f,-11f+y),Offset(0f,17f+y),3f)
            drawLine(seam,Offset(-40f,42f+y),Offset(0f,17f+y),3f)
            drawLine(seam,Offset(40f,42f+y),Offset(0f,17f+y),3f)
            drawOval(Green,Offset(-51f,-89f+y),Size(102f,80f))
            drawCircle(Ink,5f,Offset(-20f,-51f+y));drawCircle(Ink,5f,Offset(20f,-51f+y))
            drawCircle(Color.White,1.8f,Offset(-21.5f,-53f+y));drawCircle(Color.White,1.8f,Offset(18.5f,-53f+y))
            drawArc(Ink,10f,160f,false,Offset(-13f,-42f+y),Size(26f,15f),style=Stroke(2.5f))
            drawCircle(Color(0xFFDB9E9A),7f,Offset(-38f,-41f+y),alpha=.55f)
            drawCircle(Color(0xFFDB9E9A),7f,Offset(38f,-41f+y),alpha=.55f)
        }
    }
}
