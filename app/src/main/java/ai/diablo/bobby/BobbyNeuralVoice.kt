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
 * Optional high-quality neural voice. Uses the user's own OpenAI API key.
 * Android system TTS remains the offline/device fallback.
 */
class BobbyNeuralVoice(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    fun speak(text: String, apiKey: String, onFallback: (() -> Unit)? = null) {
        if (text.isBlank() || apiKey.isBlank()) { onFallback?.invoke(); return }
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("model", "gpt-4o-mini-tts")
                    put("voice", "nova")
                    put("input", text.take(3500))
                    put("response_format", "mp3")
                    put("instructions", "You are Bobby, a calm, warm, natural adult female assistant. FIRST detect the language and dialect of the input text. Speak the entire response in that same language, using its correct native pronunciation, phonology, tones, stress, rhythm and natural intonation. If the text mixes languages, preserve the language of each phrase and pronounce each phrase naturally; do not translate it into English. If the input is Yoruba, Hausa, Igbo, Nigerian Pidgin, Arabic, French, Spanish, Portuguese, Hindi, Chinese, Japanese, Korean, or any other language you can speak, use that language rather than defaulting to English. Keep names as written and pronounce them as naturally as possible. Sound warm and conversational, with relaxed pacing and no exaggerated cartoon voice.")
                }.toString()
                connection = (URL("https://api.openai.com/v1/audio/speech").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "audio/mpeg")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) throw IllegalStateException("Neural voice service returned HTTP ${connection.responseCode}")
                val audio = File(context.cacheDir, "bobby-neural-reply.mp3")
                connection.inputStream.use { input -> audio.outputStream().use { output -> input.copyTo(output) } }
                main.post {
                    runCatching {
                        player?.release()
                        player = MediaPlayer().apply {
                            setDataSource(audio.absolutePath)
                            setOnCompletionListener { it.release(); if (player === it) player = null }
                            setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; onFallback?.invoke(); true }
                            prepare()
                            start()
                        }
                    }.onFailure { onFallback?.invoke() }
                }
            } catch (_: Exception) {
                main.post { onFallback?.invoke() }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun stop() {
        main.post { player?.let { runCatching { it.stop() }; it.release() }; player = null }
    }

    fun release() {
        stop()
        worker.shutdownNow()
    }
}
