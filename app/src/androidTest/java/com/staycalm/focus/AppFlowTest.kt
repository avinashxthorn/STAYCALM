package com.staycalm.focus

import androidx.activity.compose.setContent
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
            ui.activity.stopService(android.content.Intent(ui.activity,SoundService::class.java))
            ui.activity.stopService(android.content.Intent(ui.activity,TimerService::class.java))
            Notice.sessionStopped(ui.activity)
            // UI tests run without the user-granted Usage Access / overlay permissions.
            // Opt out in the fixture; blocker permission gating is tested separately.
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE).edit()
                .clear().putBoolean("block_distracting_apps",false).commit()
            SessionUninstallGuard.releaseIfIdle(ui.activity)
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
    @Test fun accountAndRecoveryScreenIsReachable(){
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Account & backup").performScrollTo().performClick()
        ui.onNodeWithText("Welcome back").assertIsDisplayed()
        ui.onNodeWithText("Email").assertIsDisplayed()
        ui.onNodeWithText("Export full backup").assertDoesNotExist()
        screenshot("11-account-backup")
    }
    @Test fun fullBackupRestoresPlansJournalAndImportedAudio(){
        val context=ui.activity
        val store=FocusStore(context)
        val plan=store.addTask("Recovery test",LocalDate.now().toString(),"12:00",25,false)
        val source=android.net.Uri.parse("android.resource://${context.packageName}/${Ambients.all.first().raw}")
        val attachment=StudyJournal(context).import(LocalDate.now(),source)
        val audio=AudioLibrary(context).import(source)
        val zip=File(context.cacheDir,"recovery-test.zip")
        try {
            LocalBackup.export(context,android.net.Uri.fromFile(zip))
            store.deleteTask(plan); StudyJournal(context).remove(attachment); AudioLibrary(context).remove(audio)
            assertFalse(File(context.filesDir,"journal/${attachment.id}").exists())
            LocalBackup.restore(context,android.net.Uri.fromFile(zip))
            assertTrue(FocusStore(context).tasks().any{it.id==plan.id})
            assertTrue(StudyJournal(context).all().any{it.id==attachment.id})
            assertTrue(AudioLibrary(context).imported().any{it.id==audio.id})
        } finally { zip.delete() }
    }
    @Test fun corruptBackupAndActiveTimerDoNotReplaceLocalPlans(){
        val context=ui.activity
        val store=FocusStore(context)
        val plan=store.addTask("Keep this plan",LocalDate.now().toString(),"12:00",25,false)
        val zip=File(context.cacheDir,"invalid-backup.zip")
        try {
            java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(java.util.zip.ZipEntry("../escape")); out.write(byteArrayOf(1)); out.closeEntry()
            }
            assertThrows(Exception::class.java){LocalBackup.restore(context,android.net.Uri.fromFile(zip))}
            assertTrue(FocusStore(context).tasks().any{it.id==plan.id})
            val payload=CloudBackup.payload()
            store.start()
            assertThrows(Exception::class.java){CloudBackup.restore(payload)}
            assertTrue(FocusStore(context).tasks().any{it.id==plan.id})
        } finally { zip.delete(); store.abandon() }
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
    @Test fun threeSwipePagesLeadToSimpleLoginAfterGetStarted() {
        ui.runOnUiThread {
            ui.activity.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
                .edit().remove("profile_ready").remove("intro_done").commit()
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Find your focus.").assertIsDisplayed()
        ui.onNodeWithText("Log in / Restore").assertDoesNotExist()
        ui.onNodeWithTag("simple-login").assertDoesNotExist()
        screenshot("00-onboarding-focus")
        ui.onNodeWithTag("onboarding-pager").performTouchInput { swipeLeft() }
        ui.onNodeWithText("Keep every little win.").assertIsDisplayed()
        ui.onNodeWithText("Next").performClick()
        ui.onNodeWithText("Grow at your own pace.").assertIsDisplayed()
        ui.onNodeWithText("Get started").performClick()
        ui.onNodeWithTag("simple-login").assertIsDisplayed()
        ui.onNodeWithText("Welcome back").assertIsDisplayed()
        ui.onNodeWithText("Email").assertIsDisplayed()
        ui.onNodeWithText("Password").assertIsDisplayed()
        ui.onNodeWithText("Export full backup").assertDoesNotExist()
        screenshot("00-simple-login")
        ui.onNodeWithText("Sign up").performClick()
        ui.onNodeWithText("Create your account").assertIsDisplayed()
        ui.onNodeWithText("Confirm password").assertIsDisplayed()
        screenshot("00-simple-signup")
        ui.onNodeWithText("← Back").performClick()
        ui.onNodeWithText("Find your focus.").assertIsDisplayed()
    }
    @Test fun separateProfileFormStillSavesPreferences() {
        ui.runOnUiThread {
            ui.activity.setContent {
                androidx.compose.material3.MaterialTheme {
                    FirstRunOnboarding(startAtProfile=true, onFinish={ name, subject, goal, interval ->
                        Personalization(ui.activity).saveProfile(name,subject)
                        Personalization(ui.activity).setInterval(interval)
                        FocusStore(ui.activity).setDailyGoal(goal)
                    })
                }
            }
        }
        ui.onNodeWithText("Let's make it yours.").assertIsDisplayed()
        ui.onNodeWithText("Start my journey").assertIsNotEnabled()
        ui.onNodeWithTag("onboarding-name").performTextInput("Learner")
        ui.onNodeWithTag("onboarding-subject").performTextInput("Veterinary science")
        ui.onNodeWithText("Start my journey").performScrollTo().performClick()
        assertEquals("Learner",Personalization(ui.activity).name())
        assertEquals("Veterinary science",Personalization(ui.activity).subject())
        assertEquals(120,FocusStore(ui.activity).dailyGoal())
        ui.onNodeWithContentDescription("Back to introduction").performScrollTo().performClick()
        ui.onNodeWithText("Next").performClick()
        ui.onNodeWithText("Next").performClick()
        ui.onNodeWithText("Get started").performClick()
        ui.onNodeWithText("Let's make it yours.").assertIsDisplayed()
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

    @Test fun rewardsAndCancellationTransactionsSurviveNewStoreInstances(){
        ui.runOnUiThread {
            val context=ui.activity
            fun store()=FocusStore(context)
            val prefs=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
            fun due(minutes:Int,mode:String="focus"){
                store().configure(minutes,mode=mode);store().start()
                prefs.edit().putLong("end_elapsed",SystemClock.elapsedRealtime()-100)
                    .putLong("end_wall",System.currentTimeMillis()-100).commit()
            }
            // Three lifetime free cancellations, including paused sessions.
            repeat(3){
                store().configure(60);store().start();store().pause()
                assertEquals(CancellationResult.FREE,store().abandon())
                assertEquals(CancellationResult.NO_SESSION,store().abandon())
            }
            assertEquals(0,store().freeCancellationsRemaining())
            assertEquals(0L,store().rewardBalance())
            store().configure(60);store().start();store().pause()
            val before=store().state()
            assertEquals(CancellationResult.INSUFFICIENT_POINTS,store().abandon())
            store().configure(5,mode="break")
            assertEquals(before,store().state())
            // Resume without losing the active session identity and earn once.
            store().start()
            prefs.edit().putLong("end_elapsed",SystemClock.elapsedRealtime()-100)
                .putLong("end_wall",System.currentTimeMillis()-100).commit()
            assertTrue(store().completeIfDue())
            assertFalse(store().completeIfDue())
            assertEquals(1L,store().rewardBalance())
            assertEquals(1,store().logs().size)
            store().configure(60);store().start()
            assertEquals(CancellationResult.CHARGED,store().abandon())
            assertEquals(CancellationResult.NO_SESSION,store().abandon())
            assertEquals(0L,store().rewardBalance())
            due(59);assertTrue(store().completeIfDue());assertEquals(0L,store().rewardBalance())
            due(120);assertTrue(store().completeIfDue());assertEquals(2L,store().rewardBalance())
            due(60,"break");assertTrue(store().completeIfDue());assertEquals(2L,store().rewardBalance())
            store().configure(5,mode="break");store().start()
            assertEquals(CancellationResult.FREE,store().abandon())
            assertEquals(2L,store().rewardBalance())
            assertEquals(0,store().freeCancellationsRemaining())
            // A cancellation at the deadline completes and credits instead of charging.
            due(60);assertEquals(CancellationResult.COMPLETED,store().abandon())
            assertEquals(3L,store().rewardBalance())
        }
        ui.activityRule.scenario.recreate()
        ui.runOnUiThread{
            assertEquals(3L,FocusStore(ui.activity).rewardBalance())
            assertEquals(0,FocusStore(ui.activity).freeCancellationsRemaining())
        }
    }

    @Test fun simultaneousCompletionAndCancellationCannotDoubleCreditOrCharge(){
        ui.runOnUiThread{
            val context=ui.activity
            val store=FocusStore(context)
            val prefs=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
            store.configure(60);store.start()
            prefs.edit().putLong("end_elapsed",SystemClock.elapsedRealtime()-100)
                .putLong("end_wall",System.currentTimeMillis()-100).commit()
            val threads=(1..8).map{Thread{FocusStore(context).completeIfDue()}}
            threads.forEach{it.start()};threads.forEach{it.join()}
            assertEquals(1L,store.rewardBalance());assertEquals(1,store.logs().size)
            prefs.edit().putInt("free_cancellations_used",3).commit()
            store.configure(60);store.start();store.pause()
            val cancellations=(1..8).map{Thread{FocusStore(context).abandon()}}
            cancellations.forEach{it.start()};cancellations.forEach{it.join()}
            assertEquals(0L,store.rewardBalance());assertEquals(1,store.logs().size)
            assertTrue(store.state().sessionId.isEmpty())
        }
    }

    @Test fun lockedNotificationsRestoreForSilentFocusAndBreakAndUnlockImmediately(){
        val manager=ui.activity.getSystemService(android.app.NotificationManager::class.java)
        fun notice()=manager.activeNotifications.firstOrNull{it.id==1002}?.notification
        for(mode in listOf("focus","break")){
            ui.runOnUiThread{
                val store=FocusStore(ui.activity)
                store.setLockTimerNotification(true);store.configure(60,mode=mode);store.start()
            }
            ui.waitUntil(5_000){notice()?.flags?.and(android.app.Notification.FLAG_FOREGROUND_SERVICE)!=0 && notice()!=null}
            assertTrue(notice()!!.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(notice()!!.extras.getBoolean(android.app.Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            // Simulate Android delivering the swipe's delete intent, then clearing the alert.
            ui.runOnUiThread{notice()!!.deleteIntent.send();manager.cancel(1002)}
            ui.waitUntil(5_000){notice()!=null}
            ui.runOnUiThread{FocusStore(ui.activity).setLockTimerNotification(false)}
            ui.waitUntil(5_000){notice()!=null && notice()!!.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE == 0}
            assertEquals(0,notice()!!.flags and android.app.Notification.FLAG_ONGOING_EVENT)
            assertNull(notice()!!.deleteIntent)
            ui.runOnUiThread{manager.cancel(1002)}
            Thread.sleep(1_200)
            assertNull(notice())
            ui.runOnUiThread{FocusStore(ui.activity).pause()}
            ui.waitUntil(5_000){notice()==null}
            ui.runOnUiThread{FocusStore(ui.activity).abandon()}
        }
    }

    @Test fun uninstallProtectionRequiresConsentAndReleasesOnlyAfterSessionEnd(){
        val context=ui.activity
        val store=FocusStore(context)
        val prefs=context.getSharedPreferences("staycalm_v2",Context.MODE_PRIVATE)
        fun activateForSession(mode:String="focus"){
            ui.runOnUiThread{
                store.configure(60,mode=mode)
                assertFalse("A protected session must not start without approval",store.start())
                assertTrue(SessionUninstallGuard.beginActivation(context))
            }
            val component=SessionUninstallGuard.component(context).flattenToString()
            val descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dpm set-active-admin --user current $component")
            val output=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{String(it.readBytes())}
            assertTrue("Android must activate our empty-policy administrator: $output",output.contains("Success"))
            ui.waitUntil(10_000){SessionUninstallGuard.active(context) && store.state().running}
        }
        try{
            ui.runOnUiThread{
                assertTrue(store.setUninstallProtectionEnabled(true))
                store.setLockTimerNotification(false)
                prefs.edit().putLong("reward_balance",10L).putInt("free_cancellations_used",3).commit()
            }
            activateForSession()
            // Reward balance does not release an unfinished session, nor does pausing.
            ui.runOnUiThread{
                store.pause()
                SessionUninstallGuard.releaseIfIdle(context)
                assertTrue(SessionUninstallGuard.active(context))
                assertFalse(store.setUninstallProtectionEnabled(false))
                assertNotNull(SessionAdminReceiver().onDisableRequested(context,android.content.Intent()))
                assertEquals(CancellationResult.CHARGED,store.abandon())
            }
            ui.waitUntil(10_000){!SessionUninstallGuard.active(context)}
            assertEquals(9L,store.rewardBalance())
            activateForSession()
            // The foreground countdown still exists with notification locking switched off.
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            ui.waitUntil(5_000){manager.activeNotifications.any{it.id==1002}}
            ui.runOnUiThread{
                prefs.edit().putLong("end_elapsed",SystemClock.elapsedRealtime()-100)
                    .putLong("end_wall",System.currentTimeMillis()-100).commit()
                assertTrue(store.completeIfDue())
            }
            ui.waitUntil(10_000){!SessionUninstallGuard.active(context)}
            assertEquals(10L,store.rewardBalance())
            activateForSession("break")
            ui.runOnUiThread{assertEquals(CancellationResult.FREE,store.abandon())}
            ui.waitUntil(10_000){!SessionUninstallGuard.active(context)}
            assertEquals(10L,store.rewardBalance())
            activateForSession()
            ui.runOnUiThread{
                prefs.edit().putLong("reward_balance",0L).commit()
                store.pause()
                assertEquals(CancellationResult.INSUFFICIENT_POINTS,store.abandon())
                assertTrue(SessionUninstallGuard.active(context))
                prefs.edit().putLong("reward_balance",1L).commit()
                assertEquals(CancellationResult.CHARGED,store.abandon())
            }
            ui.waitUntil(10_000){!SessionUninstallGuard.active(context)}
        }finally{
            // Keep test failure/retry cleanup independent of the app's cancellation budget.
            ui.runOnUiThread{
                prefs.edit().putBoolean("running",false).putString("session_id","")
                    .remove("session_admin_pending_until").commit()
                SessionUninstallGuard.releaseIfIdle(context)
                Notice.sessionStopped(context)
            }
            ui.waitUntil(10_000){!SessionUninstallGuard.active(context)}
        }
    }

}
