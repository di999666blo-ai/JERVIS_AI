package ai.diablo.bobby

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * BOBBY speech output. User-owned XTTS voice profile first, existing neural
 * provider second, Android TTS fallback last.
 */
class BobbyNeuralVoice(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val vault = BobbySecretVault(context)
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    fun speak(text: String, apiKey: String, onFallback: (() -> Unit)? = null) {
        if (text.isBlank()) { onFallback?.invoke(); return }
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                val base = vault.get("bobby_voice_server_url").trimEnd('/')
                val profileId = vault.get("bobby_xtts_voice_profile_id")
                val usingFreeClone = base.isNotBlank() && profileId.isNotBlank()
                val audio = File(context.cacheDir, if (usingFreeClone) "bobby-free-clone.wav" else "bobby-neural-reply.mp3")
                if (usingFreeClone) {
                    val boundary = "----BobbyTTS" + System.nanoTime()
                    val form = buildString {
                        append("--$boundary\r\nContent-Disposition: form-data; name=\"voice_profile_id\"\r\n\r\n$profileId\r\n")
                        append("--$boundary\r\nContent-Disposition: form-data; name=\"text\"\r\n\r\n${text.take(3500)}\r\n")
                        append("--$boundary\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nen\r\n")
                        append("--$boundary\r\nContent-Disposition: form-data; name=\"save_to_library\"\r\n\r\nfalse\r\n")
                        append("--$boundary--\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    connection = (URL("$base/generations").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000; doOutput = true
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    }
                    connection.outputStream.use { it.write(form) }
                    val code = connection.responseCode
                    val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("XTTS generation request failed (HTTP $code): ${raw.take(250)}")
                    val jobId = JSONObject(raw).optString("job_id")
                    require(jobId.isNotBlank()) { "XTTS server returned no generation job ID." }
                    connection.disconnect(); connection = null
                    var completed = false
                    repeat(120) {
                        Thread.sleep(2000)
                        val statusConnection = URL("$base/generations/$jobId/status").openConnection() as HttpURLConnection
                        val statusJson = try {
                            statusConnection.connectTimeout = 15000; statusConnection.readTimeout = 15000
                            val statusCode = statusConnection.responseCode
                            val statusRaw = (if (statusCode in 200..299) statusConnection.inputStream else statusConnection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                            if (statusCode !in 200..299) error("XTTS status check failed (HTTP $statusCode).")
                            JSONObject(statusRaw)
                        } finally { statusConnection.disconnect() }
                        when (statusJson.optString("status")) {
                            "completed" -> { completed = true; return@repeat }
                            "failed" -> error("XTTS voice generation failed: ${statusJson.optString("error")}")
                        }
                        if (completed) return@repeat
                    }
                    require(completed) { "XTTS generation timed out. Try again or check the server." }
                    val download = (URL("$base/generations/$jobId/audio?format=wav").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000; readTimeout = 90000
                    }
                    try {
                        require(download.responseCode in 200..299) { "Could not download XTTS speech audio." }
                        download.inputStream.use { input -> audio.outputStream().use { output -> input.copyTo(output) } }
                    } finally { download.disconnect() }
                } else {
                    if (apiKey.isBlank()) { main.post { onFallback?.invoke() }; return@execute }
                    val body = JSONObject().apply {
                        put("model", "gpt-4o-mini-tts"); put("voice", "nova"); put("input", text.take(3500))
                        put("response_format", "mp3")
                        put("instructions", "You are Bobby, a calm, warm, natural adult female assistant. Speak naturally in the language of the input.")
                    }.toString()
                    connection = (URL("https://api.openai.com/v1/audio/speech").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                        setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "audio/mpeg")
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    require(connection.responseCode in 200..299) { "Speech provider returned HTTP ${connection.responseCode}." }
                    connection.inputStream.use { input -> audio.outputStream().use { output -> input.copyTo(output) } }
                }
                require(audio.length() > 512) { "Speech provider returned an empty audio file." }
                main.post {
                    runCatching {
                        player?.release()
                        player = MediaPlayer().apply {
                            setDataSource(audio.absolutePath)
                            setOnCompletionListener { it.release(); if (player === it) player = null }
                            setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; onFallback?.invoke(); true }
                            prepare(); start()
                        }
                    }.onFailure { onFallback?.invoke() }
                }
            } catch (_: Exception) {
                main.post { onFallback?.invoke() }
            } finally { connection?.disconnect() }
        }
    }

    fun stop() {
        main.post { player?.let { runCatching { it.stop() }; it.release() }; player = null }
    }

    fun release() { stop(); worker.shutdownNow() }
}
