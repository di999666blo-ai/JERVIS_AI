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

class BobbyAgentRuntime(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_agent_runtime", Context.MODE_PRIVATE)

    val tools = listOf(
        BobbyAgentTool("android_open_app", "Open installed app", "Launch an app selected by name through Android."),
        BobbyAgentTool("android_set_alarm", "Set alarm", "Open the Android Clock confirmation screen."),
        BobbyAgentTool("android_compose_email", "Compose email", "Open the user's email app with a draft; never send silently."),
        BobbyAgentTool("android_compose_sms", "Compose message", "Open the user's messaging app with a draft; never send silently."),
        BobbyAgentTool("memory_read", "Read saved memory", "Read user-approved local BOBBY memories.", false),
        BobbyAgentTool("memory_write", "Save memory", "Save a memory only after the user confirms.", true),
        BobbyAgentTool("public_api_catalog", "Search public API directory", "Catalogue UI is implemented; planner-to-catalogue execution is not yet wired.", false, false),
        BobbyAgentTool("web_search", "Web research", "Uses a configured search provider; requires provider credentials.", false, false),
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
3. Which registered tools would be needed, using only these IDs: ${tools.joinToString { it.id }}.
4. Mark each action READ-ONLY, REVERSIBLE, or CONSEQUENCE/EXTERNAL.
5. List exact user approvals or Android permissions needed before execution.
6. State which steps cannot currently be executed because a connector/runtime is unavailable.
7. A verification checklist and honest completion criteria.

Important: Planning is not execution. Do not claim any step has been completed. Do not invent connected plugins, credentials, files, web results, GitHub commits, builds, or device permissions. Do not propose bypassing Android permissions or sending messages, publishing, deleting remote data, making purchases, or trading without explicit confirmation. Keep the response concise and actionable.
""".trimIndent()
}
