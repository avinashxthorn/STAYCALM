package com.staycalm.focus

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StaycalmAiModelsTest {
    private val today = LocalDate.of(2026, 10, 21)
    private fun response(): JSONObject = JSONObject().put("summary", "Build confidence in swine housing with short recall sessions.")
        .put("insights", JSONArray(listOf("Two recent sessions covered housing.", "Review the breeds you marked as weak.", "Your exam goal benefits from regular recall.")))
        .put("days", JSONArray().apply { (1..7).forEach { day -> put(JSONObject().put("dayOffset", day)
            .put("topic", "Swine housing $day").put("minutes", 25).put("action", "Recall five housing requirements, then check your notes.")) } })
        .put("nudges", JSONArray((1..7).map { "Open Planner and practise recall for swine housing: step $it." }))
    private fun parse(o: JSONObject, budget: Int = 120) = AiCoachFormat.parse(o.toString(), today, budget, 1234, "context")

    @Test fun createsDatedTopicSpecificPlanAndRoundTripsCache() {
        val report = parse(response())
        assertEquals("2026-10-22", report.days.first().date)
        assertEquals("2026-10-28", report.days.last().date)
        assertEquals(7, report.nudges.size)
        assertEquals(report, AiStudyReport.decode(report.encode()))
    }
    @Test fun rejectsOversizedAndIncompleteOutputWithoutCreatingPlan() {
        assertThrows(Exception::class.java) { parse(response().put("insights", JSONArray())) }
        assertThrows(Exception::class.java) { parse(response().put("nudges", JSONArray(listOf("one")))) }
        assertThrows(Exception::class.java) { parse(response().put("summary", "x".repeat(401))) }
        assertThrows(Exception::class.java) { AiCoachFormat.parse("x".repeat(20_001), today, 120, 0, "hash") }
    }
    @Test fun rejectsDuplicatePastAndOutOfOrderDays() {
        for (offset in listOf(0, 1, 8)) {
            val o = response()
            o.getJSONArray("days").getJSONObject(1).put("dayOffset", offset)
            assertThrows(Exception::class.java) { parse(o) }
        }
    }
    @Test fun enforcesLearnerDailyBudgetAndTimerMaximum() {
        val o = response()
        o.getJSONArray("days").getJSONObject(0).put("minutes", 45)
        assertThrows(Exception::class.java) { parse(o, 30) }
        o.getJSONArray("days").getJSONObject(0).put("minutes", 181)
        assertThrows(Exception::class.java) { parse(o, 360) }
        o.getJSONArray("days").getJSONObject(0).put("minutes", 0)
        assertThrows(Exception::class.java) { parse(o) }
    }
    @Test fun rejectsFractionalDurationsAndInvalidPreferredTimes() {
        val o=response();o.getJSONArray("days").getJSONObject(0).put("minutes",25.5)
        assertThrows(Exception::class.java){parse(o)}
        val p=response();p.getJSONArray("days").getJSONObject(0).put("preferredTime","25:00")
        assertThrows(Exception::class.java){parse(p)}
    }
    @Test fun backupKeepsGoalsButExcludesKeysConsentAndPrivateGeneratedCache() {
        val encoded = BackupSnapshot.encode(mapOf("plans" to "[]", "logs" to "[]", "ai_goal" to "LPM Paper I",
            "ai_weak_topics" to "Housing", "ai_exam_date" to "2026-11-01", "ai_key" to "never-export",
            "ai_enabled" to true, "ai_notifications" to true, "ai_report" to "private", "ai_config_token" to "private"))
        val decoded = BackupSnapshot.decode(encoded)
        assertEquals("LPM Paper I", decoded["ai_goal"])
        assertEquals("2026-11-01", decoded["ai_exam_date"])
        assertFalse(encoded.contains("never-export"))
        assertFalse(encoded.contains("private"))
        assertFalse(decoded.containsKey("ai_enabled"))
        assertFalse(decoded.containsKey("ai_notifications"))
    }
}
