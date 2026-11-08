package ai.diablo.bobby

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import kotlin.concurrent.thread

/** Real local inference using Google's LiteRT-LM Android runtime. */
class BobbyOfflineAi(context: Context) {
    private val app = context.applicationContext
    private val store = BobbyOfflineModelStore(app)
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var loading = false

    fun isModelInstalled(): Boolean = store.status().installed

    private fun loadIfNeeded() {
        if (conversation != null) return
        synchronized(this) {
            if (conversation != null) return
            val path = store.modelFile()
            require(path.isFile && path.length() > 0) { "Offline model is not installed. Open OFFLINE AI and download it first." }
            loading = true
            try {
                val newEngine = Engine(EngineConfig(modelPath = path.absolutePath, backend = Backend.CPU()))
                newEngine.initialize()
                engine = newEngine
                conversation = newEngine.createConversation()
            } finally {
                loading = false
            }
        }
    }

    fun reply(prompt: String, callback: (Result<String>) -> Unit) {
        thread(name = "bobby-offline-inference") {
            val result = runCatching {
                require(prompt.isNotBlank()) { "Message is empty." }
                loadIfNeeded()
                val response = requireNotNull(conversation).sendMessage(prompt)
                response.text.trim().ifBlank { response.toString().trim() }
            }
            callback(result)
        }
    }

    fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
