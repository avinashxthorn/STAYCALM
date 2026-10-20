package com.staycalm.focus

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A user-chosen file backup also preserves PDFs/audio without paid Cloud Storage. */
object LocalBackup {
    private const val MAX_SNAPSHOT_BYTES = 10_000_000
    private const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
    private fun safeFile(path: String): Boolean = Regex("(journal|audio)/[A-Za-z0-9_.-]{1,120}").matches(path) && !path.contains("..")
    private fun digest(input: java.io.InputStream): ByteArray {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32_768)
        while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        return hash.digest()
    }
    fun export(context: Context, uri: Uri) {
        val uid = AccountData.uid(context)
        val accountRoot = AccountData.root(context)
        val prefs = AccountData.preferences(context)
        val snapshot = CloudBackup.payload(uid)
        BackupSnapshot.decode(snapshot, MAX_SNAPSHOT_BYTES)
        val metadata = JSONObject().put("snapshot", snapshot)
            .put("study_attachments", JSONArray(prefs.getString("study_attachments", "[]")))
            .put("imported_audio", JSONArray(prefs.getString("imported_audio", "[]")))
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the backup destination.")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("staycalm.json")); zip.write(metadata.toString().toByteArray()); zip.closeEntry()
            var size = 0L
            listOf("journal", "audio").forEach { folder ->
                File(accountRoot, folder).listFiles()?.filter { it.isFile }?.forEach { file ->
                    val path = "$folder/${file.name}"
                    require(safeFile(path)); size += file.length()
                    require(size <= MAX_ARCHIVE_BYTES) { "Your files exceed the 1 GB export limit." }
                    zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
    }
    fun restore(context: Context, uri: Uri) {
        val uid = AccountData.uid(context)
        val accountRoot = AccountData.root(context)
        require(!SessionUninstallGuard.unfinished(FocusStore(context).state())) { "Finish your timer before restoring." }
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
            var total = 0L
            val entries = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entries.size < 10_000) { "Too many files in this backup." }
                    require(entries.add(entry.name)) { "Duplicate backup file." }
                    require(entry.name == "staycalm.json" || safeFile(entry.name)) { "This is not a STAYCALM backup." }
                    require(!entry.isDirectory)
                    val target = File(staging, entry.name).apply { parentFile!!.mkdirs() }
                    target.outputStream().use { out ->
                        val buffer = ByteArray(32_768)
                        var entrySize = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count; entrySize += count
                            require(total <= MAX_ARCHIVE_BYTES) { "Backup exceeds the 1 GB restore limit." }
                            require(entry.name != "staycalm.json" || entrySize <= 32_000_000) { "Backup metadata is too large." }
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }
            val meta = JSONObject(File(staging, "staycalm.json").readText())
            val payload = meta.getString("snapshot")
            BackupSnapshot.decode(payload, MAX_SNAPSHOT_BYTES)
            val attachments = meta.getJSONArray("study_attachments")
            repeat(attachments.length()) { i ->
                val item = attachments.getJSONObject(i)
                val path = "journal/${item.getString("id")}"
                require(safeFile(path) && File(staging, path).isFile) { "Backup is missing a journal file." }
                java.time.LocalDate.parse(item.getString("date"))
                item.getString("name"); item.getString("mime"); item.getLong("addedAt")
                require(item.getLong("size") == File(staging, path).length())
            }
            val audio = meta.getJSONArray("imported_audio")
            repeat(audio.length()) { i ->
                val item = audio.getJSONObject(i)
                val path = "audio/${item.getString("file")}"
                require(safeFile(path) && File(staging, path).isFile) { "Backup is missing an audio file." }
                item.getString("id"); item.getString("title")
            }
            // All archive paths and references have been validated before any user state changes.
            // UUID filenames can be reused only when their bytes match an existing local copy.
            entries.filter { it != "staycalm.json" }.forEach { path ->
                val target = File(accountRoot, path)
                val source = File(staging, path)
                require(!target.exists() || source.inputStream().use { a -> target.inputStream().use { b ->
                    digest(a).contentEquals(digest(b))
                } }) { "A local file conflicts with this backup. Keep the current files and use another device to restore." }
            }
            entries.filter { it != "staycalm.json" }.forEach { path ->
                val target = File(accountRoot, path)
                if (!target.exists()) { target.parentFile!!.mkdirs(); File(staging, path).copyTo(target) }
            }
            CloudBackup.restore(payload, MAX_SNAPSHOT_BYTES, mapOf("study_attachments" to attachments.toString(), "imported_audio" to audio.toString()), expectedUid = uid)
        } finally { staging.deleteRecursively() }
    }
}
