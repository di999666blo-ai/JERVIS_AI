package ai.diablo.bobby

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private val Void = Color(0xFF050505)
private val Neon = Color(0xFFFF1744)
private val Panel = Color(0xFF151015)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { BobbyHome(
            onSpeak = { text, tag ->
                if (ttsReady) {
                    val locale = when (tag) {
                        "Pidgin (Nigeria)" -> Locale("en", "NG")
                        else -> Locale.forLanguageTag(tag)
                    }
                    val result = tts?.setLanguage(locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bobby-reply")
                }
            }
        ) }
    }

    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun BobbyHome(onSpeak: (String, String) -> Unit) {
    var message by remember { mutableStateOf("I dey here. Talk to me.") }
    var status by remember { mutableStateOf("READY") }
    var language by remember { mutableStateOf("Pidgin (Nigeria)") }
    var expanded by remember { mutableStateOf(false) }
    var bubbleOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val pulse = rememberInfiniteTransition(label = "bobby-pulse")
    val breathing by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing"
    )
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            message = spoken
            status = "HEARD YOU"
        } else status = "READY"
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Void, surface = Panel, primary = Neon,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Void) {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF210811), Void, Void))).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BOBBY", style = MaterialTheme.typography.headlineLarge, color = Neon)
                Text("YOUR VOICE-FIRST AI COMPANION", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(42.dp))
                Box(
                    Modifier.offset { IntOffset(bubbleOffset.x.roundToInt(), bubbleOffset.y.roundToInt()) }
                        .size(190.dp).scale(breathing)
                        .background(Brush.radialGradient(listOf(Color(0xFF8D1236), Color(0xFF250914), Void)), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                bubbleOffset += drag
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("◉", color = Neon, style = MaterialTheme.typography.displayLarge)
                }
                Spacer(Modifier.height(16.dp))
                Text(status, color = Neon, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(22.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Text(message, Modifier.padding(18.dp), color = Color.White)
                }
                Spacer(Modifier.height(18.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(language) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Pidgin (Nigeria)", "English (US)", "English (UK)", "Yoruba", "Igbo", "Hausa", "French", "Spanish", "Arabic", "Hindi", "Chinese").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { language = it; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        status = "LISTENING"
                        val localeTag = when (language) {
                            "Pidgin (Nigeria)" -> "en-NG"
                            "English (US)" -> "en-US"
                            "English (UK)" -> "en-GB"
                            "Yoruba" -> "yo-NG"
                            "Igbo" -> "ig-NG"
                            "Hausa" -> "ha-NG"
                            "French" -> "fr-FR"
                            "Spanish" -> "es-ES"
                            "Arabic" -> "ar"
                            "Hindi" -> "hi-IN"
                            "Chinese" -> "zh-CN"
                            else -> "en-US"
                        }
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Bobby")
                        }
                        runCatching { recognizer.launch(intent) }.onFailure {
                            status = "VOICE INPUT UNAVAILABLE"
                            message = "Speech recognition app/service is not available on this device."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Neon),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🎙  TALK TO BOBBY") }
                OutlinedButton(
                    onClick = { onSpeak(message, if (language == "Pidgin (Nigeria)") "Pidgin (Nigeria)" else when(language) {
                        "English (US)" -> "en-US"; "English (UK)" -> "en-GB"; "French" -> "fr-FR"; "Spanish" -> "es-ES"
                        "Arabic" -> "ar"; "Hindi" -> "hi-IN"; "Chinese" -> "zh-CN"; else -> "en"
                    }) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🔊 SPEAK") }
                Spacer(Modifier.weight(1f))
                Text("Wake phrase and floating overlay are planned next; they are not active in this first scaffold.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
