package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

private class BobbyCreationEngine(private val context: Context) {
    private val vault = BobbySecretVault(context)

    fun saveFalKey(key: String) {
        require(key.trim().length in 12..500) { "Enter a valid fal API key." }
        vault.put("fal_api_key", key.trim())
    }
    fun hasFalKey(): Boolean = vault.get("fal_api_key").isNotBlank()
    fun removeFalKey() = vault.remove("fal_api_key")

    fun createFile(title: String, body: String, format: String): File {
        require(body.isNotBlank()) { "Enter some content first." }
        require(body.length <= 120000) { "Content is limited to 120,000 characters." }
        val ext = format.lowercase()
        require(ext in listOf("pdf", "html", "xml", "json", "txt", "md", "csv", "css", "js", "py")) { "Unsupported file format." }
        val name = title.trim().ifBlank { "bobby_file" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val dir = File(context.filesDir, "creative_studio").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "bobby_" + name + "_" + stamp + "." + ext)
        when (ext) {
            "pdf" -> {
                PDFBoxResourceLoader.init(context)
                PDDocument().use { document ->
                    val lines = mutableListOf<String>()
                    body.lines().forEach { paragraph ->
                        if (paragraph.isBlank()) { lines.add(""); return@forEach }
                        var line = ""
                        paragraph.split(Regex("\\s+")).forEach { word ->
                            val candidate = if (line.isBlank()) word else line + " " + word
                            if (candidate.length > 88 && line.isNotBlank()) { lines.add(line); line = word } else line = candidate
                        }
                        if (line.isNotBlank()) lines.add(line)
                    }
                    var cursor = 0
                    while (cursor < lines.size || document.numberOfPages == 0) {
                        val page = PDPage(PDRectangle.LETTER)
                        document.addPage(page)
                        PDPageContentStream(document, page).use { stream ->
                            stream.beginText()
                            stream.setFont(PDType1Font.HELVETICA, 10f)
                            stream.setLeading(14f)
                            stream.newLineAtOffset(48f, PDRectangle.LETTER.height - 48f)
                            var count = 0
                            while (cursor < lines.size && count < 48) {
                                val safeLine = lines[cursor++].map { if (it.code in 32..255) it else '?' }.joinToString("")
                                stream.showText(safeLine.take(110))
                                stream.newLine()
                                count++
                            }
                            stream.endText()
                        }
                    }
                    document.save(file)
                }
            }
            "xml" -> {
                val parser = android.util.Xml.newPullParser()
                parser.setInput(body.reader())
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) { }
                file.writeText(body, Charsets.UTF_8)
            }
            "json" -> {
                val valid = runCatching { JSONObject(body) }.isSuccess || runCatching { JSONArray(body) }.isSuccess
                require(valid) { "JSON is invalid. Check the brackets, commas and quotation marks." }
                file.writeText(body, Charsets.UTF_8)
            }
            "html" -> {
                val html = if (body.contains("<html", true)) body else
                    """<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${name}</title></head>
<body>
${body}
</body></html>"""
                file.writeText(html, Charsets.UTF_8)
            }
            else -> file.writeText(body, Charsets.UTF_8)
        }
        require(file.exists() && file.length() > 0) { "File creation failed." }
        return file
    }

    fun generateFal(endpoint: String, input: JSONObject, resultField: String, extension: String, progress: (String) -> Unit, done: (Result<File>) -> Unit) {
        thread(name = "bobby-creative-media") {
            done(runCatching {
                val key = vault.get("fal_api_key")
                require(key.isNotBlank()) { "Save your fal API key first." }
                require(endpoint.matches(Regex("[A-Za-z0-9._/-]{3,180}")) && !endpoint.contains("..")) { "Invalid model endpoint." }
                progress("Submitting generation request…")
                val submitted = falJson("https://queue.fal.run/" + endpoint, "POST", key, input)
                var statusUrl = submitted.optString("status_url")
                var responseUrl = submitted.optString("response_url")
                require(statusUrl.startsWith("https://queue.fal.run/")) { "Provider returned an invalid status URL." }
                var completed = false
                for (attempt in 1..180) {
                    Thread.sleep(5000)
                    val state = falJson(statusUrl, "GET", key, null)
                    val status = state.optString("status")
                    progress("Generation " + status.lowercase() + " • " + attempt + "/180")
                    if (status == "COMPLETED") {
                        completed = true
                        responseUrl = state.optString("response_url").ifBlank { responseUrl }
                        break
                    }
                    require(status !in setOf("FAILED", "CANCELED", "EXPIRED")) { "Generation failed: " + state.toString().take(500) }
                }
                require(completed) { "Generation timed out. Check provider history before retrying." }
                require(responseUrl.startsWith("https://queue.fal.run/")) { "Provider returned an invalid result URL." }
                val result = falJson(responseUrl, "GET", key, null)
                val media = result.optJSONObject(resultField) ?: error("No " + resultField + " output was returned.")
                val mediaUrl = media.optString("url")
                val host = Uri.parse(mediaUrl).host.orEmpty()
                require(mediaUrl.startsWith("https://") && (host == "fal.media" || host.endsWith(".fal.media"))) { "Media URL is not from the approved fal CDN." }
                progress("Downloading generated " + extension.uppercase() + "…")
                val file = File(File(context.filesDir, "creative_studio").apply { mkdirs() },
                    "bobby_" + resultField + "_" + System.currentTimeMillis() + "." + extension)
                val connection = URL(mediaUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 20000
                    connection.readTimeout = 120000
                    require(connection.responseCode in 200..299) { "Media download failed: HTTP " + connection.responseCode }
                    connection.inputStream.use { source ->
                        file.outputStream().use { target ->
                            val buffer = ByteArray(16384)
                            var total = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 250L * 1024 * 1024) { "Media exceeds the 250 MB download limit." }
                                target.write(buffer, 0, count)
                            }
                        }
                    }
                } finally { connection.disconnect() }
                require(file.length() > 1024) { "Downloaded media is unexpectedly small." }
                progress("Ready • " + file.length() / (1024 * 1024) + " MB")
                file
            })
        }
    }

    private fun falJson(url: String, method: String, key: String, body: JSONObject?): JSONObject {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && uri.host == "queue.fal.run") { "Blocked an untrusted provider request." }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 90000
            connection.setRequestProperty("Authorization", "Key " + key)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("detail").ifBlank { JSONObject(raw).optString("message") } }.getOrDefault("")
                throw IllegalStateException("Provider HTTP " + code + if (detail.isNotBlank()) ": " + detail.take(400) else ". Check API key, access and credits.")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally { connection.disconnect() }
    }
}

@Composable
fun BobbyCreationToolsDialog(onDismiss: () -> Unit, onOpenImage3d: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { BobbyCreationEngine(context) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var tab by remember { mutableStateOf("FILES") }
    var keyDraft by remember { mutableStateOf("") }
    var fileTitle by remember { mutableStateOf("My creation") }
    var fileBody by remember { mutableStateOf("") }
    var fileFormat by remember { mutableStateOf("html") }
    var videoPrompt by remember { mutableStateOf("") }
    var audioText by remember { mutableStateOf("") }
    var audioVoice by remember { mutableStateOf("Rachel") }
    var musicPrompt by remember { mutableStateOf("") }
    var musicDurationSeconds by remember { mutableStateOf("30") }
    var musicInstrumental by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Create documents locally or connect fal for video and speech generation.") }
    var outputPath by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null && outputPath.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    File(outputPath).inputStream().use { it.copyTo(out) }
                } ?: error("Could not open export destination.")
            }.onSuccess { status = "Export complete." }.onFailure { status = "Export failed: " + it.message }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("BOBBY CREATIVE STUDIO", color = Color(0xFFFF1744)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FILES", "VIDEO", "AUDIO", "MUSIC").forEach { item ->
                        FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) })
                    }
                }
                if (tab == "FILES") {
                    Text("Create PDF, HTML, XML, JSON, text, Markdown, CSV and code files directly on this phone. PDF, XML and JSON are validated before saving.")
                    OutlinedTextField(value = fileTitle, onValueChange = { fileTitle = it.take(80) }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("pdf", "html", "xml", "json", "txt", "md", "csv", "css", "js", "py").forEach { ext ->
                            FilterChip(selected = fileFormat == ext, onClick = { fileFormat = ext }, label = { Text(ext.uppercase()) })
                        }
                    }
                    OutlinedTextField(value = fileBody, onValueChange = { fileBody = it.take(120000) }, label = { Text("Document content, markup or code") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && fileBody.isNotBlank(), onClick = {
                        runCatching { engine.createFile(fileTitle, fileBody, fileFormat) }
                            .onSuccess { outputPath = it.absolutePath; status = "FILE CREATED • " + it.name + " • " + it.length() + " bytes" }
                            .onFailure { status = "FILE FAILED • " + it.message }
                    }, modifier = Modifier.fillMaxWidth()) { Text("CREATE " + fileFormat.uppercase()) }
                } else {
                    Text("fal.ai provider • Your key is stored encrypted on this phone. Video and speech generation consume provider credits; check your account pricing before generating.")
                    OutlinedTextField(value = keyDraft, onValueChange = { keyDraft = it.take(500) }, label = { Text("fal API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = keyDraft.isNotBlank(), onClick = {
                            runCatching { engine.saveFalKey(keyDraft); keyDraft = ""; status = "fal key saved encrypted on this phone." }
                                .onFailure { status = it.message ?: "Key could not be saved." }
                        }) { Text("SAVE KEY") }
                        TextButton(onClick = { engine.removeFalKey(); status = "fal key removed from this phone." }) { Text("DISCONNECT") }
                    }
                    Text(if (engine.hasFalKey()) "FAL KEY SAVED" else "FAL NOT CONNECTED", color = Color(0xFFFF1744), style = MaterialTheme.typography.labelSmall)
                    if (tab == "VIDEO") {
                        Text("Seedance 2.5 • 5-second 720p video • synchronized audio • MP4")
                        OutlinedTextField(value = videoPrompt, onValueChange = { videoPrompt = it.take(5000) }, label = { Text("Describe your video") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && engine.hasFalKey() && videoPrompt.isNotBlank(), onClick = {
                            busy = true
                            val input = JSONObject().put("prompt", videoPrompt.trim()).put("duration", "5")
                                .put("resolution", "720p").put("generate_audio", true)
                                .put("aspect_ratio", "16:9").put("bitrate_mode", "standard").put("codec", "H264")
                            engine.generateFal("bytedance/seedance-2.5/us/text-to-video", input, "video", "mp4",
                                { message -> handler.post { status = message } }) { result ->
                                handler.post {
                                    busy = false
                                    result.onSuccess { outputPath = it.absolutePath; status = "VIDEO READY • " + it.length() / (1024 * 1024) + " MB" }
                                        .onFailure { status = "VIDEO FAILED • " + it.message }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "GENERATING VIDEO…" else "GENERATE VIDEO") }
                    } else if (tab == "AUDIO") {
                        Text("ElevenLabs v4 • expressive text-to-speech • MP3. Rachel is the default voice; enter another supported voice name if preferred.")
                        OutlinedTextField(value = audioText, onValueChange = { audioText = it.take(5000) }, label = { Text("Text to speak") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = audioVoice, onValueChange = { audioVoice = it.take(60) }, label = { Text("Voice preset") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && engine.hasFalKey() && audioText.isNotBlank(), onClick = {
                            busy = true
                            val input = JSONObject().put("text", audioText.trim()).put("voice", audioVoice.trim().ifBlank { "Rachel" })
                                .put("output_format", "mp3_44100_128").put("enable_safety_checker", true)
                            engine.generateFal("elevenlabs/tts/eleven-v4", input, "audio", "mp3",
                                { message -> handler.post { status = message } }) { result ->
                                handler.post {
                                    busy = false
                                    result.onSuccess { outputPath = it.absolutePath; status = "AUDIO READY • " + it.length() / 1024 + " KB" }
                                        .onFailure { status = "AUDIO FAILED • " + it.message }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "GENERATING AUDIO…" else "GENERATE AUDIO") }
                    } else {
                        Text("ElevenLabs Music v2.5 • create an original soundtrack or instrumental track • MP3.")
                        OutlinedTextField(value = musicPrompt, onValueChange = { musicPrompt = it.take(4000) }, label = { Text("Describe the music, mood, genre and instruments") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = musicDurationSeconds, onValueChange = { musicDurationSeconds = it.filter(Char::isDigit).take(3) }, label = { Text("Duration in seconds (3–600)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Instrumental only")
                            Switch(checked = musicInstrumental, onCheckedChange = { musicInstrumental = it })
                        }
                        Button(enabled = !busy && engine.hasFalKey() && musicPrompt.isNotBlank() && (musicDurationSeconds.toIntOrNull() ?: 0) in 3..600, onClick = {
                            busy = true
                            val input = JSONObject().put("prompt", musicPrompt.trim())
                                .put("music_length_ms", musicDurationSeconds.toInt() * 1000)
                                .put("force_instrumental", musicInstrumental).put("enable_safety_checker", true)
                                .put("output_format", "mp3_48000_192")
                            engine.generateFal("elevenlabs/music/v2.5", input, "audio", "mp3",
                                { message -> handler.post { status = message } }) { result ->
                                handler.post {
                                    busy = false
                                    result.onSuccess { outputPath = it.absolutePath; status = "MUSIC READY • " + it.length() / 1024 + " KB" }
                                        .onFailure { status = "MUSIC FAILED • " + it.message }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "GENERATING MUSIC…" else "GENERATE MUSIC") }
                    }
                }
                if (outputPath.isNotBlank() && File(outputPath).exists()) {
                    Text("READY TO EXPORT • " + File(outputPath).name, color = Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { export.launch(File(outputPath).name) }, modifier = Modifier.fillMaxWidth()) { Text("EXPORT TO PHONE") }
                }
                OutlinedButton(enabled = !busy, onClick = onOpenImage3d, modifier = Modifier.fillMaxWidth()) { Text("OPEN IMAGE & 3D GENERATION") }
                Text(status, color = Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
                Text("Files are kept in BOBBY private storage until exported. Provider generation can incur charges.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("CLOSE", color = Color(0xFFFF1744)) } }
    )
}
