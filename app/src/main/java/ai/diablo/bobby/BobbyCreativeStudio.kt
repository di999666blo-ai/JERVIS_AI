package ai.diablo.bobby

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class BobbyCreativeStudioConnector(private val context: Context) {
    private val vault = BobbySecretVault(context)

    fun saveMeshyKey(value: String) {
        require(value.trim().length in 12..500) { "Enter a valid Meshy API key." }
        vault.put("meshy_api_key", value.trim())
    }
    fun hasMeshyKey(): Boolean = vault.get("meshy_api_key").isNotBlank()
    fun removeMeshyKey() = vault.remove("meshy_api_key")

    fun generateImage(prompt: String, apiKey: String, quality: String, callback: (Result<File>) -> Unit) {
        thread(name = "bobby-image-generation") {
            callback(runCatching {
                require(prompt.trim().length in 5..32000) { "Describe the image in at least five characters." }
                require(apiKey.isNotBlank()) { "Save your OpenAI API key in AI SETTINGS first." }
                val body = JSONObject()
                    .put("model", "gpt-image-2.5-flare")
                    .put("prompt", prompt.trim())
                    .put("size", "1024x1024")
                    .put("quality", quality)
                    .put("output_format", "png")
                    .put("n", 1)
                val response = requestJson("https://api.openai.com/v1/images/generations", "POST", apiKey, body)
                val encoded = response.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json").orEmpty()
                require(encoded.isNotBlank()) { "Image API returned no image data." }
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                require(bytes.size in 1024..(25 * 1024 * 1024)) { "Generated image has an unexpected size." }
                outputFile("image", "png").apply { writeBytes(bytes) }
            })
        }
    }

    fun generateTextTo3d(prompt: String, progress: (String) -> Unit, callback: (Result<File>) -> Unit) {
        thread(name = "bobby-3d-generation") {
            callback(runCatching {
                require(prompt.trim().length in 5..600) { "3D prompt must be 5–600 characters." }
                val key = vault.get("meshy_api_key")
                require(key.isNotBlank()) { "Connect your Meshy API key first." }
                progress("Creating geometry preview…")
                val formats = org.json.JSONArray().put("glb")
                val previewBody = JSONObject().put("mode", "preview").put("prompt", prompt.trim())
                    .put("ai_model", "meshy-6").put("should_remesh", false)
                    .put("moderation", true).put("target_formats", formats)
                val previewId = requestJson("https://api.meshy.ai/openapi/v2/text-to-3d", "POST", key, previewBody).optString("result")
                require(previewId.isNotBlank()) { "Meshy returned no preview task ID." }
                waitForTask(previewId, key, progress, "Geometry preview")
                progress("Applying materials and PBR textures…")
                val refineBody = JSONObject().put("mode", "refine").put("preview_task_id", previewId)
                    .put("ai_model", "meshy-6").put("enable_pbr", true)
                    .put("moderation", true).put("target_formats", org.json.JSONArray().put("glb"))
                val refineId = requestJson("https://api.meshy.ai/openapi/v2/text-to-3d", "POST", key, refineBody).optString("result")
                require(refineId.isNotBlank()) { "Meshy returned no refine task ID." }
                val task = waitForTask(refineId, key, progress, "Texturing")
                val glbUrl = task.optJSONObject("model_urls")?.optString("glb").orEmpty()
                require(glbUrl.startsWith("https://assets.meshy.ai/")) { "Meshy did not provide a trusted GLB URL." }
                progress("Downloading GLB asset…")
                val file = outputFile("model", "glb")
                val connection = URL(glbUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 20000
                    connection.readTimeout = 90000
                    require(connection.responseCode in 200..299) { "GLB download failed." }
                    connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                } finally { connection.disconnect() }
                require(file.length() > 1024) { "Downloaded GLB is unexpectedly small." }
                progress("3D model ready • " + file.length() / (1024 * 1024) + " MB")
                file
            })
        }
    }

    private fun waitForTask(id: String, key: String, progress: (String) -> Unit, label: String): JSONObject {
        repeat(120) {
            Thread.sleep(5000)
            val task = requestJson("https://api.meshy.ai/openapi/v2/text-to-3d/" + Uri.encode(id), "GET", key, null)
            val status = task.optString("status")
            progress(label + " • " + status + " • " + task.optInt("progress") + "%")
            when (status) {
                "SUCCEEDED" -> return task
                "FAILED", "CANCELED", "EXPIRED" -> throw IllegalStateException(
                    task.optJSONObject("task_error")?.optString("message").orEmpty().ifBlank { "Meshy task " + status }
                )
            }
        }
        throw IllegalStateException("3D generation timed out. You can retry later.")
    }

    private fun requestJson(endpoint: String, method: String, key: String, body: JSONObject?): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 90000
            connection.setRequestProperty("Authorization", "Bearer " + key)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("message") }.getOrDefault("")
                throw IllegalStateException("Provider HTTP " + code + if (detail.isNotBlank()) ": " + detail else ". Check key, access and credits.")
            }
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun outputFile(prefix: String, extension: String): File {
        val dir = File(context.filesDir, "creative_studio").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "bobby_" + prefix + "_" + stamp + "." + extension)
    }
}

@Composable
fun BobbyCreativeStudioDialog(onDismiss: () -> Unit, openAiApiKey: String) {
    val context = LocalContext.current
    val connector = remember { BobbyCreativeStudioConnector(context) }
    var tab by remember { mutableStateOf("IMAGE") }
    var imagePrompt by remember { mutableStateOf("") }
    var modelPrompt by remember { mutableStateOf("") }
    var meshyKey by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("medium") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose an image or 3D workflow.") }
    var imagePath by remember { mutableStateOf("") }
    var assetPath by remember { mutableStateOf("") }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf-binary")) { uri: Uri? ->
        if (uri != null && assetPath.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    File(assetPath).inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not open export destination.")
            }.onSuccess { status = "GLB exported successfully." }
                .onFailure { status = "Export failed: " + it.message }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("BOBBY CREATIVE STUDIO", color = Color(0xFFFF1744)) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == "IMAGE", onClick = { tab = "IMAGE" }, label = { Text("IMAGE") })
                    FilterChip(selected = tab == "3D", onClick = { tab = "3D" }, label = { Text("3D MODELLER") })
                }
                if (tab == "IMAGE") {
                    Text("OpenAI Images API • Uses the OpenAI API key saved in AI SETTINGS. API billing and model access apply.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys"))) } }) { Text("OPENAI API KEYS") }
                    OutlinedTextField(value = imagePrompt, onValueChange = { imagePrompt = it.take(4000) }, label = { Text("Describe your image") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("low", "medium", "high").forEach { q ->
                            FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(q.uppercase()) })
                        }
                    }
                    Button(enabled = !busy && imagePrompt.isNotBlank() && openAiApiKey.isNotBlank(), onClick = {
                        busy = true
                        status = "Generating image…"
                        connector.generateImage(imagePrompt, openAiApiKey, quality) { result ->
                            handler.post {
                                busy = false
                                result.onSuccess { imagePath = it.absolutePath; status = "IMAGE READY • " + it.length() / 1024 + " KB" }
                                    .onFailure { status = "IMAGE FAILED • " + it.message }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "GENERATING…" else "GENERATE IMAGE") }
                    if (imagePath.isNotBlank()) {
                        val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }
                        if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Generated image", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                    }
                } else {
                    Text("Meshy Text-to-3D • Creates a textured GLB mesh. Meshy account and credits are required.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.meshy.ai/api"))) } }) { Text("GET MESHY API ACCESS") }
                    OutlinedTextField(value = meshyKey, onValueChange = { meshyKey = it.take(500) }, label = { Text("Meshy API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = meshyKey.isNotBlank(), onClick = {
                            runCatching { connector.saveMeshyKey(meshyKey); meshyKey = ""; status = "Meshy key encrypted and saved on this phone." }
                                .onFailure { status = it.message ?: "Could not save key." }
                        }) { Text("SAVE KEY") }
                        TextButton(onClick = { connector.removeMeshyKey(); status = "Meshy key removed." }) { Text("DISCONNECT") }
                    }
                    Text(if (connector.hasMeshyKey()) "MESHY CONNECTED • encrypted on device" else "MESHY NOT CONNECTED", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = {
                        modelPrompt = "Create a premium full-body 3D female-presenting futuristic AI assistant robot named BOBBY. Original stylized anime-inspired robotic character, elegant friendly face, expressive luminous red eyes, sleek black obsidian and dark gunmetal armor, vivid neon crimson illuminated circuit accents, subtle crown-like headpiece and futuristic headphones, refined humanoid proportions, articulated shoulders elbows wrists hips knees and fingers, polished PBR metal and dark glass materials, clean symmetrical design, standing upright in a confident welcoming pose, complete character including feet, isolated studio presentation, no text, no logo, no extra characters. Game-ready detailed 3D model with clean topology and textured materials."
                        status = "BOBBY robot design loaded. Tap GENERATE BOBBY ROBOT."
                    }, modifier = Modifier.fillMaxWidth()) { Text("LOAD BOBBY ROBOT DESIGN") }
                    OutlinedTextField(value = modelPrompt, onValueChange = { modelPrompt = it.take(600) }, label = { Text("Describe 3D object (max 600 characters)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && connector.hasMeshyKey() && modelPrompt.isNotBlank(), onClick = {
                        busy = true
                        status = "Starting Meshy generation…"
                        connector.generateTextTo3d(modelPrompt, { message -> handler.post { status = message } }) { result ->
                            handler.post {
                                busy = false
                                result.onSuccess { assetPath = it.absolutePath; status = "3D MODEL READY • " + it.length() / (1024 * 1024) + " MB GLB" }
                                    .onFailure { status = "3D FAILED • " + it.message }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "GENERATING 3D…" else if (modelPrompt.contains("named BOBBY")) "GENERATE BOBBY ROBOT" else "GENERATE 3D MODEL") }
                    if (assetPath.isNotBlank()) {
                        Text("BOBBY 3D MODEL • " + File(assetPath).name, color = Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
                        BobbyRobot3DPreview(assetPath, Modifier.fillMaxWidth().height(260.dp))
                        Button(onClick = { exportLauncher.launch(File(assetPath).name) }, modifier = Modifier.fillMaxWidth()) { Text("EXPORT GLB FILE") }
                    }
                }
                Text(status, color = Color(0xFFFF1744), style = MaterialTheme.typography.bodySmall)
                Text("Files stay in BOBBY private storage until exported. Providers process prompts and may charge usage.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("CLOSE", color = Color(0xFFFF1744)) } }
    )
}
