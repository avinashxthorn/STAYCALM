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

    @Test fun otherAppsAndBreaksRemainUsable() {
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.android.settings"))
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.android.chrome"))
        assertFalse(FocusBlockPolicy.shouldBlock(active, true, "com.staycalm.focus"))
        assertFalse(FocusBlockPolicy.shouldBlock(FocusState(), true, "com.instagram.android"))
        assertEquals("Instagram Lite", FocusBlockPolicy.appName("com.instagram.lite"))
    }
}
