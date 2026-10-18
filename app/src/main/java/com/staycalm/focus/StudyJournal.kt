package com.staycalm.focus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

data class StudyAttachment(
    val id: String,
    val date: String,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long
)

/** Durable, date-indexed local copies. Original provider permissions are not required later. */
class StudyJournal(private val context: Context) {
    private val prefs = context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
    private val folder = File(context.filesDir, "journal")
    private val maxFileBytes = 80L * 1024 * 1024

    @Synchronized fun all(): List<StudyAttachment> = runCatching {
        val array = JSONArray(prefs.getString("study_attachments", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            StudyAttachment(
                item.getString("id"), item.getString("date"), item.getString("name"),
                item.optString("mime", "application/octet-stream"),
                item.optLong("size"), item.optLong("addedAt")
            )
        }.filter { File(folder, it.id).isFile }
    }.getOrElse { emptyList() }

    @Synchronized private fun save(entries: List<StudyAttachment>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject()
                .put("id", entry.id).put("date", entry.date).put("name", entry.name)
                .put("mime", entry.mime).put("size", entry.size).put("addedAt", entry.addedAt))
        }
        check(prefs.edit().putString("study_attachments", json.toString()).commit()) {
            "Could not save your work journal."
        }
    }

    @Synchronized fun import(date: LocalDate, uri: Uri): StudyAttachment {
        val validDate = date.toString()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.take(180)?.ifBlank { null } ?: "Study file"
        val mime = context.contentResolver.getType(uri)?.take(120) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString().replace("-", "")
        require(folder.isDirectory || folder.mkdirs()) { "Cannot create the study journal folder." }
        val file = File(folder, id)
        try {
            var bytes = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("This file cannot be opened.")
            input.use { stream ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= maxFileBytes) { "Choose a file smaller than 80 MB." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(bytes > 0) { "This file is empty." }
            val entry = StudyAttachment(id, validDate, name, mime, bytes, System.currentTimeMillis())
            save(all() + entry)
            return entry
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    @Synchronized fun remove(entry: StudyAttachment) {
        save(all().filterNot { it.id == entry.id })
        File(folder, entry.id).delete()
    }

    fun openIntent(entry: StudyAttachment): Intent {
        require(all().any { it.id == entry.id }) { "This file is not in your journal anymore." }
        val file = File(folder, entry.id)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, entry.mime.ifBlank { "application/octet-stream" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
