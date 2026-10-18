package com.staycalm.focus

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlin.math.*
import java.util.Calendar
import kotlin.random.Random

private val rose=Color(0xFFD56888)
private val ink=Color(0xFF29212A)
private val paper=Color(0xFFFCF7F8)
private val muted=Color(0xFF897A85)
private val pale=Color(0xFFF6E8ED)
private val moss=Color(0xFF5A8976)
private val shell=Color(0xFF3E675E)
private val background=Color(0xFFF9F4F5)
private val card=Color.White
private const val PREF="staycalm_offline"

class MainActivity:ComponentActivity(){
    private val prefs by lazy {getSharedPreferences(PREF,MODE_PRIVATE)}
    private val notificationManager by lazy {getSystemService(NotificationManager::class.java)}
    private fun enableDnd():Boolean{
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        if(!prefs.contains("prior_filter"))prefs.edit().putInt("prior_filter",notificationManager.currentInterruptionFilter).apply()
        return try {notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); true }catch (_:Exception){false}
    }
    private fun restoreDnd(){
        if(!prefs.contains("prior_filter"))return
        try {
            if(notificationManager.isNotificationPolicyAccessGranted) notificationManager.setInterruptionFilter(prefs.getInt("prior_filter",NotificationManager.INTERRUPTION_FILTER_ALL))
        }catch (_:Exception){} finally {prefs.edit().remove("prior_filter").apply()}
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        setContent {
            var tab by remember {mutableIntStateOf(0)}
            var selected by remember {mutableIntStateOf(prefs.getInt("duration",25))}
            var remaining by remember {mutableLongStateOf(prefs.getLong("remaining",selected*60L))}
            var deadline by remember {mutableLongStateOf(prefs.getLong("deadline",0))}
            var running by remember {mutableStateOf(prefs.getBoolean("running",false))}
            var sessions by remember {mutableIntStateOf(prefs.getInt("sessions",0))}
            var minutes by remember {mutableIntStateOf(prefs.getInt("minutes",0))}
            var today by remember {mutableIntStateOf(prefs.getInt("today_minutes",0))}
            var music by remember {mutableStateOf(prefs.getString("sound","Rain")?:"Rain")}
            var playing by remember {mutableStateOf(false)}
            var dnd by remember {mutableStateOf(prefs.getBoolean("enable_dnd",false))}
            var dialog by remember {mutableStateOf(false)}
            var tasks by remember {mutableStateOf(readTasks())}
            var newTask by remember {mutableStateOf("")}
            val requestNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
            val sounds=listOf("Rain","Ocean","Forest","White Noise","Brown Noise","Night Breeze")
            val soundsIcon=listOf("☔","≈","♣","◉","◌","☾")
            val stage=when {minutes<25->0;minutes<60->1;minutes<120->2;minutes<240->3;minutes<480->4;else->5}
            fun stopAudio(){stopService(Intent(this,SoundService::class.java));playing=false}
            fun saveTimer(){
                prefs.edit().putInt("duration",selected).putLong("remaining",remaining)
                    .putLong("deadline",deadline).putBoolean("running",running).apply()
            }
            fun finishSession(){
                running=false;deadline=0L;remaining=selected*60L
                sessions++;minutes+=selected;today+=selected
                prefs.edit().putInt("sessions",sessions).putInt("minutes",minutes).putInt("today_minutes",today).apply()
                saveTimer();restoreDnd()
                if(playing) stopAudio()
            }
            LaunchedEffect(Unit) {
                val day=Calendar.getInstance().get(Calendar.YEAR)*1000+Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if(prefs.getInt("day",0)!=day){today=0;prefs.edit().putInt("day",day).putInt("today_minutes",0).apply()}
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            LaunchedEffect(running,deadline){
                while(running){
                    val left=((deadline-System.currentTimeMillis()+999)/1000).coerceAtLeast(0)
                    remaining=left
                    if(left<=0){finishSession();break}
                    delay(250)
                }
            }
            MaterialTheme(colorScheme=lightColorScheme(primary=rose,onPrimary=Color.White,background=background,surface=card,onSurface=ink)){
                Scaffold(containerColor=background,bottomBar={
                    NavigationBar(containerColor=Color.White,tonalElevation=4.dp){
                        listOf(Triple("Focus",Icons.Filled.Timer,0),Triple("Tasks",Icons.Filled.CheckCircle,1),Triple("Insights",Icons.Filled.Insights,2),Triple("Settings",Icons.Filled.Settings,3)).forEach{(name,ic,id)->
                            NavigationBarItem(selected=tab==id,onClick={tab=id},icon={Icon(ic,name)},label={Text(name)},colors=NavigationBarItemDefaults.colors(selectedIconColor=rose,selectedTextColor=rose,indicatorColor=pale))
                        }
                    }
                }){inner->
                    Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal=23.dp),verticalArrangement=Arrangement.spacedBy(19.dp)){
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text("STAYCALM",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=2.sp,color=ink);Text("MAKE ROOM FOR YOUR MIND",fontSize=10.sp,letterSpacing=2.sp,color=muted)}
                            Box(Modifier.size(44.dp).background(pale, CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Filled.Spa,null,tint=rose)}
                        }
                        when(tab){
                            0->{
                                Text("Find your flow.",fontSize=33.sp,fontWeight=FontWeight.Bold,color=ink,letterSpacing=(-1).sp)
                                Text("Quiet progress. One session at a time.",color=muted,fontSize=14.sp)
                                Surface(shape=RoundedCornerShape(27.dp),color=Color.White,shadowElevation=1.dp){
                                    Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)){
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("YOUR COMPANION",fontSize=11.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.Bold,color=muted);Text(listOf("Egg","First cracks","Hatching","Hatchling","Young turtle","Adult turtle")[stage],color=rose,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}
                                        Turtle(stage,Modifier.fillMaxWidth().height(183.dp))
                                        Text("GROW YOUR TURTLE",fontWeight=FontWeight.Bold,letterSpacing=1.3.sp,fontSize=13.sp,color=ink)
                                        val next=listOf(25,60,120,240,480)
                                        if(stage<5){val prev=if(stage==0)0 else next[stage-1]; val goal=next[stage]; LinearProgressIndicator(progress={((minutes-prev).toFloat()/(goal-prev)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=rose,trackColor=pale);Text("${goal-minutes} focus minutes until your next milestone",fontSize=11.sp,color=muted)}
                                        else Text("Fully grown. Keep your momentum.",color=muted,fontSize=12.sp)
                                    }
                                }
                                Surface(shape=RoundedCornerShape(27.dp),color=Color.White){
                                    Column(Modifier.padding(23.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                        Text("DEEP FOCUS",fontSize=11.sp,fontWeight=FontWeight.Bold,color=muted,letterSpacing=2.sp)
                                        Spacer(Modifier.height(13.dp))
                                        Box(contentAlignment=Alignment.Center){
                                            CircularProgressIndicator(progress={if(running)1f-(remaining.toFloat()/(selected*60f)) else 0f},modifier=Modifier.size(204.dp),strokeWidth=9.dp,color=rose,trackColor=pale,strokeCap=StrokeCap.Round)
                                            Column(horizontalAlignment=Alignment.CenterHorizontally){Text("%02d:%02d".format(remaining/60,remaining%60),fontSize=52.sp,fontWeight=FontWeight.Light,color=ink);Text(if(running)"STAY PRESENT" else "READY WHEN YOU ARE",letterSpacing=1.5.sp,fontSize=10.sp,color=muted)}
                                        }
                                        Spacer(Modifier.height(18.dp))
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,25,45,60).forEach{n->FilterChip(selected=selected==n,onClick={if(!running){selected=n;remaining=n*60L;saveTimer()}},label={Text("${n}m")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=pale,selectedLabelColor=rose))}}
                                        Spacer(Modifier.height(10.dp))
                                        Button(onClick={
                                            if(running){remaining=((deadline-System.currentTimeMillis()+999)/1000).coerceAtLeast(0);running=false;deadline=0;saveTimer();restoreDnd()}
                                            else {deadline=System.currentTimeMillis()+remaining*1000;running=true;saveTimer();if(dnd)enableDnd()}
                                        },modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)){Icon(if(running)Icons.Filled.Pause else Icons.Filled.PlayArrow,null);Spacer(Modifier.width(10.dp));Text(if(running)"Pause session" else "Begin focus",fontWeight=FontWeight.Bold)}
                                        if(running || remaining != selected*60L)TextButton(onClick={running=false;remaining=selected*60L;deadline=0;saveTimer();restoreDnd();if(playing)stopAudio()}){Text("End without saving",color=muted)}
                                    }
                                }
                                Text("FOCUS SOUNDS",fontSize=13.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
                                Surface(shape=RoundedCornerShape(22.dp),color=Color.White){
                                    Column(Modifier.padding(13.dp)){
                                        sounds.forEachIndexed{index,s->
                                            Row(Modifier.fillMaxWidth().clickable{
                                                music=s;prefs.edit().putString("sound",s).apply()
                                                if(playing){startService(Intent(this@MainActivity,SoundService::class.java).putExtra("sound",s))}
                                            }.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                                                Box(Modifier.size(40.dp).background(pale,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Text(soundsIcon[index],fontSize=22.sp,color=rose)}
                                                Spacer(Modifier.width(13.dp));Text(s,Modifier.weight(1f),fontWeight=if(music==s)FontWeight.Bold else FontWeight.Normal,color=ink)
                                                if(music==s){IconButton(onClick={
                                                    if(playing)stopAudio()
                                                    else {if(android.os.Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){startForegroundService(Intent(this@MainActivity,SoundService::class.java).putExtra("sound",s));playing=true} else requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)}
                                                }){Icon(if(playing)Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,null,tint=rose)}}
                                                else Icon(Icons.Filled.ChevronRight,null,tint=muted)
                                            }
                                            if(index<sounds.lastIndex)HorizontalDivider(color=background)
                                        }
                                    }
                                }
                            }
                            1->{
                                Text("Your priorities.",fontSize=33.sp,fontWeight=FontWeight.Bold)
                                Text("Keep it simple. Do what matters.",color=muted)
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    OutlinedTextField(newTask,{newTask=it},Modifier.weight(1f),placeholder={Text("Add a task")},singleLine=true,shape=RoundedCornerShape(17.dp))
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick={if(newTask.isNotBlank()){tasks=tasks+Task(System.currentTimeMillis(),newTask.trim(),false);writeTasks(tasks);newTask=""}}){Icon(Icons.Filled.AddCircle,"Add task",tint=rose,modifier=Modifier.size(34.dp))}
                                }
                                tasks.forEach{t->Surface(shape=RoundedCornerShape(17.dp),color=Color.White){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                    Checkbox(t.done,{done->tasks=tasks.map{if(it.id==t.id)it.copy(done=done) else it};writeTasks(tasks)})
                                    Text(t.name,Modifier.weight(1f),color=ink,textDecoration=if(t.done)TextDecoration.LineThrough else TextDecoration.None)
                                    IconButton(onClick={tasks=tasks.filterNot{it.id==t.id};writeTasks(tasks)}){Icon(Icons.Filled.Close,"Delete",tint=muted)}
                                }}}
                                if(tasks.isEmpty())Text("Nothing on your list. Add one meaningful task.",color=muted)
                            }
                            2->{
                                Text("Your progress.",fontSize=33.sp,fontWeight=FontWeight.Bold)
                                Text("Consistency, not perfection.",color=muted)
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    Metric("TODAY","${today}m",Modifier.weight(1f))
                                    Metric("TOTAL","${minutes}m",Modifier.weight(1f))
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    Metric("SESSIONS","${sessions}",Modifier.weight(1f))
                                    Metric("TURTLE", "${stage+1}/6",Modifier.weight(1f))
                                }
                                Surface(color=Color.White,shape=RoundedCornerShape(24.dp)){
                                    Column(Modifier.fillMaxWidth().padding(22.dp)){
                                        Text("Your next evolution",fontWeight=FontWeight.Bold,fontSize=19.sp)
                                        Spacer(Modifier.height(12.dp))
                                        Turtle(stage,Modifier.fillMaxWidth().height(180.dp))
                                        Text("Every finished session helps your turtle grow.",color=muted,fontSize=13.sp)
                                    }
                                }
                            }
                            else->{
                                Text("Your space.",fontSize=33.sp,fontWeight=FontWeight.Bold)
                                Text("Personal, private and distraction-free.",color=muted)
                                Surface(color=Color.White,shape=RoundedCornerShape(22.dp)){
                                    Column(Modifier.padding(18.dp)){
                                        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Focus Do Not Disturb",fontWeight=FontWeight.Bold);Text("Restrict interruptions during active focus.",fontSize=12.sp,color=muted)};Switch(dnd,{dnd=it;prefs.edit().putBoolean("enable_dnd",it).apply();if(it && !notificationManager.isNotificationPolicyAccessGranted)startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))})}
                                        HorizontalDivider(Modifier.padding(vertical=13.dp),color=background)
                                        Text("Requires Android Do Not Disturb access. Priority contacts, alarms and system exceptions follow your device's settings.",fontSize=12.sp,color=muted)
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}){Text("Manage DND permission")}
                                    }
                                }
                                Surface(color=Color.White,shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(20.dp)){Text("Local-only storage",fontWeight=FontWeight.Bold);Text("No account. No cloud. No tracking. Your progress and tasks remain on this device.",color=muted,fontSize=13.sp)}}
                                TextButton(onClick={dialog=true}){Text("Reset turtle & progress",color=rose)}
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }
            if(dialog)AlertDialog(onDismissRequest={dialog=false},title={Text("Reset all focus progress?")},text={Text("This clears your turtle growth, completed sessions and minutes. Tasks will stay.")},confirmButton={TextButton(onClick={minutes=0;sessions=0;today=0;prefs.edit().putInt("minutes",0).putInt("sessions",0).putInt("today_minutes",0).apply();dialog=false}){Text("Reset",color=rose)}},dismissButton={TextButton(onClick={dialog=false}){Text("Cancel")}})
        }
    }
    private fun readTasks():List<Task> = getSharedPreferences(PREF,MODE_PRIVATE).getString("tasks","")!!.split("\n").mapNotNull{val p=it.split("|",limit=3);if(p.size==3)Task(p[0].toLongOrNull()?:0,p[2],p[1]=="1") else null}
    private fun writeTasks(t:List<Task>){prefs.edit().putString("tasks",t.joinToString("\n"){"${it.id}|${if(it.done)1 else 0}|${it.name.replace("|"," ").replace("\n"," ")}"}).apply()}
}
private data class Task(val id:Long,val name:String,val done:Boolean)
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier){
    Surface(modifier,shape=RoundedCornerShape(22.dp),color=Color.White){
        Column(Modifier.padding(19.dp)){Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold,color=muted,letterSpacing=1.sp);Spacer(Modifier.height(10.dp));Text(value,fontWeight=FontWeight.Bold,fontSize=34.sp,color=ink)}
    }
}

@Composable private fun Turtle(stage:Int, modifier:Modifier=Modifier){
    val transition=rememberInfiniteTransition(label="turtle")
    val bob by transition.animateFloat(initialValue=-3f,targetValue=3f,animationSpec=infiniteRepeatable(tween(1700,easing=EaseInOutSine),RepeatMode.Reverse),label="bob")
    val breathe by transition.animateFloat(initialValue=.97f,targetValue=1.025f,animationSpec=infiniteRepeatable(tween(2100,easing=EaseInOutSine),RepeatMode.Reverse),label="breathe")
    Canvas(modifier){val scale=size.minDimension/220f; val x=size.width/2f; val y=size.height/2f;drawContext.transform.translate(x,y);drawContext.transform.scale(scale,scale,Offset.Zero)
        drawOval(color=Color(0xFFE8D9DF),topLeft=Offset(-91f,83f),size=androidx.compose.ui.geometry.Size(182f,16f))
        if(stage<=2){
            drawOval(Color(0xFFF2DEDA),topLeft=Offset(-67f,-72f+bob),size=androidx.compose.ui.geometry.Size(134f,155f))
            drawOval(Color(0xFFFFF8F3),topLeft=Offset(-56f,-65f+bob),size=androidx.compose.ui.geometry.Size(112f,140f))
            if(stage>=1){val p=Path().apply{moveTo(-50f,-18f+bob);lineTo(-25f,-35f+bob);lineTo(-13f,-17f+bob);lineTo(4f,-44f+bob);lineTo(23f,-27f+bob);lineTo(52f,-45f+bob)};drawPath(p,rose,style=Stroke(width=3.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))}
            if(stage>=2){
                drawOval(moss,Offset(-31f,-55f+bob),androidx.compose.ui.geometry.Size(61f,68f))
                drawCircle(ink,3.8f,Offset(-12f,-28f+bob));drawCircle(ink,3.8f,Offset(12f,-28f+bob))
                drawArc(ink,15f,150f,false,Offset(-10f,-23f+bob),androidx.compose.ui.geometry.Size(20f,16f),style=Stroke(2f))
                drawArc(Color(0xFFEBCEC6),0f,180f,false,Offset(-65f,-10f+bob),androidx.compose.ui.geometry.Size(130f,92f),style=Stroke(4f))
            }
        }else{
            val grow=when(stage){3->.72f;4->.89f;else->1.07f}
            drawContext.transform.scale(grow*breathe,grow*breathe,Offset.Zero)
            drawOval(moss,Offset(-69f,48f+bob),androidx.compose.ui.geometry.Size(48f,28f))
            drawOval(moss,Offset(22f,48f+bob),androidx.compose.ui.geometry.Size(48f,28f))
            drawOval(moss,Offset(-64f,-39f+bob),androidx.compose.ui.geometry.Size(128f,119f))
            drawOval(shell,Offset(-59f,-43f+bob),androidx.compose.ui.geometry.Size(118f,113f))
            drawOval(Color(0xFF578979),Offset(-45f,-35f+bob),androidx.compose.ui.geometry.Size(90f,82f))
            val seam=Color(0xFF9AC1A6)
            drawLine(seam,Offset(0f,-28f+bob),Offset(0f,52f+bob),2.5f)
            drawLine(seam,Offset(-41f,5f+bob),Offset(41f,5f+bob),2.5f)
            drawLine(seam,Offset(-34f,-15f+bob),Offset(0f,5f+bob),2.5f)
            drawLine(seam,Offset(34f,-15f+bob),Offset(0f,5f+bob),2.5f)
            drawOval(moss,Offset(-49f,-83f+bob),androidx.compose.ui.geometry.Size(98f,78f))
            drawCircle(ink,4.8f,Offset(-19f,-47f+bob));drawCircle(ink,4.8f,Offset(19f,-47f+bob))
            drawArc(ink,15f,150f,false,Offset(-13f,-43f+bob),androidx.compose.ui.geometry.Size(26f,18f),style=Stroke(2.3f))
            drawCircle(Color(0xFFDFA49E),7f,Offset(-37f,-34f+bob),alpha=.7f)
            drawCircle(Color(0xFFDFA49E),7f,Offset(37f,-34f+bob),alpha=.7f)
        }
    }
}

class SoundService:Service(){
    private var track:AudioTrack?=null
    private var thread:Thread?=null
    @Volatile private var active=false
    private val channel="staycalm_audio"
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(channel,"Ambient sound playback",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        val sound=intent?.getStringExtra("sound")?:"Rain"
        val notification=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("STAYCALM • ${sound}").setContentText("Ambient sound is playing").setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)).build()
        startForeground(23,notification)
        startSound(sound)
        return START_NOT_STICKY
    }
    @Synchronized private fun startSound(sound:String){
        stopSound()
        active=true
        thread=Thread{
            val rate=22050
            val size=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
            val audio=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(size*2).setTransferMode(AudioTrack.MODE_STREAM).build()
            track=audio
            val rnd=Random(System.nanoTime());val samples=ShortArray(2048)
            var low=0.0;var middle=0.0;var wave=0.0
            try{
                audio.play()
                while(active && !Thread.currentThread().isInterrupted){
                    for(i in samples.indices){
                        val white=rnd.nextDouble(-1.0,1.0)
                        low=low*.985+white*.015
                        middle=middle*.92+white*.08
                        wave+=1.0/rate
                        val v=when(sound){
                            "Rain"->white*.10+middle*.29
                            "Ocean"->middle*(.24+.19*sin(wave*2*PI/7))+low*.9
                            "Forest"->low*.7+middle*.24+sin(wave*2*PI*520)*.018*(if(sin(wave*2*PI/3)>.75)1 else 0)
                            "White Noise"->white*.23
                            "Brown Noise"->low*1.55
                            else->low*.92+middle*.17+sin(wave*2*PI*115)*.025
                        }
                        samples[i]=(v.coerceIn(-.85,.85)*32767).toInt().toShort()
                    }
                    audio.write(samples,0,samples.size)
                }
            }catch (_:Exception){}finally{try{audio.pause();audio.flush();audio.release()}catch (_:Exception){}}
        }.apply{name="StaycalmAudio";isDaemon=true;start()}
    }
    @Synchronized private fun stopSound(){active=false;thread?.interrupt();thread=null;try{track?.pause();track?.flush()}catch (_:Exception){};track=null}
    override fun onDestroy(){stopSound();super.onDestroy()}
}
