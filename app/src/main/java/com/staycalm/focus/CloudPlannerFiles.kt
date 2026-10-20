package com.staycalm.focus

import android.content.Context
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Attachment metadata travels in a recovery snapshot; bytes live in owner-only chunk documents. */
object PlannerManifest {
    fun parse(array: JSONArray): List<StudyAttachment> {
        val ids = mutableSetOf<String>()
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val item = StudyAttachment(o.getString("id"), o.getString("date"), o.getString("name"), o.getString("mime"), o.getLong("size"), o.getLong("addedAt"))
            require(PlannerFileTransfer.validId(item.id) && ids.add(item.id)) { "Invalid or duplicate planner file." }
            LocalDate.parse(item.date)
            require(item.name.length in 1..180 && item.mime.length in 1..120)
            require(item.size in 1..PlannerFileTransfer.MAX_FILE_BYTES && item.addedAt >= 0)
            item
        }
        require(items.sumOf { it.size } <= PlannerFileTransfer.MAX_COPY_BYTES) { "Planner files exceed the 160 MB cloud-copy limit. Keep a full file export for the remaining files." }
        return items
    }
    fun json(items: List<StudyAttachment>) = JSONArray().apply { items.forEach { item -> put(JSONObject()
        .put("id", item.id).put("date", item.date).put("name", item.name).put("mime", item.mime).put("size", item.size).put("addedAt", item.addedAt)) } }
}

class FirestorePlannerChunkStore(private val uid: String, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : PlannerChunkStore {
    private fun ref(id: String) = db.collection("users").document(uid).collection("plannerFiles").document(id)
    override suspend fun state(id: String): CloudFileState? = withTimeout(60_000) {
        val data = ref(id).get(Source.SERVER).await()
        if (!data.exists()) null else CloudFileState(data.getLong("size") ?: error("Invalid cloud file size."),
            data.getString("sha256") ?: error("Invalid cloud file checksum."), (data.getLong("chunks") ?: 0).toInt(),
            (data.getLong("uploaded") ?: 0).toInt(), data.getBoolean("complete") ?: false).also { it.validate() }
    }
    override suspend fun writeBatch(id: String, state: CloudFileState, chunks: Map<Int, ByteArray>) = withTimeout(60_000) {
        val file = ref(id)
        val batch = db.batch()
        chunks.forEach { (index, bytes) -> batch.set(file.collection("chunks").document(index.toString()), mapOf("data" to Blob.fromBytes(bytes))) }
        batch.set(file, mapOf("size" to state.size, "sha256" to state.sha256, "chunks" to state.chunks,
            "uploaded" to state.uploaded, "complete" to state.complete))
        batch.commit().await()
        Unit
    }
    override suspend fun readChunk(id: String, index: Int): ByteArray = withTimeout(60_000) {
        ref(id).collection("chunks").document(index.toString()).get(Source.SERVER).await().getBlob("data")?.toBytes()
            ?: error("A planner file chunk is missing in the cloud.")
    }
}

class CloudPlannerFiles(private val context: Context) {
    suspend fun upload(uid: String, items: List<StudyAttachment>, status: (String) -> Unit) = withContext(Dispatchers.IO) {
        val store = FirestorePlannerChunkStore(uid)
        items.forEachIndexed { index, item ->
            status("Uploading planner file ${index + 1}/${items.size}: ${item.name}")
            PlannerFileTransfer.upload(item.id, File(AccountData.rootFor(context, uid), "journal/${item.id}"), item.size, store) { sent, total ->
                status("Uploading ${item.name}: ${sent * 100 / total}%")
            }
        }
    }
    suspend fun restore(uid: String, payload: String, status: (String) -> Unit) = withContext(Dispatchers.IO) {
        // Old structured-only backups remain readable, but cannot contain files never uploaded.
        val root = JSONObject(payload)
        val array = root.optJSONArray("plannerFiles")
        val items = array?.let { PlannerManifest.parse(it) }
        BackupSnapshot.decode(payload)
        val staging = File(context.cacheDir, "cloud-restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store = FirestorePlannerChunkStore(uid)
            items?.forEachIndexed { index, item ->
                status("Restoring planner file ${index + 1}/${items.size}: ${item.name}")
                PlannerFileTransfer.download(item.id, item.size, File(staging, item.id), store)
            }
            require(CloudBackup.auth().currentUser?.uid == uid) { "The signed-in account changed. Restore was stopped." }
            require(!SessionUninstallGuard.unfinished(FocusStore(context).state())) { "Finish your timer before restoring." }
            // Validate every file before publishing file lists or replacing existing planner data.
            items?.forEach { item ->
                val target = File(AccountData.rootFor(context, uid), "journal/${item.id}")
                require(!target.exists() || PlannerFileTransfer.digest(target) == PlannerFileTransfer.digest(File(staging, item.id))) { "A local file conflicts with this cloud copy. Export it before restoring on a different installation." }
            }
            items?.forEach { item ->
                val target = File(AccountData.rootFor(context, uid), "journal/${item.id}")
                if (!target.exists()) { target.parentFile!!.mkdirs(); File(staging, item.id).copyTo(target) }
            }
            CloudBackup.restore(payload, expectedUid = uid, fileLists = if (array == null) emptyMap() else mapOf("study_attachments" to array.toString()))
        } finally { staging.deleteRecursively() }
    }
}
