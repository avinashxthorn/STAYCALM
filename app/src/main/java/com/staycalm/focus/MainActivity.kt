package com.staycalm.focus

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.*

private val Ink: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFFF3F4F5) else Color(0xFF202124)
private val Rose: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFF53D1B5) else Color(0xFF0E7564)
private val Blush: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFF273A35) else Color(0xFFE7F4EF)
private val Fog: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFF171717) else Color(0xFFF8F9FA)
private val Sub: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFFA5ACA9) else Color(0xFF66716D)
private val Border: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFF3B403E) else Color(0xFFE4E8E6)

private val StaycalmColors=lightColorScheme(
    primary=Color(0xFF0E7564),onPrimary=Color.White,
    background=Color(0xFFF8F9FA),onBackground=Color(0xFF202124),
    surface=Color.White,onSurface=Color(0xFF202124),
    secondary=Color(0xFF396A60),onSecondary=Color.White,
    secondaryContainer=Color(0xFFE7F4EF),onSecondaryContainer=Color(0xFF174B3D),
    primaryContainer=Color(0xFFE7F4EF),onPrimaryContainer=Color(0xFF174B3D),
    surfaceVariant=Color(0xFFF1F3F2),onSurfaceVariant=Color(0xFF66716D),
    outline=Color(0xFF9BA9A3),outlineVariant=Color(0xFFE4E8E6),
    tertiary=Color(0xFF0E7564))
private val StaycalmDarkColors=darkColorScheme(
    primary=Color(0xFF53D1B5),onPrimary=Color(0xFF09251E),
    background=Color(0xFF171717),onBackground=Color(0xFFF3F4F5),
    surface=Color(0xFF242424),onSurface=Color(0xFFF3F4F5),
    secondary=Color(0xFF9EDBCB),onSecondary=Color(0xFF173B31),
    secondaryContainer=Color(0xFF273A35),onSecondaryContainer=Color(0xFFCCF2E8),
    primaryContainer=Color(0xFF273A35),onPrimaryContainer=Color(0xFFCCF2E8),
    surfaceVariant=Color(0xFF303331),onSurfaceVariant=Color(0xFFA5ACA9),
    outline=Color(0xFF778B82),outlineVariant=Color(0xFF3B403E),
    tertiary=Color(0xFF78E5C6))

class MainActivity:ComponentActivity(){
    private lateinit var store:FocusStore
    override fun onStart(){super.onStart();StudyVideoVisibility.foreground()}
    override fun onStop(){StudyVideoVisibility.background();super.onStop()}
    override fun onResume(){super.onResume();if(::store.isInitialized){
        store.completeIfDue()
        SessionUninstallGuard.releaseIfIdle(this)
        TimerService.ensureRunning(this)
        store.tasks().filter{it.remind&&!it.done}.forEach{FocusAlarms.scheduleTask(this,it)}
    }}
    override fun onCreate(savedInstanceState:android.os.Bundle?){super.onCreate(savedInstanceState);store=FocusStore(this);Notice.channels(this);LauncherIconSwitcher.apply(this,Personalization(this).darkMode())
        setContent {
            var sessionUid by remember { mutableStateOf(AccountData.sessionUid(this@MainActivity)) }
            DisposableEffect(Unit) {
                val auth = if (CloudBackup.ready()) CloudBackup.auth() else null
                val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
                    // A new identity stays on Login until verification/recovery completes.
                    if (AccountData.uid(this@MainActivity) != sessionUid) sessionUid = null
                }
                auth?.addAuthStateListener(listener)
                onDispose { auth?.removeAuthStateListener(listener) }
            }
            key(sessionUid) {
            store = remember { FocusStore(this@MainActivity) }
            var showAccount by rememberSaveable { mutableStateOf(false) }
            val welcomePrefs = remember { AccountData.navigation(this@MainActivity) }
            var introDone by remember { mutableStateOf(welcomePrefs.getBoolean("intro_done", false)) }
            val signedIn = sessionUid != null
            val personalization=remember{Personalization(this@MainActivity)}
            val journal=remember{StudyJournal(this@MainActivity)}
            var isDark by remember{mutableStateOf(personalization.darkMode())}
            var profileName by remember{mutableStateOf(personalization.name())}
            var profileSubject by remember{mutableStateOf(personalization.subject())}
            var draftName by remember{mutableStateOf(personalization.name())}
            var draftSubject by remember{mutableStateOf(personalization.subject())}
            var profileReady by remember { mutableStateOf(personalization.isReady()) }
            var quoteInterval by remember{mutableIntStateOf(personalization.interval())}
            var quoteMenuExpanded by remember{mutableStateOf(false)}
            var journalRevision by remember{mutableIntStateOf(0)}
            var journalBusy by remember{mutableStateOf(false)}
            var journalError by remember{mutableStateOf<String?>(null)}
            var deleteAttachment by remember{mutableStateOf<StudyAttachment?>(null)}
            var page by rememberSaveable{mutableIntStateOf(if(intent.getBooleanExtra("open_ai_insights",false))2 else 0)}
            var tick by remember{mutableLongStateOf(0L)}
            var revision by remember{mutableIntStateOf(0)}
            var sound by remember{mutableStateOf(store.lastSound())}
            val playing=AudioPlayback.state.playing
            var audioRevision by remember{mutableIntStateOf(0)}
            var importing by remember{mutableStateOf(false)}
            var importError by remember{mutableStateOf<String?>(null)}
            val audioLibrary=remember{AudioLibrary(this@MainActivity)}
            val audioChoices=remember(audioRevision){audioLibrary.all()}
            val scope=rememberCoroutineScope()
            val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
                if(uri!=null){
                    importing=true
                    scope.launch{
                        try{
                            val imported=withContext(Dispatchers.IO){audioLibrary.import(uri)}
                            sound=imported.id;store.setSound(sound);audioRevision++
                            if(AudioPlayback.state.playing)startForegroundService(Intent(this@MainActivity,SoundService::class.java).putExtra("sound",sound))
                            else stopService(Intent(this@MainActivity,SoundService::class.java))
                        }catch(error:Exception){importError=error.message?:"Could not import this audio. Try another file."}
                        finally{importing=false}
                    }
                }
            }
            var openSound by remember{mutableStateOf(false)}
            var showMiniYouTube by remember{mutableStateOf(false)}
            var editTask by remember{mutableStateOf<PlanItem?>(null)}
            var adding by remember{mutableStateOf(false)}
            var selectedDate by remember{mutableStateOf(LocalDate.now())}
            val documentPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
                if(uris.isNotEmpty()){
                    val date=selectedDate
                    journalBusy=true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO){uris.forEach{journal.import(date,it)}}
                        } catch(error:Exception) {
                            journalError=error.message?:"Unable to save the selected file."
                        } finally {
                            journalRevision++
                            journalBusy=false
                        }
                    }
                }
            }
            var showReset by remember{mutableStateOf(false)}
            var cancellationError by remember{mutableStateOf<String?>(null)}
            var intention by remember{mutableStateOf(store.intention())}
            var completion by remember{mutableStateOf(store.completion())}
            var mode by remember{mutableStateOf(store.state().mode)}
            var dnd by remember{mutableStateOf(store.enableDnd())}
            var uninstallProtection by remember{mutableStateOf(store.uninstallProtectionEnabled())}
            var blockApps by remember{mutableStateOf(store.blockDistractingApps())}
            var blockerSetup by remember{mutableStateOf(false)}
            var showBlockerLockedAlert by remember{mutableStateOf(false)}
            var protectionError by remember{mutableStateOf<String?>(null)}
            var pendingProtectedStart by rememberSaveable{mutableStateOf(false)}
            val adminPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
                if(pendingProtectedStart){
                    pendingProtectedStart=false
                    if(SessionUninstallGuard.active(this@MainActivity)){
                        if(!store.start())protectionError="Could not start this session. Please try again."
                    }else protectionError="Device Admin was not enabled. Your session has not started. Enable protection when starting, or turn it off in Settings before starting."
                    SessionUninstallGuard.clearPending(this@MainActivity)
                    SessionUninstallGuard.releaseIfIdle(this@MainActivity)
                    revision++
                }
            }
            val startSession:()->Unit={
                if(store.blockDistractingApps() && store.state().mode=="focus" && !FocusAppBlocker.isEnabled(this@MainActivity)){
                    blockerSetup=true
                }else if(SessionUninstallGuard.releasing(this@MainActivity)){
                    protectionError="Android is finishing the previous session’s protection cleanup. Try starting again in a moment."
                }else if(store.uninstallProtectionEnabled() && !SessionUninstallGuard.ready(this@MainActivity)){
                    if(SessionUninstallGuard.beginActivation(this@MainActivity)){
                        pendingProtectedStart=true
                        try{adminPermission.launch(SessionUninstallGuard.activationIntent(this@MainActivity))}
                        catch(_:Exception){
                            pendingProtectedStart=false
                            SessionUninstallGuard.clearPending(this@MainActivity)
                            protectionError="Android could not open Device Admin activation. Your session has not started."
                        }
                    }else protectionError="Could not save the protection request. Please try again."
                }else if(!store.start())protectionError="Could not start this session. Please try again."
                revision++
            }
            var lockedTimerNotification by remember{mutableStateOf(store.lockTimerNotification())}
            var goal by remember{mutableFloatStateOf(store.dailyGoal().toFloat())}
            var volume by remember{mutableFloatStateOf(store.soundVolume())}
            var duration by remember{mutableIntStateOf(store.state().durationMinutes)}
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
            LaunchedEffect(profileReady){
                AiCoachWork.schedule(this@MainActivity)
                if(profileReady && Build.VERSION.SDK_INT>=33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            var knownLogCount by remember{mutableIntStateOf(store.logs().size)}
            LaunchedEffect(Unit){while(true){
                tick=System.currentTimeMillis()
                if(store.completeIfDue())revision++
                completion=store.completion()
                if(store.logs().size != knownLogCount){knownLogCount=store.logs().size;revision++}
                delay(1000)
            }}
            val current=remember(revision,tick){store.state()}
            val left=remember(current,tick){store.remaining(current)}
            val rewardBalance=remember(revision,tick){store.rewardBalance()}
            val freeCancellations=remember(revision,tick){store.freeCancellationsRemaining()}
            val logs=remember(revision){store.logs()}
            val plans=remember(revision){store.tasks()}
            val journalFiles=remember(selectedDate,journalRevision){journal.all().filter{it.date==selectedDate.toString()}}
            val archiveDates=remember(journalRevision){journal.all().map{it.date}.toSet()}
            val total=logs.sumOf{it.minutes}
            val stage=Evolution.stage(total)
            val today=logs.filter{Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()}.sumOf{it.minutes}
            CompositionLocalProvider(LocalStaycalmDark provides isDark) {
            MaterialTheme(colorScheme=if(isDark)StaycalmDarkColors else StaycalmColors,typography=pinkTypography()){
                if (showAccount || (!signedIn && introDone)) {
                    AccountScreen(this@MainActivity, welcome = !signedIn, onSignedOut = { sessionUid=null },
                        onClose = { showAccount=false
                            if(!signedIn){ introDone=false; welcomePrefs.edit().putBoolean("intro_done",false).apply() } },
                        onRestored = { showAccount=false; recreate() },
                        onSignedIn = { _ -> AccountData.finishLogin(this@MainActivity); sessionUid=AccountData.sessionUid(this@MainActivity); showAccount=false })
                } else if (!signedIn || !profileReady) {
                    FirstRunOnboarding(startAtProfile = signedIn,
                        signedInEmail = if(signedIn) CloudBackup.auth().currentUser?.email else null,
                        onAccount = { introDone=true; welcomePrefs.edit().putBoolean("intro_done",true).apply()
                            if(!signedIn)showAccount=true }, onFinish = { name, subject, dailyGoal, quoteMinutes ->
                        personalization.setInterval(quoteMinutes)
                        personalization.saveProfile(name, subject)
                        store.setDailyGoal(dailyGoal)
                        profileName=personalization.name()
                        profileSubject=personalization.subject()
                        draftName=profileName
                        draftSubject=profileSubject
                        quoteInterval=personalization.interval()
                        goal=dailyGoal.toFloat()
                        profileReady=true
                    })
                } else {
                Scaffold(containerColor=Fog,bottomBar={
                    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){
                        listOf(Triple("Focus",Icons.Rounded.Timer,0),Triple("Planner",Icons.Rounded.CalendarMonth,1),Triple("Insights",Icons.Rounded.BarChart,2),Triple("Settings",Icons.Rounded.Tune,3)).forEach{(name,icon,index)->
                            NavigationBarItem(selected=page==index,onClick={page=index},icon={Icon(icon,name)},label={Text(name,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Rose,selectedTextColor=Rose,indicatorColor=Blush))
                        }
                    }
                }){pad->
                    Column(Modifier.fillMaxSize().padding(pad)){
                        Row(Modifier.fillMaxWidth().padding(start=23.dp,end=23.dp,top=23.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                            Column(Modifier.weight(1f)){
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                    Text("staycalm.",fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp,fontSize=25.sp,color=Ink)
                                    if(page==0)Surface(onClick={page=2},shape=CircleShape,color=Blush){
                                        Text("★ $rewardBalance pts",modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp),fontWeight=FontWeight.Bold,fontSize=12.sp,color=Rose)
                                    }
                                }
                                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),fontSize=11.sp,color=Sub)
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
                                if(page==0) Surface(
                                    onClick={
                                        // Stop any underlying YouTube embed before opening the mini browser.
                                        StudyVideoVisibility.background()
                                        showMiniYouTube=true
                                    },shape=CircleShape,color=Blush
                                ){
                                    Icon(Icons.Rounded.OndemandVideo,"Open mini YouTube",tint=Rose,
                                        modifier=Modifier.padding(12.dp).size(20.dp))
                                }
                                Surface(onClick={page=3},shape=CircleShape,color=Blush){
                                    Icon(Icons.Rounded.Tune,"Open settings",tint=Rose,
                                        modifier=Modifier.padding(12.dp).size(20.dp))
                                }
                            }
                        }
                        AnimatedContent(
                            targetState=page,
                            transitionSpec={
                                (slideInHorizontally(animationSpec=tween(280,easing=FastOutSlowInEasing),initialOffsetX={ if(targetState>initialState) it/6 else -it/6 }) +
                                    fadeIn(animationSpec=tween(250))) togetherWith
                                (slideOutHorizontally(animationSpec=tween(200),targetOffsetX={ if(targetState>initialState) -it/6 else it/6 }) +
                                    fadeOut(animationSpec=tween(190)))
                            },
                            label="smooth page navigation"
                        ){displayedPage->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                            Spacer(Modifier.height(8.dp))
                            when(displayedPage){
                                0->{
                                    Surface(shape=RoundedCornerShape(22.dp),color=Blush,border=BorderStroke(1.dp,Border)){
                                        Column(Modifier.fillMaxWidth().padding(18.dp)){
                                            Text("GOOD TO SEE YOU, ${profileName.ifBlank{"FRIEND"}.uppercase()}",fontWeight=FontWeight.SemiBold,fontSize=11.sp,letterSpacing=1.1.sp,color=Rose)
                                            Spacer(Modifier.height(6.dp))
                                            Text(StudyQuotes.next(profileName,profileSubject),color=Ink,fontSize=13.sp,lineHeight=20.sp)
                                        }
                                    }
                                    StudioFocus(
                                        mode=mode,intention=intention,
                                        onIntention={intention=it;store.setIntention(it);store.saveIntentionToPlanner(it);selectedDate=LocalDate.now();revision++},
                                        onMode={newMode->store.configure(if(newMode=="break")5 else 25,mode=newMode);mode=store.state().mode;revision++},
                                        durationMinutes=current.durationMinutes,
                                        remainingMs=left,
                                        running=current.running,
                                        totalMinutes=total,
                                        minutesToday=today,
                                        dailyGoal=store.dailyGoal(),
                                        selectedSound=audioLibrary.find(sound),
                                        onImport={audioPicker.launch(arrayOf("audio/*"))},importing=importing,
                                        onDuration={newMinutes->store.configure(newMinutes,mode=mode);duration=store.state().durationMinutes;revision++},
                                        onToggle={
                                            if(current.running)store.pause()else startSession()
                                            revision++
                                        },
                                        onAbandon={showReset=true},
                                        onSoundSelect={openSound=true},
                                        onOpenPlanner={page=1}
                                    )
                                    FocusMediaHub(
                                        focusMode=current.mode=="focus",
                                        sessionActive=current.sessionId.isNotEmpty(),
                                        onOpenMiniYouTube={
                                            StudyVideoVisibility.background()
                                            showMiniYouTube=true
                                        }
                                    )
                                    if(blockApps && current.mode=="focus" && SessionUninstallGuard.unfinished(current)){
                                        Text(if(FocusAppBlocker.isEnabled(this@MainActivity))
                                            "Instagram and Instagram Lite are covered until this focus session ends. YouTube stays available."
                                            else "Optional Instagram blocking needs Usage access and Display over other apps.",fontSize=12.sp,color=Rose)
                                    }
                                    if(uninstallProtection && SessionUninstallGuard.unfinished(current)){
                                        Text(if(SessionUninstallGuard.active(this@MainActivity))
                                            "Session uninstall protection active · released after completion or End session"
                                            else "Uninstall protection is off in Android. Resume to enable it again.",fontSize=12.sp,color=Rose)
                                    }
                                    Surface(shape=RoundedCornerShape(22.dp),color=Blush){
                                        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                            Text("★ $rewardBalance reward points",fontWeight=FontWeight.Bold,fontSize=18.sp,color=Rose)
                                            Text("Complete a focus session: earn 1 point per full hour. $freeCancellations of 3 free cancellations left.",fontSize=12.sp,color=Sub)
                                            Text("After your free cancellations, ending a focus session costs 1 point. With no points, finish or pause the session.",fontSize=12.sp,color=Sub)
                                        }
                                    }
                                }
                                1->{
                                    Header("Plan with purpose.","A little structure, more room to breathe.")
                                    val month=YearMonth.from(selectedDate)
                                    Surface(shape=RoundedCornerShape(25.dp),color=MaterialTheme.colorScheme.surface){
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
                                                            if(plans.any{it.date==day.toString()} || day.toString() in archiveDates)Box(Modifier.padding(top=2.dp).size(4.dp).background(if(day==selectedDate)Color.White else Rose,CircleShape))
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
                                        Surface(shape=RoundedCornerShape(19.dp),color=MaterialTheme.colorScheme.surface){
                                            Row(Modifier.fillMaxWidth().clickable{editTask=task}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                                Checkbox(checked=task.done,onCheckedChange={store.replaceTask(task.copy(done=it));revision++},colors=CheckboxDefaults.colors(checkedColor=Rose))
                                                Column(Modifier.weight(1f)){Text(task.title,fontWeight=FontWeight.SemiBold,color=if(task.done)Sub else Ink,textDecoration=if(task.done)TextDecoration.LineThrough else null)
                                                    Text("${task.time}  ·  ${task.duration} min${if(task.remind)"  ·  Reminder" else ""}",color=Sub,fontSize=11.sp)}
                                                if(!task.done)IconButton(enabled=current.sessionId.isEmpty() && !current.running,onClick={
                                                    store.configure(task.duration,task.id,"focus");mode="focus";intention=task.title;store.setIntention(task.title);startSession();revision++;page=0
                                                }){Icon(Icons.Rounded.PlayCircle,"Focus on ${task.title}",tint=Rose)}
                                            }
                                        }
                                    }
                                    Button(onClick={adding=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Plan something")}
                                    StudyJournalSection(date=selectedDate,attachments=journalFiles,busy=journalBusy,
                                        onImport={documentPicker.launch(arrayOf("*/*"))},
                                        onOpen={entry->
                                            try{startActivity(Intent.createChooser(journal.openIntent(entry),"Open study file"))}
                                            catch(error:Exception){journalError=error.message?:"No app is available to open this file."}
                                        },
                                        onDelete={deleteAttachment=it})
                                }
                                2->{
                                    Header("Every minute matters.","A gentler look at how far you’ve come.")
                                    StaycalmAiCard(onPlanAdded={revision++})
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                        MiniMetric("$rewardBalance","REWARD POINTS",Modifier.weight(1f))
                                        MiniMetric("$freeCancellations / 3","FREE CANCELLATIONS",Modifier.weight(1f))
                                    }
                                    val streak=FocusStats.streak(logs.map{Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()},LocalDate.now())
                                    Surface(shape=RoundedCornerShape(24.dp),color=Blush){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.LocalFireDepartment,null,tint=Rose,modifier=Modifier.size(32.dp));Spacer(Modifier.width(14.dp));Column{Text("$streak day streak",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink);Text("Small steps. A steady rhythm.",fontSize=12.sp,color=Sub)}}}
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                        MiniMetric("${today} min","TODAY",Modifier.weight(1f))
                                        MiniMetric("${total} min","ALL TIME",Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                        MiniMetric("${logs.size}","SESSIONS",Modifier.weight(1f))
                                        MiniMetric("${stage+1} / 6","TURTLE STAGE",Modifier.weight(1f))
                                    }
                                    Surface(shape=RoundedCornerShape(25.dp),color=MaterialTheme.colorScheme.surface){
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
                                                    val barHeight by animateFloatAsState(targetValue=max(5f,(tallies[idx].toFloat()/maxMinutes)*90f),animationSpec=tween(600,easing=FastOutSlowInEasing),label="seven day insight bar")
                                                     Box(Modifier.fillMaxWidth(.67f).height(barHeight.dp).background(if(day==LocalDate.now())Rose else Blush,RoundedCornerShape(topStart=7.dp,topEnd=7.dp)))
                                                    Spacer(Modifier.height(7.dp))
                                                    Text(day.dayOfWeek.name.take(1),fontSize=12.sp,color=Sub)
                                                }}
                                            }
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,Border)){
                                        Column(Modifier.padding(20.dp)){
                                            Text("Small steps become chapters",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                                            TurtleAtelier(stage,Modifier.fillMaxWidth().height(195.dp))
                                            Text("Your companion evolves only after completed focus sessions.",color=Sub,fontSize=13.sp)
                                        }
                                    }
                                    if(logs.isNotEmpty()){
                                        Text("Recent moments",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
                                        logs.takeLast(10).reversed().forEach{log->
                                            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                                                Icon(Icons.Rounded.CheckCircle,null,tint=Rose);Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)){Text(log.intention.ifBlank{"A moment of focus"},fontWeight=FontWeight.SemiBold,color=Ink,maxLines=2,overflow=TextOverflow.Ellipsis);Text(Instant.ofEpochMilli(log.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")),fontSize=11.sp,color=Sub)}
                                                Text("${log.minutes}m",fontWeight=FontWeight.Bold,color=Rose)
                                            }}
                                        }
                                    }
                                }
                                else->{
                                    Header("Make it yours.","A little control goes a long way.")
                                    Button(onClick={showAccount=true},modifier=Modifier.fillMaxWidth()) { Text("Account & backup") }
                                    Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface){
                                        Column(Modifier.fillMaxWidth().padding(19.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
                                            Text("Your space, ${profileName.ifBlank{"friend"}}",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                            if(CloudBackup.ready() && CloudBackup.auth().currentUser != null) {
                                                Text("✓ Login successful",color=Rose,fontWeight=FontWeight.SemiBold)
                                                Text(CloudBackup.auth().currentUser?.email.orEmpty(),color=Sub,fontSize=12.sp)
                                            }
                                            OutlinedTextField(draftName,{draftName=it.take(40)},label={Text("What should we call you?")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                            OutlinedTextField(draftSubject,{draftSubject=it.take(65)},label={Text("What are you studying?")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                            OutlinedButton(onClick={
                                                personalization.saveProfile(draftName,draftSubject)
                                                profileName=personalization.name()
                                                profileSubject=personalization.subject()
                                            },enabled=draftName.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save my profile")}
                                            HorizontalDivider()
                                            Row(verticalAlignment=Alignment.CenterVertically){
                                                Column(Modifier.weight(1f)){
                                                    Text("Dark pink mode",fontWeight=FontWeight.SemiBold)
                                                    Text("A softer screen for evening study.",fontSize=12.sp,color=Sub)
                                                }
                                                Switch(checked=isDark,onCheckedChange={isDark=it;personalization.setDarkMode(it);LauncherIconSwitcher.apply(this@MainActivity,it)})
                                            }
                                            HorizontalDivider()
                                            Text("Study encouragement",fontWeight=FontWeight.SemiBold)
                                            Text("Gentle reminders between 8 am and 10 pm, never during a focus session. Android may delay alerts.",fontSize=12.sp,color=Sub)
                                            Box{
                                                OutlinedButton(onClick={quoteMenuExpanded=true}){
                                                    Icon(Icons.Rounded.NotificationsActive,null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(when(quoteInterval){0->"Motivation off";60->"Every hour";120->"Every 2 hours";else->"Every 3 hours"})
                                                    Icon(Icons.Rounded.ArrowDropDown,null)
                                                }
                                                DropdownMenu(expanded=quoteMenuExpanded,onDismissRequest={quoteMenuExpanded=false}){
                                                    listOf(0,60,120,180).forEach{minutes->
                                                        DropdownMenuItem(
                                                            text={Text(when(minutes){0->"Off";60->"Every hour";120->"Every 2 hours";else->"Every 3 hours"})},
                                                            onClick={quoteInterval=minutes;personalization.setInterval(minutes);quoteMenuExpanded=false}
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface){
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
                                            Row(verticalAlignment=Alignment.CenterVertically){
                                                Column(Modifier.weight(1f)){
                                                    Text("Block Instagram (optional)",fontWeight=FontWeight.Bold)
                                                    Text("Optionally cover Instagram and Instagram Lite during unfinished focus sessions, including pauses. YouTube stays available, including videos during focus.",fontSize=12.sp,color=Sub)
                                                }
                                                Switch(checked=blockApps,onCheckedChange={requested->
                                                    if(!requested && blockApps && current.mode=="focus" &&
                                                        SessionUninstallGuard.unfinished(current)){
                                                        showBlockerLockedAlert=true
                                                    }else if(store.setBlockDistractingApps(requested)){
                                                        blockApps=requested
                                                        if(requested && !FocusAppBlocker.isEnabled(this@MainActivity))blockerSetup=true
                                                        revision++
                                                    }else if(!requested)showBlockerLockedAlert=true
                                                })
                                            }
                                            if(blockApps && current.mode=="focus" && SessionUninstallGuard.unfinished(current))
                                                Text("App blocking is locked until this focus session completes or is ended, even during pauses.",fontSize=11.sp,color=Rose)
                                            if(blockApps){
                                                Text(if(FocusAppBlocker.isEnabled(this@MainActivity))
                                                    "Usage access and Display over other apps granted · optional Instagram blocking ready."
                                                    else "Setup required for optional Instagram blocking: grant Usage access and Display over other apps.",
                                                    color=if(FocusAppBlocker.isEnabled(this@MainActivity))Sub else Rose,fontSize=11.sp)
                                                TextButton(onClick={blockerSetup=true}){Text("Set up Instagram blocking")}
                                            }
                                            Text("Optional basic Instagram blocking uses on-device Usage access and app overlays only. No strict Recent-apps or Accessibility protection. YouTube videos remain available during focus.",fontSize=11.sp,color=Sub)
                                            HorizontalDivider(color=Border)
                                            Spacer(Modifier.height(12.dp))
                                            Row(verticalAlignment=Alignment.CenterVertically){
                                                Column(Modifier.weight(1f)){
                                                    Text("Protect session from uninstall",fontWeight=FontWeight.Bold)
                                                    Text("Ask for Device Admin when starting. Keep protection through pauses; release after completion or an allowed End session.",fontSize=12.sp,color=Sub)
                                                }
                                                Switch(checked=uninstallProtection,enabled=!SessionUninstallGuard.unfinished(current),onCheckedChange={
                                                    if(store.setUninstallProtectionEnabled(it)){uninstallProtection=it;revision++}
                                                })
                                            }
                                            Text("Android asks permission for each new protected session. Reward points alone do not release protection. Android Settings can always deactivate Device Admin. A foreground countdown stays active during protected timers.",fontSize=11.sp,color=Sub)
                                            if(SessionUninstallGuard.unfinished(current))Text("Finish or end this session to change uninstall protection.",fontSize=11.sp,color=Rose)
                                            HorizontalDivider(color=Border)
                                            Spacer(Modifier.height(12.dp))
                                            Row(verticalAlignment=Alignment.CenterVertically){
                                                Column(Modifier.weight(1f)){
                                                    Text("Lock timer notification",fontWeight=FontWeight.Bold)
                                                    Text("Keep the focus or break countdown visible in the background. Dismissed alerts are restored while this is on.",fontSize=12.sp,color=Sub)
                                                }
                                                Switch(checked=lockedTimerNotification,onCheckedChange={
                                                    lockedTimerNotification=it
                                                    store.setLockTimerNotification(it)
                                                    if(it && Build.VERSION.SDK_INT>=33 &&
                                                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                })
                                            }
                                            Text("Android 14+ permits swiping ongoing alerts; the app restores the locked countdown. Pausing stops it. System notification blocking and force-stop override this setting.",fontSize=11.sp,color=Sub)
                                            TextButton(onClick={startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}){Text("Open notification permissions")}
                                            if(Build.VERSION.SDK_INT>=31){
                                                Spacer(Modifier.height(8.dp))
                                                HorizontalDivider(color=Border)
                                                Spacer(Modifier.height(12.dp))
                                                Text("Precise session and planner reminders",fontWeight=FontWeight.Bold,color=Ink)
                                                Text(
                                                    if(FocusAlarms.preciseAllowed(this@MainActivity))
                                                        "Enabled. Session completion and planner reminders use exact Android alarms."
                                                    else "Grant exact-alarm permission for on-time planner reminders; otherwise Android may delay notifications.",
                                                    fontSize=12.sp,color=Sub)
                                                if(!FocusAlarms.preciseAllowed(this@MainActivity)){
                                                    TextButton(onClick={
                                                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                            Uri.parse("package:"+packageName)))
                                                    }){Text("Allow precise reminders")}
                                                }
                                            }
                                            HorizontalDivider(color=Border)
                                            Spacer(Modifier.height(12.dp))
                                            Text("Sound volume",fontWeight=FontWeight.Bold)
                                            Slider(value=volume,onValueChange={volume=it;store.setVolume(it);if(playing)startService(Intent(this@MainActivity,SoundService::class.java).setAction("volume"))})
                                        }
                                    }
                                    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface){
                                        Column(Modifier.padding(19.dp)){Text("Your data, your choice",fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(7.dp));Text("Your data saves locally. Account sync uploads plans, history, profile, preferences and rewards to your private Firebase account. Planner files and imported audio need a full ZIP export. No analytics trackers or AI.",color=Sub,fontSize=13.sp)}
                                    }
                                    Text("STAYCALM · FOCUS EDITION 4.3",fontSize=10.sp,letterSpacing=1.5.sp,color=Sub)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        }
                    }
                }
            if(deleteAttachment!=null)AlertDialog(
                onDismissRequest={deleteAttachment=null},
                title={Text("Delete this saved file?")},
                text={Text("This removes the private copy from your work archive. The original file stays untouched.")},
                confirmButton={TextButton(onClick={
                    deleteAttachment?.let{journal.remove(it)}
                    journalRevision++
                    deleteAttachment=null
                }){Text("Delete file")}},
                dismissButton={TextButton(onClick={deleteAttachment=null}){Text("Cancel")}}
            )
            if(journalError!=null)AlertDialog(onDismissRequest={journalError=null},
                title={Text("Work archive")},text={Text(journalError.orEmpty())},
                confirmButton={TextButton(onClick={journalError=null}){Text("OK")}})
            if(showReset){
                val cost=store.cancellationCost()
                val canCancel=current.mode=="break" || cost==0L || rewardBalance>=cost
                AlertDialog(onDismissRequest={showReset=false},title={Text("End this moment?")},text={Text(
                    if(current.mode=="break") "End this break for free."
                    else if(freeCancellations>0) "Use 1 free cancellation? $freeCancellations remaining. Unfinished focus earns no points."
                    else if(canCancel) "Ending this focus session costs 1 reward point. Balance: $rewardBalance. Unfinished focus earns no points."
                    else "No free cancellations or reward points left. Complete this session to earn points, or pause and resume later."
                )},confirmButton={TextButton(enabled=canCancel,onClick={
                    when(store.abandon()){
                        CancellationResult.INSUFFICIENT_POINTS->cancellationError="You need 1 reward point to cancel. Finish this session or pause and resume later."
                        CancellationResult.SAVE_FAILED->cancellationError="Could not save this change. Please try again."
                        else->Unit
                    }
                    revision++;showReset=false
                }){Text("End session")}},dismissButton={TextButton(onClick={showReset=false}){Text("Keep going")}})
            }
            if(blockerSetup)AlertDialog(onDismissRequest={blockerSetup=false},
                title={Text("Optional Instagram blocking")},
                text={
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("Optional basic blocking covers Instagram and Instagram Lite during unfinished focus sessions. YouTube remains available. It uses Usage access and Display over other apps only; no Accessibility service or strict Recents interception.",fontSize=13.sp)
                        Text(if(FocusAppBlocker.hasUsageAccess(this@MainActivity))
                            "✓ Usage access allowed" else "1. Grant Usage access",fontWeight=FontWeight.SemiBold)
                        if(!FocusAppBlocker.hasUsageAccess(this@MainActivity))
                            TextButton(onClick={startActivity(FocusAppBlocker.usageAccessIntent())}){Text("Open Usage access settings")}
                        Text(if(FocusAppBlocker.hasOverlayAccess(this@MainActivity))
                            "✓ Display over other apps allowed" else "2. Allow Display over other apps",fontWeight=FontWeight.SemiBold)
                        if(!FocusAppBlocker.hasOverlayAccess(this@MainActivity))
                            TextButton(onClick={startActivity(FocusAppBlocker.overlayAccessIntent(this@MainActivity))}){Text("Open Display over other apps settings")}
                        Text("After granting these two optional permissions, return to STAYCALM. You can revoke either through Android Settings.",fontSize=12.sp,color=Sub)
                    }
                },
                confirmButton={TextButton(onClick={blockerSetup=false;revision++}){Text("Done")}},
                dismissButton={TextButton(onClick={blockerSetup=false}){Text("Not now")}})
            if(protectionError!=null)AlertDialog(onDismissRequest={protectionError=null},title={Text("Session protection")},text={Text(protectionError.orEmpty())},confirmButton={TextButton(onClick={protectionError=null}){Text("OK")}})
            if(cancellationError!=null)AlertDialog(onDismissRequest={cancellationError=null},title={Text("Session kept")},text={Text(cancellationError.orEmpty())},confirmButton={TextButton(onClick={cancellationError=null}){Text("OK")}})
            if(completion!=null)AlertDialog(onDismissRequest={store.dismissCompletion();completion=null},icon={Icon(Icons.Rounded.LocalFlorist,null,tint=Rose)},title={Text(if(mode=="focus")"A little more bloomed." else "Welcome back.")},text={Text(completion.orEmpty())},confirmButton={TextButton(onClick={store.dismissCompletion();completion=null;mode=if(mode=="focus")"break" else "focus";store.configure(if(mode=="break")5 else 25,mode=mode);revision++}){Text(if(mode=="focus")"Take a 5-minute break" else "Back to focus")}},dismissButton={TextButton(onClick={store.dismissCompletion();completion=null}){Text("Lovely")}})
            if(showBlockerLockedAlert)AlertDialog(
                onDismissRequest={showBlockerLockedAlert=false},
                icon={Icon(Icons.Rounded.Lock,null,tint=Rose)},
                title={Text("App blocking is locked")},
                text={Text("Block Apps can't be turned off until your focus session ends. It stays on even if you pause. Finish the timer or use End session to unlock the switch.")},
                confirmButton={TextButton(onClick={showBlockerLockedAlert=false}){Text("Got it")}}
            )
            if(importError!=null)AlertDialog(onDismissRequest={importError=null},title={Text("Audio could not be added")},text={Text(importError.orEmpty())},confirmButton={TextButton(onClick={importError=null}){Text("OK")}})
            if(showMiniYouTube) MiniYouTubeBrowser(onDismiss={showMiniYouTube=false})
            if(openSound)AlertDialog(onDismissRequest={openSound=false},title={Text("Your focus audio")},text={
                Column(Modifier.heightIn(max=455.dp).verticalScroll(rememberScrollState())){
                    Text("Every track loops until the session ends.",fontSize=12.sp,color=Sub)
                    TextButton(enabled=!importing,onClick={openSound=false;audioPicker.launch(arrayOf("audio/*"))}){Icon(Icons.Rounded.Add,null);Text("Add your audio")}
                    audioChoices.forEach {ambient->
                        Row(Modifier.fillMaxWidth().clickable{
                            sound=ambient.id;store.setSound(sound);openSound=false
                            if(playing)startForegroundService(Intent(this@MainActivity,SoundService::class.java).putExtra("sound",sound)) else stopService(Intent(this@MainActivity,SoundService::class.java))
                        }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Rounded.GraphicEq,null,tint=if(sound==ambient.id)Rose else Sub)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){Text(ambient.label,fontWeight=FontWeight.Bold);Text(ambient.detail,fontSize=12.sp,color=Sub)}
                            if(sound==ambient.id)Icon(Icons.Rounded.Check,null,tint=Rose)
                            if(ambient.localFile!=null)IconButton(onClick={
                                if(AudioPlayback.state.id==ambient.id)stopService(Intent(this@MainActivity,SoundService::class.java))
                                audioLibrary.remove(ambient);audioRevision++
                                if(sound==ambient.id){sound=Ambients.all.first().id;store.setSound(sound)}
                            }){Icon(Icons.Rounded.DeleteOutline,"Remove ${ambient.label}",tint=Sub)}
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
                     if(remind && Build.VERSION.SDK_INT>=33 &&
                         checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                         permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },onDelete={item->store.deleteTask(item);revision++;editTask=null;adding=false})
            }
                }
            }
            }
            } // account identity: dispose every remembered private screen on logout/switch
        }
    }
}

@Composable private fun Header(title:String,subtitle:String){
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=Ink,letterSpacing=(-1).sp);Text(subtitle,fontSize=13.sp,color=Sub)}
}
@Composable private fun SmallLabel(s:String){Text(s,fontSize=10.sp,letterSpacing=1.3.sp,fontWeight=FontWeight.Bold,color=Sub)}
@Composable private fun MiniMetric(value:String,label:String,modifier:Modifier=Modifier){
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,Border)){
        Column(Modifier.padding(19.dp)){Text(value,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(3.dp));SmallLabel(label)}
    }
}
@Composable private fun EmptyPanel(title:String,subtitle:String){
    Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface){
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
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
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
            if(remind && !FocusAlarms.preciseAllowed(context) && Build.VERSION.SDK_INT>=31){
                Text("Allow Alarms & reminders for on-time delivery. Otherwise Android may delay alerts.",fontSize=11.sp,color=Rose)
                TextButton(onClick={
                    runCatching{context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:"+context.packageName)))}
                }){Text("Allow on-time reminders")}
            }
            if(remind && Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                Text("Allow notification permission to see the reminder.",fontSize=11.sp,color=Rose)
            if(item!=null)TextButton(onClick={onDelete(item)}){Text("Delete task",color=Rose)}
        }
    },confirmButton={TextButton(enabled=title.isNotBlank(),onClick={onSave(title.trim(),date,time,duration,remind)}){Text("Save plan")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
