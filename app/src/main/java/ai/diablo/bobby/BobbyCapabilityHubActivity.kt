package ai.diablo.bobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CapabilityCard(
    val number: String,
    val title: String,
    val status: String,
    val summary: String,
    val available: String,
    val limit: String
)

class BobbyCapabilityHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFFF1744),
                background = Color(0xFF050505),
                surface = Color(0xFF171017)
            )) {
                CapabilityHubScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun CapabilityHubScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val cards = listOf(
        CapabilityCard("01", "Everyday AI assistant", "🟡 READY WHEN CONNECTED",
            "Chat, explain, plan, brainstorm, summarise and keep context across saved conversations.",
            "Gemini-compatible online AI, selectable models, local chat archive, rename/delete/search, saved memory context and offline-model foundation.",
            "Online answers need a provider key and internet. Offline model must be downloaded and tested on this phone."),
        CapabilityCard("02", "Voice and communication", "🟡 PARTLY READY",
            "Talk to BOBBY and hear replies in supported installed languages.",
            "Android speech recognition, Android text-to-speech, neural voice path and floating-robot tap-to-talk entry.",
            "No reliable always-on wake word, full-duplex conversation, multi-speaker identification or AI voice injection into cellular calls."),
        CapabilityCard("03", "Internet research", "🟡 PARTLY READY",
            "Research current topics, gather sources and ask BOBBY to analyse findings.",
            "Gemini Search grounding path and optional Brave Search connector with encrypted key storage.",
            "Brave needs the user's own API key. Browser automation and guaranteed citations for every provider are not complete."),
        CapabilityCard("04", "Files and documents", "🟡 PARTLY READY",
            "Import supported files and ask BOBBY to explain or summarise them.",
            "Text formats, PDF text, DOCX, XLSX, PPTX, ZIP text extraction and image preparation for a vision-capable provider.",
            "Scanned-PDF OCR, robust audio/video transcription and full multimodal offline analysis are not complete. Selected content may be sent to the configured AI provider."),
        CapabilityCard("05", "Coding and software work", "🟡 FOUNDATION",
            "Plan coding tasks, inspect supported GitHub information and help prepare changes.",
            "Task planner, task history, GitHub identity verification foundation and optional reviewable Codex GitHub workflow.",
            "In-app GitHub repository editing/PR operations are not broadly wired. The Codex workflow requires its own repository secret and user review."),
        CapabilityCard("06", "Images, video, audio and creative work", "🟡 PROVIDER-KEY REQUIRED",
            "Generate images, short videos, speech, music and 3D assets, and create common document/code files.",
            "OpenAI GPT Image 2.5 Flare image generation; fal.ai Seedance 2.5 video, ElevenLabs v4 speech and ElevenLabs Music v2.5; Meshy text-to-3D; local PDF, HTML, XML, JSON, Markdown, CSV and code-file creation.",
            "OpenAI, fal.ai and Meshy require separate provider access, valid keys and usage credits where applicable. The Android build has not yet been functionally tested on the target phone. Image/video editing and full media analysis are not yet complete."),
        CapabilityCard("07", "Phone assistance", "🟡 PERMISSION-BASED",
            "Open apps, prepare simple phone actions and interact with visible accessible controls.",
            "App launch, Clock handoff for alarms, reminders, accessibility bridge, visible-screen text reading and user-approved screen capture.",
            "Accessibility and screen capture require explicit Android approval. Some apps, protected screens and custom controls cannot be operated."),
        CapabilityCard("08", "Personal organisation", "🟡 PARTLY READY",
            "Keep profile details, memories, conversations and reminders organised locally.",
            "User-controlled memory vault, profile, important dates, conversation archive and scheduled local reminders.",
            "Full Google Calendar/Drive sync and a universal task manager are not complete. Memory stays local unless the user chooses to share content."),
        CapabilityCard("09", "Connected accounts and services", "🟡 LIMITED CONNECTORS",
            "Connect selected services only through their real provider adapters.",
            "GitHub read-only identity verification, Deriv account-authorisation foundation and selected integration setup screens.",
            "A catalogue entry is not a live connection. Broad Gmail, Drive, social and OAuth actions still need complete provider adapters and testing."),
        CapabilityCard("10", "Automation and agents", "🟡 CONTROLLED FOUNDATION",
            "Plan multi-step work, keep task status and run a limited set of approved Android actions.",
            "Registered tool inventory, task history, local reminders, app launch and Clock handoff.",
            "The planner does not automatically execute arbitrary AI-generated commands. Consequential actions require explicit approval; background workflows are limited."),
        CapabilityCard("11", "Trading intelligence", "🟡 LIVE DATA • READ-ONLY ANALYSIS",
            "Inspect live Deriv public-market prices and chart observations with specialist analysis agents.",
            "Trader Skill Pack, public active-market catalogue, historical one-minute candles, live tick stream, Chart Analysis Agent and Risk Agent.",
            "No broker order placement, account portfolio execution or autonomous trading is enabled. Live feed requires a valid Deriv App ID and network access.")
    )

    Surface(Modifier.fillMaxSize(), color = Color(0xFF050505)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF171017)).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("BOBBY CAPABILITIES", color = Color.White, fontSize = 19.sp)
                    Text("11 capability areas • honest live status", color = Color.LightGray, fontSize = 12.sp)
                }
                TextButton(onClick = onBack) { Text("CLOSE", color = Color(0xFFFF1744)) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "BOBBY is a chat-first assistant. Some tools are ready, some need your API keys or Android permissions, and some remain planned. A successful APK build does not prove a feature works on every phone.",
                    color = Color(0xFFCCCCCC), fontSize = 13.sp
                )
                cards.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF171017))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("${item.number}  ${item.title}", color = Color.White, fontSize = 16.sp)
                            Text(item.status, color = when {
                                item.status.startsWith("🔴") -> Color(0xFFFF7070)
                                else -> Color(0xFFFF667F)
                            }, fontSize = 10.sp)
                            Text(item.summary, color = Color(0xFFE6E6E6), fontSize = 13.sp)
                            Text("IN THE APP", color = Color(0xFFFF1744), fontSize = 10.sp)
                            Text(item.available, color = Color.LightGray, fontSize = 12.sp)
                            Text("LIMITS", color = Color(0xFFFFB74D), fontSize = 10.sp)
                            Text(item.limit, color = Color(0xFFBBBBBB), fontSize = 12.sp)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF241A1A))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PRIVACY AND SAFETY — APPLIES TO ALL 11", color = Color.White, fontSize = 13.sp)
                        Text(
                            "Provider keys are stored using Android Keystore-backed encryption where implemented. Android permissions cannot be bypassed. Review before sending files or personal data to an AI provider. Tor Browser protects its own browser traffic only; it does not make BOBBY or other apps untraceable.",
                            color = Color(0xFFCCCCCC), fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
