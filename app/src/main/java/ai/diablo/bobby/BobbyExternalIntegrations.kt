package ai.diablo.bobby

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class BobbyExternalStatus(val provider: String, val detail: String)

/**
 * User-owned external account adapters.
 * Tokens are encrypted with Android Keystore via BobbySecretVault.
 * Google tokens need Gmail and Calendar scopes. Social tokens must have the
 * scopes granted by the corresponding provider app. No credentials are embedded.
 */
class BobbyExternalIntegrations(private val context: Context) {
    private val vault = BobbySecretVault(context)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun save(provider: String, token: String) {
        require(provider in PROVIDERS) { "Unsupported provider." }
        require(token.trim().length >= 12) { "Enter a valid access token." }
        vault.put("external_${provider}_token", token.trim())
    }

    fun has(provider: String): Boolean =
        provider in PROVIDERS && vault.get("external_${provider}_token").isNotBlank()

    fun remove(provider: String) {
        if (provider in PROVIDERS) vault.remove("external_${provider}_token")
    }

    fun verifyGoogle(callback: (Result<String>) -> Unit) {
        run("google", callback) { token ->
            val gmail = getJson("https://gmail.googleapis.com/gmail/v1/users/me/profile", token)
            val calendar = getJson("https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=1", token)
            val email = gmail.optString("emailAddress", "Google account")
            val count = calendar.optJSONArray("items")?.length() ?: 0
            "Google connected as $email. Gmail and Calendar scopes verified."
        }
    }

    fun verifyMeta(callback: (Result<String>) -> Unit) = run("meta", callback) { token ->
        val data = getJson("https://graph.facebook.com/me?fields=id,name", token)
        "Meta connected: ${data.optString("name", "account")} (ID ${data.optString("id")})."
    }

    fun verifyX(callback: (Result<String>) -> Unit) = run("x", callback) { token ->
        val data = getJson("https://api.x.com/2/users/me", token)
        val user = data.optJSONObject("data") ?: error("X returned no user profile.")
        "X connected as @${user.optString("username")}."
    }

    fun verifyTelegram(callback: (Result<String>) -> Unit) = run("telegram", callback) { token ->
        val data = getJson("https://api.telegram.org/bot$token/getMe", null)
        require(data.optBoolean("ok")) { data.optString("description").ifBlank { "Telegram rejected this bot token." } }
        val user = data.optJSONObject("result") ?: error("Telegram returned no bot profile.")
        "Telegram bot connected: @${user.optString("username")}."
    }

    fun recentGmail(callback: (Result<String>) -> Unit) = run("google", callback) { token ->
        val list = getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=5&q=is%3Aunread", token)
        val items = list.optJSONArray("messages")
        if (items == null || items.length() == 0) "Gmail is connected. No unread messages were returned."
        else "Gmail is connected. ${items.length()} unread message IDs retrieved. Open Gmail to review message contents."
    }

    fun upcomingCalendar(callback: (Result<String>) -> Unit) = run("google", callback) { token ->
        val now = java.time.Instant.now().toString()
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=5&orderBy=startTime&singleEvents=true&timeMin=" +
            URLEncoder.encode(now, "UTF-8")
        val data = getJson(url, token)
        val items = data.optJSONArray("items")
        if (items == null || items.length() == 0) "Calendar is connected. No upcoming events were returned."
        else {
            val lines = (0 until items.length()).mapNotNull { i ->
                items.optJSONObject(i)?.let { event ->
                    val start = event.optJSONObject("start")?.optString("dateTime")
                        ?: event.optJSONObject("start")?.optString("date").orEmpty()
                    "${event.optString("summary", "Untitled event")} — $start"
                }
            }
            "Upcoming calendar events:\n" + lines.joinToString("\n")
        }
    }

    fun postX(text: String, callback: (Result<String>) -> Unit) {
        require(text.isNotBlank() && text.length <= 280) { "X post must contain 1–280 characters." }
        run("x", callback) { token ->
            val body = JSONObject().put("text", text).toString()
            val result = postJson("https://api.x.com/2/tweets", token, body)
            "X post published. Post ID: ${result.optJSONObject("data")?.optString("id").orEmpty()}"
        }
    }

    fun sendTelegram(chatId: String, text: String, callback: (Result<String>) -> Unit) {
        require(chatId.isNotBlank() && text.isNotBlank()) { "Enter a chat ID and message." }
        require(text.length <= 4096) { "Telegram messages must be 4096 characters or fewer." }
        run("telegram", callback) { token ->
            val body = JSONObject().put("chat_id", chatId).put("text", text).toString()
            val result = postJson("https://api.telegram.org/bot$token/sendMessage", null, body)
            require(result.optBoolean("ok")) { "Telegram did not accept the message." }
            "Telegram message sent."
        }
    }

    private fun run(provider: String, callback: (Result<String>) -> Unit, action: (String) -> String) {
        worker.execute {
            val result = runCatching {
                val token = vault.get("external_${provider}_token")
                require(token.isNotBlank()) { "Save the $provider access token first." }
                action(token)
            }
            main.post { callback(result) }
        }
    }

    private fun getJson(url: String, bearer: String?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Provider returned HTTP $code: ${raw.take(280)}")
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun postJson(url: String, bearer: String?, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Provider returned HTTP $code: ${raw.take(280)}")
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    fun close() { worker.shutdownNow() }

    companion object {
        val PROVIDERS = setOf("google", "meta", "x", "telegram")
    }
}

@androidx.compose.runtime.Composable
fun BobbyExternalIntegrationsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connector = androidx.compose.runtime.remember { BobbyExternalIntegrations(context) }
    var googleToken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var metaToken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var xToken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var telegramToken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var telegramChat by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var telegramMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var xPost by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Tokens are encrypted on this phone. Configure OAuth/API access in each provider's developer console first.") }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var universalOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    fun check(action: ((Result<String>) -> Unit) -> Unit) {
        busy = true
        action { result ->
            busy = false
            status = result.fold({ "SUCCESS • $it" }, { "ERROR • ${it.message ?: "Request failed"}" })
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { androidx.compose.material3.Text("BOBBY • ACCOUNT CONNECTIONS", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.heightIn(max = 560.dp).verticalScroll(scroll),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(onClick = { universalOpen = true }) { androidx.compose.material3.Text("OPEN UNIVERSAL OAUTH CONNECTOR") }
                androidx.compose.material3.Text("Connect supported OAuth providers using their official sign-in screen. No passwords are entered into BOBBY.")
                androidx.compose.material3.Text("GOOGLE • GMAIL + CALENDAR", color = androidx.compose.ui.graphics.Color(0xFFFF1744))
                androidx.compose.material3.Text("Paste a short-lived Google OAuth access token granted both Gmail (gmail.readonly) and Calendar (calendar.readonly) scopes. A full in-app Google sign-in flow still requires your OAuth client configuration.")
                androidx.compose.material3.OutlinedTextField(googleToken, { googleToken = it }, label = { androidx.compose.material3.Text("Google access token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(onClick = { runCatching { connector.save("google", googleToken); googleToken = ""; status = "Google token saved securely." }.onFailure { status = it.message ?: "Save failed." } }) { androidx.compose.material3.Text("SAVE") }
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("google"), onClick = { check(connector::verifyGoogle) }) { androidx.compose.material3.Text("VERIFY GMAIL + CALENDAR") }
                }
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("google"), onClick = { check(connector::recentGmail) }) { androidx.compose.material3.Text("CHECK UNREAD MAIL") }
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("google"), onClick = { check(connector::upcomingCalendar) }) { androidx.compose.material3.Text("UPCOMING EVENTS") }
                }
                androidx.compose.material3.TextButton(onClick = { connector.remove("google"); status = "Google token removed from this phone." }) { androidx.compose.material3.Text("DISCONNECT GOOGLE") }

                androidx.compose.material3.Divider()
                androidx.compose.material3.Text("META • FACEBOOK / INSTAGRAM / THREADS", color = androidx.compose.ui.graphics.Color(0xFFFF1744))
                androidx.compose.material3.Text("Use a Meta user access token from an approved Meta app. Available permissions depend on app review and account type.")
                androidx.compose.material3.OutlinedTextField(metaToken, { metaToken = it }, label = { androidx.compose.material3.Text("Meta access token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(onClick = { runCatching { connector.save("meta", metaToken); metaToken = ""; status = "Meta token saved securely." }.onFailure { status = it.message ?: "Save failed." } }) { androidx.compose.material3.Text("SAVE") }
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("meta"), onClick = { check(connector::verifyMeta) }) { androidx.compose.material3.Text("VERIFY META") }
                }
                androidx.compose.material3.TextButton(onClick = { connector.remove("meta"); status = "Meta token removed." }) { androidx.compose.material3.Text("DISCONNECT META") }

                androidx.compose.material3.Divider()
                androidx.compose.material3.Text("X", color = androidx.compose.ui.graphics.Color(0xFFFF1744))
                androidx.compose.material3.Text("Use an X user access token with users.read and tweet.write scopes. Publishing is a separate action and requires your confirmation.")
                androidx.compose.material3.OutlinedTextField(xToken, { xToken = it }, label = { androidx.compose.material3.Text("X user access token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(onClick = { runCatching { connector.save("x", xToken); xToken = ""; status = "X token saved securely." }.onFailure { status = it.message ?: "Save failed." } }) { androidx.compose.material3.Text("SAVE") }
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("x"), onClick = { check(connector::verifyX) }) { androidx.compose.material3.Text("VERIFY X") }
                }
                androidx.compose.material3.OutlinedTextField(xPost, { xPost = it.take(280) }, label = { androidx.compose.material3.Text("Post text (max 280)") })
                androidx.compose.material3.Button(enabled = !busy && connector.has("x") && xPost.isNotBlank(), onClick = { status = "Review this post before publishing: $xPost"; check { done -> connector.postX(xPost) { result -> done(result); if (result.isSuccess) xPost = "" } } }) { androidx.compose.material3.Text("PUBLISH X POST") }
                androidx.compose.material3.TextButton(onClick = { connector.remove("x"); status = "X token removed." }) { androidx.compose.material3.Text("DISCONNECT X") }

                androidx.compose.material3.Divider()
                androidx.compose.material3.Text("TELEGRAM BOT", color = androidx.compose.ui.graphics.Color(0xFFFF1744))
                androidx.compose.material3.Text("Uses a bot token from BotFather. This cannot control your personal Telegram account.")
                androidx.compose.material3.OutlinedTextField(telegramToken, { telegramToken = it }, label = { androidx.compose.material3.Text("Telegram bot token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(onClick = { runCatching { connector.save("telegram", telegramToken); telegramToken = ""; status = "Telegram bot token saved securely." }.onFailure { status = it.message ?: "Save failed." } }) { androidx.compose.material3.Text("SAVE") }
                    androidx.compose.material3.TextButton(enabled = !busy && connector.has("telegram"), onClick = { check(connector::verifyTelegram) }) { androidx.compose.material3.Text("VERIFY BOT") }
                }
                androidx.compose.material3.OutlinedTextField(telegramChat, { telegramChat = it }, label = { androidx.compose.material3.Text("Telegram chat ID") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(telegramMessage, { telegramMessage = it }, label = { androidx.compose.material3.Text("Message to send") })
                androidx.compose.material3.Button(enabled = !busy && connector.has("telegram") && telegramChat.isNotBlank() && telegramMessage.isNotBlank(), onClick = {
                    status = "Sending the message to the chat ID you entered…"
                    check { done -> connector.sendTelegram(telegramChat, telegramMessage) { result -> done(result); if (result.isSuccess) telegramMessage = "" } }
                }) { androidx.compose.material3.Text("SEND TELEGRAM MESSAGE") }
                androidx.compose.material3.TextButton(onClick = { connector.remove("telegram"); status = "Telegram bot token removed." }) { androidx.compose.material3.Text("DISCONNECT TELEGRAM") }

                if (busy) androidx.compose.material3.CircularProgressIndicator()
                androidx.compose.material3.Text(status, color = androidx.compose.ui.graphics.Color(0xFFFF1744), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("DONE") } }
    )
    if (universalOpen) BobbyUniversalPluginConnectorDialog(onDismiss = { universalOpen = false })
}
