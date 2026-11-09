package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class BobbyPublicApiEntry(
    val name: String,
    val description: String,
    val documentationUrl: String,
    val auth: String,
    val https: String,
    val cors: String,
    val category: String,
    val source: String = "public-apis/public-apis"
)

/**
 * Community API directory importer. This downloads and parses metadata only:
 * it never calls arbitrary API endpoints listed by the upstream project.
 */
class BobbyPublicApiCatalog(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_public_api_catalog", Context.MODE_PRIVATE)
    private val readmeUrl = "https://raw.githubusercontent.com/public-apis/public-apis/master/README.md"
    private val sourceUrl = "https://github.com/public-apis/public-apis"

    fun cachedEntries(): List<BobbyPublicApiEntry> = parseCached(prefs.getString("entries", "[]").orEmpty())
    fun lastUpdated(): Long = prefs.getLong("updated_at", 0L)
    fun lastError(): String = prefs.getString("last_error", "").orEmpty()

    fun search(query: String = "", category: String = "", limit: Int = 250): List<BobbyPublicApiEntry> {
        val q = query.trim().lowercase()
        return cachedEntries().asSequence().filter { entry ->
            (q.isBlank() || entry.name.lowercase().contains(q) ||
                entry.description.lowercase().contains(q) ||
                entry.category.lowercase().contains(q) ||
                entry.auth.lowercase().contains(q)) &&
                (category.isBlank() || entry.category.equals(category, true))
        }.take(limit.coerceIn(1, 1000)).toList()
    }

    fun categories(): List<String> = cachedEntries().map { it.category }.distinct().sorted()

    fun refresh(callback: (Result<Int>) -> Unit) {
        thread(name = "bobby-public-api-catalog") {
            val result = runCatching {
                val connection = (URL(readmeUrl).openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 25000
                    connection.setRequestProperty("Accept", "text/plain")
                    connection.setRequestProperty("User-Agent", "BOBBY-Android-PublicApiCatalog/1.0")
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("Catalogue source returned HTTP $code")
                    val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    require(raw.length in 1000..5_000_000) { "Upstream catalogue size is outside the safe limit." }
                    val parsed = parseReadme(raw)
                    require(parsed.size >= 50) { "Upstream catalogue format changed; keeping the last known-good copy." }
                    val json = JSONArray()
                    parsed.forEach { item ->
                        json.put(JSONObject()
                            .put("name", item.name).put("description", item.description)
                            .put("documentationUrl", item.documentationUrl).put("auth", item.auth)
                            .put("https", item.https).put("cors", item.cors).put("category", item.category)
                            .put("source", item.source))
                    }
                    prefs.edit().putString("entries", json.toString())
                        .putLong("updated_at", System.currentTimeMillis()).remove("last_error").apply()
                    parsed.size
                } finally { connection.disconnect() }
            }
            result.onFailure { prefs.edit().putString("last_error", it.message.orEmpty().take(300)).apply() }
            callback(result)
        }
    }

    internal fun parseReadme(markdown: String): List<BobbyPublicApiEntry> {
        val output = LinkedHashMap<String, BobbyPublicApiEntry>()
        var category = "General"
        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("### ")) {
                category = line.removePrefix("### ").trim().removePrefix("[")
                    .substringBefore("]( ").substringBefore("](#").removeSuffix("]")
                    .ifBlank { "General" }.take(80)
            } else if (line.startsWith("|") && line.count { it == '|' } >= 5 &&
                !line.contains("---") && !line.contains("API |") && !line.contains("Description")) {
                val cells = line.trim('|').split('|').map { it.trim() }
                if (cells.size >= 5) {
                    val link = Regex("\\[([^\\]]{1,100})\\]\\((https://[^)\\s]+)\\)").find(cells[0])
                    val name = (link?.groupValues?.getOrNull(1) ?: cells[0].removeSurrounding("**"))
                        .replace("**", "").trim()
                    val url = link?.groupValues?.getOrNull(2).orEmpty()
                    val description = cells.getOrNull(1).orEmpty().replace(Regex("<[^>]*>"), "").take(500)
                    if (name.length in 2..100 && description.isNotBlank() && safeDocsUrl(url)) {
                        val entry = BobbyPublicApiEntry(
                            name = name,
                            description = description,
                            documentationUrl = url,
                            auth = cells.getOrNull(2).orEmpty().take(60),
                            https = cells.getOrNull(3).orEmpty().take(20),
                            cors = cells.getOrNull(4).orEmpty().take(20),
                            category = category
                        )
                        output.putIfAbsent((category + ":" + name).lowercase(), entry)
                    }
                }
            }
        }
        return output.values.toList()
    }

    private fun safeDocsUrl(value: String): Boolean {
        val parsed = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase().orEmpty()
        return parsed.scheme.equals("https", true) && host.isNotBlank() &&
            !host.equals("localhost") && !host.endsWith(".local") &&
            !host.startsWith("127.") && !host.startsWith("10.") &&
            !host.startsWith("192.168.") && !host.startsWith("169.254.")
    }

    private fun parseCached(raw: String): List<BobbyPublicApiEntry> = runCatching {
        val rows = JSONArray(raw)
        (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { row ->
                BobbyPublicApiEntry(
                    row.optString("name"), row.optString("description"),
                    row.optString("documentationUrl"), row.optString("auth"),
                    row.optString("https"), row.optString("cors"),
                    row.optString("category", "General"), row.optString("source", "public-apis/public-apis")
                )
            }
        }
    }.getOrDefault(emptyList())
}
