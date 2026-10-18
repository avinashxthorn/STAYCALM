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
            Personalization(ui.activity).saveProfile("Test Student","Veterinary medicine")
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
        fun shell(command:String):String {
            val descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{String(it.readBytes())}
        }
        shell("mkdir -p /data/local/tmp/staycalm-screenshots")
        shell("cp ${file.absolutePath} /data/local/tmp/staycalm-screenshots/$name.png")
        val exported=shell("ls /data/local/tmp/staycalm-screenshots/$name.png")
        assertTrue("Screenshot must survive test cleanup: $exported",exported.contains("$name.png"))
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
    @Test fun allFourTracksDecodeAndPersonalImportPersists(){
        val context=ui.activity
        assertEquals(4,Ambients.all.size)
        for(track in Ambients.all){
            val media=android.media.MediaPlayer.create(context,track.raw)
            assertNotNull("Track must decode: ${track.label}",media)
            try{assertTrue(media.duration>120_000)}finally{media.release()}
        }
        val library=AudioLibrary(context)
        val imported=library.import(android.net.Uri.parse("android.resource://${context.packageName}/${Ambients.all.first().raw}"))
        assertTrue(AudioLibrary(context).imported().any{it.id==imported.id})
        assertTrue(File(context.filesDir,"audio/${imported.localFile}").length()>0)
        library.remove(imported)
        assertFalse(AudioLibrary(context).imported().any{it.id==imported.id})
    }
    @Test fun journalFilesStayOnSelectedDayAndCanBeReopened(){
        val context=ui.activity
        val journal=StudyJournal(context)
        val yesterday=LocalDate.now().minusDays(1)
        val uri=android.net.Uri.parse("android.resource://${context.packageName}/${Ambients.all.first().raw}")
        val entry=journal.import(yesterday,uri)
        assertEquals(yesterday.toString(),entry.date)
        assertTrue(journal.all().any{it.id==entry.id})
        val reopen=StudyJournal(context).openIntent(entry)
        assertEquals(android.content.Intent.ACTION_VIEW,reopen.action)
        assertTrue(reopen.data.toString().startsWith("content://"))
        journal.remove(entry)
        assertFalse(journal.all().any{it.id==entry.id})
    }
    @Test fun darkModeAndMotivationSettingsPersist(){
        val preferences=Personalization(ui.activity)
        preferences.saveProfile("Komi","Vet physiology")
        preferences.setDarkMode(true)
        preferences.setInterval(180)
        val restored=Personalization(ui.activity)
        assertEquals("Komi",restored.name())
        assertEquals("Vet physiology",restored.subject())
        assertTrue(restored.darkMode())
        assertEquals(180,restored.interval())
        restored.setInterval(0)
    }
    @Test fun threeSwipePagesLeadToSeparateProfileAndPersist() {
        ui.runOnUiThread {
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
                .edit().remove("profile_ready").commit()
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Find your focus.").assertIsDisplayed()
        ui.onNodeWithText("Let your focus bloom.").assertDoesNotExist()
        screenshot("00-onboarding-focus")
        // Verify a physical swipe, not just Next button navigation.
        ui.onNodeWithTag("onboarding-pager").performTouchInput { swipeLeft() }
        ui.onNodeWithText("Keep every little win.").assertIsDisplayed()
        screenshot("00-onboarding-journal")
        ui.onNodeWithText("Next").performClick()
        ui.onNodeWithText("Grow at your own pace.").assertIsDisplayed()
        ui.onNodeWithText("Get started").performClick()
        ui.onNodeWithText("Let's make it yours.").assertIsDisplayed()
        screenshot("00-onboarding-profile")
        ui.onNodeWithText("Start my journey").assertIsNotEnabled()
        ui.onNodeWithTag("onboarding-name").performTextInput("Learner")
        ui.onNodeWithTag("onboarding-subject").performTextInput("Veterinary science")
        ui.onNodeWithText("Start my journey").performScrollTo().performClick()
        ui.onNodeWithText("Let your focus bloom.").assertIsDisplayed()
        ui.runOnUiThread {
            val profile = Personalization(ui.activity)
            assertTrue(profile.isReady())
            assertEquals("Learner", profile.name())
            assertEquals("Veterinary science", profile.subject())
            assertEquals(120, FocusStore(ui.activity).dailyGoal())
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Find your focus.").assertDoesNotExist()
        ui.onNodeWithText("Let your focus bloom.").assertIsDisplayed()
    }
    @Test fun onboardingSkipOpensProfileNotHome() {
        ui.runOnUiThread {
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
                .edit().remove("profile_ready").commit()
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Skip").performClick()
        ui.onNodeWithText("Let's make it yours.").assertIsDisplayed()
        ui.onNodeWithText("Let your focus bloom.").assertDoesNotExist()
        ui.onNodeWithContentDescription("Back to introduction").performClick()
        ui.onNodeWithText("Find your focus.").assertIsDisplayed()
    }
    @Test fun playerPausesLoopsAndStopsAtSessionEnd(){
        fun command(action:String,position:Long=0){ui.runOnUiThread{
            val intent=android.content.Intent(ui.activity,SoundService::class.java).setAction(action).putExtra("position",position)
            if(action=="play")ui.activity.startForegroundService(intent) else ui.activity.startService(intent)
        }}
        command("play")
        ui.waitUntil(20_000){AudioPlayback.state.playing && AudioPlayback.state.duration>120_000}
        command("pause")
        ui.waitUntil(5_000){!AudioPlayback.state.playing}
        ui.onNodeWithContentDescription("Play audio").performScrollTo().assertIsDisplayed()
        screenshot("05-focus-player")
        val duration=AudioPlayback.state.duration
        command("seek",duration-2_000)
        ui.waitUntil(5_000){AudioPlayback.state.position>=duration-3_000}
        command("play")
        ui.waitUntil(10_000){AudioPlayback.state.playing && AudioPlayback.state.position<5_000}
        ui.runOnUiThread{
            val store=FocusStore(ui.activity)
            store.configure(5);store.start()
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE).edit()
                .putLong("end_elapsed",SystemClock.elapsedRealtime()-100)
                .putLong("end_wall",System.currentTimeMillis()-100).commit()
        }
        ui.waitUntil(5_000){AudioPlayback.state.id.isEmpty()}
        ui.runOnUiThread{assertEquals(1,FocusStore(ui.activity).logs().size)}
    }

}
