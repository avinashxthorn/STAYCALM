package com.staycalm.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StaycalmAiCard(onPlanAdded: () -> Unit) {
    val context = LocalContext.current
    val ai = remember { StaycalmAi(context) }
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf(ai.report()) }
    var settings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var configRevision by remember { mutableIntStateOf(0) }
    var planRevision by remember { mutableIntStateOf(0) }
    suspend fun load(force: Boolean) {
        busy = true; error = null; status = null
        try { report = ai.refresh(force) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            error = when (e) {
                is java.io.IOException -> "Could not reach Gemini. Check your internet connection. Your saved plan is still here."
                is org.json.JSONException -> "Gemini returned an incomplete plan. Try again later."
                else -> e.message ?: "AI coaching is unavailable. Try again later."
            }
        } finally { busy = false }
    }
    LaunchedEffect(configRevision) { if (ai.enabled() && ai.hasKey()) load(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Staycalm AI", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Your study coach · Powered by Gemini", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("Insights and next steps shaped around your study topic, focus sessions and future goal.")
            if (!ai.enabled()) Text("Connect Gemini to build your first personalized study plan.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { settings = true }, enabled = !busy) { Text("Coach settings") }
                if (ai.enabled()) Button(onClick = { scope.launch { load(true) } }, enabled = !busy) { Text("Refresh") }
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Staycalm AI is reviewing your study routine…", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            report?.takeIf { ai.enabled() }?.let { saved ->
                Text("Generated ${Instant.ofEpochMilli(saved.generatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}",
                    style = MaterialTheme.typography.labelSmall)
                if (ai.currentReport() == null) Text("Your study context has changed. Refresh to update this saved plan.", style = MaterialTheme.typography.bodySmall)
                Text(saved.summary, fontWeight = FontWeight.SemiBold)
                Text("Your study insights", fontWeight = FontWeight.Bold)
                saved.insights.forEach { Text("• $it") }
                Text("Your next seven days", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Suggested sessions start at 18:00. You can change the time in Planner. Adding a session also enables its reminder.",
                    style = MaterialTheme.typography.bodySmall)
                saved.days.forEachIndexed { index, day ->
                    val id = "ai-${saved.generatedAt}-$index"
                    val added = remember(planRevision, saved.generatedAt, index) { FocusStore(context).tasks().any { it.id == id } }
                    val past = LocalDate.parse(day.date).atTime(18, 0).isBefore(java.time.LocalDateTime.now())
                    HorizontalDivider()
                    Text("${LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM"))} · ${day.minutes} min", style = MaterialTheme.typography.labelMedium)
                    Text(day.topic, fontWeight = FontWeight.SemiBold)
                    Text(day.action, style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !added && !past && !busy, onClick = {
                        val store = FocusStore(context)
                        if (store.tasks().none { it.id == id }) {
                            store.savePlans(store.tasks() + PlanItem(id, "${day.topic}: ${day.action}".take(450), day.date, "18:00", day.minutes, false, true))
                            planRevision++; onPlanAdded(); status = "Added ${day.topic} to Planner."
                        }
                    }) { Text(if (added) "Added to planner" else if (past) "Date passed · refresh your plan" else "Add to planner") }
                }
                Text("AI suggestions can be imperfect. Adjust the plan to your syllabus and available time.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (settings) AiCoachSettings(ai, onDismiss = { settings = false }, onSaved = {
        settings = false; report = ai.report(); configRevision++
    })
}

@Composable
private fun AiCoachSettings(ai: StaycalmAi, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var goal by remember { mutableStateOf(ai.goal()) }
    var weak by remember { mutableStateOf(ai.weakTopics()) }
    var exam by remember { mutableStateOf(ai.examDate()) }
    var secret by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(ai.enabled()) }
    var nudges by remember { mutableStateOf(ai.notifications()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Staycalm AI coach") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(goal, { goal = it.take(300) }, label = { Text("Future study goal") }, placeholder = { Text("e.g. Prepare for LPM Paper I") })
            OutlinedTextField(weak, { weak = it.take(300) }, label = { Text("Topics to improve") }, placeholder = { Text("e.g. Swine breeds and housing") })
            OutlinedTextField(exam, { exam = it.take(10) }, label = { Text("Exam date (optional)") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(secret, { secret = it.take(512) }, label = { Text(if (ai.hasKey()) "Replace Gemini API key (optional)" else "Gemini API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("Your personal key is encrypted on this device and excluded from backups. It is sent only to Google's Gemini API.", style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(consent, { consent = it }); Text("Enable AI: send my profile name, subject, focus topic, goals, recent sessions and planner topics to Google Gemini.", modifier = Modifier.padding(top = 10.dp)) }
            Text("Study files, audio, chats, passwords and login details are never sent. Google's free tier has quotas and may use submitted data to improve its products. Use a project on Google's free tier; Staycalm cannot control Google billing.", style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(nudges, { nudges = it }, enabled = consent); Text("Personalized AI coaching notifications", modifier = Modifier.padding(top = 10.dp)) }
            Text("Uses your existing encouragement interval. Notifications pause during focus and between 22:00 and 08:00. Android may delay delivery. AI refreshes at most six times per day; saved messages work offline.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            try { ai.configure(goal, weak, exam, secret, consent, nudges); secret = ""; onSaved() }
            catch (_: java.time.format.DateTimeParseException) { error = "Use an exam date in YYYY-MM-DD format." }
            catch (e: Exception) { error = e.message ?: "Could not save coach settings." }
        }) { Text("Save coach") }
    }, dismissButton = {
        Row {
            if (ai.hasKey()) TextButton(onClick = {
                try { ai.disconnect(); secret = ""; onSaved() }
                catch (_: Exception) { error = "Could not remove the key. Try again." }
            }) { Text("Disconnect") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    })
}
