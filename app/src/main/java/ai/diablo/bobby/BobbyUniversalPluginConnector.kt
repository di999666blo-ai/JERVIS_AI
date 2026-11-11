package ai.diablo.bobby

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Generic OAuth 2.0 Authorization Code + PKCE connector.
 * Provider client IDs and endpoints are user-configured; no provider secrets
 * or credentials are embedded in the APK. Access tokens are encrypted by
 * BobbySecretVault. Each connection is limited to the scopes the user enters.
 */
data class BobbyOAuthConfig(
    val id: String,
    val displayName: String,
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val profileEndpoint: String,
    val scopes: String
) {
    fun validate() {
        require(id.matches(Regex("[a-z0-9_]{2,48}"))) { "Use a provider ID containing lowercase letters, numbers or underscores." }
        require(displayName.trim().length in 2..48) { "Provider name must be 2–48 characters." }
        require(clientId.isNotBlank() && clientId.length <= 300) { "Enter the OAuth client ID from the provider developer console." }
        listOf(authorizationEndpoint, tokenEndpoint, profileEndpoint).forEach {
            require(it.startsWith("https://", true) && Uri.parse(it).host?.isNotBlank() == true) {
                "OAuth endpoints must be valid HTTPS URLs."
            }
        }
        require(scopes.isNotBlank() && scopes.length <= 1000) { "Enter only the permissions this connection needs." }
    }
}

class BobbyUniversalOAuth(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("bobby_universal_oauth", Context.MODE_PRIVATE)
    private val vault = BobbySecretVault(app)
    private val worker = Executors.newSingleThreadExecutor()

    fun start(config: BobbyOAuthConfig) {
        config.validate()
        val verifier = randomUrlSafe(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val state = randomUrlSafe(32)
        val json = JSONObject()
            .put("id", config.id).put("name", config.displayName)
            .put("client_id", config.clientId)
            .put("authorization_endpoint", config.authorizationEndpoint)
            .put("token_endpoint", config.tokenEndpoint)
            .put("profile_endpoint", config.profileEndpoint)
            .put("scopes", config.scopes)
        prefs.edit()
            .putString("pending_config", json.toString())
            .putString("pending_verifier", verifier)
            .putString("pending_state", state)
            .putString("last_status", "Waiting for provider sign-in…")
            .apply()

        val uri = Uri.parse(config.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", config.scopes.trim())
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun status(): String = prefs.getString("last_status", "No OAuth connection attempted.").orEmpty()
    fun isConnected(id: String): Boolean = vault.get("oauth_${id}_token").isNotBlank()
    fun disconnect(id: String) {
        vault.remove("oauth_${id}_token")
        prefs.edit().remove("connected_${id}").apply()
        prefs.edit().putString("last_status", "Disconnected locally. Revoke access in the provider account settings if you also want to revoke provider consent.").apply()
    }

    fun connectedProviders(): List<String> = prefs.getStringSet("connected_ids", emptySet()).orEmpty().toList().sorted()

    fun handleCallback(uri: Uri, callback: (Result<String>) -> Unit) {
        val expectedState = prefs.getString("pending_state", null)
        val actualState = uri.getQueryParameter("state")
        if (uri.getQueryParameter("error") != null) {
            val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            prefs.edit().putString("last_status", "Sign-in cancelled or denied: $error").remove("pending_state").remove("pending_verifier").apply()
            callback(Result.failure(IllegalStateException(error)))
            return
        }
        if (expectedState.isNullOrBlank() || actualState != expectedState) {
            callback(Result.failure(IllegalStateException("OAuth state validation failed. Start the connection again.")))
            return
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Provider callback did not contain an authorisation code.")))
            return
        }
        val raw = prefs.getString("pending_config", null)
        val verifier = prefs.getString("pending_verifier", null)
        if (raw.isNullOrBlank() || verifier.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("The sign-in request expired. Start again.")))
            return
        }
        val config = JSONObject(raw)
        val id = config.getString("id")
        val tokenEndpoint = config.getString("token_endpoint")
        val clientId = config.getString("client_id")
        worker.execute {
            val result = runCatching {
                val form = linkedMapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "code_verifier" to verifier
                )
                val response = postForm(tokenEndpoint, form)
                val token = response.optString("access_token")
                require(token.isNotBlank()) {
                    response.optString("error_description").ifBlank {
                        response.optString("error").ifBlank { "Provider did not return an access token." }
                    }
                }
                val profileUrl = config.getString("profile_endpoint")
                val profile = getJson(profileUrl, token)
                val label = profile.optString("name")
                    .ifBlank { profile.optString("login") }
                    .ifBlank { profile.optString("email") }
                    .ifBlank { profile.optString("username") }
                    .ifBlank { "account verified" }
                vault.put("oauth_${id}_token", token)
                vault.put("oauth_${id}_name", config.getString("name"))
                vault.put("oauth_${id}_scopes", config.getString("scopes"))
                val ids = prefs.getStringSet("connected_ids", emptySet()).orEmpty().toMutableSet()
                ids.add(id)
                prefs.edit().putStringSet("connected_ids", ids)
                    .putString("connected_${id}", label)
                    .putString("last_status", "CONNECTED • ${config.getString("name")} • $label")
                    .remove("pending_config").remove("pending_verifier").remove("pending_state").apply()
                "Connected ${config.getString("name")} • $label"
            }
            result.onFailure { prefs.edit().putString("last_status", "Connection failed: ${it.message}").apply() }
            callback(result)
        }
    }

    private fun postForm(endpoint: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Token endpoint HTTP $code: ${raw.take(350)}")
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun getJson(endpoint: String, token: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Profile check HTTP $code: ${raw.take(350)}")
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun randomUrlSafe(bytes: Int): String {
        val data = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val REDIRECT_URI = "ai.diablo.bobby://oauth/callback"
    }
}

/** Receives only the app's private OAuth redirect and completes code exchange. */
class BobbyOAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        BobbyUniversalOAuth(this).handleCallback(uri) {
            runOnUiThread { finish() }
        }
    }
}

@Composable
fun BobbyUniversalPluginConnectorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val oauth = remember { BobbyUniversalOAuth(context) }
    var providerId by remember { mutableStateOf("google") }
    var providerName by remember { mutableStateOf("Google") }
    var clientId by remember { mutableStateOf("") }
    var authUrl by remember { mutableStateOf("https://accounts.google.com/o/oauth2/v2/auth") }
    var tokenUrl by remember { mutableStateOf("https://oauth2.googleapis.com/token") }
    var profileUrl by remember { mutableStateOf("https://openidconnect.googleapis.com/v1/userinfo") }
    var scopes by remember { mutableStateOf("openid email profile") }
    var status by remember { mutableStateOf(oauth.status()) }
    var busy by remember { mutableStateOf(false) }
    val connected = remember { mutableStateListOf<String>().apply { addAll(oauth.connectedProviders()) } }
    val scroll = rememberScrollState()

    fun preset(id: String, name: String, auth: String, token: String, profile: String, defaultScopes: String) {
        providerId = id; providerName = name; authUrl = auth; tokenUrl = token; profileUrl = profile; scopes = defaultScopes
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY • UNIVERSAL CONNECTOR", color = Color(0xFFFF1744)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 580.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Connect a service through its own official OAuth sign-in. The provider asks you to approve the requested scopes; BOBBY never asks for your account password.")
                Text("QUICK SETUP")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { preset("google", "Google", "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token", "https://openidconnect.googleapis.com/v1/userinfo", "openid email profile") }) { Text("GOOGLE") }
                    OutlinedButton(onClick = { preset("github", "GitHub", "https://github.com/login/oauth/authorize", "https://github.com/login/oauth/access_token", "https://api.github.com/user", "read:user user:email") }) { Text("GITHUB") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { preset("microsoft", "Microsoft", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize", "https://login.microsoftonline.com/common/oauth2/v2.0/token", "https://graph.microsoft.com/v1.0/me", "openid profile email User.Read") }) { Text("MICROSOFT") }
                    OutlinedButton(onClick = { preset("slack", "Slack", "https://slack.com/oauth/v2/authorize", "https://slack.com/api/oauth.v2.access", "https://slack.com/api/auth.test", "users:read") }) { Text("SLACK") }
                }
                Text("CUSTOM OAUTH 2.0 + PKCE", color = Color(0xFFFF1744))
                OutlinedTextField(providerId, { providerId = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(48) }, label = { Text("Provider ID (e.g. notion)") }, singleLine = true)
                OutlinedTextField(providerName, { providerName = it }, label = { Text("Service name") }, singleLine = true)
                OutlinedTextField(clientId, { clientId = it }, label = { Text("OAuth client ID") }, singleLine = true)
                OutlinedTextField(authUrl, { authUrl = it }, label = { Text("Authorisation endpoint") }, singleLine = true)
                OutlinedTextField(tokenUrl, { tokenUrl = it }, label = { Text("Token endpoint") }, singleLine = true)
                OutlinedTextField(profileUrl, { profileUrl = it }, label = { Text("Profile / test endpoint (JSON)") }, singleLine = true)
                OutlinedTextField(scopes, { scopes = it }, label = { Text("Requested scopes") })
                Text("Redirect URI to register in the provider console: ${BobbyUniversalOAuth.REDIRECT_URI}", style = MaterialTheme.typography.bodySmall)
                Text("Important: create a public/native OAuth client where supported, enable Authorization Code + PKCE (S256), and register the exact redirect URI. Some providers require a confidential client secret or do not support PKCE; those need a provider-specific secure backend and will not connect through this public-client flow.")
                Button(
                    enabled = clientId.isNotBlank() && providerId.length >= 2,
                    onClick = {
                        runCatching {
                            BobbyOAuthConfig(providerId, providerName, clientId.trim(), authUrl.trim(), tokenUrl.trim(), profileUrl.trim(), scopes.trim()).also { it.validate() }
                            oauth.start(BobbyOAuthConfig(providerId, providerName, clientId.trim(), authUrl.trim(), tokenUrl.trim(), profileUrl.trim(), scopes.trim()))
                            status = "Provider sign-in opened. Finish approval in your browser, then return to BOBBY."
                        }.onFailure { status = it.message ?: "Could not start OAuth." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SIGN IN WITH PROVIDER") }
                Spacer(Modifier.height(4.dp))
                Text("CONNECTED SERVICES", color = Color(0xFFFF1744))
                if (connected.isEmpty()) Text("No OAuth accounts connected yet.")
                connected.forEach { id ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(id)
                        TextButton(onClick = { oauth.disconnect(id); connected.remove(id); status = "Disconnected $id on this phone." }) { Text("DISCONNECT") }
                    }
                }
                if (busy) CircularProgressIndicator()
                Text(status, color = Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}
