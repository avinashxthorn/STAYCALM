package com.staycalm.focus

import org.json.JSONArray
import org.json.JSONObject

/** Portable user data only. Never copies tokens, permission flags or a live timer. */
object BackupSnapshot {
    const val MAX_BYTES = 750_000
    val strings = setOf("plans", "logs", "intention", "profile_name", "profile_subject", "profile_phone", "sound", "youtube_link", "rss_link", "auto_intention_plan_id", "auto_intention_plan_date", "ai_goal", "ai_weak_topics", "ai_exam_date", "ai_focus_area", "ai_preferred_time")
    val ints = setOf("daily_goal", "quote_interval", "free_cancellations_used")
    val longs = setOf("reward_balance")
    val booleans = setOf("profile_ready", "dark_theme")
    val floats = setOf("volume")
    val keys = strings + ints + longs + booleans + floats

    fun encode(values: Map<String, *>): String = JSONObject().put("schema", 1).put("values",
        JSONObject().apply { keys.sorted().forEach { key -> values[key]?.let { put(key, it) } } }).toString()

    fun decode(payload: String, maxBytes: Int = MAX_BYTES): Map<String, Any> {
        require(payload.toByteArray(Charsets.UTF_8).size <= maxBytes) { if (maxBytes == MAX_BYTES) "Cloud backup is too large (maximum 750 KB). Export a file instead." else "This file contains too much structured data to restore safely." }
        val root = JSONObject(payload)
        require(root.getInt("schema") == 1) { "This backup needs a newer app version." }
        val data = root.getJSONObject("values")
        require(data.has("plans") && data.has("logs")) { "This backup is incomplete." }
        val result = mutableMapOf<String, Any>()
        for (key in data.keys()) {
            require(key in keys) { "Unexpected backup field: $key" }
            val value = data.get(key)
            result[key] = when (key) {
                in strings -> { require(value is String); value }
                in ints -> { require(value is Number && value.toDouble() == value.toInt().toDouble()); value.toInt() }
                in longs -> { require(value is Number && value.toDouble() == value.toLong().toDouble()); value.toLong() }
                in booleans -> { require(value is Boolean); value }
                in floats -> { require(value is Number && value.toDouble().isFinite()); value.toFloat() }
                else -> error("Unsupported backup field")
            }
        }
        val plans = JSONArray(result.getValue("plans") as String)
        val planIds = mutableSetOf<String>()
        repeat(plans.length()) { i ->
            val p = plans.getJSONObject(i)
            require(p.getString("id").isNotBlank() && planIds.add(p.getString("id")))
            require(p.getString("title").length <= 1000)
            java.time.LocalDate.parse(p.getString("date")); java.time.LocalTime.parse(p.getString("time"))
            require(p.getInt("duration") in 1..1440)
            p.getBoolean("done"); p.getBoolean("remind")
            require(p.optString("kind", "focus") in listOf("focus", "break"))
        }
        val logs = JSONArray(result.getValue("logs") as String)
        val logIds = mutableSetOf<String>()
        repeat(logs.length()) { i ->
            val log = logs.getJSONObject(i)
            require(log.getString("id").isNotBlank() && logIds.add(log.getString("id")))
            require(log.getLong("at") >= 0 && log.getInt("minutes") in 0..1440)
        }
        (result["daily_goal"] as? Int)?.let { require(it in 30..360) }
        (result["quote_interval"] as? Int)?.let { require(it in listOf(0, 60, 120, 180)) }
        (result["reward_balance"] as? Long)?.let { require(it >= 0) }
        (result["free_cancellations_used"] as? Int)?.let { require(it in 0..RewardPolicy.FREE_CANCELLATIONS) }
        (result["volume"] as? Float)?.let { require(it in 0f..1f) }
        return result
    }
}
