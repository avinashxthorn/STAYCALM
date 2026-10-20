package com.staycalm.focus

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AccountMigrationTest {
    private fun fixture(action: (Context, AccountTestApplication) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccountTestApplication
        val previous = app.identity
        val prefix = "migration_${UUID.randomUUID()}_"
        val folder = File(app.cacheDir, prefix).apply { mkdirs() }
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                names.add(prefix + name)
                return app.getSharedPreferences(prefix + name, mode)
            }
        }
        try { action(context, app) }
        finally {
            names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            folder.deleteRecursively(); app.identity = previous; CloudBackup.bindAccount()
        }
    }

    @Test fun upgradePreservesHistoryFilesAndConsentOnlyForTheSignedInOwner() = fixture { context, app ->
        app.identity = "upgrade-a"
        val legacy = context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
        legacy.edit().putString("logs", "history-a").putString("profile_name", "Owner A")
            .putBoolean("profile_ready", true).putStringSet("blocked_packages", setOf("example.app")).commit()
        context.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE).edit().putString("owner", "upgrade-a").putString("device", "original-device").commit()
        File(context.filesDir, "journal/file-a").apply { parentFile!!.mkdirs(); writeText("private file a") }
        AccountData.migrateLegacy(context)
        assertEquals("history-a", AccountData.preferences(context).getString("logs", null))
        assertEquals(setOf("example.app"), AccountData.preferences(context).getStringSet("blocked_packages", null))
        assertEquals("original-device", AccountData.metadata(context).getString("device", null))
        assertEquals("private file a", File(AccountData.root(context), "journal/file-a").readText())
        assertTrue(legacy.all.isEmpty())
        app.identity = "upgrade-b"
        AccountData.migrateLegacy(context)
        assertTrue(AccountData.preferences(context).all.isEmpty())
        assertTrue(AccountData.metadata(context).all.isEmpty())
        assertFalse(File(AccountData.root(context), "journal/file-a").exists())
    }

    @Test fun earlierSignedOutDataIsNeverClaimedByTheNextLogin() = fixture { context, app ->
        app.identity = null
        val legacy = context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE)
        legacy.edit().putString("logs", "unclaimed-private-history").commit()
        File(context.filesDir, "journal/unclaimed").apply { parentFile!!.mkdirs(); writeText("private") }
        AccountData.migrateLegacy(context)
        app.identity = "new-account"
        AccountData.migrateLegacy(context)
        assertTrue(AccountData.preferences(context).all.isEmpty())
        assertFalse(File(AccountData.root(context), "journal/unclaimed").exists())
        assertEquals("unclaimed-private-history", legacy.getString("logs", null))
        assertTrue(File(context.filesDir, "journal/unclaimed").isFile)
    }

    @Test fun interruptedMigrationRemainsBoundToTheOriginalOwner() = fixture { context, app ->
        app.identity = "new-account"
        AccountData.navigation(context).edit().putString("legacy_owner", "original-owner").commit()
        context.getSharedPreferences("staycalm_v2", Context.MODE_PRIVATE).edit().putString("logs", "original-history").commit()
        // A previous process already moved this folder before being interrupted.
        File(AccountData.rootFor(context, "original-owner"), "journal/file").apply { parentFile!!.mkdirs(); writeText("original") }
        AccountData.migrateLegacy(context)
        assertEquals("original-history", AccountData.preferencesFor(context, "original-owner").getString("logs", null))
        assertTrue(AccountData.preferences(context).all.isEmpty())
        assertEquals("original", File(AccountData.rootFor(context, "original-owner"), "journal/file").readText())
    }
}
