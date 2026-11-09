package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

/** Instant voice cloning and encrypted local credentials for BOBBY. */
class BobbyVoiceCloneConnector(private val context: Context) {
    private val vault = BobbySecretVault(context)

    fun saveApiKey(value: String) {
        require(value.trim().length in 12..500) { "Enter a valid ElevenLabs API key." }
        vault.put("elevenlabs_api_key", value.trim())
    }

    fun hasApiKey(): Boolean = vault.get("elevenlabs_api_key").isNotBlank()
    fun hasClonedVoice(): Boolean = vault.get("elevenlabs_voice_id").isNotBlank()
    fun voiceId(): String = vault.get("elevenlabs_voice_id")

    fun clearLocalSettings() {
        vault.remove("elevenlabs_api_key")
        vault.remove("elevenlabs_voice_id")
    }

    fun cloneFromAudio(uri: Uri, voiceName: String, speakerPermissionConfirmed: Boolean, callback: (Result<Boolean>) -> Unit) {
        thread(name = "bobby-voice-clone") {
            callback(runCatching {
                require(speakerPermissionConfirmed) { "Confirm that you own this voice or have the speaker's explicit permission." }
                val name = voiceName.trim()
                require(name.length in 2..40) { "Voice name must be 2–40 characters." }
                val key = vault.get("elevenlabs_api_key")
                require(key.isNotBlank()) { "Save your ElevenLabs API key first." }
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25 * 1024 * 1024) { "Audio sample is larger than 25 MB. Choose a shorter or compressed recording." }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                } ?: error("Could not read the selected audio file.")
                require(bytes.size >= 16 * 1024) { "Audio sample is too small. Choose a clear recording with enough speech." }
                val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
                val extension = when (mime.lowercase()) {
                    "audio/wav", "audio/x-wav" -> "wav"
                    "audio/mp4", "audio/m4a" -> "m4a"
                    "audio/ogg" -> "ogg"
                    "audio/webm" -> "webm"
                    else -> "mp3"
                }
                val boundary = "----BobbyVoice" + UUID.randomUUID().toString().replace("-", "")
                val body = ByteArrayOutputStream()
                fun field(fieldName: String, value: String) {
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n" + value + "\r\n").toByteArray(Charsets.UTF_8))
                }
                field("name", name)
                field("description", "BOBBY personal assistant voice. Uploaded by the user with speaker permission.")
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"bobby_sample." + extension + "\"\r\nContent-Type: " + mime + "\r\n\r\n").toByteArray(Charsets.UTF_8))
                body.write(bytes)
                body.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                val connection = URL("https://api.elevenlabs.io/v1/voices/add").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 25000
                    connection.readTimeout = 90000
                    connection.doOutput = true
                    connection.setRequestProperty("xi-api-key", key)
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    val code = connection.responseCode
                    val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val detail = runCatching { JSONObject(raw).optString("detail").ifBlank { JSONObject(raw).optString("message") } }.getOrDefault("")
                        throw IllegalStateException("Voice cloning failed (HTTP $code)" + if (detail.isNotBlank()) ": $detail" else ". Check API access, account limits and credits.")
                    }
                    val response = JSONObject(raw)
                    val id = response.optString("voice_id")
                    require(id.isNotBlank()) { "The voice service returned no voice ID." }
                    vault.put("elevenlabs_voice_id", id)
                    response.optBoolean("requires_verification", false)
                } finally { connection.disconnect() }
            })
        }
    }
}
 
@androidx.compose.runtime.Composable
fun BobbyVoiceCloneDialog(onDismiss: () -> Unit, onPreview: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connector = androidx.compose.runtime.remember { BobbyVoiceCloneConnector(context) }
    var apiKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var voiceName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("BOBBY") }
    var selectedUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
    var selectedName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var consent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var status by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            if (connector.hasClonedVoice()) "CUSTOM VOICE SAVED • BOBBY will use it for spoken replies."
            else "No custom voice saved. Choose an audio sample to create one."
        )
    }
    val handler = androidx.compose.runtime.remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio sample selected"
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            status = "Audio selected. Confirm speaker permission, then clone."
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { androidx.compose.material3.Text("BOBBY CUSTOM VOICE", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)
            ) {
                androidx.compose.material3.Text("Clone a voice from an audio recording and use it for BOBBY's spoken replies. The sample is uploaded to ElevenLabs for processing.")
                androidx.compose.material3.Text("No separate voice-cloning APK is needed. ElevenLabs still requires its own API account/key; cloning and speech generation may use paid credits or plan limits.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.LightGray)
                androidx.compose.material3.OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it.take(500) },
                    label = { androidx.compose.material3.Text("ElevenLabs API key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true, modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.material3.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching { connector.saveApiKey(apiKey); apiKey = ""; status = "API key encrypted and saved on this phone." }
                            .onFailure { status = it.message ?: "Could not save API key." }
                    }) { androidx.compose.material3.Text("SAVE KEY", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://elevenlabs.io/app/settings/api-keys"))) }
                    }) { androidx.compose.material3.Text("GET API KEY", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = voiceName, onValueChange = { voiceName = it.take(40) },
                    label = { androidx.compose.material3.Text("Voice name") }, singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(if (selectedName.isBlank()) "CHOOSE AUDIO SAMPLE" else "AUDIO: " + selectedName.take(50))
                }
                androidx.compose.material3.Text("Use a clear recording with one speaker and little background noise. MP3, WAV, M4A and other common audio formats are accepted by the phone picker.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = consent, onCheckedChange = { consent = it })
                    androidx.compose.material3.Text("I own this voice or have the speaker's explicit permission to clone it.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Button(
                    enabled = !busy && connector.hasApiKey() && selectedUri != null && consent && voiceName.isNotBlank(),
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        busy = true
                        status = "Uploading sample and creating BOBBY's voice…"
                        connector.cloneFromAudio(uri, voiceName, consent) { result ->
                            handler.post {
                                busy = false
                                result.onSuccess { verification ->
                                    status = if (verification) "VOICE CREATED • Provider verification is required before it can be used." else "VOICE CREATED • BOBBY's custom voice is now selected."
                                }.onFailure { status = "CLONING FAILED • " + (it.message ?: "Unknown error") }
                            }
                        }
                    }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { androidx.compose.material3.Text(if (busy) "CLONING VOICE…" else "CLONE VOICE FOR BOBBY") }
                if (connector.hasClonedVoice()) {
                    androidx.compose.material3.Text("CUSTOM VOICE CONNECTED", color = androidx.compose.ui.graphics.Color(0xFF8BC34A))
                    androidx.compose.material3.OutlinedButton(onClick = { onPreview("Hello. I'm Bobby, and this is my custom voice. How can I help you today?") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Text("TEST BOBBY'S VOICE")
                    }
                }
                androidx.compose.material3.Text(status, color = androidx.compose.ui.graphics.Color(0xFFFF1744), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDismiss() }) { androidx.compose.material3.Text("DONE", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) } },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                connector.clearLocalSettings()
                status = "Local API key and custom voice selection cleared from this phone."
                apiKey = ""
            }) { androidx.compose.material3.Text("DISCONNECT", color = androidx.compose.ui.graphics.Color(0xFFFF1744)) }
        }
    )
}
