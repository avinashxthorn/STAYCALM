package com.staycalm.focus

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StaycalmAiTest {
    @Test fun personalKeyIsEncryptedAccountScopedAndNeverExported() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccountTestApplication
        val previous = app.identity
        val prefix = "ai_${UUID.randomUUID()}_"
        val folder = File(app.cacheDir, prefix).apply { mkdirs() }
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                names.add(prefix + name)
                return app.getSharedPreferences(prefix + name, mode)
            }
        }
        val ownerA = prefix + "a"
        val ownerB = prefix + "b"
        val testSecret = "fixture_only_this_is_not_a_real_api_key"
        try {
            app.identity = ownerA
            val a = AiKeyVault(context, ownerA)
            a.save(testSecret)
            assertEquals(testSecret, a.read())
            assertFalse(folder.listFiles()!!.single().readText().contains(testSecret))
            assertFalse(StaycalmAi(context).enabled())
            assertFalse(StaycalmAi(context).notifications())
            assertNull(StaycalmAi(context).notification())
            app.identity = ownerB
            assertNull(AiKeyVault(context, ownerB).read())
            assertFalse(StaycalmAi(context).hasKey())
            app.identity = ownerA
            assertEquals(testSecret, a.read())
            assertFalse(CloudBackup.payload(ownerA).contains(testSecret))
            a.clear()
            assertNull(a.read())
        } finally {
            AiKeyVault(context, ownerA).clear(); AiKeyVault(context, ownerB).clear()
            names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            folder.deleteRecursively(); app.identity = previous; CloudBackup.bindAccount()
        }
    }

    @Test fun disabledCoachNeverMakesNetworkRequest() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccountTestApplication
        val previous = app.identity
        val owner = "disabled-${UUID.randomUUID()}"
        app.identity = owner
        try {
            val error = runCatching { StaycalmAi(app).refresh() }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(AccountData.preferences(app).getLong("ai_last_request", 0) == 0L)
        } finally { AccountData.preferences(app).edit().clear().commit(); app.identity = previous; CloudBackup.bindAccount() }
    }
}
