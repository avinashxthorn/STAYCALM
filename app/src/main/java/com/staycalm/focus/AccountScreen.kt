package com.staycalm.focus

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(context: Context, onClose: () -> Unit, onRestored: () -> Unit, onSignedIn: (Boolean) -> Unit = {}, welcome: Boolean = false, onSignedOut: () -> Unit = {}) {
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); refresh++ } }
    val user = remember(refresh) { if (CloudBackup.ready()) CloudBackup.auth().currentUser else null }
    var showLogin by remember { mutableStateOf(user == null) }
    // Keep recovery failures on the login screen until the user explicitly chooses a next step.
    if (welcome || user == null || showLogin) {
        LoginScreen(context, onBack = onClose, onSignedIn = { restored ->
            showLogin = false; refresh++
            onSignedIn(restored)
        })
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf<List<CloudCopy>?>(null) }
    var selected by remember { mutableStateOf<CloudCopy?>(null) }
    var importConfirm by remember { mutableStateOf(false) }
    fun runAction(action: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                message = CloudBackup.errorMessage(error)
            } finally { busy = false; refresh++ }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runAction {
            try { withContext(Dispatchers.IO) { LocalBackup.export(context, uri) }; message = "Backup file saved." }
            catch (error: Exception) {
                withContext(Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                throw error
            }
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runAction { withContext(Dispatchers.IO) { LocalBackup.restore(context, uri) }; onRestored() }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onClose, enabled = !busy) { Text("← Back") }
            Text("Your account", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✓ Login successful", fontWeight = FontWeight.Bold)
                    Text(user.email.orEmpty())
                    Text(if (user.isEmailVerified) "Email verified" else "Check your inbox to verify your email", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!user.isEmailVerified) {
                Button(onClick = { runAction {
                    user.reload().await(); user.getIdToken(true).await()
                    message = if (CloudBackup.auth().currentUser?.isEmailVerified == true) "Email verified. You can enable cloud sync now." else "Open the verification email first."
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Check verification") }
                TextButton(onClick = { runAction { user.sendEmailVerification().await(); message = "Verification email sent." } }, enabled = !busy) { Text("Resend verification email") }
            } else {
                Text("Cloud sync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(CloudBackup.status())
                if (CloudBackup.enabled() && CloudBackup.needsUpload()) Text("New changes are waiting to upload.", style = MaterialTheme.typography.bodySmall)
                Text("Plans, completed session history, insights, profile and rewards recover with your account. Planner files and imported audio stay on this phone; export a full ZIP to move them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (CloudBackup.enabled()) runAction { CloudBackup.upload(user.uid, force = true); message = "Synced successfully: plans, session history, insights, profile and rewards." }
                    else consent = true
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (CloudBackup.enabled()) "Sync now" else "Enable cloud sync")
                }
                OutlinedButton(onClick = { runAction {
                    copies = CloudBackup.copies()
                    if (copies!!.isEmpty()) message = "No cloud copies found yet."
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from cloud") }
                copies?.forEach { copy ->
                    OutlinedButton(onClick = { selected = copy }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${copy.label}\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(copy.updatedAt))}")
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = { more = !more }, enabled = !busy) { Text(if (more) "Hide more options" else "More options") }
            if (more) {
                HorizontalDivider()
                Text("Full file backup", style = MaterialTheme.typography.titleMedium)
                Text("Export a ZIP to keep your planner files and imported audio along with your plans and progress. Files are not included in cloud sync.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { export.launch("STAYCALM-backup-${java.time.LocalDate.now()}.zip") }, enabled = !busy) { Text("Export full backup") }
                OutlinedButton(onClick = { importConfirm = true }, enabled = !busy) { Text("Restore from backup file") }
                if (CloudBackup.enabled()) TextButton(onClick = { CloudBackup.disable(); refresh++ }, enabled = !busy) { Text("Turn off automatic sync") }
            }
            TextButton(onClick = {
                if (SessionUninstallGuard.unfinished(FocusStore(context).state())) message = "Finish or end your current session before logging out."
                else logout = true
            }, enabled = !busy) { Text("Log out") }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Enable cloud sync?") },
        text = { Text("Upload this device's plans, session history, profile, preferences and rewards to ${user.email}. Planner files and imported audio are saved only in full ZIP exports.") },
        confirmButton = { TextButton(onClick = { consent = false; runAction { CloudBackup.enable(); CloudBackup.upload(user.uid, force = true); message = "Cloud sync is on. Plans and progress uploaded successfully." } }) { Text("Enable sync") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
    selected?.let { copy -> AlertDialog(onDismissRequest = { selected = null }, title = { Text("Restore this cloud copy?") },
        text = { Text("This replaces current plans, session history, profile, preferences and rewards. Local planner files and audio are kept. Files do not download from the cloud. Finish any timer first.") },
        confirmButton = { TextButton(onClick = { selected = null; runAction { CloudBackup.restoreCloud(copy); CloudBackup.enable(); onRestored() } }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }) }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Log out?") },
        text = { Text("Your files, session history and insights will be hidden until you log in again. Your private account copy stays on this phone. Sync first to recover new changes on another phone.") },
        confirmButton = { TextButton(onClick = {
            val syncFirst = CloudBackup.enabled() && CloudBackup.needsUpload()
            logout = false
            runAction {
                if (syncFirst) CloudBackup.upload(user.uid, force = true)
                CloudBackup.signOut(); copies = null; showLogin = true; onSignedOut()
            }
        }) { Text(if (CloudBackup.enabled() && CloudBackup.needsUpload()) "Sync and log out" else "Log out") } },
        dismissButton = { Row {
            if (CloudBackup.enabled() && CloudBackup.needsUpload()) TextButton(onClick = {
                logout = false
                runAction { CloudBackup.signOut(); copies = null; showLogin = true; onSignedOut() }
            }) { Text("Log out without syncing") }
            TextButton(onClick = { logout = false }) { Text("Cancel") }
        } })
    if (importConfirm) AlertDialog(onDismissRequest = { importConfirm = false }, title = { Text("Restore a file backup?") },
        text = { Text("This replaces current plans, progress and file lists. Export current data first if you want to keep it.") },
        confirmButton = { TextButton(onClick = { importConfirm = false; importBackup.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("Cancel") } })
}
