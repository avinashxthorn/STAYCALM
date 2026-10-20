package com.staycalm.focus

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class StaycalmAiFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var previous: String? = null
    private val owner = "ai-flow-${UUID.randomUUID()}"
    @Before fun reset() { ui.runOnUiThread {
        val app = ui.activity.application as AccountTestApplication
        previous = app.identity
        app.identity = owner
        AccountData.finishLogin(ui.activity)
        AccountData.preferences(ui.activity).edit().clear().commit()
    } }
    @After fun cleanup() { ui.runOnUiThread {
        val context = ui.activity
        FocusStore(context).tasks().forEach { FocusAlarms.cancelTask(context, it.id) }
        AccountData.preferencesFor(context, owner).edit().clear().commit()
        (context.application as AccountTestApplication).identity = previous
        CloudBackup.bindAccount()
    } }
    private fun mount() = ui.runOnUiThread {
        ui.activity.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { StaycalmAiCard {} } }
        }
    }
    @Test fun usesOnlyStaycalmAiBrandingAndNeverAsksForAKey() {
        mount()
        ui.onNodeWithText("Staycalm AI").assertExists()
        ui.onNodeWithText("AI coaching is off. You can enable it in AI settings.").assertExists()
        ui.onNodeWithText("AI settings").performScrollTo().performClick()
        ui.onNodeWithText("Gemini API key").assertDoesNotExist()
        ui.onNodeWithText("AI personalized notifications").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(0L, AccountData.preferences(ui.activity).getLong("ai_last_request", 0L))
    }
    @Test fun savedPlanOnlyEntersPlannerAfterTapAndCannotBeAddedTwice() {
        val report = AiStudyReport("Make progress toward your LPM exam.", listOf("Recall breeds.", "Practise housing questions.", "Review weak topics."),
            (1..7).map { AiStudyDay(LocalDate.now().plusDays(it.toLong()).toString(), "Swine topic $it", 25, "Practise five recall questions.") },
            (1..7).map { "Take one study step $it." }, System.currentTimeMillis(), "fixture")
        ui.runOnUiThread { AccountData.preferences(ui.activity).edit().putBoolean("ai_enabled", true).putString("ai_report", report.encode()).commit() }
        mount()
        assertTrue(FocusStore(ui.activity).tasks().isEmpty())
        ui.onAllNodesWithText("Review & add").onFirst().performScrollTo().performClick()
        ui.onNodeWithText("Add task").performClick()
        ui.onNodeWithText("Added to planner").assertIsNotEnabled()
        val task = FocusStore(ui.activity).tasks().single()
        assertTrue(task.title.contains("Swine topic 1"))
        assertTrue(task.remind)
        assertEquals("18:00", task.time)
        assertEquals(report.days.first().date, task.date)
    }
    @Test fun plannedBreakKeepsItsModeAndRepeatedSavingIsIdempotent() {
        val store=FocusStore(ui.activity)
        val draft=AiPlanDraft("Stretch and water",LocalDate.now().plusDays(1).toString(),"18:00",5,"break",sourceId="break-fixture")
        val first=store.addAiTask(draft)
        val second=store.addAiTask(draft)
        assertEquals(first.id,second.id)
        assertEquals(1,store.tasks().size)
        assertEquals("break",store.tasks().single().kind)
        store.configure(first.duration,first.id,first.kind)
        assertEquals("break",store.state().mode)
    }

}
