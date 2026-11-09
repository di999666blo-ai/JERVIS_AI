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
 * BOBBY speech output. A user-created ElevenLabs voice takes priority; the
 * existing OpenAI neural voice is the next option and Android TTS is fallback.
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
                val cloneKey = vault.get("elevenlabs_api_key")
                val cloneId = vault.get("elevenlabs_voice_id")
                val usingClone = cloneKey.isNotBlank() && cloneId.isNotBlank()
                val requestBody = if (usingClone) {
                    JSONObject().apply {
                        put("text", text.take(3500))
                        put("model_id", "eleven_multilingual_v2")
                        put("voice_settings", JSONObject().put("stability", 0.52).put("similarity_boost", 0.78))
                    }.toString()
                } else {
                    if (apiKey.isBlank()) { main.post { onFallback?.invoke() }; return@execute }
                    JSONObject().apply {
                        put("model", "gpt-4o-mini-tts")
                        put("voice", "nova")
                        put("input", text.take(3500))
                        put("response_format", "mp3")
                        put("instructions", "You are Bobby, a calm, warm, natural adult female assistant. FIRST detect the language and dialect of the input text. Speak the entire response in that same language, using its correct native pronunciation, phonology, tones, stress, rhythm and natural intonation. If the text mixes languages, preserve the language of each phrase and pronounce each phrase naturally; do not translate it into English. If the input is Yoruba, Hausa, Igbo, Nigerian Pidgin, Arabic, French, Spanish, Portuguese, Hindi, Chinese, Japanese, Korean, or any other language you can speak, use that language rather than defaulting to English. Keep names as written and pronounce them as naturally as possible. Sound warm and conversational, with relaxed pacing and no exaggerated cartoon voice.")
                    }.toString()
                }
                val endpoint = if (usingClone) {
                    "https://api.elevenlabs.io/v1/text-to-speech/" + cloneId + "?output_format=mp3_44100_128"
                } else "https://api.openai.com/v1/audio/speech"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "audio/mpeg")
                    if (usingClone) setRequestProperty("xi-api-key", cloneKey)
                    else setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300)
                    throw IllegalStateException("Speech provider returned HTTP $code" + if (detail.isNotBlank()) ": $detail" else "")
                }
                val audio = File(context.cacheDir, "bobby-neural-reply.mp3")
                connection.inputStream.use { input -> audio.outputStream().use { output -> input.copyTo(output) } }
                require(audio.length() > 512) { "Speech provider returned an empty audio file." }
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
