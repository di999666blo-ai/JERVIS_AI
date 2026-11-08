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

/** Announces incoming-call notifications in the phone's preferred language. */
class BobbyCallAnnouncerService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastAnnouncement = ""
    private var lastAt = 0L

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("bobby_call_announcer", "BOBBY call announcements", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val locale = Locale.getDefault()
            if (tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = locale
            }
        }
    }

    private fun localizedAnnouncement(caller: String, locale: Locale): String {
        return when (locale.language) {
            "yo" -> "$caller ń pè é."
            "ha" -> "$caller na kira."
            "ig" -> "$caller na-akpọ gị."
            "fr" -> "$caller vous appelle."
            "es" -> "$caller está llamando."
            "pt" -> "$caller está a ligar."
            "de" -> "$caller ruft an."
            "it" -> "$caller ti sta chiamando."
            "ar" -> "$caller يتصل بك."
            "hi" -> "$caller आपको कॉल कर रहे हैं."
            "zh" -> "$caller 正在打电话给你。"
            "ja" -> "$caller から電話がかかっています。"
            "ko" -> "$caller 님에게서 전화가 오고 있습니다."
            "ru" -> "$caller звонит вам."
            else -> "$caller is calling."
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras: Bundle = n.extras ?: return
        val categoryCall = n.category == Notification.CATEGORY_CALL
        val callStyle = Build.VERSION.SDK_INT >= 31 && extras.containsKey(Notification.EXTRA_CALL_TYPE)
        val packageLooksLikePhone = listOf("dialer", "phone", "incall", "telecom").any { sbn.packageName.contains(it, true) }
        if (!categoryCall && !callStyle && !(packageLooksLikePhone && n.category == Notification.CATEGORY_SERVICE)) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val caller = when {
            title.isNotBlank() && !title.equals("incoming call", true) -> title
            text.isNotBlank() -> text
            sub.isNotBlank() -> sub
            else -> "Someone"
        }.take(80)

        val now = System.currentTimeMillis()
        if (caller == lastAnnouncement && now - lastAt < 15000) return
        lastAnnouncement = caller
        lastAt = now

        if (ready) {
            val locale = Locale.getDefault()
            val supported = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (supported >= TextToSpeech.LANG_AVAILABLE) tts?.language = locale
            tts?.speak(localizedAnnouncement(caller, locale), TextToSpeech.QUEUE_FLUSH, null, "bobby-incoming-call")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
