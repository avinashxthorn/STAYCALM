package com.staycalm.focus

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FocusStatsTest {
    private val today=LocalDate.of(2026,10,19)
    @Test fun emptyHistoryHasNoStreak(){assertEquals(0,FocusStats.streak(emptyList(),today))}
    @Test fun yesterdayKeepsStreakAlive(){assertEquals(2,FocusStats.streak(listOf(today.minusDays(1),today.minusDays(2)),today))}
    @Test fun duplicatesAndFutureDatesDoNotInflateStreak(){assertEquals(1,FocusStats.streak(listOf(today,today,today.plusDays(1)),today))}
    @Test fun missedDayEndsStreak(){assertEquals(0,FocusStats.streak(listOf(today.minusDays(2)),today))}
    @Test fun crossesYearBoundary(){val day=LocalDate.of(2027,1,1);assertEquals(3,FocusStats.streak(listOf(day,day.minusDays(1),day.minusDays(2)),day))}
    @Test fun wallClockChangeDoesNotChangeSameBootTimer(){assertEquals(90_000L,FocusClock.remainingMs(true,0,100_000,1_700_000,1_500_000,10_000,9_000_000,true))}
    @Test fun displayRoundsUpWithoutLosingFirstSecond(){assertEquals("25:00",FocusClock.displayTime(1_499_999));assertEquals("00:01",FocusClock.displayTime(1));assertEquals("00:00",FocusClock.displayTime(-1))}
}
