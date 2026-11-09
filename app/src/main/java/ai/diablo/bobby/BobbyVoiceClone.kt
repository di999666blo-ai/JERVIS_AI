package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Free/open-source XTTS voice-cloning connector.
 * The model runs on a user-controlled VoiceCloner server; no paid API key is used.
 */
class BobbyVoiceCloneConnector(private val context: Context) {
    private val vault = BobbySecretVault(context)

    fun saveServerUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        val parsed = java.net.URI(normalized)
        require(parsed.scheme in listOf("https", "http") && !parsed.host.isNullOrBlank()) {
            "Enter a valid server URL, for example https://your-server.example"
        }
        require(parsed.scheme == "https" || parsed.host in listOf("localhost", "127.0.0.1")) {
            "Use HTTPS for remote servers. HTTP is allowed only for localhost."
        }
        vault.put("bobby_voice_server_url", normalized)
    }

    fun serverUrl(): String = vault.get("bobby_voice_server_url")
    fun hasClonedVoice(): Boolean = vault.get("bobby_xtts_voice_profile_id").isNotBlank()
    fun voiceProfileId(): String = vault.get("bobby_xtts_voice_profile_id")

    fun clearLocalSettings() {
        vault.remove("bobby_voice_server_url")
        vault.remove("bobby_xtts_voice_profile_id")
    }

    fun cloneFromAudio(uri: Uri, voiceName: String, consentConfirmed: Boolean, callback: (Result<Boolean>) -> Unit) {
        thread(name = "bobby-free-voice-clone") {
            callback(runCatching {
                require(consentConfirmed) { "Confirm you own this voice or have the speaker's explicit permission." }
                val base = serverUrl().ifBlank { error("Save your free/open-source voice server URL first.") }
                val name = voiceName.trim()
                require(name.length in 2..40) { "Voice name must be 2–40 characters." }
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25 * 1024 * 1024) { "Audio sample exceeds 25 MB." }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                } ?: error("Could not read the selected audio file.")
                require(bytes.size >= 16 * 1024) { "Audio sample is too small. Choose a clear voice recording." }
                val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
                val extension = when (mime.lowercase()) {
                    "audio/wav", "audio/x-wav" -> ".wav"
                    "audio/mp4", "audio/m4a" -> ".m4a"
                    "audio/ogg" -> ".ogg"
                    "audio/webm" -> ".webm"
                    else -> ".mp3"
                }
                val boundary = "----BobbyXTTS" + UUID.randomUUID().toString().replace("-", "")
                val body = ByteArrayOutputStream()
                fun field(key: String, value: String) {
                    body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n").toByteArray())
                }
                field("name", name)
                field("consent_confirmed", "true")
                body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"bobby_sample$extension\"\r\nContent-Type: $mime\r\n\r\n").toByteArray())
                body.write(bytes)
                body.write("\r\n--$boundary--\r\n".toByteArray())
                val connection = (URL("$base/voice-profiles").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 180000; doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    val code = connection.responseCode
                    val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("Free voice server returned HTTP $code: ${raw.take(350)}")
                    val json = JSONObject(raw)
                    val id = json.optString("voice_profile_id").ifBlank { json.optString("id") }
                    require(id.isNotBlank() && json.optString("status") == "ready") {
                        "Voice server did not finish creating a voice profile."
                    }
                    vault.put("bobby_xtts_voice_profile_id", id)
                    false
                } finally { connection.disconnect() }
            })
        }
    }
}

@androidx.compose.runtime.Composable
fun BobbyVoiceCloneDialog(onDismiss: () -> Unit, onPreview: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connector = androidx.compose.runtime.remember { BobbyVoiceCloneConnector(context) }
    var serverUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(connector.serverUrl()) }
    var voiceName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("BOBBY") }
    var selectedUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
    var selectedName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var consent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var status by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(if (connector.hasClonedVoice()) "CUSTOM XTTS VOICE SAVED • Ready for speech generation." else "Free voice cloning is selected. Connect your own XTTS server.")
    }
    val handler = androidx.compose.runtime.remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio sample selected"
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            status = "Audio selected. Confirm permission, then create the voice profile."
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { androidx.compose.material3.Text("BOBBY FREE CUSTOM VOICE", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) },
        text = {
            Column(
                modifier = androidx.compose.ui.Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                androidx.compose.material3.Text("ElevenLabs has been removed from this voice-cloning path. BOBBY now supports the free, open-source XTTS VoiceCloner server. You upload a sample; the server creates a reusable voice profile.")
                androidx.compose.material3.Text("Important: the cloning model is too large for this Android phone to run reliably. A computer or hosted server must run the free model. The model software is free for personal/non-commercial use; hosting may still have infrastructure costs.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Text("BEST FREE-LICENSE ALTERNATIVE: OpenVoice V2", color = androidx.compose.ui.graphics.Color(0xFF8BC34A))
                androidx.compose.material3.Text("OpenVoice V2 is MIT-licensed and its authors state it is free for commercial and research use. Native V2 languages are English, Spanish, French, Chinese, Japanese and Korean. It is not an online free API: you must run its model server on your own capable computer or hosted server. BOBBY's current connector below is XTTS-compatible, not an OpenVoice server, so OpenVoice is not connected yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/myshell-ai/OpenVoice"))) }
                }) { androidx.compose.material3.Text("OPEN OPENVOICE V2 • MIT LICENSE", color = androidx.compose.ui.graphics.Color(0xFF8BC34A)) }
                androidx.compose.material3.Text("CURRENT CONNECTOR BELOW: XTTS VoiceCloner. XTTS model weights use a non-commercial license; do not use that model commercially without separate permission. Its server may also cost money to host.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(
                    value = serverUrl, onValueChange = { serverUrl = it.take(300) },
                    label = { androidx.compose.material3.Text("Your XTTS server HTTPS URL") },
                    placeholder = { androidx.compose.material3.Text("https://your-server.example") },
                    singleLine = true, modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { connector.saveServerUrl(serverUrl); status = "Free XTTS server address saved securely on this phone." }
                        .onFailure { status = it.message ?: "Invalid server address." }
                }) { androidx.compose.material3.Text("SAVE SERVER ADDRESS", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
                androidx.compose.material3.Text("Server setup: use the open-source VoiceCloner project. It provides XTTS voice profiles and speech generation endpoints.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/arya-lunawat/VoiceCloner"))) }
                }) { androidx.compose.material3.Text("OPEN FREE VOICECLONER PROJECT", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
                androidx.compose.material3.OutlinedTextField(
                    value = voiceName, onValueChange = { voiceName = it.take(40) },
                    label = { androidx.compose.material3.Text("Voice name") }, singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(if (selectedName.isBlank()) "CHOOSE VOICE RECORDING" else "AUDIO: " + selectedName.take(50))
                }
                androidx.compose.material3.Text("Choose a clear recording with one speaker and little background noise. MP3, WAV, M4A and other common audio formats are supported.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = consent, onCheckedChange = { consent = it })
                    androidx.compose.material3.Text("I own this voice or have the speaker's explicit permission to clone it.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Button(
                    enabled = !busy && serverUrl.isNotBlank() && selectedUri != null && consent && voiceName.isNotBlank(),
                    onClick = {
                        runCatching { connector.saveServerUrl(serverUrl) }.onFailure {
                            status = it.message ?: "Save a valid server URL first."
                            return@Button
                        }
                        val uri = selectedUri ?: return@Button
                        busy = true
                        status = "Uploading recording to your XTTS server and creating the voice profile…"
                        connector.cloneFromAudio(uri, voiceName, consent) { result ->
                            handler.post {
                                busy = false
                                result.onSuccess { status = "VOICE PROFILE CREATED • Free XTTS voice selected for BOBBY." }
                                    .onFailure { status = "CLONING FAILED • " + (it.message ?: "Unknown error") }
                            }
                        }
                    }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { androidx.compose.material3.Text(if (busy) "CREATING VOICE PROFILE…" else "CLONE VOICE FOR BOBBY") }
                if (connector.hasClonedVoice()) {
                    androidx.compose.material3.Text("CUSTOM VOICE PROFILE CONNECTED", color = androidx.compose.ui.graphics.Color(0xFF8BC34A))
                    androidx.compose.material3.OutlinedButton(onClick = { onPreview("Hello. I'm Bobby, and this is my custom voice. How can I help you today?") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Text("TEST BOBBY'S VOICE")
                    }
                }
                androidx.compose.material3.Text(status, color = androidx.compose.ui.graphics.Color(0xFFFF1744), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("DONE", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) } },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                connector.clearLocalSettings()
                serverUrl = ""
                status = "Saved server address and custom voice profile cleared from this phone."
            }) { androidx.compose.material3.Text("DISCONNECT", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
        }
    )
}
