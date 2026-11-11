package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BobbyAiTrace(
    val timestamp: Long,
    val taskType: String,
    val model: String,
    val success: Boolean,
    val latencyMs: Long,
    val failureType: String
)

data class BobbyModelPerformance(
    val model: String,
    val requests: Int,
    val successes: Int,
    val averageLatencyMs: Long
) {
    val successRate: Int get() = if (requests == 0) 0 else successes * 100 / requests
}

/** Privacy-preserving local traces: prompts, API keys and personal content are never stored. */
class BobbyTraceStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_ai_learning_traces", Context.MODE_PRIVATE)

    fun record(request: String, model: String, success: Boolean, latencyMs: Long, failureType: String = "") {
        val rows = JSONArray(prefs.getString("traces", "[]"))
        rows.put(JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("taskType", classify(request))
            .put("model", model.take(100))
            .put("success", success)
            .put("latencyMs", latencyMs.coerceAtLeast(0))
            .put("failureType", failureType.take(80)))
        while (rows.length() > 200) rows.remove(0)
        prefs.edit().putString("traces", rows.toString()).apply()
    }

    fun recent(limit: Int = 40): List<BobbyAiTrace> {
        val rows = JSONArray(prefs.getString("traces", "[]"))
        return (0 until rows.length()).mapNotNull { index ->
            runCatching {
                rows.getJSONObject(index).let { row ->
                    BobbyAiTrace(row.optLong("timestamp"), row.optString("taskType"), row.optString("model"),
                        row.optBoolean("success"), row.optLong("latencyMs"), row.optString("failureType"))
                }
            }.getOrNull()
        }.asReversed().take(limit.coerceIn(1, 200))
    }

    fun performance(): List<BobbyModelPerformance> =
        recent(200).groupBy { it.model }.map { (model, rows) ->
            BobbyModelPerformance(model, rows.size, rows.count { it.success },
                if (rows.isEmpty()) 0 else rows.map { it.latencyMs }.average().toLong())
        }.sortedByDescending { it.requests }

    fun clear() { prefs.edit().remove("traces").apply() }

    private fun classify(request: String): String {
        val text = request.lowercase()
        return when {
            Regex("\\b(code|program|debug|kotlin|python|javascript|compile)\\b").containsMatchIn(text) -> "coding"
            Regex("\\b(research|latest|news|source|compare|search)\\b").containsMatchIn(text) -> "research"
            Regex("\\b(file|document|pdf|spreadsheet|image|screenshot)\\b").containsMatchIn(text) -> "files"
            Regex("\\b(plan|schedule|remind|organise|organize|task)\\b").containsMatchIn(text) -> "planning"
            Regex("\\b(trade|trading|market|forex|deriv|quotex|portfolio)\\b").containsMatchIn(text) -> "trading"
            else -> "conversation"
        }
    }
}
