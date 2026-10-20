package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class BackupSnapshotTest {
    private val values = mapOf("plans" to "[]", "logs" to "[]", "profile_name" to "Komii", "daily_goal" to 120,
        "reward_balance" to 5L, "volume" to .6f, "profile_ready" to true)
    @Test fun preservesDataButNeverRestoresLiveSessionsOrTokens() {
        val result = BackupSnapshot.decode(BackupSnapshot.encode(values + mapOf("running" to true, "session_id" to "live", "idToken" to "secret")))
        assertEquals("Komii", result["profile_name"])
        assertEquals(5L, result["reward_balance"])
        assertEquals(120, result["daily_goal"])
        assertEquals(.6f, result["volume"])
        assertFalse(result.containsKey("running")); assertFalse(result.containsKey("session_id")); assertFalse(result.containsKey("idToken"))
    }
    @Test fun rejectsIncompleteBackupBeforeReplacingAnything() {
        assertThrows(Exception::class.java) { BackupSnapshot.decode("{\"schema\":1,\"values\":{}}") }
    }
    @Test fun rejectsMalformedPlans() {
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values + ("plans" to "[{\"id\":\"a\"}]"))) }
    }
    @Test fun refusesOversizedAndFutureBackups() {
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values + ("profile_name" to "x".repeat(750001)))) }
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values).replace("\"schema\":1", "\"schema\":2")) }
    }
    @Test fun refusesWrongTypesAndUnsafeSettings() {
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values + ("daily_goal" to "120"))) }
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values + ("reward_balance" to -5L))) }
        assertThrows(Exception::class.java) { BackupSnapshot.decode(BackupSnapshot.encode(values + ("volume" to 2f))) }
    }
}
