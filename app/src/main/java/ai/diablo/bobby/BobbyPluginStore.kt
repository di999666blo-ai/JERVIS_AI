package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BobbyPluginDescriptor(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Local plugin catalogue and configuration registry.
 * Registry state is not proof that a remote integration is implemented or connected.
 */
class BobbyPluginStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_plugin_registry", Context.MODE_PRIVATE)
    private val vault = BobbySecretVault(context)

    val catalog = listOf(
        BobbyPluginDescriptor("github", "GitHub", "Repositories, issues, pull requests and Actions. Token setup is available; API operations are a pending connector milestone."),
        BobbyPluginDescriptor("deriv", "Deriv", "Secure account verification is implemented. Live market data and trading actions are not yet available."),
        BobbyPluginDescriptor("web_research", "Web Research", "Web search, page reading and source citations. Connector implementation pending."),
        BobbyPluginDescriptor("google_drive", "Google Drive", "Cloud file search and document access. OAuth connector implementation pending.")
    )

    fun isEnabled(id: String): Boolean = prefs.getBoolean("enabled_$id", false)

    fun setEnabled(id: String, enabled: Boolean) {
        require(id.matches(Regex("[a-z0-9_]{1,48}")))
        prefs.edit().putBoolean("enabled_$id", enabled).apply()
    }

    fun saveGitHubToken(token: String) {
        require(token.trim().isNotEmpty()) { "Enter a GitHub fine-grained token." }
        require(token.length <= 500) { "Token is too long." }
        vault.put("github_pat", token.trim())
    }

    fun hasGitHubToken(): Boolean = vault.get("github_pat").isNotBlank()

    fun removeGitHubToken() = vault.remove("github_pat")

    fun addCustom(name: String, baseUrl: String) {
        val cleanName = name.trim()
        val cleanUrl = baseUrl.trim()
        require(cleanName.length in 2..40) { "Connector name must be 2–40 characters." }
        require(cleanUrl.startsWith("https://", ignoreCase = true)) { "Only HTTPS connector URLs are allowed." }
        require(android.net.Uri.parse(cleanUrl).host?.isNotBlank() == true) { "Enter a valid HTTPS URL." }
        val rows = JSONArray(prefs.getString("custom_connectors", "[]"))
        require(rows.length() < 20) { "The custom connector list is full." }
        require((0 until rows.length()).none { rows.getJSONObject(it).optString("name").equals(cleanName, true) }) {
            "A connector with that name already exists."
        }
        rows.put(JSONObject().put("name", cleanName).put("url", cleanUrl))
        prefs.edit().putString("custom_connectors", rows.toString()).apply()
    }

    fun customConnectors(): List<Pair<String, String>> {
        val rows = JSONArray(prefs.getString("custom_connectors", "[]"))
        return (0 until rows.length()).map {
            rows.getJSONObject(it).let { row -> row.optString("name") to row.optString("url") }
        }
    }
}
