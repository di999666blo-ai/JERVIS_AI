package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BobbyConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatLine>
)

/** Local, per-mode conversation archive. No network sync and no credentials are stored here. */
class BobbyConversationStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_conversation_archive", Context.MODE_PRIVATE)

    private fun key(mode: BobbyMode) = "archive_${mode.name.lowercase()}"

    fun list(mode: BobbyMode): List<BobbyConversation> = runCatching {
        val array = JSONArray(prefs.getString(key(mode), "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesJson.length()).map { j ->
                val message = messagesJson.getJSONObject(j)
                ChatLine(message.optBoolean("assistant"), message.optString("text"))
            }
            BobbyConversation(item.optString("id"), item.optString("title", "New chat"), item.optLong("updatedAt"), messages)
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun load(mode: BobbyMode, id: String): List<ChatLine>? =
        list(mode).firstOrNull { it.id == id }?.messages

    fun ensureActive(mode: BobbyMode, legacy: List<ChatLine>): String {
        val existing = list(mode).firstOrNull()
        if (existing != null) return existing.id
        val id = UUID.randomUUID().toString()
        save(mode, id, legacy)
        return id
    }

    fun create(mode: BobbyMode, initial: List<ChatLine>): String {
        val id = UUID.randomUUID().toString()
        save(mode, id, initial)
        return id
    }

    fun save(mode: BobbyMode, id: String, messages: List<ChatLine>) {
        val previous = list(mode).firstOrNull { it.id == id }
        val firstUser = messages.firstOrNull { !it.fromBobby }?.text.orEmpty()
        val title = if (previous?.title.isNullOrBlank() || previous?.title == "New chat") {
            firstUser.replace(Regex("\\s+"), " ").take(48).ifBlank { "New chat" }
        } else previous?.title ?: "New chat"
        val session = BobbyConversation(id, title, System.currentTimeMillis(), messages.takeLast(120))
        val all = (list(mode).filterNot { it.id == id } + session).sortedByDescending { it.updatedAt }.take(40)
        write(mode, all)
    }

    fun rename(mode: BobbyMode, id: String, title: String) {
        val updated = list(mode).map { if (it.id == id) it.copy(title = title.trim().take(60), updatedAt = System.currentTimeMillis()) else it }
        write(mode, updated)
    }

    fun delete(mode: BobbyMode, id: String) {
        write(mode, list(mode).filterNot { it.id == id })
    }

    private fun write(mode: BobbyMode, sessions: List<BobbyConversation>) {
        val array = JSONArray()
        sessions.forEach { session ->
            val messages = JSONArray()
            session.messages.forEach { line ->
                messages.put(JSONObject().put("assistant", line.fromBobby).put("text", line.text.take(8000)))
            }
            array.put(JSONObject().put("id", session.id).put("title", session.title)
                .put("updatedAt", session.updatedAt).put("messages", messages))
        }
        prefs.edit().putString(key(mode), array.toString()).apply()
    }
}
