package com.staycalm.focus

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Authentication and verification finish before any private workspace is opened. */
@Composable
fun LoginScreen(context: Context, onBack: () -> Unit, onSignedIn: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var signingUp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var verification by remember { mutableStateOf(false) }
    var recoveryFailed by remember { mutableStateOf(false) }
    var resendSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(resendSeconds) { if (resendSeconds > 0) { delay(1000); resendSeconds-- } }

    suspend fun recover() {
        verification = false
        // Verification claims must be refreshed before accessing verified-owner rules.
        withTimeout(30_000) { CloudBackup.auth().currentUser!!.getIdToken(true).await() }
        val restored = CloudBackup.recoverAfterLogin()
        AccountData.finishLogin(context)
        Toast.makeText(context, "Login successful", Toast.LENGTH_LONG).show()
        onSignedIn(restored)
    }
    fun failure(problem: Exception) {
        if (problem is kotlinx.coroutines.CancellationException && problem !is kotlinx.coroutines.TimeoutCancellationException) throw problem
        val user = if (CloudBackup.ready()) CloudBackup.auth().currentUser else null
        if (user != null && user.isEmailVerified) recoveryFailed = true
        error = when (problem) {
            is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Choose a stronger password that meets this account's password requirements."
            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException,
            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Email or password is incorrect. Check them and try again."
            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Could not create this account. Try logging in or resetting your password."
            is com.google.firebase.FirebaseNetworkException -> "Cannot connect. Check your internet and try again."
            is com.google.firebase.FirebaseTooManyRequestsException -> "Too many attempts. Please wait a little before trying again."
            is kotlinx.coroutines.TimeoutCancellationException -> "The connection took too long. Please try again."
            else -> if (problem is IllegalArgumentException) problem.message else CloudBackup.errorMessage(problem)
        }
    }
    LaunchedEffect(Unit) {
        val user = if (CloudBackup.ready()) CloudBackup.auth().currentUser else null
        if (user != null) {
            AccountData.beginLogin(context)
            email = user.email.orEmpty()
            if (!user.isEmailVerified) verification = true
            else {
                busy = true
                try { recover() } catch (problem: Exception) { failure(problem) } finally { busy = false }
            }
        }
    }
    fun submit() {
        if (busy) return
        busy = true; error = null; notice = null; keyboard?.hide()
        scope.launch {
            try {
                require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
                require(password.isNotEmpty()) { "Enter your password." }
                if (signingUp) {
                    require(password.length >= 6) { "Use a password with at least 6 characters." }
                    require(password == confirm) { "Passwords do not match." }
                }
                require(CloudBackup.ready()) { "Cloud login is not configured yet." }
                AccountData.beginLogin(context)
                withTimeout(30_000) {
                    if (signingUp) CloudBackup.auth().createUserWithEmailAndPassword(email.trim(), password).await()
                    else CloudBackup.auth().signInWithEmailAndPassword(email.trim(), password).await()
                }
                CloudBackup.bindAccount()
                password = ""; confirm = ""; visible = false
                val user = CloudBackup.auth().currentUser!!
                if (!user.isEmailVerified) {
                    verification = true
                    if (signingUp) {
                        notice = "Account created. Verify your email to continue."
                        try {
                            withTimeout(30_000) { user.sendEmailVerification().await() }
                            resendSeconds = 60
                        } catch (problem: Exception) {
                            if (problem is kotlinx.coroutines.CancellationException && problem !is kotlinx.coroutines.TimeoutCancellationException) throw problem
                            error = "Your account was created, but the email could not be sent. Tap Resend verification email."
                        }
                    }
                } else recover()
            } catch (problem: Exception) { failure(problem) } finally { busy = false }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp).testTag("simple-login"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack, enabled = !busy) { Text("← Back") }
            Spacer(Modifier.height(12.dp))
            Text("staycalm.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (verification) "Verify your email" else if (recoveryFailed) "Recover your account" else if (signingUp) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (verification) {
                Text("Open the verification link sent to $email, then return here. Check your spam folder too.")
                Button(onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            withTimeout(30_000) { CloudBackup.auth().currentUser!!.reload().await() }
                            require(CloudBackup.auth().currentUser?.isEmailVerified == true) { "Open the verification link first, then try again." }
                            recover()
                        } catch (problem: Exception) { failure(problem) } finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("I've verified my email") }
                TextButton(onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            withTimeout(30_000) { CloudBackup.auth().currentUser!!.sendEmailVerification().await() }
                            resendSeconds = 60; notice = "Verification email sent."
                        } catch (problem: Exception) { failure(problem) } finally { busy = false }
                    }
                }, enabled = !busy && resendSeconds == 0) { Text(if (resendSeconds > 0) "Resend in ${resendSeconds}s" else "Resend verification email") }
            } else if (!recoveryFailed) {
                Text("Your plans, session history, insights and planner files stay with your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !signingUp, onClick = { signingUp = false; password = ""; confirm = ""; visible = false; error = null }, label = { Text("Login") }, enabled = !busy)
                    FilterChip(selected = signingUp, onClick = { signingUp = true; password = ""; confirm = ""; visible = false; error = null }, label = { Text("Sign up") }, enabled = !busy)
                }
                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), enabled = !busy,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = !busy) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide password" else "Show password") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (signingUp) ImeAction.Next else ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                if (signingUp) {
                    Text("Use at least 6 characters. A longer, unique password is safer.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
                        enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { submit() }, enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (signingUp) "Create account" else "Log in") }
                if (!signingUp) TextButton(onClick = {
                    busy = true; error = null; notice = null; keyboard?.hide()
                    scope.launch {
                        try {
                            require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address first." }
                            require(CloudBackup.ready()) { "Cloud login is not configured yet." }
                            try { withTimeout(30_000) { CloudBackup.auth().sendPasswordResetEmail(email.trim()).await() } }
                            catch (_: com.google.firebase.auth.FirebaseAuthInvalidUserException) { /* Do not reveal account existence. */ }
                            notice = "If that account exists, a reset link will arrive in your inbox."
                        } catch (problem: Exception) { failure(problem) } finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
            } else {
                Text("You are signed in. Cloud recovery could not finish. Retry to recover your plans, history, insights and files.")
                Button(onClick = {
                    busy = true; error = null
                    scope.launch { try { recover() } catch (problem: Exception) { failure(problem) } finally { busy = false } }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Retry cloud recovery") }
                // Continuing never enables uploads, so an empty device cannot overwrite its cloud copy.
                OutlinedButton(onClick = { AccountData.finishLogin(context); onSignedIn(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue without restoring") }
            }
            if (verification || recoveryFailed) TextButton(onClick = {
                CloudBackup.signOut(); verification = false; recoveryFailed = false; error = null; notice = null; password = ""; confirm = ""
            }, enabled = !busy) { Text("Use another account") }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Please wait…", style = MaterialTheme.typography.bodySmall) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
