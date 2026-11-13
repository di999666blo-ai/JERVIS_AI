package ai.diablo.bobby

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Narrow bridge from BOBBY's bundled, local dashboard asset to existing native
 * app actions. It deliberately exposes no arbitrary Android APIs or file paths.
 */
class BobbyHtmlDashboardBridge(
    private val onSend: (String) -> Unit,
    private val onNavigate: (String) -> Unit,
    private val onSelectMode: (String) -> Unit,
    private val onTheme: (Boolean) -> Unit,
    private val onVoice: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun sendMessage(message: String) {
        val clean = message.trim().take(12000)
        if (clean.isNotEmpty()) mainHandler.post { onSend(clean) }
    }

    @JavascriptInterface
    fun openSection(section: String) {
        val allowed = setOf("home", "chat", "tools", "plugins", "memory", "files", "more", "settings", "research", "video", "image", "voice", "deriv")
        if (section.lowercase() in allowed) mainHandler.post { onNavigate(section) }
    }

    @JavascriptInterface
    fun selectMode(mode: String) {
        if (mode.lowercase() in setOf("friendly", "work", "dark", "caveman")) {
            mainHandler.post { onSelectMode(mode) }
        }
    }

    @JavascriptInterface
    fun setLightTheme(enabled: Boolean) {
        mainHandler.post { onTheme(enabled) }
    }

    @JavascriptInterface
    fun startVoiceInput() {
        mainHandler.post { onVoice() }
    }
}
