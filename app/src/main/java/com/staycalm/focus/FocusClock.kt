package com.staycalm.focus

import kotlin.math.abs

/** Pure clock math. A monotonic deadline prevents UI tick drift. A wall-clock fallback
 * is used only if the recorded boot epoch no longer matches the current boot. */
object FocusClock {
    fun remainingMs(running:Boolean,pausedMs:Long,endElapsed:Long,endWall:Long,bootOrigin:Long,
        nowElapsed:Long,nowWall:Long):Long {
        if(!running)return pausedMs.coerceAtLeast(0)
        val sameBoot=abs((nowWall-nowElapsed)-bootOrigin)<120_000L
        return (if(sameBoot)endElapsed-nowElapsed else endWall-nowWall).coerceAtLeast(0L)
    }
    fun progress(remainingMs:Long,durationMinutes:Int):Float {
        val full=durationMinutes.coerceAtLeast(1)*60_000.0
        return (1.0-(remainingMs/full)).coerceIn(0.0,1.0).toFloat()
    }
}
