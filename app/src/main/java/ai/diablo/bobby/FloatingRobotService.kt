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
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator

class FloatingRobotService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleContainer: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = FrameLayout(this)
        val robot = ImageView(this).apply {
            setImageResource(R.drawable.bobby_robot_face)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(116, 10, 48), Color.rgb(15, 5, 10))).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), Color.rgb(255, 23, 68))
            }
            elevation = dp(8).toFloat()
            contentDescription = "BOBBY assistant. Tap to talk or drag to move."
        }
        val robotSize = dp(88)
        val robotParams = FrameLayout.LayoutParams(robotSize, robotSize, Gravity.BOTTOM or Gravity.START)
        container.addView(robot, robotParams)

        // A separate, clearly visible close control disables the floating assistant.
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
            elevation = dp(12).toFloat()
            contentDescription = "Close and disable BOBBY floating bubble"
            setOnClickListener { stopSelf() }
        }
        val closeParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END)
        container.addView(close, closeParams)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val layout = WindowManager.LayoutParams(
            dp(104), dp(104), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(18); y = dp(160) }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f
            private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = layout.x; startY = layout.y
                        downX = event.rawX; downY = event.rawY; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) moved = true
                        layout.x = startX + dx; layout.y = startY + dy
                        runCatching { windowManager.updateViewLayout(container, layout) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val launch = Intent(this@FloatingRobotService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(EXTRA_START_VOICE, true)
                            }
                            startActivity(launch)
                        }
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(container, layout)
        bubbleContainer = container

        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            robot,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1.045f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1.045f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.84f, 1f)
        ).apply {
            duration = 1350
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "ai.diablo.bobby.STOP_FLOATING_ROBOT"
        const val EXTRA_START_VOICE = "ai.diablo.bobby.EXTRA_START_VOICE"
    }

    override fun onDestroy() {
        bubbleContainer?.let { runCatching { windowManager.removeView(it) } }
        bubbleContainer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
