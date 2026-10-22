package com.staycalm.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class FocusCoachSetup(val area:String,val goal:String,val weak:String,val targetDate:String,val preferredTime:String,val enabled:Boolean,val notifications:Boolean)

private val introTitles = listOf(
    "Find your focus.",
    "Keep every little win.",
    "Grow at your own pace."
)
private val introDescriptions = listOf(
    "Set a timer, play calming sounds, and give one thing your full attention.",
    "Plan your day and save your notes or files on the dates you worked.",
    "See your progress, grow your little companion, and find fresh motivation."
)
private val introQuotes = listOf(
    "One moment at a time.",
    "Small steps become chapters.",
    "Progress, not perfection."
)
private val introCaptions = listOf("01 / A CALMER START", "02 / YOUR STUDY STORY", "03 / KEEP BLOOMING")

/** Three swipeable introductions followed by a real, separate profile screen.
 *  A profile is saved only after the last screen's submit button succeeds.
 */
@Composable
fun FirstRunOnboarding(
    onAccount: () -> Unit = {},
    startAtProfile: Boolean = false,
    signedInEmail: String? = null,
    onFinish: (name: String, subject: String, goalMinutes: Int, quoteMinutes: Int, coach: FocusCoachSetup) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var profileStep by rememberSaveable { mutableStateOf(startAtProfile) }
    LaunchedEffect(startAtProfile) { if (startAtProfile) profileStep = true }
    var name by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableIntStateOf(120) }
    var quotes by rememberSaveable { mutableIntStateOf(120) }
    var area by rememberSaveable { mutableStateOf("Study") }
    var futureGoal by rememberSaveable { mutableStateOf("") }
    var weak by rememberSaveable { mutableStateOf("") }
    var targetDate by rememberSaveable { mutableStateOf("") }
    var preferredTime by rememberSaveable { mutableStateOf("18:00") }
    var aiEnabled by rememberSaveable { mutableStateOf(true) }
    var aiNotifications by rememberSaveable { mutableStateOf(true) }
    var setupError by remember { mutableStateOf<String?>(null) }
    val backdrop = if (LocalStaycalmDark.current) {
        Brush.verticalGradient(listOf(Color(0xFF161B19), Color(0xFF232925), Color(0xFF171717)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F8F6), Color(0xFFF8F9FA)))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(backdrop)
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 15.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFlorist, contentDescription = null,
                tint = scheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("staycalm.", fontWeight = FontWeight.SemiBold, fontSize = 23.sp,
                color = scheme.onBackground, letterSpacing = (-0.7).sp)
            Spacer(Modifier.weight(1f))
            if (profileStep) {
                Text("YOUR SPACE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp, color = scheme.primary)
            }
        }
        if (!profileStep) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pager"),
                pageSpacing = 12.dp
            ) { index ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(introCaptions[index], fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp, color = scheme.primary)
                    Spacer(Modifier.height(19.dp))
                    IntroIllustration(index)
                    Spacer(Modifier.height(30.dp))
                    Text(introTitles[index], fontSize = 31.sp, lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        color = scheme.onBackground, letterSpacing = (-1.1).sp)
                    Spacer(Modifier.height(11.dp))
                    Text(introDescriptions[index], fontSize = 14.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center, color = scheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 305.dp))
                    Spacer(Modifier.height(15.dp))
                    Text("“${introQuotes[index]}”", fontSize = 13.sp,
                        color = scheme.primary, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { dot ->
                    val selected = dot == pager.currentPage
                    val animatedWidth by animateDpAsState(if (selected) 27.dp else 8.dp,
                        animationSpec = tween(280), label = "page progress dot")
                    Box(
                        Modifier.padding(horizontal = 4.dp)
                            .size(width = animatedWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) scheme.primary else scheme.primary.copy(alpha = .23f))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "Previous page")
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {
                        if (pager.currentPage == 2) { if (startAtProfile) profileStep = true else onAccount() }
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text(if (pager.currentPage == 2) "Get started" else "Next",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconButton(onClick = { profileStep = false }) {
                    Icon(Icons.Rounded.ArrowBack, "Back to introduction",
                        tint = scheme.onBackground)
                }
                Box(
                    Modifier.fillMaxWidth().height(124.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(112.dp).clip(CircleShape)
                        .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, null, tint = scheme.primary,
                            modifier = Modifier.size(43.dp))
                    }
                }
                signedInEmail?.let {
                    Text("✓ Login successful", color = scheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(it, color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("Let's make it yours.", fontSize = 30.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                    color = scheme.onBackground)
                Text("A little about you, and we'll find your rhythm.",
                    fontSize = 14.sp, color = scheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("Your name *") }, placeholder = { Text("What should we call you?") },
                    leadingIcon = { Icon(Icons.Rounded.PersonOutline, null) },
                    singleLine = true, shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth().testTag("onboarding-name")
                )
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it.take(65) },
                    label = { Text("What would you like to focus on?") },
                    placeholder = { Text("e.g. A study topic, work skill or personal goal") },
                    leadingIcon = { Icon(Icons.Rounded.MenuBook, null) },
                    singleLine = true, shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth().testTag("onboarding-subject")
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Study","Work","Personal","Other").forEach { label ->
                        FilterChip(selected=area==label,onClick={area=label},label={Text(label,fontSize=11.sp)})
                    }
                }
                OutlinedTextField(futureGoal,{futureGoal=it.take(300)},label={Text("Your future goal")},placeholder={Text("e.g. Prepare for an exam or finish a project")},modifier=Modifier.fillMaxWidth().testTag("onboarding-future-goal"))
                OutlinedTextField(weak,{weak=it.take(300)},label={Text("What needs more practice? (optional)")},modifier=Modifier.fillMaxWidth().testTag("onboarding-weak-topics"))
                OutlinedTextField(targetDate,{targetDate=it.take(10)},label={Text("Target date · YYYY-MM-DD (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("onboarding-target-date"))
                OutlinedTextField(preferredTime,{preferredTime=it.take(5)},label={Text("Preferred focus time · HH:mm")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("onboarding-focus-time"))
                Row { Checkbox(aiEnabled,{aiEnabled=it});Column(Modifier.padding(top=10.dp)){Text("Automatic Staycalm AI coaching",fontWeight=FontWeight.SemiBold);Text("Your focus details, recent sessions and planner topics are processed by a cloud AI service. Files, audio, passwords and chats are not sent.",fontSize=11.sp,color=scheme.onSurfaceVariant)} }
                Row { Checkbox(aiNotifications,{aiNotifications=it},enabled=aiEnabled);Column(Modifier.padding(top=10.dp)){Text("AI personalized notifications",fontWeight=FontWeight.SemiBold);Text("Can arrive at any hour when enabled. Change this in Settings whenever you like.",fontSize=11.sp,color=scheme.onSurfaceVariant)} }
                Text("YOUR DAILY FOCUS GOAL", fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = scheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf(30, 60, 120).forEach { minutes ->
                        FilterChip(
                            selected = goal == minutes, onClick = { goal = minutes },
                            label = { Text(if (minutes == 120) "2 hours" else "${minutes} min",
                                fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = scheme.primaryContainer,
                                selectedLabelColor = scheme.primary
                            )
                        )
                    }
                }
                Text("A LITTLE MOTIVATION", fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = scheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 60 to "1 hour", 120 to "2 hours", 180 to "3 hours").forEach { (minutes, label) ->
                        FilterChip(
                            selected = quotes == minutes, onClick = { quotes = minutes },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = scheme.primaryContainer,
                                selectedLabelColor = scheme.primary
                            )
                        )
                    }
                }
                Text("Your profile joins your account backup when cloud sync is enabled.",
                    fontSize = 11.sp, color = scheme.onSurfaceVariant)
                setupError?.let{Text(it,color=scheme.error)}
                Button(
                    onClick = {
                        try {
                            require(AiCoachFormat.validTime(preferredTime)){"Use a focus time in HH:mm format."}
                            if(targetDate.isNotBlank())require(!java.time.LocalDate.parse(targetDate).isBefore(java.time.LocalDate.now())){"Choose today or a future target date."}
                            onFinish(name.trim(),subject.trim(),goal,quotes,FocusCoachSetup(area,futureGoal,weak,targetDate,preferredTime,aiEnabled,aiNotifications))
                        }catch(_:java.time.format.DateTimeParseException){setupError="Use a target date in YYYY-MM-DD format."}
                        catch(e:Exception){setupError=e.message?:"Please check your focus details."}
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text("Start my journey", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.AutoAwesome, null)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun IntroIllustration(page: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(244.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(236.dp).clip(CircleShape)
            .background(scheme.primary.copy(alpha = if (LocalStaycalmDark.current) .12f else .095f)))
        when (page) {
            0 -> {
                Surface(
                    modifier = Modifier.width(249.dp).height(204.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surface,
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = .12f))
                ) {
                    Column(Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Spa, null, tint = scheme.primary,
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("FOCUS TIME", fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                color = scheme.primary, letterSpacing = 1.3.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.Headphones, null,
                                tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(11.dp))
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.size(107.dp)) {
                            CircularProgressIndicator(
                                progress = { .72f },
                                color = scheme.primary, trackColor = scheme.primaryContainer,
                                strokeWidth = 7.dp, modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("25:00", fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp, color = scheme.onSurface)
                                Text("BREATHE & BEGIN", fontSize = 7.sp,
                                    letterSpacing = .5.sp, color = scheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(color = scheme.primaryContainer, shape = CircleShape) {
                            Text("✦ one task at a time", modifier = Modifier.padding(
                                horizontal = 13.dp, vertical = 6.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = scheme.primary)
                        }
                    }
                }
            }
            1 -> {
                Surface(
                    modifier = Modifier.width(267.dp).height(211.dp),
                    shape = RoundedCornerShape(26.dp), color = scheme.surface,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = .12f))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("October", fontWeight = FontWeight.Bold,
                                fontSize = 17.sp, color = scheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.CalendarToday, null, tint = scheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("12", "13", "14", "15", "16").forEachIndexed { index, date ->
                                Box(
                                    Modifier.weight(1f).height(37.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (index == 3) scheme.primary else scheme.primaryContainer.copy(alpha = .6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(date, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (index == 3) scheme.onPrimary else scheme.onSurface)
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = scheme.primaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary,
                                    modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(9.dp))
                                Text("Chapter reviewed", color = scheme.onSurface,
                                    fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, null, tint = scheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Notes saved to this day", fontSize = 11.sp,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> {
                Surface(
                    modifier = Modifier.width(267.dp).height(214.dp),
                    shape = RoundedCornerShape(28.dp), color = scheme.surface,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = .12f))
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GROW WITH EVERY SESSION", fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
                            color = scheme.primary)
                        TurtleAtelier(4, Modifier.fillMaxWidth().height(156.dp))
                        Text("Every minute adds up.", color = scheme.onSurfaceVariant,
                            fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
