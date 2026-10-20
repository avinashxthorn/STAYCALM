package com.staycalm.focus

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(context: Context, onClose: () -> Unit, onRestored: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords intentionally never enter savedInstanceState or app preferences.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf<List<CloudCopy>?>(null) }
    var restore by remember { mutableStateOf<CloudCopy?>(null) }
    var importConfirm by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE) }
    var phone by rememberSaveable { mutableStateOf(prefs.getString("profile_phone", "") ?: "") }
    LaunchedEffect(Unit) { while (true) { delay(2000); refresh++ } }
    // Reading refresh makes asynchronous auth and worker status updates visible.
    val user = remember(refresh, busy) { if (CloudBackup.ready()) CloudBackup.auth().currentUser else null }
    fun runAction(action: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            try { action() }
            catch (error: Exception) { message = error.localizedMessage ?: "Something went wrong. Try again." }
            finally { busy = false; refresh++ }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runAction {
            try { withContext(Dispatchers.IO) { LocalBackup.export(context, uri) }; message = "Backup file saved. Keep a copy outside this phone; it contains your private data." }
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
            .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onClose, enabled = !busy) { Text("← Back") }
            Text("Account & backup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Keep your plans and progress when you change phones. Email login has no SMS charges.")
            if (!CloudBackup.ready()) {
                Text("Cloud setup is needed", fontWeight = FontWeight.Bold)
                Text("The app owner must add the Firebase Android configuration, enable Email/Password sign-in, and publish the database rules. Local saving and file backups work now.")
            } else if (user == null) {
                Text(if (create) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !busy)
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !busy)
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide password" else "Show password") }
                if (create) OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Confirm password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !busy)
                Button(onClick = { runAction {
                    require(email.trim().isNotEmpty() && password.length >= 6) { "Enter an email and a password of at least 6 characters." }
                    if (create) {
                        require(password == confirmPassword) { "Passwords do not match." }
                        val result = CloudBackup.auth().createUserWithEmailAndPassword(email.trim(), password).await()
                        password = ""; confirmPassword = ""
                        result.user!!.sendEmailVerification().await()
                        message = "Verification email sent. Open it, then return here and check verification."
                    } else {
                        CloudBackup.auth().signInWithEmailAndPassword(email.trim(), password).await()
                        password = ""; confirmPassword = ""
                    }
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (create) "Sign up" else "Log in") }
                TextButton(onClick = { create = !create; password = ""; confirmPassword = "" }, enabled = !busy) {
                    Text(if (create) "Already have an account? Log in" else "New here? Sign up")
                }
                TextButton(onClick = { runAction {
                    require(email.trim().isNotBlank()) { "Enter your email first." }
                    CloudBackup.auth().sendPasswordResetEmail(email.trim()).await()
                    message = "If an account exists for that email, a password reset link will arrive."
                } }, enabled = !busy) { Text("Forgot password?") }
            } else {
                Text(user.email.orEmpty(), fontWeight = FontWeight.Bold)
                if (!user.isEmailVerified) {
                    Text("Verify your email to protect cloud backups.")
                    OutlinedButton(onClick = { runAction { user.sendEmailVerification().await(); message = "Verification email sent." } }, enabled = !busy) { Text("Send verification email") }
                    Button(onClick = { runAction {
                        user.reload().await(); user.getIdToken(true).await()
                        message = if (CloudBackup.auth().currentUser?.isEmailVerified == true) "Email verified. You can now enable backup or restore." else "Email is not verified yet. Check your inbox and spam folder."
                    } }, enabled = !busy) { Text("Check verification") }
                } else {
                    if (CloudBackup.enabled() && CloudBackup.needsUpload()) Text("Changes are waiting for a confirmed cloud backup.")
                    Text(CloudBackup.status())
                    Text("Cloud copies include plans, focus history, reward balance, your profile and study preferences. PDF/journal files and imported audio stay local; use Export full backup below.")
                    OutlinedTextField(phone, { phone = it.take(30) }, label = { Text("Phone number (optional)") }, singleLine = true,
                        supportingText = { Text("Profile detail only; this number is not verified and cannot log you in.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedButton(onClick = { prefs.edit().putString("profile_phone", phone.trim()).apply(); message = "Phone number saved on this device." }, enabled = !busy) { Text("Save phone number") }
                    if (!CloudBackup.enabled()) {
                        Button(onClick = { consent = true }, enabled = !busy) { Text("Enable backup for this device") }
                    } else {
                        Button(onClick = { runAction { CloudBackup.upload(user.uid, force = true); message = "Cloud backup completed. PDFs and audio still need a file backup." } }, enabled = !busy) { Text("Back up now") }
                        TextButton(onClick = { CloudBackup.disable(); refresh++ }, enabled = !busy) { Text("Turn off automatic backup") }
                    }
                    OutlinedButton(onClick = { runAction { copies = CloudBackup.copies(); if (copies!!.isEmpty()) message = "No cloud backups found for this account." } }, enabled = !busy) { Text("Find cloud backups to restore") }
                    copies?.forEach { copy ->
                        val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(copy.updatedAt))
                        OutlinedButton(onClick = { restore = copy }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restore ${copy.label}\n$date") }
                    }
                }
                TextButton(onClick = { signOut = true }, enabled = !busy) { Text("Log out") }
            }
            HorizontalDivider()
            Text("Full file backup", style = MaterialTheme.typography.titleLarge)
            Text("Export a ZIP containing plans, history, profile, journal files and imported audio. Save it to Google Drive or another safe location. Export again after important changes; this is a manual backup.")
            OutlinedButton(onClick = { export.launch("STAYCALM-backup-${java.time.LocalDate.now()}.zip") }, enabled = !busy) { Text("Export full backup") }
            OutlinedButton(onClick = { importConfirm = true }, enabled = !busy) { Text("Restore from backup file") }
            Text("Cloud backup runs after changes when Android allows background work and internet is available. Check the last successful backup before uninstalling. Restore replaces local plans and progress; it does not merge simultaneous changes from different devices.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Back up this device's data?") },
        text = { Text("Upload this phone's current plans and progress to ${user?.email}. If you changed phones, restore your existing cloud copy first. Other devices keep separate copies. Only enable this for data you want in this account.") },
        confirmButton = { TextButton(onClick = { consent = false; runAction { CloudBackup.enable(); CloudBackup.upload(user!!.uid, force = true); message = "Backup enabled and first cloud copy saved." } }) { Text("Enable & back up") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
    if (restore != null) AlertDialog(onDismissRequest = { restore = null }, title = { Text("Replace local plans and progress?") },
        text = { Text("Export a full backup first if you want to keep current data. This restores the selected cloud copy, keeps local PDFs/audio, and turns off automatic backup until you enable it again. Finish any active timer first.") },
        confirmButton = { TextButton(onClick = { val copy = restore!!; restore = null; runAction { CloudBackup.restore(copy.payload); onRestored() } }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { restore = null }) { Text("Cancel") } })
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text("Log out?") },
        text = { Text("Local data stays on this phone. Automatic cloud backup stops. Back up now or export a file before uninstalling or switching accounts.") },
        confirmButton = { TextButton(onClick = { CloudBackup.signOut(); signOut = false; copies = null; refresh++ }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { signOut = false }) { Text("Cancel") } })
    if (importConfirm) AlertDialog(onDismissRequest = { importConfirm = false }, title = { Text("Restore a full backup?") },
        text = { Text("This replaces current plans, history, profile and file lists. Export your current data first if you want to keep it. Finish any active timer first.") },
        confirmButton = { TextButton(onClick = { importConfirm = false; importBackup.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("Cancel") } })
}
