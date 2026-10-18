package com.staycalm.focus

import java.time.LocalDate

object FocusStats {
    /** A streak remains alive until the end of today, even before today's first session. */
    fun streak(dates:List<LocalDate>,today:LocalDate):Int {
        val days=dates.filter{!it.isAfter(today)}.toSet()
        var cursor=if(today in days)today else today.minusDays(1)
        var result=0
        while(cursor in days){result++;cursor=cursor.minusDays(1)}
        return result
    }
}
