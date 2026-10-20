package com.staycalm.focus

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class AiStudyDay(val date: String, val topic: String, val minutes: Int, val action: String, val preferredTime: String = "18:00")
data class AiStudyReport(val summary: String, val insights: List<String>, val days: List<AiStudyDay>,
    val nudges: List<String>, val generatedAt: Long, val contextHash: String,
    val breakAdvice: String = "After your current session, take a short stretch and water break.", val breakMinutes: Int = 5) {
    fun encode(): String = JSONObject().put("summary", summary).put("insights", JSONArray(insights))
        .put("days", JSONArray().apply { days.forEach { put(JSONObject().put("date", it.date)
            .put("topic", it.topic).put("minutes", it.minutes).put("action", it.action).put("preferredTime", it.preferredTime)) } })
        .put("nudges", JSONArray(nudges)).put("generatedAt", generatedAt).put("contextHash", contextHash)
        .put("breakAdvice",breakAdvice).put("breakMinutes",breakMinutes).toString()
    companion object {
        fun decode(raw: String): AiStudyReport {
            require(raw.length <= 24000)
            val o = JSONObject(raw)
            val days = o.getJSONArray("days"); require(days.length() == 7)
            return AiStudyReport(o.getString("summary"), AiCoachFormat.texts(o.getJSONArray("insights"),3,350),
                (0 until 7).map { i -> val d=days.getJSONObject(i)
                    LocalDate.parse(d.getString("date")); require(d.getInt("minutes") in 5..180)
                    val time=d.optString("preferredTime","18:00"); require(AiCoachFormat.validTime(time))
                    AiStudyDay(d.getString("date"),d.getString("topic"),d.getInt("minutes"),d.getString("action"),time)
                }, AiCoachFormat.texts(o.getJSONArray("nudges"),7,180),o.getLong("generatedAt"),o.getString("contextHash"),
                o.optString("breakAdvice","After your current session, take a short stretch and water break."),o.optInt("breakMinutes",5).also { require(it in 5..15) })
        }
    }
}

/** The server supplies text and typed suggestions, never executable commands. */
object AiCoachFormat {
    fun validTime(value: String) = value.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]"))
    fun texts(array:JSONArray,count:Int,max:Int):List<String> {
        require(array.length()==count){"Staycalm AI returned incomplete suggestions. Please try again later."}
        return (0 until count).map { text(array.getString(it),max) }
    }
    private fun text(value:String,max:Int):String {
        require(value.isNotBlank() && value.length<=max && value.none{it.isISOControl()&&it!='\n'}){"Staycalm AI needs another try."}
        return value.trim()
    }
    private fun integer(o:JSONObject,key:String):Int {
        val n=o.get(key);require(n is Number && n.toDouble()==n.toInt().toDouble()){"Invalid suggestion duration."};return n.toInt()
    }
    fun parse(raw:String,today:LocalDate,dailyBudget:Int,now:Long,hash:String):AiStudyReport {
        require(raw.length<=20000){"Staycalm AI response was too large."}
        val o=JSONObject(raw);val days=o.getJSONArray("days");require(days.length()==7){"Incomplete focus plan. Try again later."}
        val plan=(0 until 7).map { i -> val d=days.getJSONObject(i);val offset=integer(d,"dayOffset")
            require(offset==i+1){"Invalid focus dates. Try again later."};val minutes=integer(d,"minutes")
            require(minutes in 5..dailyBudget.coerceIn(30,180)){"The plan exceeds your focus budget."}
            val time=d.optString("preferredTime","18:00");require(validTime(time)){"Invalid suggested time."}
            AiStudyDay(today.plusDays(offset.toLong()).toString(),text(d.getString("topic"),100),minutes,text(d.getString("action"),350),time)
        }
        val breakMinutes=if(o.has("breakMinutes"))integer(o,"breakMinutes") else 5
        require(breakMinutes in 5..15){"Invalid break duration."}
        return AiStudyReport(text(o.getString("summary"),400),texts(o.getJSONArray("insights"),3,350),plan,
            texts(o.getJSONArray("nudges"),7,180),now,hash,
            text(o.optString("breakAdvice","After your current session, take a short stretch and water break."),250),breakMinutes)
    }
}
