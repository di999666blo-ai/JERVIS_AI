package ai.diablo.bobby

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DerivAccountInfo(val loginId: String, val currency: String, val balance: Double, val isVirtual: Boolean)

/**
 * Deriv v3 connection and authorization adapter only. No order-placement methods
 * are exposed; trading must be a separately confirmed, risk-guarded milestone.
 */
class BobbyDerivConnector(context: Context) {
    private val vault = BobbySecretVault(context.applicationContext)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    @Volatile private var socket: WebSocket? = null

    fun saveCredentials(appId: String, apiToken: String, expectedLoginId: String = "") {
        require(appId.trim().matches(Regex("[0-9]{1,12}"))) { "Enter the numeric Deriv App ID." }
        require(apiToken.trim().length >= 10) { "The Deriv API token looks too short." }
        vault.put("deriv_app_id", appId.trim())
        vault.put("deriv_api_token", apiToken.trim())
        if (expectedLoginId.isBlank()) vault.remove("deriv_expected_login_id") else vault.put("deriv_expected_login_id", expectedLoginId.trim())
    }
    fun hasCredentials() = vault.get("deriv_app_id").isNotBlank() && vault.get("deriv_api_token").isNotBlank()
    fun clearCredentials() {
        disconnect(); vault.remove("deriv_app_id"); vault.remove("deriv_api_token"); vault.remove("deriv_expected_login_id")
    }
    fun disconnect() { socket?.close(1000, "Disconnected by user"); socket = null }

    fun connectAndVerify(callback: (Result<DerivAccountInfo>) -> Unit) {
        val appId = vault.get("deriv_app_id"); val token = vault.get("deriv_api_token"); val expectedLoginId = vault.get("deriv_expected_login_id")
        if (appId.isBlank() || token.isBlank()) {
            callback(Result.failure(IllegalStateException("Add your Deriv App ID and API token first."))); return
        }
        val request = Request.Builder().url("wss://ws.derivws.com/websockets/v3?app_id=$appId").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("authorize", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    if (json.has("error")) throw IllegalStateException(json.getJSONObject("error").optString("message", "Deriv authorization failed."))
                    if (json.optString("msg_type") == "authorize") {
                        val auth = json.getJSONObject("authorize")
                        val actualLoginId = auth.optString("loginid")
                        require(expectedLoginId.isBlank() || expectedLoginId.equals(actualLoginId, ignoreCase = true)) {
                            "Connected Deriv login ID does not match the account ID you entered."
                        }
                        callback(Result.success(DerivAccountInfo(
                            actualLoginId, auth.optString("currency", "USD"),
                            auth.optDouble("balance", 0.0),
                            auth.optBoolean("is_virtual", auth.optString("loginid").startsWith("VRTC"))
                        )))
                    }
                }.onFailure { callback(Result.failure(it)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callback(Result.failure(t))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
            }
        })
    }
}
