package ai.diablo.bobby

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private val BobbySocialPlatforms = listOf("YouTube", "TikTok", "Instagram", "Facebook")

class BobbyPostDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_post_drafts", Context.MODE_PRIVATE)
    fun save(title: String, caption: String, platforms: Set<String>) {
        val items = runCatching { JSONArray(prefs.getString("drafts", "[]")) }.getOrDefault(JSONArray())
        items.put(JSONObject().put("id", System.currentTimeMillis()).put("title", title.take(120))
            .put("caption", caption.take(5000)).put("platforms", JSONArray(platforms.toList()))
            .put("createdAt", System.currentTimeMillis()).put("status", "DRAFT"))
        while (items.length() > 30) items.remove(0)
        prefs.edit().putString("drafts", items.toString()).apply()
    }
    fun count(): Int = runCatching { JSONArray(prefs.getString("drafts", "[]")).length() }.getOrDefault(0)
}

@Composable
fun BobbyVideoStudioDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, (Result<String>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var topic by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Short vertical video (30–60 seconds)") }
    var script by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Create a video script and storyboard with your configured AI provider.") }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val export = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && script.isNotBlank()) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(script) }
        }.onSuccess { status = "Video script exported." }.onFailure { status = "Export failed: " + it.message }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("BOBBY VIDEO STUDIO", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Uses the AI provider already configured in BOBBY. Online generation may use paid API credits. Offline mode can draft text only; it cannot fetch footage or render a complete video.")
                OutlinedTextField(topic, { topic = it.take(1200) }, label = { Text("Video topic or idea") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(format, {}, readOnly = true, label = { Text("Video format") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Short vertical video (30–60 seconds)", "YouTube video (3–8 minutes)", "Explainer (1–3 minutes)", "Product promotion (15–30 seconds)").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { format = it; expanded = false })
                        }
                    }
                }
                Button(enabled = !busy && topic.isNotBlank(), onClick = {
                    busy = true
                    status = "Writing script, hook, scenes, voiceover and captions…"
                    onGenerate("Create a production-ready video package for: $topic. Format: $format. Return: title, 3 hook options, timed scene-by-scene storyboard, spoken voiceover, on-screen text, shot/footage suggestions, music mood, closing call-to-action, and a social caption with relevant hashtags. Be specific and avoid fabricated facts.") { result ->
                        handler.post {
                            busy = false
                            result.onSuccess { script = it; status = "SCRIPT READY • storyboard and captions generated." }
                                .onFailure { status = "GENERATION FAILED • " + (it.message ?: "AI request failed.") }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "WORKING…" else "CREATE VIDEO PLAN") }
                if (script.isNotBlank()) {
                    OutlinedTextField(script, { script = it }, label = { Text("Editable video package") }, minLines = 8, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { export.launch("bobby_video_script.txt") }, modifier = Modifier.fillMaxWidth()) { Text("EXPORT SCRIPT") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, script)
                        context.startActivity(Intent.createChooser(send, "Share video script"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("SHARE VIDEO PLAN") }
                }
                Text(status, color = androidx.compose.ui.graphics.Color(0xFFFF1744))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
fun BobbyAutoPostDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { BobbyPostDraftStore(context) }
    var title by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(BobbySocialPlatforms.toSet()) }
    var status by remember { mutableStateOf("Prepare once, then share to your selected social apps.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY SOCIAL PUBLISHER", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Selected platforms")
                BobbySocialPlatforms.forEach { platform ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = platform in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + platform else selected - platform
                        })
                        Text(platform)
                        Spacer(Modifier.weight(1f))
                        Text("Not connected", style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.Gray)
                    }
                }
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Post title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(caption, { caption = it.take(5000) }, label = { Text("Caption / post text") },
                    minLines = 4, modifier = Modifier.fillMaxWidth())
                Text("Saved drafts on this phone: ${store.count()}", style = MaterialTheme.typography.bodySmall)
                Button(enabled = title.isNotBlank() && caption.isNotBlank() && selected.isNotEmpty(), onClick = {
                    store.save(title, caption, selected)
                    status = "Draft saved locally. No post has been published."
                }, modifier = Modifier.fillMaxWidth()) { Text("SAVE DRAFT") }
                Button(enabled = caption.isNotBlank() && selected.isNotEmpty(), onClick = {
                    val text = buildString {
                        append(title.trim())
                        if (isNotBlank() && caption.isNotBlank()) append("\\n\\n")
                        append(caption.trim())
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Choose a social app to share to"))
                    status = "Android share sheet opened. Review and tap Publish inside the selected platform."
                }, modifier = Modifier.fillMaxWidth()) { Text("SHARE / PUBLISH WITH CONFIRMATION") }
                Text("Direct background auto-publishing is not enabled yet. Each platform requires its own approved OAuth/API connection and permissions. BOBBY will not silently publish or claim a post succeeded.", style = MaterialTheme.typography.bodySmall)
                Text(status, color = androidx.compose.ui.graphics.Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}
