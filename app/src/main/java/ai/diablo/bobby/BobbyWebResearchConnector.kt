package ai.diablo.bobby

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread

data class BobbyWebResult(val title: String, val url: String, val description: String)

/** Brave Search API adapter. Requires the user's own API subscription key. */
class BobbyWebResearchConnector(context: Context) {
    private val vault = BobbySecretVault(context)
    fun saveKey(key: String) {
        require(key.trim().length in 10..500) { "Enter a valid Brave Search API key." }
        vault.put("brave_search_key", key.trim())
    }
    fun hasKey(): Boolean = vault.get("brave_search_key").isNotBlank()
    fun removeKey() = vault.remove("brave_search_key")

    fun search(query: String, callback: (Result<List<BobbyWebResult>>) -> Unit) {
        thread(name = "bobby-web-search") {
            val result = runCatching {
                require(query.trim().length >= 2) { "Enter a search query." }
                val key = vault.get("brave_search_key")
                require(key.isNotBlank()) { "Connect Brave Search in Web Research settings first." }
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val connection = (URL("https://api.search.brave.com/res/v1/web/search?q=" + encoded + "&count=6").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 25000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("X-Subscription-Token", key)
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("Brave Search API error $code: " + runCatching { JSONObject(raw).optString("message") }.getOrDefault("request rejected"))
                    val rows = JSONObject(raw).optJSONObject("web")?.optJSONArray("results")
                    (0 until (rows?.length() ?: 0)).map { i ->
                        val row = rows!!.getJSONObject(i)
                        BobbyWebResult(row.optString("title"), row.optString("url"), row.optString("description"))
                    }
                } finally { connection.disconnect() }
            }
            callback(result)
        }
    }
}
