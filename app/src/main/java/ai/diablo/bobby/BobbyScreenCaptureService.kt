package ai.diablo.bobby
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class BobbyScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var finished = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CAPTURE) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "BOBBY screen capture", NotificationManager.IMPORTANCE_LOW))
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL).setContentTitle("BOBBY screen capture").setContentText("Capturing the screen you authorised…").setSmallIcon(android.R.drawable.ic_menu_view).build()
        else Notification.Builder(this).setContentTitle("BOBBY screen capture").setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_DATA) as? Intent)
        if (data == null || resultCode == 0) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        val media = projection ?: run { stopSelf(); return START_NOT_STICKY }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1); val height = metrics.heightPixels.coerceAtLeast(1)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        media.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopCapture() } }, android.os.Handler(mainLooper))
        reader?.setOnImageAvailableListener({ source ->
            if (finished) return@setOnImageAvailableListener
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]; val pixelStride = plane.pixelStride; val rowPadding = plane.rowStride - pixelStride * width
                val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height); padded.recycle()
                val file = File(cacheDir, "bobby-screen-" + System.currentTimeMillis() + ".jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }; bitmap.recycle()
                finished = true
                sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName).putExtra(EXTRA_PATH, file.absolutePath))
                stopCapture()
            } catch (_: Exception) { sendBroadcast(Intent(ACTION_FAILED).setPackage(packageName)); stopCapture() }
            finally { image.close() }
        }, android.os.Handler(mainLooper))
        display = media.createVirtualDisplay("BOBBY-screen-capture", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        return START_NOT_STICKY
    }
    private fun stopCapture() {
        runCatching { display?.release() }; display = null
        runCatching { reader?.setOnImageAvailableListener(null, null); reader?.close() }; reader = null
        runCatching { projection?.stop() }; projection = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { stopCapture(); super.onDestroy() }
    companion object {
        const val ACTION_CAPTURE = "ai.diablo.bobby.CAPTURE_SCREEN"
        const val ACTION_CAPTURED = "ai.diablo.bobby.SCREEN_CAPTURED"
        const val ACTION_FAILED = "ai.diablo.bobby.SCREEN_CAPTURE_FAILED"
        const val EXTRA_RESULT_CODE = "result_code"; const val EXTRA_DATA = "projection_data"; const val EXTRA_PATH = "screenshot_path"
        private const val CHANNEL = "bobby_screen_capture"; private const val NOTIFICATION_ID = 7812
    }
}