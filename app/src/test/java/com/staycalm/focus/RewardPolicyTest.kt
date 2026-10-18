package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class RewardPolicyTest {
    @Test fun onlyFullHoursInCompletedFocusSessionsEarnPoints(){
        for((minutes,points) in listOf(5 to 0L,30 to 0L,59 to 0L,60 to 1L,90 to 1L,119 to 1L,120 to 2L,180 to 3L)){
            assertEquals(points,RewardPolicy.earnedPoints(minutes,"focus"))
            assertEquals(0L,RewardPolicy.earnedPoints(minutes,"break"))
        }
        assertEquals(0L,RewardPolicy.earnedPoints(-60,"focus"))
    }
    @Test fun fourthAndLaterFocusCancellationsCostOnePoint(){
        for(used in 0..10){
            assertEquals((3-used).coerceAtLeast(0),RewardPolicy.freeRemaining(used))
            assertEquals(if(used<3)0L else 1L,RewardPolicy.cancellationCost(used,"focus"))
            assertEquals(0L,RewardPolicy.cancellationCost(used,"break"))
        }
    }
}
