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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class StaycalmApplication : Application() {
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
        meta = app.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)
        data = app.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
        if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
        data.registerOnSharedPreferenceChangeListener(listener)
        if (ready()) auth().addAuthStateListener { schedule() }
    }
    fun ready() = FirebaseApp.getApps(app).isNotEmpty()
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun enabled(): Boolean = ready() && auth().currentUser?.uid?.let { meta.getString("owner", null) == it } == true
    fun status(): String = meta.getString("status", "Cloud backup is off. Your data is saved on this device.")!!
    fun deviceId(): String = meta.getString("device", null) ?: UUID.randomUUID().toString().also {
        check(meta.edit().putString("device", it).commit())
    }
    fun payload(): String = BackupSnapshot.encode(data.all + mapOf(
        "plans" to (data.getString("plans", "[]") ?: "[]"),
        "logs" to (data.getString("logs", "[]") ?: "[]")))
    fun enable() {
        val user = auth().currentUser ?: error("Sign in first.")
        require(user.isEmailVerified) { "Verify your email first, then tap Check verification." }
        check(meta.edit().putString("owner", user.uid).remove("hash").commit())
        schedule()
    }
    fun disable() {
        check(meta.edit().remove("owner").putString("status", "Cloud backup is off. Local data and existing cloud copies are kept.").commit())
        WorkManager.getInstance(app).cancelUniqueWork(WORK)
        WorkManager.getInstance(app).cancelUniqueWork("$WORK-periodic")
    }
    fun signOut() { disable(); auth().signOut() }
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
    suspend fun upload(expectedUid: String, force: Boolean = false) {
        val user = auth().currentUser ?: error("Sign in to back up.")
        require(enabled() && user.uid == expectedUid && user.isEmailVerified) { "Cloud backup is not enabled for this account." }
        val payload = payload()
        BackupSnapshot.decode(payload)
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        if (!force && hash == meta.getString("hash", null)) return
        val ref = FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("backups").document(deviceId())
        ref.set(mapOf("schema" to 1, "payload" to payload, "label" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(100),
            "updatedAt" to FieldValue.serverTimestamp())).await()
        // Never let a callback after logout change the next account's status/hash.
        if (enabled() && auth().currentUser?.uid == expectedUid) {
            meta.edit().putString("hash", hash).putString("status", "Last cloud backup: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))}").apply()
            if (payload != payload()) schedule()
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
    fun restore(payload: String) {
        val values = BackupSnapshot.decode(payload)
        require(!SessionUninstallGuard.unfinished(FocusStore(app).state())) { "Finish your timer before restoring a backup." }
        // Stop future uploads before replacing local state; no live timers/permission grants restored.
        disable()
        val previousPlans = FocusStore(app).tasks()
        val editor = data.edit()
        BackupSnapshot.keys.forEach { editor.remove(it) }
        putValues(editor, values)
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
        return try { CloudBackup.upload(uid); Result.success() }
        catch (error: Exception) {
            CloudBackup.failure(error.localizedMessage ?: "Check internet and Firebase setup")
            if (error is IllegalArgumentException) Result.failure() else Result.retry()
        }
    }
}
