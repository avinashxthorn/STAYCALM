package com.staycalm.focus

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class StaycalmAiTest {
    @get:Rule val permission=GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private fun fixture(action:(Context,AccountTestApplication)->Unit){
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccountTestApplication
        val previous=app.identity;app.identity="ai-${UUID.randomUUID()}"
        try{action(app,app)}finally{AiCoachWork.cancel(app);app.getSystemService(NotificationManager::class.java).cancel(5011);AccountData.preferences(app).edit().clear().commit();app.identity=previous;CloudBackup.bindAccount()}
    }
    @Test fun savedFocusDetailsAreScopedToTheAccountWithoutAnyPersonalKey(){fixture{context,app->
        Personalization(context).saveProfile("Learner","LPM")
        val first=app.identity;val ai=StaycalmAi(context)
        ai.saveDetails("Pass Paper I","Swine housing","","Study","18:00",true,true)
        assertTrue(ai.enabled());assertTrue(ai.notifications());assertEquals("Pass Paper I",ai.goal())
        app.identity="another-${UUID.randomUUID()}"
        assertEquals("",StaycalmAi(context).goal());assertFalse(StaycalmAi(context).enabled())
        app.identity=first
        assertEquals("Pass Paper I",StaycalmAi(context).goal())
    }}
    @Test fun aiNotificationsAreIndependentOfGenericQuotesAndAllowedAtNight(){fixture{context,_->
        Personalization(context).saveProfile("Learner","LPM");Personalization(context).setInterval(0)
        val ai=StaycalmAi(context);ai.saveDetails("LPM exam","Housing","","Study","18:00",true,true)
        val now=System.currentTimeMillis()
        val report=AiStudyReport("Keep progressing.",listOf("Recall breeds.","Review housing.","Practise questions."),
            (1..7).map{AiStudyDay(LocalDate.now().plusDays(it.toLong()).toString(),"Topic $it",25,"Recall five points.")},
            (1..7).map{"Practise housing step $it."},now,ai.contextFingerprint())
        val prefs=AccountData.preferences(context)
        prefs.edit().putString("ai_report",report.encode()).commit()
        val manager=context.getSystemService(NotificationManager::class.java)
        fun waitForNotice(present:Boolean):android.service.notification.StatusBarNotification? {
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            do {
                val found=manager.activeNotifications.firstOrNull{it.id==5011}
                if((found!=null)==present)return found
                Thread.sleep(50)
            }while(android.os.SystemClock.elapsedRealtime()<deadline)
            return manager.activeNotifications.firstOrNull{it.id==5011}
        }
        manager.cancel(5011);waitForNotice(false)
        for(hour in listOf(0,12,23)){
            prefs.edit().putLong("ai_last_notification",0).commit();MotivationAlarms.deliver(context,hour)
            val n=waitForNotice(true)
            assertNotNull("AI notification missing at hour $hour",n)
            assertEquals("Staycalm AI · Your focus coach",n!!.notification.extras.getString("android.title"))
            manager.cancel(5011);waitForNotice(false)
        }
        ai.setNotifications(false);prefs.edit().putLong("ai_last_notification",0).commit();MotivationAlarms.deliver(context,23)
        assertNull(waitForNotice(false))
    }}
    @Test fun disabledCoachNeverRequestsAnIdentityTokenOrContactsTheServer(){fixture{context,_->
        runBlocking{val error=runCatching{StaycalmAi(context).refresh()}.exceptionOrNull();assertTrue(error is IllegalStateException)}
        assertEquals(0L,AccountData.preferences(context).getLong("ai_last_request",0))
    }}
}
