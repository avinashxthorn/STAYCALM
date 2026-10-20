package com.staycalm.focus

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlannerFileTransferTest {
    private val id = "a".repeat(32)
    private class MemoryStore : PlannerChunkStore {
        var header: CloudFileState? = null
        val chunks = mutableMapOf<Int, ByteArray>()
        var batches = 0
        var failBatch = -1
        override suspend fun state(id: String) = header
        override suspend fun writeBatch(id: String, state: CloudFileState, chunks: Map<Int, ByteArray>) {
            batches++
            if (batches == failBatch) error("Network interrupted")
            this.chunks.putAll(chunks.mapValues { it.value.copyOf() }); header = state
        }
        override suspend fun readChunk(id: String, index: Int) = chunks[index] ?: error("Missing chunk")
    }
    @Test fun plannerFileRecoversAfterTheOriginalLocalFileIsDeleted() = runBlocking {
        val dir = Files.createTempDirectory("planner-roundtrip").toFile()
        try {
            val source = File(dir, "source.pdf")
            source.writeBytes(ByteArray(PlannerFileTransfer.CHUNK_BYTES + 123) { (it % 251).toByte() })
            val expected = PlannerFileTransfer.digest(source)
            val size = source.length()
            val store = MemoryStore()
            PlannerFileTransfer.upload(id, source, size, store)
            assertTrue(store.header!!.complete)
            source.delete()
            val recovered = File(dir, "restored.pdf")
            PlannerFileTransfer.download(id, size, recovered, store)
            assertEquals(expected, PlannerFileTransfer.digest(recovered))
            assertEquals(size, recovered.length())
        } finally { dir.deleteRecursively() }
    }
    @Test fun interruptedUploadResumesAtTheLastAtomicBatch() = runBlocking {
        val dir = Files.createTempDirectory("planner-resume").toFile()
        try {
            val source = File(dir, "source")
            source.writeBytes(ByteArray(PlannerFileTransfer.CHUNK_BYTES * 9 + 17) { (it % 239).toByte() })
            val store = MemoryStore().apply { failBatch = 2 }
            try { PlannerFileTransfer.upload(id, source, source.length(), store); fail("Expected network failure") } catch (_: IllegalStateException) { }
            assertEquals(8, store.header!!.uploaded)
            assertFalse(store.header!!.complete)
            val first = store.chunks[0]!!
            store.failBatch = -1
            PlannerFileTransfer.upload(id, source, source.length(), store)
            assertTrue(store.header!!.complete)
            assertSame(first, store.chunks[0]) // Completed checkpoint wasn't uploaded again.
            val previousBatches = store.batches
            PlannerFileTransfer.upload(id, source, source.length(), store)
            assertEquals(previousBatches, store.batches)
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptedOrPartialCloudFileNeverBecomesARecoveredFile() = runBlocking {
        val dir = Files.createTempDirectory("planner-corrupt").toFile()
        try {
            val source = File(dir, "source").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val store = MemoryStore()
            PlannerFileTransfer.upload(id, source, 3, store)
            store.chunks[0] = byteArrayOf(3, 2, 1)
            val recovered = File(dir, "restored")
            try { PlannerFileTransfer.download(id, 3, recovered, store); fail("Expected checksum failure") } catch (_: IllegalArgumentException) { }
            assertFalse(recovered.exists())
            store.header = store.header!!.copy(uploaded = 0, complete = false)
            try { PlannerFileTransfer.download(id, 3, recovered, store); fail("Expected incomplete upload failure") } catch (_: IllegalArgumentException) { }
            assertFalse(recovered.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun unsafeIdsAndInvalidChunkCountsAreRejected() {
        assertFalse(PlannerFileTransfer.validId("../escape"))
        assertThrows(Exception::class.java) { CloudFileState(524289, "a".repeat(64), 1, 1, true).validate() }
        assertThrows(Exception::class.java) { PlannerManifest.parse(org.json.JSONArray("[{\"id\":\"../escape\",\"date\":\"2026-10-20\",\"name\":\"x\",\"mime\":\"text/plain\",\"size\":1,\"addedAt\":0}]")) }
    }
}
