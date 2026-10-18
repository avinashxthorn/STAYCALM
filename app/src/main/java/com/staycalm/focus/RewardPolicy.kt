package com.staycalm.focus

/** Rewards belong to completed focus sessions; partial sessions and breaks never earn points. */
object RewardPolicy {
    const val FREE_CANCELLATIONS = 3
    fun earnedPoints(minutes: Int, mode: String): Long =
        if (mode == "focus") (minutes.coerceAtLeast(0) / 60).toLong() else 0L
    fun freeRemaining(used: Int): Int = (FREE_CANCELLATIONS - used).coerceIn(0, FREE_CANCELLATIONS)
    fun cancellationCost(used: Int, mode: String): Long =
        if (mode == "focus" && freeRemaining(used) == 0) 1L else 0L
}

enum class CancellationResult { NO_SESSION, COMPLETED, FREE, CHARGED, INSUFFICIENT_POINTS, SAVE_FAILED }
