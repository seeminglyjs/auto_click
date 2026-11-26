package com.seeminglyjs.auto_click

import org.json.JSONObject

/** Dart 의 ActionStep 과 같은 구조. 좌표는 0.0 ~ 1.0 정규화 값. */
data class Step(
    val type: String,
    val x: Double,
    val y: Double,
    val x2: Double,
    val y2: Double,
    val durationMs: Long,
    val delayAfterMs: Long,
    val repeat: Int,
) {
    companion object {
        fun fromJson(o: JSONObject) = Step(
            type = o.optString("type", "tap"),
            x = o.optDouble("x", 0.5).coerceIn(0.0, 1.0),
            y = o.optDouble("y", 0.5).coerceIn(0.0, 1.0),
            x2 = o.optDouble("x2", 0.5).coerceIn(0.0, 1.0),
            y2 = o.optDouble("y2", 0.5).coerceIn(0.0, 1.0),
            durationMs = o.optLong("durationMs", 40),
            delayAfterMs = o.optLong("delayAfterMs", 500),
            repeat = o.optInt("repeat", 1).coerceAtLeast(1),
        )
    }
}

/** Dart 의 Routine 과 같은 구조. */
data class Routine(
    val id: String,
    val name: String,
    val steps: List<Step>,
    val loopCount: Int,
    val loopIntervalMs: Long,
    val startDelayMs: Long,
    val maxRunSeconds: Int,
    val randomOffsetPx: Int,
    val randomDelayMs: Long,
) {
    companion object {
        fun fromJson(o: JSONObject): Routine {
            val arr = o.optJSONArray("steps")
            val steps = buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(Step.fromJson(it)) }
                }
            }
            return Routine(
                id = o.optString("id"),
                name = o.optString("name", "루틴"),
                steps = steps,
                loopCount = o.optInt("loopCount", 1),
                loopIntervalMs = o.optLong("loopIntervalMs", 500),
                startDelayMs = o.optLong("startDelayMs", 1500),
                maxRunSeconds = o.optInt("maxRunSeconds", 0),
                randomOffsetPx = o.optInt("randomOffsetPx", 0),
                randomDelayMs = o.optLong("randomDelayMs", 0),
            )
        }
    }
}
