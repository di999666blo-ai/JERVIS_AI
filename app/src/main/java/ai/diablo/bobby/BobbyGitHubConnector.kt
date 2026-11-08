package ai.diablo.bobby

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class BobbyGitHubAccount(val login: String, val name: String, val publicRepos: Int)

/** Read-only GitHub identity connection test. No write operations are exposed. */
class BobbyGitHubConnector(context: android.content.Context) {
    private val vault = BobbySecretVault(context)
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    fun verify(callback: (Result<BobbyGitHubAccount>) -> Unit) {
        thread(name = "bobby-github-verify") {
            val result = runCatching {
                val token = vault.get("github_pat")
                require(token.isNotBlank()) { "Add a GitHub fine-grained token first." }
                val request = Request.Builder().url("https://api.github.com/user")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28").get().build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching { JSONObject(raw).optString("message") }.getOrDefault("")
                        throw IllegalStateException("GitHub rejected the token (" + response.code + "): " + message)
                    }
                    val json = JSONObject(raw)
                    BobbyGitHubAccount(json.optString("login"), json.optString("name").ifBlank { json.optString("login") }, json.optInt("public_repos"))
                }
            }
            callback(result)
        }
    }
}
