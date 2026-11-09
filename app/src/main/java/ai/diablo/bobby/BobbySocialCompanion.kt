package ai.diablo.bobby

import android.content.Context

/**
 * Lightweight, user-controlled guest/session context for natural group conversations.
 * It stores only guest display names when the owner explicitly introduces someone.
 * It does not perform voice biometrics or infer a person's identity.
 */
class BobbySocialCompanion(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_social_companion", Context.MODE_PRIVATE)

    fun introduceGuest(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(48)
        require(clean.length >= 2 && clean.matches(Regex("[\\p{L}][\\p{L} .'-]*"))) {
            "Please tell me the guest's first name."
        }
        prefs.edit()
            .putString("active_guest", clean)
            .putLong("introduced_at", System.currentTimeMillis())
            .apply()
        return clean
    }

    fun activeGuest(): String = prefs.getString("active_guest", "").orEmpty()

    fun endGuestSession() {
        prefs.edit().remove("active_guest").remove("introduced_at").apply()
    }

    fun contextInstruction(): String {
        val guest = activeGuest()
        if (guest.isBlank()) return ""
        return """
            SOCIAL COMPANION SESSION:
            The owner has explicitly introduced a guest named $guest. Address the guest warmly when the conversation is clearly directed to them.
            Start with a natural introduction and ask how they are. Continue politely with relevant follow-up questions and inclusive conversation.
            Do not claim the owner told you personal facts about this guest unless those facts are present in the conversation or user-approved memory.
            Do not invent details about their family, work, preferences, identity or history. Ask rather than assume.
            When asked to entertain the guest, offer a short choice such as chess, a quiz, riddles, music or conversation. Ask before launching an app.
            If the guest agrees to chess, offer to open the installed chess app or use an available in-app chess experience; do not claim a game has started until it actually opens.
            You are an AI assistant, not a human. Be warm and socially natural without pretending to be human.
        """.trimIndent()
    }
}
