package com.staycalm.focus

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** First-time authentication has no backup controls or technical configuration panels. */
@Composable
fun LoginScreen(context: Context, onBack: () -> Unit, onSignedIn: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var signingUp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val resumeSession = remember { CloudBackup.ready() && CloudBackup.auth().currentUser != null }
    var recoveryFailed by remember { mutableStateOf(resumeSession) }
    LaunchedEffect(Unit) {
        if (resumeSession) {
            busy = true
            try { val restored = CloudBackup.recoverAfterLogin(); onSignedIn(restored) }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                error = "Login successful. Cloud recovery could not finish: ${CloudBackup.errorMessage(failure)}"
            } finally { busy = false }
        }
    }
    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
                require(password.length >= 6) { "Use a password with at least 6 characters." }
                require(CloudBackup.ready()) { "Cloud login is not configured yet." }
                if (signingUp) {
                    require(password == confirm) { "Passwords do not match." }
                    val result = CloudBackup.auth().createUserWithEmailAndPassword(email.trim(), password).await()
                    // Account creation succeeded even if delivery of the verification email fails.
                    try { result.user!!.sendEmailVerification().await() } catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure /* Resend from account settings. */ }
                } else CloudBackup.auth().signInWithEmailAndPassword(email.trim(), password).await()
                password = ""; confirm = ""
                Toast.makeText(context, if (signingUp) "Sign up successful" else "Login successful", Toast.LENGTH_LONG).show()
                try {
                    val restored = CloudBackup.recoverAfterLogin()
                    onSignedIn(restored)
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                    // Never send a new phone into an empty planner after a failed cloud restore.
                    recoveryFailed = true
                    error = "Login successful. Cloud recovery could not finish: ${CloudBackup.errorMessage(failure)}"
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                error = failure.localizedMessage ?: "Could not sign in. Please try again."
            } finally { busy = false }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("simple-login"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack, enabled = !busy) { Text("← Back") }
            Spacer(Modifier.height(26.dp))
            Text("staycalm.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (signingUp) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(if (signingUp) "A calm space for your plans and progress." else "Log in to keep your study story with you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!recoveryFailed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !signingUp, onClick = { signingUp = false; password = ""; confirm = "" }, label = { Text("Login") }, enabled = !busy)
                    FilterChip(selected = signingUp, onClick = { signingUp = true; password = ""; confirm = "" }, label = { Text("Sign up") }, enabled = !busy)
                }
                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                if (signingUp) OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                Button(onClick = { submit() }, enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (signingUp) "Create account" else "Log in") }
                if (!signingUp) TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            require(email.trim().isNotBlank()) { "Enter your email first." }
                            require(CloudBackup.ready()) { "Cloud login is not configured yet." }
                            CloudBackup.auth().sendPasswordResetEmail(email.trim()).await()
                            error = "If that account exists, a reset link will arrive in your inbox."
                        } catch (failure: Exception) {
                            if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                            error = failure.localizedMessage
                        } finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
            } else {
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try { val restored = CloudBackup.recoverAfterLogin(); onSignedIn(restored) }
                        catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure; error = CloudBackup.errorMessage(failure) }
                        finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Retry cloud recovery") }
                OutlinedButton(onClick = { onSignedIn(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue without restoring") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Signing in and checking your cloud data…", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
