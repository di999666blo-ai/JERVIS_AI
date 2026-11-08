package ai.diablo.bobby

enum class BobbyMode { FRIENDLY, WORKING, DARK }
enum class BobbySection(val label: String) {
    HOME("Home"), CHAT("AI Chat"), VOICE("Voice"), FILES("Files"), RESEARCH("Research"),
    TRADING("Trading"), BROWSER("Browser"), PLUGINS("Plugins"), MEMORY("Memory"), SETTINGS("Settings")
}

data class BobbyCapabilityStatus(val name: String, val available: Boolean, val detail: String)

object BobbyCapabilityReport {
    fun current(): List<BobbyCapabilityStatus> = listOf(
        BobbyCapabilityStatus("Online AI", true, "Available when a compatible provider and API key are configured."),
        BobbyCapabilityStatus("Speech recognition", true, "Uses Android speech recognition service; offline availability depends on installed language packs."),
        BobbyCapabilityStatus("Text-to-speech", true, "Uses Android TTS voices installed on the device; a female voice is not guaranteed."),
        BobbyCapabilityStatus("Persistent memory", true, "Local user-controlled memory store; credentials are filtered."),
        BobbyCapabilityStatus("Text file import", true, "TXT, Markdown, CSV, HTML, JSON, XML and common source files; 2 MB limit."),
        BobbyCapabilityStatus("PDF/image/audio/video analysis", false, "Requires dedicated parsers or a compatible multimodal provider; not yet connected."),
        BobbyCapabilityStatus("Web research and browser automation", false, "Not yet implemented."),
        BobbyCapabilityStatus("Trading", false, "No broker adapter or order execution is enabled; demo risk guard only."),
        BobbyCapabilityStatus("Plugins and offline local LLM", false, "Architecture planned; runtime manager/local model not yet implemented.")
    )
}
