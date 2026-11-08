package ai.diablo.bobby

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlin.concurrent.thread

/** On-device Qwen3 inference using Google's LiteRT-LM runtime. */
class BobbyOfflineAi(context: Context) {
    private val app = context.applicationContext
    private val store = BobbyOfflineModelStore(app)
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var busy = false

    fun isModelInstalled(): Boolean = store.status().installed
    fun isReady(): Boolean = conversation != null

    private fun loadIfNeeded() {
        if (conversation != null) return
        synchronized(this) {
            if (conversation != null) return
            val path = store.modelFile()
            require(path.isFile && path.length() in 500_000_000L..750_000_000L) {
                "The offline model is missing or incomplete. Open OFFLINE AI and download it again."
            }
            val newEngine = Engine(
                EngineConfig(
                    modelPath = path.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = app.cacheDir.absolutePath
                )
            )
            try {
                newEngine.initialize()
                val config = ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are BOBBY, the user's personal AI assistant running fully offline on this phone. " +
                        "Automatically detect the language of every user message and answer in that same language. " +
                        "Support multilingual and code-switched conversation; do not force English. " +
                        "Be accurate, practical, warm and direct. Never pretend to access the internet, phone, accounts, files or apps. " +
                        "If you do not know something, say so. You cannot browse or perform external actions while offline. " +
                        "Keep answers concise by default and explain limitations honestly."
                    ),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7),
                    maxOutputToken = 768
                )
                engine = newEngine
                conversation = newEngine.createConversation(config)
            } catch (error: Throwable) {
                runCatching { newEngine.close() }
                throw error
            }
        }
    }

    fun reply(prompt: String, callback: (Result<String>) -> Unit) {
        thread(name = "bobby-offline-inference") {
            val result = runCatching {
                require(prompt.isNotBlank()) { "Message is empty." }
                synchronized(this) {
                    check(!busy) { "Offline AI is already generating a reply. Please wait." }
                    busy = true
                }
                try {
                    loadIfNeeded()
                    val answer = requireNotNull(conversation).sendMessage(prompt).toString().trim()
                    require(answer.isNotBlank()) { "The offline model returned an empty answer." }
                    answer
                } finally {
                    synchronized(this) { busy = false }
                }
            }
            callback(result)
        }
    }

    @Synchronized
    fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
