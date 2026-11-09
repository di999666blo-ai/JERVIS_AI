package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BobbyMemory(
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val updatedAt: Long
)

/** Local, user-controlled memory. Secrets are rejected/redacted and never used for credentials. */
class BobbyMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_user_memory", Context.MODE_PRIVATE)
    private val lock = Any()

    fun save(category: String, title: String, content: String): BobbyMemory {
        require(title.isNotBlank() && content.isNotBlank()) { "Title and content are required." }
        val safe = redactSecrets(content.trim()).take(12000)
        require(safe.isNotBlank()) { "Memory contains no safe content after secret filtering." }
        val item = BobbyMemory(UUID.randomUUID().toString(), category.trim().ifBlank { "General" }.take(60),
            title.trim().take(160), safe, System.currentTimeMillis())
        synchronized(lock) {
            val all = readAll().filterNot { it.title.equals(item.title, true) && it.category.equals(item.category, true) }.toMutableList()
            all.add(0, item)
            writeAll(all.take(500))
        }
        return item
    }

    fun list(): List<BobbyMemory> = synchronized(lock) { readAll().sortedByDescending { it.updatedAt } }

    /** Updates an existing memory while preserving its stable ID. */
    fun update(id: String, category: String, title: String, content: String): BobbyMemory {
        require(title.isNotBlank() && content.isNotBlank()) { "Title and content are required." }
        val safe = redactSecrets(content.trim()).take(12000)
        require(safe.isNotBlank()) { "Memory contains no safe content after secret filtering." }
        synchronized(lock) {
            val old = readAll().firstOrNull { it.id == id } ?: error("Memory no longer exists.")
            val updated = old.copy(category = category.trim().ifBlank { "General" }.take(60),
                title = title.trim().take(160), content = safe, updatedAt = System.currentTimeMillis())
            val all = readAll().filterNot { it.id == id || (it.title.equals(updated.title, true) && it.category.equals(updated.category, true)) }
            writeAll((listOf(updated) + all).take(500))
            return updated
        }
    }

    /** Explicit natural-language memory command. Returns null when the message is not a save request. */
    fun rememberFromMessage(message: String): BobbyMemory? {
        val match = Regex("(?is)^\\s*(?:bobby[, ]+)?(?:please\\s+)?remember\\s+(?:this|that|the following)?\\s*[:,-]?\\s*(.+?)\\s*$")
            .matchEntire(message) ?: return null
        val raw = match.groupValues[1].trim().trim('"', '\\'', ' ')
        if (raw.length < 3 || raw.equals("this", true) || raw.equals("that", true)) return null
        val category = when {
            Regex("(?i)\\b(project|app|repository|github|build|code|feature)\\b").containsMatchIn(raw) -> "Projects"
            Regex("(?i)\\b(birthday|anniversary|appointment|meeting|date|remind)\\b").containsMatchIn(raw) -> "Important Dates"
            Regex("(?i)\\b(always|prefer|preference|instruction|please use|don't|do not)\\b").containsMatchIn(raw) -> "Instructions"
            Regex("(?i)\\b(my name|i am|i'm|my favourite|my favorite|i like|i prefer)\\b").containsMatchIn(raw) -> "Personal"
            else -> "General"
        }
        val title = raw.replace(Regex("\\s+"), " ").take(72).let { if (it.length == 72) it.take(69) + "..." else it }
        return save(category, title, raw)
    }
    fun search(query: String): List<BobbyMemory> {
        val q = query.trim()
        if (q.isEmpty()) return list()
        return list().filter { it.title.contains(q, true) || it.content.contains(q, true) || it.category.contains(q, true) }
    }
    fun delete(id: String) = synchronized(lock) { writeAll(readAll().filterNot { it.id == id }) }
    fun clear() = synchronized(lock) { prefs.edit().remove(KEY).apply() }
    fun exportJson(): String = synchronized(lock) { JSONArray().also { a -> readAll().forEach { a.put(toJson(it)) } }.toString(2) }

    private fun readAll(): List<BobbyMemory> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let {
            BobbyMemory(it.optString("id"), it.optString("category"), it.optString("title"),
                it.optString("content"), it.optLong("updatedAt"))
        } }
    }.getOrDefault(emptyList())

    private fun writeAll(items: List<BobbyMemory>) {
        val a = JSONArray()
        items.forEach { a.put(toJson(it)) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }
    private fun toJson(m: BobbyMemory) = JSONObject().put("id", m.id).put("category", m.category)
        .put("title", m.title).put("content", m.content).put("updatedAt", m.updatedAt)

    private fun redactSecrets(value: String): String {
        var s = value
        val patterns = listOf(
            Regex("(?i)(api[_ -]?key|access[_ -]?token|password|secret|private[_ -]?key)\\s*[:=]\\s*[^\\s,;]+"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
            Regex("\\b[A-Za-z0-9_-]{32,}\\b")
        )
        patterns.forEach { s = it.replace(s, "[REDACTED]") }
        return s
    }
    companion object { private const val KEY = "memories_v1" }
}
