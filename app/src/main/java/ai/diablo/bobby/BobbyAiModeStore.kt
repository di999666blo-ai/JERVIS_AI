package ai.diablo.bobby

import android.content.Context

enum class BobbyAiMode(val label: String) {
    AUTO("AUTO"),
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE")
}

class BobbyAiModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bobby_ai_routing", Context.MODE_PRIVATE)

    fun mode(): BobbyAiMode {
        val stored = prefs.getString("mode", BobbyAiMode.AUTO.name)
        return runCatching { BobbyAiMode.valueOf(stored ?: BobbyAiMode.AUTO.name) }
            .getOrDefault(BobbyAiMode.AUTO)
    }

    fun setMode(mode: BobbyAiMode) {
        prefs.edit().putString("mode", mode.name).apply()
    }
}
