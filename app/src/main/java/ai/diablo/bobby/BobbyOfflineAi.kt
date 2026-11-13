package ai.diablo.bobby

import android.content.Context
import android.app.ActivityManager
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
            val memoryManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            memoryManager.getMemoryInfo(memoryInfo)
            require(!memoryInfo.lowMemory && memoryInfo.availMem >= MIN_FREE_RAM_BYTES) {
                "Not enough free RAM to safely load the offline model. Close other apps and try again. This model can require around 2–3 GB of process memory; the phone may be unable to run it reliably."
            }
            val path = store.modelFile()
            require(path.isFile && store.verifyInstalledModel()) {
                "The offline model is missing or failed its integrity check. Open OFFLINE AI and download the official model again."
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
                        "You are BOBBY, an independent AI assistant running fully offline on this phone. " +
                        "Automatically detect the language of every user message and answer in that same language. " +
                        "Support multilingual and code-switched conversation; do not force English. " +
                        "Be accurate, practical, warm and direct. Never pretend to access the internet, phone, accounts, files or apps. " +
                        "If you do not know something, say so. You cannot browse or perform external actions while offline. " +
                        "Keep answers concise by default and explain limitations honestly."
                    ),
                    samplerConfig = SamplerConfig(topK = 24, topP = 0.85, temperature = 0.7),
                    maxOutputToken = 256
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
                    val boundedPrompt = boundPrompt(prompt)
                    synchronized(this) {
                        val activeConversation = requireNotNull(conversation) {
                            "Offline AI was closed before generation could start. Please try again."
                        }
                        val answer = activeConversation.sendMessage(boundedPrompt).toString().trim()
                        require(answer.isNotBlank()) { "The offline model returned an empty answer." }
                        answer
                    }
                } finally {
                    synchronized(this) { busy = false }
                }
            }
            callback(result)
        }
    }

    private fun boundPrompt(prompt: String): String {
        if (prompt.length <= MAX_PROMPT_CHARS) return prompt
        val prefix = prompt.take(PREFIX_CHARS)
        val recent = prompt.takeLast(RECENT_CHARS)
        return prefix + "\n\n[Earlier context shortened to fit the offline model window.]\n\n" + recent
    }

    /** Prevent concurrent model shutdown while a conversation is generating. */
    @Synchronized
    fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }

    companion object {
        // Leave headroom for Android, the UI, model weights, and the KV cache.
        private const val MIN_FREE_RAM_BYTES = 1_800L * 1024L * 1024L
        private const val MAX_PROMPT_CHARS = 9_000
        private const val PREFIX_CHARS = 2_000
        private const val RECENT_CHARS = 6_500
    }
}
