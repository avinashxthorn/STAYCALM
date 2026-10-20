package com.staycalm.focus

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

open class StaycalmApplication : Application() {
    open fun accountUid(): String? = if (FirebaseApp.getApps(this).isEmpty()) null else FirebaseAuth.getInstance().currentUser?.uid
    open fun verifiedAccountUid(): String? = if (FirebaseApp.getApps(this).isEmpty()) null else FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid
    override fun onCreate() { super.onCreate(); CloudBackup.initialize(this) }
}

data class CloudCopy(val id: String, val label: String, val updatedAt: Long, val payload: String)

object CloudBackup {
    private const val WORK = "staycalm-cloud-backup"
    private lateinit var app: Context
    private lateinit var meta: SharedPreferences
    private lateinit var data: SharedPreferences
    // A strong reference is required: SharedPreferences holds listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in BackupSnapshot.keys) schedule()
    }
    fun initialize(context: Context) {
        app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
        AccountData.migrateLegacy(app)
        bindAccount()
        if (ready()) {
            // Recovery always reads from the server; avoid persisting private backup blobs twice.
            FirebaseFirestore.getInstance().firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
            var previousUid = AccountData.uid(app)
            auth().addAuthStateListener {
                val uid = AccountData.uid(app)
                if (uid != previousUid) {
                    app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
                    app.stopService(android.content.Intent(app, SoundService::class.java))
                    app.stopService(android.content.Intent(app, TimerService::class.java))
                    StudyVideoVisibility.background()
                }
                previousUid = uid
                bindAccount(); schedule(); AiCoachWork.schedule(app)
            }
        }
    }
    fun bindAccount() {
        if (::data.isInitialized) data.unregisterOnSharedPreferenceChangeListener(listener)
        data = AccountData.preferences(app)
        meta = AccountData.metadata(app)
        data.registerOnSharedPreferenceChangeListener(listener)
    }
    fun ready() = FirebaseApp.getApps(app).isNotEmpty()
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun enabled(): Boolean = ready() && auth().currentUser?.uid?.let { meta.getString("owner", null) == it } == true
    fun status(): String = meta.getString("status", "Cloud backup is off. Your data is saved on this device.")!!
    fun deviceId(): String = meta.getString("device", null) ?: UUID.randomUUID().toString().also {
        check(meta.edit().putString("device", it).commit())
    }
    fun payload(uid: String? = AccountData.uid(app)): String {
        val values = AccountData.preferencesFor(app, uid).all
        return BackupSnapshot.encode(values + mapOf(
            "plans" to (values["plans"] ?: "[]"), "logs" to (values["logs"] ?: "[]")))
    }
    fun errorMessage(error: Exception): String = if (error is kotlinx.coroutines.TimeoutCancellationException)
        "Cloud sync paused. Check your connection and retry"
        else if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
        error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
        "Cloud access was denied. Publish the updated STAYCALM firestore.rules and verify your email"
        else error.localizedMessage ?: "Check your connection and try again"
    suspend fun restoreCloud(copy: CloudCopy) {
        val uid = auth().currentUser?.uid ?: error("Sign in first.")
        // Older snapshots may contain plannerFiles metadata. Decode only structured data;
        // cloud restore never downloads file bytes or changes local attachment/audio lists.
        restore(copy.payload, expectedUid = uid)
    }
    suspend fun recoverAfterLogin(enableSync: Boolean = false): Boolean {
        bindAccount()
        // A first login on a fresh installation can safely recover without replacing local work.
        require(auth().currentUser?.isEmailVerified == true) { "Verify your email before opening your account." }
        if (Personalization(app).isReady() ||
            FocusStore(app).tasks().isNotEmpty() || FocusStore(app).logs().isNotEmpty() || StudyJournal(app).all().isNotEmpty()) {
            if (enableSync) enable() else disable()
            return false
        }
        val latest = copies().firstOrNull()
        if (latest != null) restoreCloud(latest)
        if (enableSync) enable() else disable()
        return latest != null
    }
    fun enable() {
        val user = auth().currentUser ?: error("Sign in first.")
        require(user.isEmailVerified) { "Verify your email first, then tap Check verification." }
        check(meta.edit().putString("owner", user.uid).remove("hash").putString("status", "Backup enabled. Waiting for the first confirmed cloud upload.").commit())
        schedule()
    }
    fun disable() {
        check(meta.edit().remove("owner").putString("status", "Cloud backup is off. Local data and existing cloud copies are kept.").commit())
        WorkManager.getInstance(app).cancelUniqueWork(WORK)
        WorkManager.getInstance(app).cancelUniqueWork("$WORK-periodic")
    }
    fun signOut() {
        require(!SessionUninstallGuard.unfinished(FocusStore(app).state())) { "Finish or end your current session before logging out." }
        disable()
        FocusStore(app).tasks().forEach { FocusAlarms.cancelTask(app, it.id) }
        FocusAlarms.cancelFocus(app)
        MotivationAlarms.cancel(app)
        AiCoachWork.cancel(app)
        app.stopService(android.content.Intent(app, SoundService::class.java))
        app.stopService(android.content.Intent(app, TimerService::class.java))
        app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        app.revokeUriPermission(android.net.Uri.parse("content://${app.packageName}.fileprovider/"), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        StudyVideoVisibility.background()
        AccountData.beginLogin(app)
        check(AccountData.navigation(app).edit().putBoolean("intro_done", true).commit())
        auth().signOut()
        bindAccount()
    }
    fun schedule() {
        if (!::meta.isInitialized || !enabled()) return
        val uid = auth().currentUser!!.uid
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val input = workDataOf("uid" to uid)
        val next = OneTimeWorkRequestBuilder<CloudBackupWorker>().setInputData(input)
            .setConstraints(constraints).setInitialDelay(20, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // KEEP ensures continuous editing cannot indefinitely postpone an existing backup.
        WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, next)
        val periodic = PeriodicWorkRequestBuilder<CloudBackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints).setInputData(input).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("$WORK-periodic", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }
    private fun hash(payload: String) = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    fun needsUpload() = hash(payload()) != meta.getString("hash", null)
    private val uploadMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun upload(expectedUid: String, force: Boolean = false) = uploadMutex.withLock {
        performUpload(expectedUid, force)
    }
    private suspend fun performUpload(expectedUid: String, force: Boolean) {
        val accountMeta = meta
        val user = auth().currentUser ?: error("Sign in to back up.")
        require(enabled() && user.uid == expectedUid && user.isEmailVerified) { "Cloud backup is not enabled for this account." }
        val snapshotString = payload(expectedUid)
        BackupSnapshot.decode(snapshotString)
        val hash = hash(snapshotString)
        if (!force && hash == accountMeta.getString("hash", null)) return
        require(enabled() && auth().currentUser?.uid == expectedUid) { "Cloud sync stopped because the account changed." }
        val ref = FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("backups").document(deviceId())
        try { kotlinx.coroutines.withTimeout(30_000) { ref.set(mapOf("schema" to 1, "payload" to snapshotString, "label" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(100),
            "updatedAt" to FieldValue.serverTimestamp())).await() } }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { error("Upload not confirmed yet. Check your connection and try again") }
        // Never let a callback after logout change the next account's status/hash.
        if (enabled() && auth().currentUser?.uid == expectedUid) {
            accountMeta.edit().putString("hash", hash).putString("status", "Last cloud backup: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))}").apply()
            if (snapshotString != payload()) schedule()
        }
    }
    suspend fun copies(): List<CloudCopy> {
        val uid = auth().currentUser?.uid ?: error("Sign in first.")
        require(auth().currentUser!!.isEmailVerified) { "Verify your email before restoring." }
        // Server-only reads: never present a stale offline cache as a recoverable cloud copy.
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("backups")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20).get(Source.SERVER).await()
            .documents.map { CloudCopy(it.id, it.getString("label") ?: "Device", it.getTimestamp("updatedAt")?.toDate()?.time ?: 0,
                it.getString("payload") ?: error("Incomplete cloud backup.")) }
    }
    fun restore(payload: String, maxBytes: Int = BackupSnapshot.MAX_BYTES, fileLists: Map<String, String> = emptyMap(), expectedUid: String? = AccountData.uid(app)) {
        require(AccountData.uid(app) == expectedUid) { "The account changed. Restore was stopped." }
        require(fileLists.keys.all { it in setOf("study_attachments", "imported_audio") })
        val values = BackupSnapshot.decode(payload, maxBytes)
        require(!SessionUninstallGuard.unfinished(FocusStore(app).state())) { "Finish your timer before restoring a backup." }
        // Stop future uploads before replacing local state; no live timers/permission grants restored.
        disable()
        val previousPlans = FocusStore(app).tasks()
        val editor = AccountData.preferencesFor(app, expectedUid).edit()
        BackupSnapshot.keys.forEach { editor.remove(it) }
        putValues(editor, values)
        fileLists.forEach { (key, value) -> editor.putString(key, value) }
        require(AccountData.uid(app) == expectedUid) { "The account changed. Restore was stopped." }
        check(editor.commit()) { "Could not restore this backup. Your cloud copy is unchanged." }
        previousPlans.forEach { FocusAlarms.cancelTask(app, it.id) }
        FocusStore(app).tasks().filter { it.remind && !it.done }.forEach { FocusAlarms.scheduleTask(app, it) }
        MotivationAlarms.schedule(app)
        LauncherIconSwitcher.apply(app, Personalization(app).darkMode())
    }
    fun putValues(editor: SharedPreferences.Editor, values: Map<String, Any>) {
        values.forEach { (key, value) -> when(value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
        } }
    }
    fun failure(message: String) { meta.edit().putString("status", "Cloud backup pending: $message. Local data is still saved.").apply() }
}

class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        if (!CloudBackup.ready() || !CloudBackup.enabled() || CloudBackup.auth().currentUser?.uid != uid) return Result.success()
        return try {
            CloudBackup.upload(uid)
            // Changes arriving during the network request must receive another attempt.
            if (CloudBackup.enabled() && CloudBackup.needsUpload()) Result.retry() else Result.success()
        }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            if (CloudBackup.auth().currentUser?.uid == uid) CloudBackup.failure(CloudBackup.errorMessage(error))
            if (error is IllegalArgumentException) Result.failure() else Result.retry()
        }
    }
}
