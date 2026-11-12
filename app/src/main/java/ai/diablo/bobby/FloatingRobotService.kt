package ai.diablo.bobby

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Floating BOBBY companion. Uses the same locally saved full-body avatar look
 * as the main Avatar Studio; remains lightweight and compatible with Android 8+.
 */
class FloatingRobotService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleContainer: FrameLayout? = null
    private var avatarView: Bobby3DHumanView? = null
    private var pulse: ObjectAnimator? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(30, 5, 18), Color.rgb(5, 3, 8))).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(2), Color.rgb(255, 23, 68))
            }
            elevation = dp(12).toFloat()
        }

        // Render BOBBY's actual current avatar settings in the floating companion.
        val savedLook = BobbyAvatarStore(this).load()
        val avatar = Bobby3DHumanView(this, savedLook, false).apply {
            setZOrderOnTop(false)
            contentDescription = "BOBBY full-body companion. Tap to open voice assistant; drag to move."
        }
        val avatarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        avatarParams.setMargins(dp(5), dp(18), dp(5), dp(5))
        container.addView(avatar, avatarParams)

        val badge = TextView(this).apply {
            text = "BOBBY"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(180, 8, 45))
            }
            elevation = dp(10).toFloat()
        }
        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        badgeParams.setMargins(dp(8), dp(5), 0, 0)
        container.addView(badge, badgeParams)

        val close = TextView(this).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(210, 20, 55))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(16).toFloat()
            contentDescription = "Close and disable BOBBY floating bubble"
            setOnClickListener { stopSelf() }
        }
        val closeParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END)
        container.addView(close, closeParams)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val layout = WindowManager.LayoutParams(
            dp(178), dp(286), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(120) }

        val dragAndOpen = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x; startY = layout.y
                    downX = event.rawX; downY = event.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) moved = true
                    layout.x = startX + dx; layout.y = startY + dy
                    runCatching { windowManager.updateViewLayout(container, layout) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val launch = Intent(this@FloatingRobotService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(EXTRA_START_VOICE, true)
                        }
                        startActivity(launch)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        avatar.setOnTouchListener(dragAndOpen)
        badge.setOnTouchListener(dragAndOpen)
        container.setOnTouchListener(dragAndOpen)

        windowManager.addView(container, layout)
        bubbleContainer = container
        avatarView = avatar

        pulse = ObjectAnimator.ofPropertyValuesHolder(
            container,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.985f, 1.015f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.985f, 1.015f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.90f, 1f)
        ).apply {
            duration = 1500
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse?.start()
    }

    private var startX = 0
    private var startY = 0
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "ai.diablo.bobby.STOP_FLOATING_ROBOT"
        const val EXTRA_START_VOICE = "ai.diablo.bobby.EXTRA_START_VOICE"
    }

    override fun onDestroy() {
        pulse?.cancel()
        pulse = null
        avatarView?.onPause()
        bubbleContainer?.let { runCatching { windowManager.removeView(it) } }
        avatarView = null
        bubbleContainer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
