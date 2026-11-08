package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray

enum class BobbyMode { FRIENDLY, DARK }

/** Stores independent operating mode and chat sessions locally on this device. */
class BobbyModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_operating_modes", Context.MODE_PRIVATE)

    fun mode(): BobbyMode = runCatching {
        BobbyMode.valueOf(prefs.getString("active_mode", BobbyMode.FRIENDLY.name) ?: BobbyMode.FRIENDLY.name)
    }.getOrDefault(BobbyMode.FRIENDLY)

    fun setMode(mode: BobbyMode) {
        prefs.edit().putString("active_mode", mode.name).apply()
    }

    fun loadHistory(mode: BobbyMode): List<ChatLine> = runCatching {
        val raw = prefs.getString("history_${mode.name.lowercase()}", "[]") ?: "[]"
        val json = JSONArray(raw)
        (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            ChatLine(item.optBoolean("assistant"), item.optString("text"))
        }
    }.getOrDefault(emptyList())

    fun saveHistory(mode: BobbyMode, history: List<ChatLine>) {
        val json = JSONArray()
        history.takeLast(250).forEach { line ->
            json.put(org.json.JSONObject().put("assistant", line.fromBobby).put("text", line.text.take(12000)))
        }
        prefs.edit().putString("history_${mode.name.lowercase()}", json.toString()).apply()
    }

    fun clearHistory(mode: BobbyMode) {
        prefs.edit().remove("history_${mode.name.lowercase()}").apply()
    }
}
