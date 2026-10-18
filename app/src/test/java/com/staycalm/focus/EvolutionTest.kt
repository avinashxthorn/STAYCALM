package com.staycalm.focus

import org.junit.Assert.*
import org.junit.Test

class EvolutionTest {
    @Test fun progressionOnlyAtCompletedMilestones(){
        assertEquals(0,Evolution.stage(0))
        assertEquals(0,Evolution.stage(29))
        assertEquals(1,Evolution.stage(30))
        assertEquals(2,Evolution.stage(90))
        assertEquals(3,Evolution.stage(180))
        assertEquals(4,Evolution.stage(360))
        assertEquals(5,Evolution.stage(720))
        assertNull(Evolution.next(720))
    }
    @Test fun nextMilestone(){ assertEquals(30,Evolution.next(0));assertEquals(180,Evolution.next(100)) }
}
