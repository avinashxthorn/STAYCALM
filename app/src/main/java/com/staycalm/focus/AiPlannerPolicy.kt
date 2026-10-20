package com.staycalm.focus

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AiPlanDraft(val title:String,val date:String,val time:String,val minutes:Int,val kind:String="focus",val remind:Boolean=true,val sourceId:String="")

object AiPlannerPolicy {
    fun id(draft:AiPlanDraft):String = "ai-"+MessageDigest.getInstance("SHA-256")
        .digest((draft.sourceId.ifBlank{"${draft.kind}|${draft.date}|${draft.time}|${draft.title.trim().lowercase()}"}).toByteArray())
        .joinToString(""){"%02x".format(it)}.take(24)
    private fun start(task:PlanItem):LocalDateTime? = runCatching { LocalDate.parse(task.date).atTime(LocalTime.parse(task.time)) }.getOrNull()
    fun validate(draft:AiPlanDraft,existing:List<PlanItem>,now:LocalDateTime):AiPlanDraft {
        require(draft.title.trim().isNotEmpty() && draft.title.length<=450){"Enter a task title (up to 450 characters)."}
        require(draft.kind in listOf("focus","break")){"Choose a focus session or break."}
        require(draft.minutes in 5..if(draft.kind=="break")15 else 180){"Choose a valid session duration."}
        require(AiCoachFormat.validTime(draft.time)){"Use a time in HH:mm format."}
        val date=runCatching{LocalDate.parse(draft.date)}.getOrElse{error("Use a date in YYYY-MM-DD format.")}
        require(!date.isAfter(now.toLocalDate().plusYears(1))){"Choose a date within the next year."}
        val from=date.atTime(LocalTime.parse(draft.time));val until=from.plusMinutes(draft.minutes.toLong())
        require(!from.isBefore(now.plusMinutes(1))){"Choose a start time at least one minute from now."}
        require(existing.none { task -> !task.done && start(task)?.let{from<it.plusMinutes(task.duration.toLong())&&until>it}==true }){
            "This time overlaps another planner task. Choose another time or use Find free time."
        }
        return draft.copy(title=draft.title.trim())
    }
    fun freeTime(date:String,time:String,minutes:Int,existing:List<PlanItem>,now:LocalDateTime):LocalDateTime {
        var candidate=runCatching{LocalDate.parse(date).atTime(LocalTime.parse(time))}.getOrDefault(now.plusMinutes(2))
        if(candidate<now.plusMinutes(2))candidate=now.plusMinutes(2)
        candidate=candidate.withSecond(0).withNano(0)
        repeat(200){
            val end=candidate.plusMinutes(minutes.toLong())
            val overlaps=existing.filter{!it.done}.mapNotNull{task->start(task)?.let{it to it.plusMinutes(task.duration.toLong())}}
                .filter{candidate<it.second && end>it.first}
            if(overlaps.isEmpty())return candidate
            candidate=overlaps.maxOf{it.second}.plusMinutes(5)
        }
        error("No free time found. Choose a time manually.")
    }
}
