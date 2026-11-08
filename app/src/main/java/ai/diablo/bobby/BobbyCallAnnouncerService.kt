package ai.diablo.bobby
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Announces incoming-call notifications after the user enables Notification Access. */
class BobbyCallAnnouncerService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null; private var ready = false
    private var lastAnnouncement = ""; private var lastAt = 0L
    override fun onCreate() {
        super.onCreate(); tts = TextToSpeech(applicationContext, this)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("bobby_call_announcer", "BOBBY call announcements", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS; if (ready) tts?.language = Locale.getDefault() }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return; val extras: Bundle = n.extras ?: return
        val categoryCall = n.category == Notification.CATEGORY_CALL
        val callStyle = Build.VERSION.SDK_INT >= 31 && extras.containsKey(Notification.EXTRA_CALL_TYPE)
        val packageLooksLikePhone = listOf("dialer", "phone", "incall", "telecom").any { sbn.packageName.contains(it, true) }
        if (!categoryCall && !callStyle && !(packageLooksLikePhone && n.category == Notification.CATEGORY_SERVICE)) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val caller = when { title.isNotBlank() && !title.equals("incoming call", true) -> title; text.isNotBlank() -> text; sub.isNotBlank() -> sub; else -> "Someone" }.take(80)
        val now = System.currentTimeMillis()
        if (caller == lastAnnouncement && now - lastAt < 15000) return
        lastAnnouncement = caller; lastAt = now
        if (ready) tts?.speak("$caller is calling.", TextToSpeech.QUEUE_FLUSH, null, "bobby-incoming-call")
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); tts = null; super.onDestroy() }
}