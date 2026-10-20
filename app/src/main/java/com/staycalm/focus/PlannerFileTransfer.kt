package com.staycalm.focus

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Bounded streaming transfer with durable checkpoints; no whole-file byte arrays. */
data class CloudFileState(val size: Long, val sha256: String, val chunks: Int, val uploaded: Int, val complete: Boolean) {
    fun validate() {
        require(size in 1..PlannerFileTransfer.MAX_FILE_BYTES)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(chunks == ((size + PlannerFileTransfer.CHUNK_BYTES - 1) / PlannerFileTransfer.CHUNK_BYTES).toInt())
        require(uploaded in 0..chunks && complete == (uploaded == chunks))
    }
}

interface PlannerChunkStore {
    suspend fun state(id: String): CloudFileState?
    suspend fun writeBatch(id: String, state: CloudFileState, chunks: Map<Int, ByteArray>)
    suspend fun readChunk(id: String, index: Int): ByteArray
}

object PlannerFileTransfer {
    const val CHUNK_BYTES = 512 * 1024
    const val MAX_FILE_BYTES = 80L * 1024 * 1024
    const val MAX_COPY_BYTES = 160L * 1024 * 1024
    const val BATCH_CHUNKS = 8 // 4 MiB, below Firestore's 10 MiB request limit.
    fun validId(id: String) = id.matches(Regex("[a-f0-9]{32}"))
    fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(32_768)
            while (true) { val count = input.read(bytes); if (count < 0) break; hash.update(bytes, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun upload(id: String, file: File, expectedSize: Long, store: PlannerChunkStore, progress: (Long, Long) -> Unit = { _, _ -> }) {
        require(validId(id)) { "Invalid planner file ID." }
        require(file.isFile && file.length() == expectedSize && expectedSize in 1..MAX_FILE_BYTES) { "A planner file is missing or too large to upload." }
        val sha = digest(file)
        val count = ((expectedSize + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
        val previous = store.state(id)
        previous?.validate()
        require(previous == null || (previous.size == expectedSize && previous.sha256 == sha)) { "A planner file conflicts with its cloud copy." }
        if (previous?.complete == true) return
        var uploaded = previous?.uploaded ?: 0
        RandomAccessFile(file, "r").use { input ->
            input.seek(uploaded.toLong() * CHUNK_BYTES)
            while (uploaded < count) {
                val batch = linkedMapOf<Int, ByteArray>()
                repeat(minOf(BATCH_CHUNKS, count - uploaded)) { offset ->
                    val index = uploaded + offset
                    val length = minOf(CHUNK_BYTES.toLong(), expectedSize - index.toLong() * CHUNK_BYTES).toInt()
                    batch[index] = ByteArray(length).also { input.readFully(it) }
                }
                val next = uploaded + batch.size
                val state = CloudFileState(expectedSize, sha, count, next, next == count)
                store.writeBatch(id, state, batch)
                uploaded = next
                progress(minOf(expectedSize, uploaded.toLong() * CHUNK_BYTES), expectedSize)
            }
        }
        require(file.length() == expectedSize && digest(file) == sha) { "The planner file changed during upload. Try again." }
    }
    suspend fun download(id: String, expectedSize: Long, target: File, store: PlannerChunkStore, progress: (Long, Long) -> Unit = { _, _ -> }) {
        require(validId(id)) { "Invalid planner file ID." }
        val state = store.state(id) ?: error("This planner file has not finished uploading. Keep the original phone's copy and sync it again.")
        state.validate()
        require(state.complete && state.size == expectedSize) { "This planner file has not finished uploading." }
        target.parentFile!!.mkdirs()
        try {
            FileOutputStream(target).use { output ->
                var written = 0L
                repeat(state.chunks) { index ->
                    val bytes = store.readChunk(id, index)
                    val expected = minOf(CHUNK_BYTES.toLong(), state.size - written).toInt()
                    require(bytes.size == expected) { "A cloud file chunk is missing or damaged." }
                    output.write(bytes); written += bytes.size; progress(written, state.size)
                }
                output.fd.sync()
            }
            require(target.length() == state.size && digest(target) == state.sha256) { "Cloud file integrity check failed. Local data has not been replaced." }
        } catch (error: Exception) { target.delete(); throw error }
    }
}
