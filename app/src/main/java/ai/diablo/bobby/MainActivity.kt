package ai.diablo.bobby

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private val Void = Color(0xFF050505)
private val Neon = Color(0xFFFF1744)
private val Panel = Color(0xFF171017)
data class ChatLine(val fromBobby: Boolean, val text: String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var selectedVoice: Voice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            val configStore = BobbyConfigStore(this)
            BobbyHome(
                onSpeak = { text -> if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bobby-reply") },
                onInstallVoice = { runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } },
                onToggleOverlay = { toggleFloatingRobot() },
                initialEndpoint = configStore.endpoint(),
                initialModel = configStore.model(),
                hasSavedKey = configStore.apiKey().isNotBlank(),
                onSaveConfig = { endpoint, model, key -> configStore.save(endpoint, model, key) },
                onAskAi = { history, done ->
                    BobbyAiClient.reply(AiSettings(configStore.endpoint(), configStore.model(), configStore.apiKey()), history) { result ->
                        runOnUiThread { done(result) }
                    }
                }
            )
        }
    }

    private var floatingRobotRunning = false

    private fun toggleFloatingRobot() {
        if (!Settings.canDrawOverlays(this)) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(permissionIntent)
            return
        }

        if (floatingRobotRunning) {
            stopService(Intent(this, FloatingRobotService::class.java))
            floatingRobotRunning = false
        } else {
            startService(Intent(this, FloatingRobotService::class.java))
            floatingRobotRunning = true
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val engine = tts ?: return
            engine.language = Locale.UK
            selectedVoice = engine.voices?.filter { it.locale.language == "en" }?.sortedBy {
                val name = it.name.lowercase(Locale.ROOT)
                when { "female" in name || "woman" in name -> 0; "en-gb" in name || "gb" in name -> 1; else -> 2 }
            }?.firstOrNull()
            selectedVoice?.let { engine.voice = it }
            engine.setSpeechRate(0.94f)
            engine.setPitch(1.08f)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun BobbyHome(
    onSpeak: (String) -> Unit,
    onInstallVoice: () -> Unit,
    onToggleOverlay: () -> Unit,
    initialEndpoint: String,
    initialModel: String,
    hasSavedKey: Boolean,
    onSaveConfig: (String, String, String) -> Unit,
    onAskAi: (List<ChatLine>, (Result<String>) -> Unit) -> Unit
) {
    val appContext = LocalContext.current
    val memoryStore = remember { BobbyMemoryStore(appContext) }
    val profileStore = remember { BobbyProfileStore(appContext) }
    var profile by remember { mutableStateOf(profileStore.load()) }
    var onboardingOpen by remember { mutableStateOf(profile == null) }
    var weatherText by remember { mutableStateOf("Add your city in your profile to check today’s weather.") }
    var weatherLoading by remember { mutableStateOf(false) }
    var memoryOpen by remember { mutableStateOf(false) }
    var memoryTitle by remember { mutableStateOf("") }
    var memoryContent by remember { mutableStateOf("") }
    var memoryItems by remember { mutableStateOf(memoryStore.list()) }
    var draft by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("IDLE") }
    var settingsOpen by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var model by remember { mutableStateOf(initialModel) }
    var apiKey by remember { mutableStateOf("") }
    var aiEnabled by remember { mutableStateOf(hasSavedKey) }
    var bubbleOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val chat = remember { mutableStateListOf(ChatLine(true, profile?.let { "Hey " + it.name + "! 😊 I’m Bobby, your buddy. I’ve got your back. What’s the plan today?" } ?: "Hey, I’m Bobby. Let’s get acquainted! 😊")) }
    LaunchedEffect(profile) { profile?.reminders()?.forEach { reminder -> if (chat.none { it.text == reminder }) chat.add(0, ChatLine(true, reminder)) } }
    val pulse = rememberInfiniteTransition(label = "bobby-avatar")
    val breathing by pulse.animateFloat(0.96f, 1.045f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathing")
    val listening by pulse.animateFloat(0.92f, 1.12f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "listening")

    fun answerFor(input: String): String {
        val q = input.trim().lowercase(Locale.ROOT)
        return when {
            q.isBlank() -> "I’m listening. Say something whenever you’re ready."
            listOf("hello", "hi", "hey", "good morning", "good evening", "how are you").any { q.contains(it) } ->
                "Hey! I’m doing well, and I’m glad you’re here. How’s your day going?"
            q.contains("who are you") || q.contains("your name") ->
                "I’m Bobby, your personal assistant. I’m still being built, but we can already chat, use voice input, and have me speak replies."
            q.contains("thank") -> "You’re very welcome. I’ve got you."
            q.contains("sad") || q.contains("stressed") || q.contains("tired") ->
                "I’m sorry it’s been heavy. Want to tell me what happened? I’ll listen without judging."
            q.contains("joke") -> "Why did the phone need glasses? It kept losing its contacts. I know, terrible one."
            q.contains("time") -> "I can’t read your phone’s live clock in this first chat build yet. I don’t want to guess."
            else -> "I hear you. I can keep the conversation going, but my full online AI brain is not connected yet, so I can’t claim this is deep AI understanding. Tell me a little more and I’ll do my best with what’s available."
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        chat.add(ChatLine(false, clean))
        draft = ""
        if (!aiEnabled) {
            val reply = answerFor(clean)
            chat.add(ChatLine(true, reply))
            state = "SPEAKING"
            onSpeak(reply)
            state = "IDLE"
        } else {
            state = "THINKING"
            onAskAi(chat.toList()) { result ->
                val reply = result.getOrElse { "I couldn't reach the AI service: ${it.message ?: "unknown error"}. Check your connection and API settings." }
                chat.add(ChatLine(true, reply))
                state = if (result.isSuccess) "SPEAKING" else "ERROR"
                if (result.isSuccess) onSpeak(reply)
                state = if (result.isSuccess) "IDLE" else "ERROR"
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { BobbyFileReader(appContext).read(uri) }
                .onSuccess { file ->
                    if (!file.text.isNullOrBlank()) chat.add(ChatLine(true, "Imported ${file.name}. Text was extracted locally."))
                    else chat.add(ChatLine(true, "${file.name}: ${file.note}"))
                }
                .onFailure { chat.add(ChatLine(true, "File import failed: ${it.message}")) }
        }
    }

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) send(spoken) else state = "IDLE"
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Void, surface = Panel, primary = Neon, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Void) {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF280A16), Void, Void))).padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BOBBY", style = MaterialTheme.typography.headlineLarge, color = Neon)
                        Text("YOUR PERSONAL AI COMPANION", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { memoryItems = memoryStore.list(); memoryOpen = true }) { Text("MEMORY", color = Neon) }
                        TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("ADD FILE", color = Neon) }
                        TextButton(onClick = { settingsOpen = true }) { Text("AI SETTINGS", color = Neon) }
                        TextButton(onClick = onToggleOverlay) { Text("FLOATING ROBOT", color = Neon) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onboardingOpen = true }, modifier = Modifier.weight(1f)) {
                        Text(if (profile == null) "SET UP PROFILE" else "MY PROFILE")
                    }
                    OutlinedButton(onClick = {
                        val city = profile?.city.orEmpty()
                        if (city.isBlank()) weatherText = "Add your city in MY PROFILE first."
                        else {
                            weatherLoading = true
                            Thread {
                                val result = runCatching { BobbyWeather.today(city) }.getOrElse { "Weather unavailable: " + (it.message ?: "network error") }
                                android.os.Handler(android.os.Looper.getMainLooper()).post { weatherText = result; weatherLoading = false }
                            }.start()
                        }
                    }, modifier = Modifier.weight(1f), enabled = !weatherLoading) {
                        Text(if (weatherLoading) "CHECKING…" else "TODAY’S WEATHER ☀")
                    }
                }
                Text(weatherText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.offset { IntOffset(bubbleOffset.x.roundToInt(), bubbleOffset.y.roundToInt()) }
                        .size(116.dp).scale(if (state == "LISTENING") listening else breathing)
                        .background(Brush.radialGradient(listOf(Color(0xFFB51D50), Color(0xFF3C0B1D), Void)), CircleShape)
                        .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); bubbleOffset += drag } },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = state, label = "avatar-expression") { current ->
                        Text(when (current) { "LISTENING" -> "◉"; "THINKING" -> "◌"; "SPEAKING" -> "◉"; else -> "☺" }, color = Neon, fontSize = 58.sp)
                    }
                }
                Text(state, color = Neon, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(chat) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromBobby) Arrangement.Start else Arrangement.End) {
                            Surface(color = if (line.fromBobby) Panel else Color(0xFF421020), shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                                Text(line.text, Modifier.padding(13.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).background(Panel, RoundedCornerShape(18.dp)).padding(14.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Neon),
                        decorationBox = { inner -> if (draft.isEmpty()) Text("Talk to Bobby…", color = Color.Gray); inner() }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { send(draft) }, colors = ButtonDefaults.buttonColors(containerColor = Neon)) { Text("SEND") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        state = "LISTENING"
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk naturally to Bobby")
                        }
                        runCatching { recognizer.launch(intent) }.onFailure { state = "VOICE UNAVAILABLE" }
                    }) { Text("🎙 TALK") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onInstallVoice) { Text("⬇ VOICE DATA") }
                }
                Text(
                    if (aiEnabled) "AI connected by your saved provider settings. Voice output uses your installed Android TTS voice."
                    else "Offline starter chat is active. Add your AI provider key in AI SETTINGS for intelligent online conversation. Voice data installs through Android TTS.",
                    color = Color.Gray, style = MaterialTheme.typography.labelSmall
                )
                if (onboardingOpen) {
                    BobbyOnboarding(
                        initial = profile,
                        onSave = { updated ->
                            runCatching { profileStore.save(updated) }
                                .onSuccess {
                                    profile = updated
                                    onboardingOpen = false
                                    chat.add(ChatLine(true, "Lovely to meet you, " + updated.name + "! 😊 I’ll remember your profile and the important dates you save here."))
                                    if (updated.city.isNotBlank()) weatherText = "Weather location saved: " + updated.city + ". Tap TODAY’S WEATHER for current conditions."
                                }
                                .onFailure { chat.add(ChatLine(true, "Profile could not be saved: " + (it.message ?: "unknown error"))) }
                        },
                        onDismiss = if (profile == null) null else ({ onboardingOpen = false })
                    )
                }
                if (memoryOpen) {
                    AlertDialog(
                        onDismissRequest = { memoryOpen = false },
                        title = { Text("BOBBY MEMORY") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Saved locally on this phone. Never save passwords, API keys or broker credentials.")
                                OutlinedTextField(value = memoryTitle, onValueChange = { memoryTitle = it }, label = { Text("Memory title") }, singleLine = true)
                                OutlinedTextField(value = memoryContent, onValueChange = { memoryContent = it }, label = { Text("What should Bobby remember?") }, minLines = 2)
                                Text("SAVED MEMORIES (${memoryItems.size})", color = Neon)
                                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                                    items(memoryItems) { item ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(item.title, color = Color.White)
                                                Text(item.content.take(100), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = { memoryStore.delete(item.id); memoryItems = memoryStore.list() }) { Text("DELETE", color = Neon) }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { memoryStore.clear(); memoryItems = emptyList() }) { Text("CLEAR ALL", color = Neon) }
                                    TextButton(onClick = {
                                        val report = memoryStore.exportJson()
                                        val share = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, report) }
                                        runCatching { appContext.startActivity(Intent.createChooser(share, "Export BOBBY memory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }) { Text("EXPORT", color = Neon) }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                runCatching { memoryStore.save("General", memoryTitle, memoryContent) }
                                    .onSuccess { memoryTitle = ""; memoryContent = ""; memoryItems = memoryStore.list() }
                                    .onFailure { chat.add(ChatLine(true, "Memory was not saved: ${it.message}")) }
                            }) { Text("SAVE MEMORY", color = Neon) }
                        },
                        dismissButton = { TextButton(onClick = { memoryOpen = false }) { Text("CLOSE") } }
                    )
                }
                if (settingsOpen) {
                    AlertDialog(
                        onDismissRequest = { settingsOpen = false },
                        title = { Text("Bobby AI connection") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("OpenAI-compatible API. Your key is encrypted on this device.")
                                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("HTTPS API base URL") }, singleLine = true)
                                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true)
                                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key (paste here)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                                Text("Default: OpenAI API. Key is stored with Android Keystore encryption; it is never committed to GitHub.", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                runCatching {
                                    onSaveConfig(endpoint, model, apiKey)
                                    aiEnabled = true
                                    apiKey = ""
                                    settingsOpen = false
                                }.onFailure {
                                    chat.add(ChatLine(true, "Could not save AI settings: ${it.message}"))
                                }
                            }) { Text("SAVE SECURELY") }
                        },
                        dismissButton = {
                            TextButton(onClick = { settingsOpen = false }) { Text("CANCEL") }
                        }
                    )
                }
            }
        }
    }
}
