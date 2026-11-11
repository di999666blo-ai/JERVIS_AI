package ai.diablo.bobby

import android.speech.tts.TextToSpeech
import java.util.Locale

/** Language selection and safe-length chunking for Android's on-device speech engine. */
object BobbySpeechLanguage {
    fun detectLanguageTag(text: String): String {
        val s = text.lowercase(Locale.ROOT)
        return when {
            s.any { it in '\u0600'..'\u06ff' } -> "ar"
            s.any { it in '\u0900'..'\u097f' } -> "hi"
            s.any { it in '\u3040'..'\u30ff' } -> "ja"
            s.any { it in '\uac00'..'\ud7af' } -> "ko"
            s.any { it in '\u4e00'..'\u9fff' } -> "zh"
            s.any { it in '\u0400'..'\u04ff' } -> "ru"
            s.contains("ẹ") || s.contains("ọ") || s.contains("ụ") ||
                listOf(" bawo", " e ka", " mo dup", " o ṣe", " kò ", " jẹ ").any { " $s ".contains(it) } -> "yo-NG"
            listOf(" sannu", " yaya", " lafiya", " na gode", " ina kwana").any { s.contains(it) } -> "ha-NG"
            listOf(" kedu", " daalụ", " imela", " ọ dị", "anyị").any { s.contains(it) } -> "ig-NG"
            listOf(" hola", " gracias", " buenos días", " buenas noches", " por favor").any { s.contains(it) } -> "es"
            listOf(" bonjour", " merci", " je suis", " s'il vous plaît", " bonne nuit").any { s.contains(it) } -> "fr"
            listOf(" obrigado", " obrigada", " bom dia", " boa noite", " você").any { s.contains(it) } -> "pt"
            listOf(" guten tag", " danke", " ich bin", " bitte", " guten morgen").any { s.contains(it) } -> "de"
            listOf(" buongiorno", " grazie", " per favore", " come stai").any { s.contains(it) } -> "it"
            else -> "en"
        }
    }

    fun configureLocalTts(tts: TextToSpeech, text: String): Boolean {
        val locale = Locale.forLanguageTag(detectLanguageTag(text))
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        tts.language = locale
        val matching = tts.voices?.filter { it.locale.language == locale.language }
            ?.sortedBy { if (it.isNetworkConnectionRequired) 1 else 0 }?.firstOrNull()
        if (matching != null) tts.voice = matching
        return true
    }

    fun speakLocal(tts: TextToSpeech, text: String) {
        if (text.isBlank()) return
        if (!configureLocalTts(tts, text)) {
            val fallback = Locale.getDefault()
            if (tts.isLanguageAvailable(fallback) >= TextToSpeech.LANG_AVAILABLE) tts.language = fallback
        }
        val max = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(100)
        val chunks = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= max) { chunks += remaining; break }
            var cut = remaining.lastIndexOf(' ', max)
            if (cut < max / 2) cut = max
            chunks += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trimStart()
        }
        chunks.forEachIndexed { index, chunk ->
            tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "bobby-reply-$index")
        }
    }
}
