package ai.diablo.bobby

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class AiSettings(val endpoint: String, val model: String, val apiKey: String)

object BobbyAiClient {
    fun reply(settings: AiSettings, conversation: List<ChatLine>, callback: (Result<String>) -> Unit) {
        thread(name = "bobby-ai-request") {
            val result = runCatching {
                require(settings.endpoint.startsWith("https://")) { "Use a secure HTTPS API endpoint." }
                require(settings.apiKey.isNotBlank()) { "Add your AI API key in Bobby settings." }
                require(settings.model.isNotBlank()) { "Enter an AI model name." }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "You are Bobby, a warm, natural, respectful personal AI companion. " +
                        "Speak like a thoughtful human conversation partner, not a form or robotic help desk. " +
                        "Be concise by default, ask one useful follow-up when appropriate, admit uncertainty, " +
                        "and never pretend to have performed actions you have not performed."))
                conversation.takeLast(24).forEach { line ->
                    messages.put(JSONObject()
                        .put("role", if (line.fromBobby) "assistant" else "user")
                        .put("content", line.text))
                }
                val body = JSONObject()
                    .put("model", settings.model)
                    .put("messages", messages)
                    .put("temperature", 0.75)
                    .put("stream", false)
                val connection = (URL(settings.endpoint.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 45000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                        throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "AI service returned HTTP $code.")
                    }
                    val json = JSONObject(raw)
                    val content = json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content").trim()
                    require(content.isNotBlank()) { "The AI service returned an empty reply." }
                    content
                } finally {
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }
}
