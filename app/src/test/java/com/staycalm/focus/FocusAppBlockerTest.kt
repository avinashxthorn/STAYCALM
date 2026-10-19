package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class FocusAppBlockerTest {
    private val active = FocusState(running = true, mode = "focus", sessionId = "focus-123")
    private val paused = active.copy(running = false)

    @Test fun onlyChosenAppsAreBlockedInFocus() {
        for (pkg in listOf("com.instagram.android", "com.instagram.lite", "com.google.android.youtube")) {
            assertTrue(FocusBlockPolicy.shouldBlock(active, true, pkg))
            assertTrue(FocusBlockPolicy.shouldBlock(paused, true, pkg))
            assertFalse(FocusBlockPolicy.shouldBlock(active, false, pkg))
            assertFalse(FocusBlockPolicy.shouldBlock(active.copy(sessionId = ""), true, pkg))
            assertFalse(FocusBlockPolicy.shouldBlock(active.copy(mode = "break"), true, pkg))
        }
    }

    @Test fun recentsTransitionsMustNotBeTreatedAsSafeApps() {
        assertTrue(FocusBlockPolicy.isSystemTransition("com.android.systemui"))
        assertTrue(FocusBlockPolicy.isSystemTransition("android"))
        assertTrue(FocusBlockPolicy.isSystemTransition("com.oplus.recents"))
        assertFalse(FocusBlockPolicy.isSystemTransition("com.instagram.lite"))
        assertFalse(FocusBlockPolicy.isSystemTransition("com.android.settings"))
        assertTrue(FocusBlockPolicy.isLauncher("com.google.android.apps.nexuslauncher"))
        assertTrue(FocusBlockPolicy.isLauncher("com.oplus.launcher"))
        assertFalse(FocusBlockPolicy.isLauncher("com.instagram.android"))
    }

    @Test fun aPausedProtectedSessionStillBlocksButAnEndedSessionDoesNot() {
        assertTrue(FocusBlockPolicy.shouldBlock(paused, true, "com.instagram.lite"))
        assertTrue(FocusBlockPolicy.shouldBlock(active, true, "com.google.android.youtube"))
        assertFalse(FocusBlockPolicy.shouldBlock(paused.copy(sessionId = ""), true, "com.instagram.lite"))
        assertFalse(FocusBlockPolicy.shouldBlock(paused.copy(mode = "break"), true, "com.instagram.lite"))
    }

    @Test fun otherAppsAndBreaksRemainUsable() {
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.android.settings"))
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.android.chrome"))
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.staycalm.focus"))
        assertFalse(FocusBlockPolicy.shouldBlock(FocusState(), true, "com.instagram.android"))
        assertEquals("Instagram Lite", FocusBlockPolicy.appName("com.instagram.lite"))
    }
}
