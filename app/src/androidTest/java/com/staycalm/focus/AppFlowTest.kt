package com.staycalm.focus

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AppFlowTest {
    @get:Rule(order=0) val permission=androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order=1) val ui=createAndroidComposeRule<MainActivity>()
    @Before fun reset(){
        ui.runOnUiThread{
            FocusStore(ui.activity).abandon()
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE).edit().clear().commit()
        }
        ui.activityRule.scenario.recreate()
    }
    private fun screenshot(name:String){
        ui.waitForIdle()
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        val folder=File(ui.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        val file=File(folder,"$name.png")
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        // Gradle uninstalls the app at the end, so preserve captures before app-data cleanup.
        val command="mkdir -p /data/local/tmp/staycalm-screenshots && cp ${file.absolutePath} /data/local/tmp/staycalm-screenshots/$name.png && echo SAVED"
        val descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val result=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{String(it.readBytes())}
        assertTrue("Screenshot must survive test cleanup: $result",result.contains("SAVED"))
    }
    @Test fun pinkScreensAndPlannerFlow(){
        ui.onNodeWithText("Let your focus bloom.").assertIsDisplayed()
        screenshot("01-focus")
        ui.onNodeWithText("Planner").performClick()
        ui.onNodeWithContentDescription("New task").performScrollTo().performClick()
        ui.onNodeWithText("What will you work on?").performTextInput("Read a chapter")
        ui.onNodeWithText("Save plan").performClick()
        ui.onNodeWithText("Read a chapter").performScrollTo().assertIsDisplayed()
        screenshot("02-planner")
        ui.onNodeWithContentDescription("Focus on Read a chapter").performClick()
        ui.onNodeWithText("Pause session").performScrollTo().assertIsDisplayed()
        ui.runOnUiThread{assertEquals("Read a chapter",FocusStore(ui.activity).intention());FocusStore(ui.activity).abandon()}
        ui.onNodeWithText("Insights").performClick()
        ui.onNodeWithText("Every minute matters.").assertIsDisplayed()
        screenshot("03-insights")
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Make it yours.").assertIsDisplayed()
        screenshot("04-settings")
    }
    @Test fun focusCanPauseResumeAndEndWithoutCredit(){
        ui.onNodeWithText("Start focus").performScrollTo().performClick()
        ui.onNodeWithText("Pause session").performScrollTo().performClick()
        ui.onNodeWithText("Resume session").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("End session").performScrollTo().performClick()
        ui.onNodeWithText("End this moment?").assertIsDisplayed()
        ui.onNode(hasText("End session") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithText("Start focus").performScrollTo().assertIsDisplayed()
        ui.runOnUiThread{assertTrue(FocusStore(ui.activity).logs().isEmpty())}
    }
    @Test fun breaksDoNotCountAndCompletionIsIdempotent(){
        ui.runOnUiThread{
            val store=FocusStore(ui.activity)
            val prefs=ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
            fun due(mode:String){
                store.configure(5,mode=mode)
                store.start()
                prefs.edit().putLong("end_elapsed",SystemClock.elapsedRealtime()-100).putLong("end_wall",System.currentTimeMillis()-100).commit()
            }
            due("break")
            assertTrue(store.completeIfDue())
            assertTrue(store.logs().isEmpty())
            store.dismissCompletion()
            store.setIntention("A test intention")
            due("focus")
            assertTrue(store.completeIfDue())
            assertFalse(FocusStore(ui.activity).completeIfDue())
            assertEquals(1,store.logs().size)
            assertEquals(5,store.completedMinutes())
            assertEquals("A test intention",store.logs().single().intention)
        }
    }
    @Test fun timerSurvivesActivityRecreation(){
        ui.onNodeWithText("Start focus").performScrollTo().performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Pause session").performScrollTo().assertIsDisplayed()
        ui.runOnUiThread{assertTrue(FocusStore(ui.activity).state().running)}
    }
    @Test fun breakAndCustomDuration(){
        ui.onNodeWithText("Short break").performClick()
        ui.onNodeWithText("05:00").assertExists()
        ui.onNodeWithText("Start break").performScrollTo().performClick()
        ui.onNodeWithText("Pause session").performScrollTo().assertIsDisplayed()
        ui.runOnUiThread{assertEquals("break",FocusStore(ui.activity).state().mode)}
    }
}
