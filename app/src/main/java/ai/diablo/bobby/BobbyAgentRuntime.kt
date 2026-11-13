package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device orchestration foundation for BOBBY.
 * This is an ECC-inspired runtime, not the Codex desktop plugin or a bundled Codex CLI.
 * It only advertises tools that are explicitly registered here.
 */
data class BobbyAgentTool(
    val id: String,
    val label: String,
    val description: String,
    val requiresConfirmation: Boolean = true,
    val available: Boolean = true
)

data class BobbyAgentTask(
    val id: String,
    val request: String,
    val status: String,
    val result: String,
    val updatedAt: Long
)

data class BobbyAgentProfile(
    val id: String,
    val name: String,
    val purpose: String,
    val focus: String,
    val safetyRules: String
)

/**
 * On-device specialist definitions. These are prompt profiles for BOBBY's
 * configured AI provider; they do not run independent models or grant tools.
 */
private val bobbySpecialistProfiles = listOf(
    BobbyAgentProfile(
        "general", "BOBBY General Assistant",
        "Handles everyday questions, explanations and practical help.",
        "Answer the user's actual question directly. Be clear, useful and concise; separate facts, assumptions and uncertainty.",
        "Never invent sources, actions, device access or completed work."
    ),
    BobbyAgentProfile(
        "planner", "BOBBY Planner",
        "Breaks multi-step goals into practical, verifiable tasks.",
        "Define the goal, sequence, dependencies, required inputs, acceptance checks and the smallest next step.",
        "A plan is not execution. Do not say a task is done until a connected tool actually confirms it."
    ),
    BobbyAgentProfile(
        "research", "BOBBY Research",
        "Evaluates evidence, sources and current information.",
        "Prefer primary sources, compare dates, distinguish confirmed facts from interpretation and list unresolved gaps.",
        "Do not fabricate browsing, citations, statistics or current facts when no live source is connected."
    ),
    BobbyAgentProfile(
        "android", "Android Specialist",
        "Helps with Android, Kotlin, Jetpack Compose, permissions and phone constraints.",
        "Respect the repository's actual SDK and Gradle versions, lifecycle, coroutine cancellation, accessibility and low-memory devices.",
        "Do not bypass Android permissions or claim device testing from compilation alone."
    ),
    BobbyAgentProfile(
        "coding", "Coding Specialist",
        "Plans and reviews code changes against the existing project.",
        "Inspect existing conventions, propose minimal coherent changes, explain affected files and identify regression risks.",
        "Do not invent repository access, commits, tests or build results. Preserve existing user data and app structure."
    ),
    BobbyAgentProfile(
        "security", "Security Specialist",
        "Reviews privacy, credentials, permissions, imported data and external actions.",
        "Apply least privilege, secure storage, input validation, consent and clear data-transfer disclosure.",
        "Never expose secrets, silently transmit private files or bypass user approval."
    ),
    BobbyAgentProfile(
        "qa", "QA and Verification Specialist",
        "Creates acceptance checks and assesses whether a feature is genuinely verified.",
        "Separate source implementation, unit tests, build/CI, artifact inspection and physical-device testing.",
        "Never infer runtime success from a successful compile; state tests not run and known limitations."
    ),
    BobbyAgentProfile(
        "creative", "Creative Studio Specialist",
        "Helps plan image, video, audio and 3D creative tasks.",
        "Clarify the required output format, asset dimensions, model/provider needs, quality checks and phone storage limits.",
        "Do not claim media was generated or edited unless a generation tool returns a real asset."
    ),
    BobbyAgentProfile(
        "trading-risk", "Trading Risk Specialist",
        "Provides read-only market, portfolio and risk analysis.",
        "Check instrument specifications, data freshness, exposure, stake assumptions, stop distance and user-defined risk limits.",
        "Never promise profits or place trades. Clearly distinguish demo from live and analysis from execution."
    )
)

class BobbyAgentRuntime(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_agent_runtime", Context.MODE_PRIVATE)

    val specialistAgents: List<BobbyAgentProfile> = bobbySpecialistProfiles

    fun specialist(id: String): BobbyAgentProfile? =
        specialistAgents.firstOrNull { it.id == id }

    /**
     * Lightweight routing hint only. The selected profile still needs to be
     * passed to the configured AI request; this method does not execute tools.
     */
    fun routeSpecialist(request: String): BobbyAgentProfile {
        val text = request.lowercase()
        val selected = when {
            listOf("deriv", "trade", "trading", "portfolio", "stake", "risk", "market", "chart").any { keyword -> text.contains(keyword) } -> "trading-risk"
            listOf("android", "kotlin", "compose", "apk", "permission", "gradle", "phone").any { keyword -> text.contains(keyword) } -> "android"
            listOf("security", "secret", "api key", "privacy", "permission", "token").any { keyword -> text.contains(keyword) } -> "security"
            listOf("test", "verify", "bug", "crash", "build failed", "qa").any { keyword -> text.contains(keyword) } -> "qa"
            listOf("image", "video", "audio", "music", "3d", "design", "creative").any { keyword -> text.contains(keyword) } -> "creative"
            listOf("code", "coding", "github", "repository", "function", "implement").any { keyword -> text.contains(keyword) } -> "coding"
            listOf("research", "latest", "source", "compare", "evidence", "news").any { keyword -> text.contains(keyword) } -> "research"
            listOf("plan", "steps", "workflow", "organise", "organize").any { keyword -> text.contains(keyword) } -> "planner"
            else -> "general"
        }
        return specialist(selected) ?: bobbySpecialistProfiles.first()
    }

    fun specialistInstruction(agentId: String, request: String): String {
        val agent = specialist(agentId) ?: bobbySpecialistProfiles.first()
        return """
You are ${agent.name}, a specialist inside BOBBY's on-device agent system.
Purpose: ${agent.purpose}
User request: ${request.take(4000)}

Specialist focus:
${agent.focus}

Mandatory safety and truth rules:
${agent.safetyRules}

You are an assistant profile, not an independent model or unrestricted executor. Use only tools explicitly registered and available in BOBBY. If a required tool is unavailable, say so and give the user the precise next step. Do not claim actions, research, tests or outcomes that have not actually happened. Return a direct, structured, concise answer.
        """.trimIndent()
    }

    val tools = listOf(
        BobbyAgentTool("android_open_app", "Open installed app", "Launch an app selected by name through Android."),
        BobbyAgentTool("android_set_alarm", "Set alarm", "Open the Android Clock confirmation screen."),
        BobbyAgentTool("android_compose_email", "Compose email", "Open the user's email app with a draft; never send silently."),
        BobbyAgentTool("android_compose_sms", "Compose message", "Open the user's messaging app with a draft; never send silently."),
        BobbyAgentTool("memory_read", "Read saved memory", "Read user-approved local BOBBY memories.", false),
        BobbyAgentTool("memory_write", "Save memory", "Save a memory only after the user confirms.", true),
        BobbyAgentTool("public_api_catalog", "Search public API directory", "Catalogue UI is implemented; planner-to-catalogue execution is not yet wired.", false, false),
        BobbyAgentTool("web_search", "Web research", "Uses a configured search provider; requires provider credentials.", false, false),
        BobbyAgentTool("dexter_financial_research", "Dexter financial research", "Runs multi-pass financial source gathering and AI analysis through BOBBY; requires a connected search provider and AI model.", false, true),
        BobbyAgentTool("trader_skill_pack", "Trader Skill Pack", "Runs one of 11 AI-guided trading research, portfolio, budgeting and risk workflows. Analysis only; no broker execution.", false, true),
        BobbyAgentTool("deriv_public_market_data", "Deriv live market data", "Read-only Deriv active market catalogue, historical one-minute candles and live tick subscription.", false, true),
        BobbyAgentTool("chart_analysis_agent", "Chart analysis agent", "Analyses observed price history using EMA proxies and momentum; not a probability or trade instruction.", false, true),
        BobbyAgentTool("risk_analysis_agent", "Risk analysis agent", "Summarises observed price steps and volatility without placing orders or assuming stake size.", false, true),
        BobbyAgentTool("github", "GitHub operations", "Requires a connected GitHub token and per-action confirmation.", true, false),
        BobbyAgentTool("plugin_adapter", "Connected plugins", "Only real, connected adapters may execute; catalogue entries are not tools.", true, false),
        BobbyAgentTool("apk_update", "Update BOBBY", "Checks the official GitHub release, verifies the APK digest when available, and hands installation to Android for user approval.", true, true)
    )

    fun saveTask(request: String, status: String, result: String): BobbyAgentTask {
        require(request.isNotBlank()) { "Task request is empty." }
        val task = BobbyAgentTask(
            id = java.util.UUID.randomUUID().toString(),
            request = request.take(4000),
            status = status.take(40),
            result = result.take(12000),
            updatedAt = System.currentTimeMillis()
        )
        val rows = JSONArray(prefs.getString("tasks", "[]"))
        rows.put(JSONObject()
            .put("id", task.id)
            .put("request", task.request)
            .put("status", task.status)
            .put("result", task.result)
            .put("updatedAt", task.updatedAt))
        while (rows.length() > 40) rows.remove(0)
        prefs.edit().putString("tasks", rows.toString()).apply()
        return task
    }

    fun recentTasks(): List<BobbyAgentTask> {
        val rows = JSONArray(prefs.getString("tasks", "[]"))
        return (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { row ->
                BobbyAgentTask(
                    row.optString("id"), row.optString("request"), row.optString("status"),
                    row.optString("result"), row.optLong("updatedAt")
                )
            }
        }.reversed()
    }

    fun clearHistory() { prefs.edit().remove("tasks").apply() }

    fun plannerInstruction(request: String, mode: BobbyMode): String = """
You are BOBBY's task planner operating under an ECC-inspired, permission-gated mobile agent runtime.
Operating mode: $mode
User request: $request

Return:
1. Goal in one sentence.
2. A numbered, practical plan with small steps.
3. Which registered tools would be needed, using only these IDs: ${tools.joinToString { it.id }}. Suggested specialist: ${routeSpecialist(request).name}. Available specialist profiles: ${specialistAgents.joinToString { it.name }}.
4. Mark each action READ-ONLY, REVERSIBLE, or CONSEQUENCE/EXTERNAL.
5. List exact user approvals or Android permissions needed before execution.
6. State which steps cannot currently be executed because a connector/runtime is unavailable.
7. A verification checklist and honest completion criteria.

Important: Planning is not execution. Do not claim any step has been completed. Do not invent connected plugins, credentials, files, web results, GitHub commits, builds, or device permissions. Do not propose bypassing Android permissions or sending messages, publishing, deleting remote data, making purchases, or trading without explicit confirmation. Keep the response concise and actionable.
""".trimIndent()
}
