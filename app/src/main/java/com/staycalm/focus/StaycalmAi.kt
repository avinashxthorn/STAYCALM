package com.staycalm.focus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/** Personal keys are entered at runtime, scoped to the account and excluded from every backup. */
class AiKeyVault(context: Context, uid: String?) {
    private val id = MessageDigest.getInstance("SHA-256").digest((uid ?: "signed_out").toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = File(context.noBackupFilesDir, "ai-key-$id")
    private val alias = "staycalm-ai-$id"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun read(): String? = runCatching {
        if (!file.exists()) return null
        val parts = file.readText().split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
    fun save(value: String) {
        val clean = value.trim()
        require(clean.length in 25..512 && clean.all { it.isLetterOrDigit() || it in "._-" }) { "Enter a valid Gemini API key." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val text = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(clean.toByteArray()), Base64.NO_WRAP)
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        check(temp.renameTo(file)) { "Could not save the API key." }
    }
    fun clear() { file.delete(); KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
}

class StaycalmAi(private val context: Context) {
    private val owner = AccountData.uid(context)
    private val prefs = AccountData.preferencesFor(context, owner)
    private val vault = AiKeyVault(context, owner)
    fun enabled() = prefs.getBoolean("ai_enabled", false)
    fun notifications() = prefs.getBoolean("ai_notifications", false)
    fun hasKey() = vault.read() != null
    fun goal() = prefs.getString("ai_goal", "").orEmpty()
    fun weakTopics() = prefs.getString("ai_weak_topics", "").orEmpty()
    fun examDate() = prefs.getString("ai_exam_date", "").orEmpty()
    private fun token() = prefs.getString("ai_config_token", "").orEmpty()
    private fun invalidate() = prefs.edit().remove("ai_report").putString("ai_config_token", UUID.randomUUID().toString())
    fun configure(goal: String, weak: String, exam: String, apiKey: String, consent: Boolean, nudges: Boolean) {
        require(owner == AccountData.uid(context)) { "Account changed. Reopen Insights." }
        if (exam.isNotBlank()) require(!LocalDate.parse(exam.trim()).isBefore(LocalDate.now())) { "Choose today or a future exam date." }
        if (apiKey.isNotBlank()) vault.save(apiKey)
        require(!consent || hasKey()) { "Add your Gemini API key first." }
        check(invalidate().putString("ai_goal", goal.trim().take(300)).putString("ai_weak_topics", weak.trim().take(300))
            .putString("ai_exam_date", exam.trim()).putBoolean("ai_enabled", consent)
            .putBoolean("ai_notifications", consent && nudges).commit())
        AiCoachWork.schedule(context)
    }
    fun disconnect() {
        check(invalidate().putBoolean("ai_enabled", false).putBoolean("ai_notifications", false).commit())
        vault.clear()
        AiCoachWork.schedule(context)
    }
    fun report(): AiStudyReport? = runCatching { AiStudyReport.decode(prefs.getString("ai_report", null) ?: return null) }.getOrNull()

    private fun studyContext(): JSONObject {
        check(owner == AccountData.uid(context)) { "Account changed." }
        val store = FocusStore(context)
        val profile = Personalization(context)
        val today = LocalDate.now()
        val recent = store.logs().filter { it.at >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14) }
        return JSONObject().put("today", today.toString()).put("name", profile.name()).put("subject", profile.subject())
            .put("focusTopic", store.intention()).put("futureGoal", goal()).put("weakTopics", weakTopics())
            .put("examDate", examDate()).put("dailyGoalMinutes", store.dailyGoal())
            .put("maximumSessionMinutes", store.dailyGoal().coerceAtMost(180))
            .put("recentSessions", JSONArray().apply { recent.takeLast(40).forEach {
                put(JSONObject().put("date", Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                    .put("minutes", it.minutes).put("topic", it.intention.take(120)))
            } })
            .put("planner", JSONArray().apply { store.tasks().filter { runCatching {
                LocalDate.parse(it.date) in today.minusDays(7)..today.plusDays(7)
            }.getOrDefault(false) }.take(30).forEach {
                put(JSONObject().put("topic", it.title.take(120)).put("date", it.date).put("time", it.time)
                    .put("minutes", it.duration).put("completed", it.done))
            } })
    }
    private fun hash(data: String) = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun currentReport(): AiStudyReport? = report()?.takeIf { enabled() && it.contextHash == hash(studyContext().toString()) }

    suspend fun refresh(force: Boolean = false): AiStudyReport = mutex.withLock {
        check(enabled() && owner == AccountData.uid(context)) { "Enable Staycalm AI in coach settings first." }
        val configuration = token()
        val data = studyContext().toString()
        val fingerprint = hash(data)
        val now = System.currentTimeMillis()
        val cached = report()
        if (!force && cached != null && cached.contextHash == fingerprint && now - cached.generatedAt in 0..TimeUnit.HOURS.toMillis(12)) return@withLock cached
        val last = prefs.getLong("ai_last_request", 0L)
        require(now - last >= 60_000) { "Please wait one minute before refreshing AI coaching." }
        val day = LocalDate.now().toString()
        val count = if (prefs.getString("ai_request_day", "") == day) prefs.getInt("ai_request_count", 0) else 0
        require(count < 6) { "Today's six coach refreshes are used. Your saved plan is still available; try again tomorrow." }
        val secret = vault.read() ?: error("Add your Gemini API key in coach settings.")
        check(prefs.edit().putLong("ai_last_request", now).putString("ai_request_day", day)
            .putInt("ai_request_count", count + 1).commit())
        val result = withContext(Dispatchers.IO) {
            check(enabled() && owner == AccountData.uid(context) && token() == configuration) { "Coach settings changed. Refresh again." }
            request(secret, data, LocalDate.parse(day), fingerprint)
        }
        check(enabled() && owner == AccountData.uid(context) && token() == configuration) { "Coach settings changed. Refresh again." }
        check(prefs.edit().putString("ai_report", result.encode()).commit())
        result
    }
    private fun request(secret: String, data: String, today: LocalDate, fingerprint: String): AiStudyReport {
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/${AiCoachFormat.MODEL}:generateContent").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("x-goog-api-key", secret)
            val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", AiCoachFormat.SYSTEM))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Learner study data:\n$data")))))
                .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 4096)
                    .put("responseMimeType", "application/json").put("responseSchema", AiCoachFormat.schema()))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (connection.responseCode) {
                200 -> Unit
                400, 401, 403 -> error("Gemini could not authorize this request. Check your key, API access and model availability in Google AI Studio.")
                404 -> error("This Gemini model is unavailable for your project. Check Google AI Studio.")
                429 -> error("Gemini's free quota is busy or used up. Keep your saved plan and try again later.")
                else -> error("Gemini is temporarily unavailable. Your saved plan is kept.")
            }
            val raw = connection.inputStream.use { input ->
                val buffer = ByteArray(100_001)
                var size = 0
                while (size < buffer.size) {
                    val read = input.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    size += read
                }
                buffer.copyOf(size)
            }
            require(raw.size <= 100_000) { "AI response was too large." }
            val candidate = JSONObject(String(raw, Charsets.UTF_8)).getJSONArray("candidates").getJSONObject(0)
            require(candidate.optString("finishReason") == "STOP") { "Gemini could not finish this plan. Try again later." }
            val parts = candidate.getJSONObject("content").getJSONArray("parts")
            val response = (0 until parts.length()).map { parts.getJSONObject(it) }
                .filterNot { it.optBoolean("thought", false) }.joinToString("") { it.optString("text") }
            return AiCoachFormat.parse(response, today, JSONObject(data).getInt("dailyGoalMinutes"), System.currentTimeMillis(), fingerprint)
        } finally { connection.disconnect() }
    }
    fun notification(): String? {
        if (!enabled() || !notifications()) return null
        val report = currentReport() ?: return null
        if (System.currentTimeMillis() - report.generatedAt !in 0..TimeUnit.HOURS.toMillis(24)) return null
        val index = prefs.getInt("ai_nudge_index", 0).mod(report.nudges.size)
        prefs.edit().putInt("ai_nudge_index", (index + 1).mod(report.nudges.size)).apply()
        return report.nudges[index]
    }
    companion object { private val mutex = Mutex() }
}

object AiCoachWork {
    private const val NAME = "staycalm-ai-coach"
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
        WorkManager.getInstance(context).cancelUniqueWork("$NAME-refresh")
    }
    fun refreshSoon(context: Context) {
        val ai = StaycalmAi(context)
        if (AccountData.sessionUid(context) == null || !ai.enabled() || !ai.notifications() || Personalization(context).interval() == 0 || !ai.hasKey()) return
        val work = OneTimeWorkRequestBuilder<AiCoachWorker>()
            .setInputData(workDataOf("owner" to AccountData.uid(context)))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$NAME-refresh", ExistingWorkPolicy.KEEP, work)
    }
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val ai = StaycalmAi(context)
        if (AccountData.sessionUid(context) == null || !ai.enabled() || !ai.notifications() || Personalization(context).interval() == 0 || !ai.hasKey()) { cancel(context); return }
        val work = PeriodicWorkRequestBuilder<AiCoachWorker>(12, TimeUnit.HOURS)
            .setInputData(workDataOf("owner" to AccountData.uid(context)))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
    }
}

class AiCoachWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AccountData.sessionUid(applicationContext) == null || inputData.getString("owner") != AccountData.uid(applicationContext)) return Result.success()
        val ai = StaycalmAi(applicationContext)
        if (!ai.enabled() || !ai.notifications() || Personalization(applicationContext).interval() == 0) return Result.success()
        return try { ai.refresh(); Result.success() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { Result.success() /* Next interval retries; never loop against free quotas. */ }
    }
}
