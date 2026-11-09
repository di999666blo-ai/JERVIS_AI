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
    fun scrollDown(): Boolean = service?.scroll(true) == true
    fun scrollUp(): Boolean = service?.scroll(false) == true
    fun tap(x: Float, y: Float): Boolean = service?.tap(x, y) == true
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean =
        service?.swipe(x1, y1, x2, y2, durationMs) == true
    fun goBack(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
    fun goHome(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
}
