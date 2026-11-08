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
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator

class FloatingRobotService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "☺"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 23, 68))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(116, 10, 48), Color.rgb(15, 5, 10))).apply {
                shape = GradientDrawable.OVAL
                setStroke(3, Color.rgb(255, 23, 68))
            }
            elevation = 14f
            contentDescription = "Bobby floating assistant. Tap to open, drag to move."
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val layout = WindowManager.LayoutParams(
            68, 68, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }
        view.setOnTouchListener(object : View.OnTouchListener {
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
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) moved = true
                        layout.x = startX + dx; layout.y = startY + dy
                        windowManager.updateViewLayout(view, layout)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val launch = packageManager.getLaunchIntentForPackage(packageName)
                            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            if (launch != null) startActivity(launch)
                        }
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(view, layout)
        bubble = view
        params = layout
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.07f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.07f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.78f, 1f)
        ).apply {
            duration = 1250
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

    companion object { const val ACTION_STOP = "ai.diablo.bobby.STOP_FLOATING_ROBOT" }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
