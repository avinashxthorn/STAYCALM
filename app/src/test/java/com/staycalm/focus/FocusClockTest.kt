package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class FocusClockTest {
    @Test fun monotonicClockHandlesDelayedUiUpdates() {
        val boot=1_500_000L
        val startElapsed=5_000L
        val startWall=boot+startElapsed
        val endElapsed=startElapsed+25*60_000L
        val endWall=startWall+25*60_000L
        assertEquals(1_500_000L, FocusClock.remainingMs(true,0L,endElapsed,endWall,boot,startElapsed,startWall))
        assertEquals(60_000L, FocusClock.remainingMs(true,0L,endElapsed,endWall,boot,startElapsed+1_440_000L,startWall+1_440_000L))
        assertEquals(0L, FocusClock.remainingMs(true,0L,endElapsed,endWall,boot,startElapsed+2_000_000L,startWall+2_000_000L))
    }
    @Test fun usesWallFallbackAfterReboot() {
        assertEquals(30_000L,FocusClock.remainingMs(true,0L,100_000L,1_700_030_000L,1_600_000_000L,2_000L,1_700_000_000L))
    }
    @Test fun pausedAndProgressAreClamped() {
        assertEquals(32_000L,FocusClock.remainingMs(false,32_000L,0L,0L,0L,9L,9L))
        assertEquals(0f,FocusClock.progress(9_000_000L,25))
        assertEquals(1f,FocusClock.progress(-99L,25))
        assertEquals(.5f,FocusClock.progress(750_000L,25),.0001f)
    }
}
