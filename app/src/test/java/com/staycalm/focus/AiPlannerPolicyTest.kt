package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class AiPlannerPolicyTest {
    private val now=LocalDateTime.of(2026,10,21,12,0)
    private fun task(date:String="2026-10-22",time:String="18:00",minutes:Int=30)=PlanItem("existing","Existing commitment",date,time,minutes,false,true)
    private fun draft(time:String="19:00",minutes:Int=25)=AiPlanDraft("Recall swine housing","2026-10-22",time,minutes)
    @Test fun rejectsOverlapsIncludingCrossMidnightAndAllowsAdjacentSessions(){
        assertThrows(Exception::class.java){AiPlannerPolicy.validate(draft("18:10"),listOf(task()),now)}
        assertEquals("18:30",AiPlannerPolicy.validate(draft("18:30"),listOf(task()),now).time)
        assertThrows(Exception::class.java){AiPlannerPolicy.validate(draft("00:05"),listOf(task("2026-10-21","23:50",30)),now)}
    }
    @Test fun refusesPastMalformedAndOversizedTasks(){
        for(d in listOf(draft().copy(date="2026-10-20"),draft("24:00"),draft(minutes=0),draft(minutes=181),draft().copy(title=""),draft().copy(kind="shell"),draft().copy(kind="break",minutes=25))){
            assertThrows(Exception::class.java){AiPlannerPolicy.validate(d,emptyList(),now)}
        }
    }
    @Test fun findsARealFreeSlotAfterConflictsAndRoundsFutureTimes(){
        val free=AiPlannerPolicy.freeTime("2026-10-22","18:00",25,listOf(task()),now)
        assertEquals(LocalDateTime.of(2026,10,22,18,35),free)
        assertTrue(AiPlannerPolicy.freeTime("2026-10-20","18:00",25,emptyList(),now)>now.plusMinutes(1))
    }
    @Test fun suggestionIdentityIsStableAcrossRefreshAndReviewedTimeEdits(){
        val d=draft().copy(sourceId="focus|2026-10-22|housing")
        assertEquals(AiPlannerPolicy.id(d),AiPlannerPolicy.id(d.copy(time="20:00",title="A clearer title")))
        assertNotEquals(AiPlannerPolicy.id(d),AiPlannerPolicy.id(d.copy(sourceId="focus|2026-10-23|housing")))
    }
    @Test fun breaksRemainDistinctFromFocusAndIgnoreCompletedCommitments(){
        val result=AiPlannerPolicy.validate(draft("18:00",5).copy(kind="break"),listOf(task().copy(done=true)),now)
        assertEquals("break",result.kind)
    }
}
