package ai.diablo.bobby

import android.content.Context
import java.util.UUID

data class BobbyExecutionPreview(
    val id: String,
    val title: String,
    val detail: String,
    val approvalRequired: Boolean = true
)

data class BobbyExecutionResult(val success: Boolean, val message: String)

/**
 * Deterministic, approval-gated Android task execution.
 * Only explicit supported command forms are executable; model-generated prose
 * is never treated as an instruction or as user approval.
 */
class BobbyTaskExecutionEngine(context: Context) {
    private val appContext = context.applicationContext
    private val runtime = BobbyAgentRuntime(appContext)
    private val pending = LinkedHashMap<String, PendingAction>()

    private sealed class PendingAction {
        data class OpenApp(val name: String) : PendingAction()
        data class SetAlarm(val hour: Int, val minute: Int, val label: String) : PendingAction()
    }

    /**
     * Supported forms:
     *   open app <installed app name>
     *   set alarm HH:mm [label]
     * Preparing never launches anything. A returned ID must be explicitly
     * approved through executeApproved().
     */
    @Synchronized
    fun prepare(request: String): Result<BobbyExecutionPreview> {
        val input = request.trim()
        require(input.isNotEmpty()) { "Enter a task first." }

        val openMatch = Regex("(?i)^open app\\s+(.{1,80})$").matchEntire(input)
        val alarmMatch = Regex("(?i)^set alarm\\s+(\\d{1,2}):(\\d{2})(?:\\s+(.{1,80}))?$").matchEntire(input)

        val action = when {
            openMatch != null -> {
                val name = openMatch.groupValues[1].trim()
                require(name.isNotBlank()) { "Specify the installed app name." }
                PendingAction.OpenApp(name)
            }
            alarmMatch != null -> {
                val hour = alarmMatch.groupValues[1].toInt()
                val minute = alarmMatch.groupValues[2].toInt()
                require(hour in 0..23 && minute in 0..59) { "Use a valid 24-hour time, such as 07:30." }
                PendingAction.SetAlarm(hour, minute, alarmMatch.groupValues[3].ifBlank { "BOBBY alarm" })
            }
            else -> throw IllegalArgumentException(
                "Not executable yet. Use 'open app <name>' or 'set alarm HH:mm [label]'."
            )
        }

        val id = UUID.randomUUID().toString()
        pending[id] = action
        val preview = when (action) {
            is PendingAction.OpenApp -> BobbyExecutionPreview(
                id, "Open ${action.name}",
                "BOBBY will launch the matching installed app. No message or transaction will be sent."
            )
            is PendingAction.SetAlarm -> BobbyExecutionPreview(
                id, "Set alarm at %02d:%02d".format(action.hour, action.minute),
                "Android Clock will open with '${action.label}'. You must confirm/save the alarm in Clock."
            )
        }
        runtime.saveTask(input, "PENDING_APPROVAL", preview.detail)
        return Result.success(preview)
    }

    /** Pending approvals are memory-only and expire when the app process exits. */
    @Synchronized
    fun executeApproved(previewId: String): BobbyExecutionResult {
        val action = pending.remove(previewId)
            ?: return BobbyExecutionResult(false, "Approval expired or was not found. Prepare the task again.")
        return try {
            val message = when (action) {
                is PendingAction.OpenApp -> "Opened " + BobbyAndroidActions.openApp(appContext, action.name) + "."
                is PendingAction.SetAlarm -> {
                    BobbyAndroidActions.setAlarm(appContext, action.hour, action.minute, action.label)
                    "Android Clock opened with the alarm details. Confirm/save it in Clock to finish."
                }
            }
            runtime.saveTask(previewId, "HANDED_OFF", message)
            BobbyExecutionResult(true, message)
        } catch (error: Exception) {
            val message = error.message ?: "Android could not complete the action."
            runtime.saveTask(previewId, "FAILED", message)
            BobbyExecutionResult(false, message)
        }
    }

    @Synchronized
    fun reject(previewId: String): Boolean {
        if (pending.remove(previewId) == null) return false
        runtime.saveTask(previewId, "REJECTED", "User declined the proposed action.")
        return true
    }
}
