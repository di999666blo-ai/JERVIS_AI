package ai.diablo.bobby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent

/** Foreground media-button listener. It never records audio while idle. */
class BobbyWakeService : Service() {
    private var mediaSession: MediaSession? = null
    private var lastWakeAt = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        mediaSession = MediaSession(this, "BOBBY-Wake").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        event.keyCode in setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY)) {
                        wakeBobby("headset")
                    }
                    return true
                }
            })
            setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
            isActive = true
        }
    }

    private fun wakeBobby(source: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWakeAt < 1400L) return
        lastWakeAt = now
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(FloatingRobotService.EXTRA_START_VOICE, true)
            putExtra(EXTRA_WAKE_SOURCE, source)
        }
        runCatching { startActivity(launch) }
    }

    private fun buildNotification(): Notification {
        val channelId = "bobby_wake_listener"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "BOBBY wake listener", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shows when BOBBY's button wake listener is active." })
        }
        val openIntent = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 11,
            Intent(this, BobbyWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId)
            else Notification.Builder(this)
        return builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BOBBY wake listener is on")
            .setContentText("Press your headset button to wake BOBBY. No idle audio recording.")
            .setContentIntent(openIntent).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "STOP", stopIntent).build()).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.run { isActive = false; release() }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "ai.diablo.bobby.STOP_WAKE_LISTENER"
        const val EXTRA_WAKE_SOURCE = "ai.diablo.bobby.WAKE_SOURCE"
        const val PREFS = "bobby_wake_preferences"
        const val KEY_ENABLED = "wake_listener_enabled"
        const val NOTIFICATION_ID = 7412
    }
}
