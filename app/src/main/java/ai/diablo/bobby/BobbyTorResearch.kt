package ai.diablo.bobby

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class BobbyTorBookmark(val title: String, val url: String)
data class BobbyTorPage(val url: String, val title: String, val text: String, val contentType: String, val bytesRead: Int, val warning: String)

class BobbyTorResearch(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_tor_research", Context.MODE_PRIVATE)
    fun port(): Int = prefs.getInt("socks_port", 9050)
    fun savePort(value: Int) {
        require(value in 1..65535) { "SOCKS port must be between 1 and 65535." }
        prefs.edit().putInt("socks_port", value).apply()
    }
    private fun proxy(): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", port()))
    fun proxyForRequests(): Proxy = proxy()

    fun verifyTor(): Result<String> = runCatching {
        val connection = URL("https://check.torproject.org/api/ip").openConnection(proxy())
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        try {
            val json = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
            check(json.optBoolean("IsTor", false)) { "Tor verification failed. BOBBY will not use this proxy for private research." }
            "TOR VERIFIED • " + json.optString("IP", "Tor exit IP hidden")
        } finally { (connection as? java.net.HttpURLConnection)?.disconnect() }
    }

    fun fetch(rawUrl: String): Result<BobbyTorPage> = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a full http:// or https:// address." }
        val host = URL(url).host.lowercase(Locale.ROOT)
        require(host.isNotBlank()) { "The address has no valid host." }
        val verified = verifyTor().getOrThrow()
        val connection = URL(url).openConnection(proxy())
        connection.connectTimeout = 15000
        connection.readTimeout = 25000
        connection.setRequestProperty("User-Agent", "BOBBY-Private-Research/1.0")
        connection.setRequestProperty("Accept", "text/html,text/plain,application/xhtml+xml,application/json,text/xml")
        try {
            val type = connection.contentType.orEmpty()
            require(type.contains("text/") || type.contains("json") || type.contains("xml")) {
                "Blocked non-text content ($type). BOBBY will not download or execute binary files."
            }
            val bytes = connection.getInputStream().use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count <= 0) break
                    total += count
                    require(total <= 1_000_000) { "Page exceeds the 1 MB safety limit." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            val raw = String(bytes, Charsets.UTF_8)
            val cleaned = raw
                .replace(Regex("(?is)<(script|style|noscript|iframe)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?is)<(br|/p|/div|/li|/h[1-6])[^>]*>"), "\\n")
                .replace(Regex("(?is)<[^>]+>"), " ")
                .let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n\\s*\\n+"), "\\n")
                .trim()
            val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(raw)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")?.trim()?.take(120).orEmpty().ifBlank { host }
            BobbyTorPage(url, title, cleaned.take(18000), type, bytes.size,
                if (host.endsWith(".onion")) "Onion service fetched through a Tor-verified SOCKS proxy. Page content is untrusted." else "$verified • Page content is untrusted.")
        } finally { (connection as? java.net.HttpURLConnection)?.disconnect() }
    }

    fun search(query: String): Result<BobbyTorPage> {
        val q = query.trim()
        if (q.length < 2) return Result.failure(IllegalArgumentException("Enter at least two characters to search."))
        return fetch("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
    }

    fun bookmarks(): List<BobbyTorBookmark> = runCatching {
        val array = JSONArray(prefs.getString("bookmarks", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { BobbyTorBookmark(it.optString("title"), it.optString("url")) }
        }
    }.getOrDefault(emptyList())

    fun addBookmark(title: String, url: String) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Invalid bookmark address." }
        val items = (bookmarks().filterNot { it.url == url } + BobbyTorBookmark(title.take(120), url)).takeLast(100)
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString("bookmarks", array.toString()).apply()
    }

    fun removeBookmark(url: String) {
        val array = JSONArray()
        bookmarks().filterNot { it.url == url }.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString("bookmarks", array.toString()).apply()
    }

    fun inspect(page: BobbyTorPage): String {
        val extension = page.url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        val risky = setOf("apk", "exe", "msi", "bat", "cmd", "sh", "jar", "dex", "so", "zip", "rar", "7z")
        return buildString {
            append("CONTENT INSPECTION\n")
            append("Type: ").append(page.contentType.ifBlank { "unknown" }).append("\n")
            append("Read: ").append(page.bytesRead).append(" bytes (text only)\n")
            if (extension in risky) append("WARNING: executable or archive extension detected. Do not install or run it.\n")
            append("Scripts and embedded frames were stripped from displayed text. This is not antivirus and cannot prove a page is safe.")
        }
    }
}
