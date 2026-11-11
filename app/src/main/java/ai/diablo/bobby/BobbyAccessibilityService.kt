package ai.diablo.bobby

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Opt-in Android accessibility bridge.
 *
 * It only operates on the currently visible UI after the user enables this service
 * in Android Settings. It does not hide automation, defeat security prompts, or
 * access protected/system-secure content.
 */
class BobbyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Keep this service event-driven and privacy-minimal: do not log screen text.
        BobbyAccessibilityController.lastPackage = event?.packageName?.toString()
    }

    private var lastVolumeDownAt = 0L
    private var volumeDownCount = 0

    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode != android.view.KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = android.os.SystemClock.elapsedRealtime()
            volumeDownCount = if (now - lastVolumeDownAt <= 1500L) volumeDownCount + 1 else 1
            lastVolumeDownAt = now
            if (volumeDownCount >= 3) {
                volumeDownCount = 0
                val launch = android.content.Intent(this, MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(FloatingRobotService.EXTRA_START_VOICE, true)
                    putExtra(BobbyWakeService.EXTRA_WAKE_SOURCE, "volume")
                }
                runCatching { startActivity(launch) }
                return true
            }
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        BobbyAccessibilityController.attach(this)
    }

    override fun onDestroy() {
        BobbyAccessibilityController.detach(this)
        super.onDestroy()
    }

    internal fun clickVisibleText(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            nodes.firstOrNull { it.isVisibleToUser && it.isEnabled && clickNodeOrParent(it) } != null
        } finally {
            root.recycle()
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(6) {
            val candidate = current ?: return false
            if (candidate.isClickable && candidate.isEnabled) {
                val success = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                candidate.recycle()
                return success
            }
            val parent = candidate.parent
            candidate.recycle()
            current = parent
        }
        current?.recycle()
        return false
    }

    internal fun enterText(fieldHint: String, value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val nodes = root.findAccessibilityNodeInfosByText(fieldHint)
            val field = nodes.firstOrNull {
                it.isVisibleToUser && it.isEnabled &&
                    (it.isEditable || it.className?.toString()?.contains("EditText", true) == true)
            } ?: return false
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycle()
        }
    }

    internal fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return ""
        return try {
            val collected = ArrayList<String>()
            fun visit(node: AccessibilityNodeInfo) {
                if (!node.isVisibleToUser) return
                val text = node.text?.toString()?.trim().orEmpty()
                val description = node.contentDescription?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) collected.add(text)
                else if (description.isNotBlank()) collected.add(description)
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child)
                    child.recycle()
                    if (collected.size >= 120) break
                }
            }
            visit(root)
            collected.distinct().take(120).joinToString(". ").take(6000)
        } finally {
            root.recycle()
        }
    }

    internal fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val direction = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            fun visit(node: AccessibilityNodeInfo): Boolean {
                if (node.isVisibleToUser && node.isScrollable && node.performAction(direction)) return true
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val done = visit(child)
                    child.recycle()
                    if (done) return true
                }
                return false
            }
            visit(root)
        } finally {
            root.recycle()
        }
    }

    internal fun tap(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    internal fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        if (listOf(x1, y1, x2, y2).any { it < 0f }) return false
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100, 1500))
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        const val ACTION_BACK = "back"
        const val ACTION_HOME = "home"
    }
}

/** Narrow controller exposed to BOBBY's command/action layer. */
object BobbyAccessibilityController {
    @Volatile private var service: BobbyAccessibilityService? = null
    @Volatile var lastPackage: String? = null
        internal set

    internal fun attach(value: BobbyAccessibilityService) { service = value }
    internal fun detach(value: BobbyAccessibilityService) {
        if (service === value) service = null
    }

    fun isEnabledAndConnected(): Boolean = service != null
    fun clickText(label: String): Boolean =
        label.isNotBlank() && service?.clickVisibleText(label) == true
    fun enterText(fieldHint: String, value: String): Boolean =
        fieldHint.isNotBlank() && service?.enterText(fieldHint, value) == true
    fun readVisibleText(): String = service?.readVisibleText().orEmpty()
    fun scrollDown(): Boolean = service?.scroll(true) == true
    fun scrollUp(): Boolean = service?.scroll(false) == true
    fun tap(x: Float, y: Float): Boolean = service?.tap(x, y) == true
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean =
        service?.swipe(x1, y1, x2, y2, durationMs) == true
    fun goBack(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
    fun goHome(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
}
