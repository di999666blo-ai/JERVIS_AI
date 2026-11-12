package ai.diablo.bobby

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Imports a self-contained, rigged GLB into app-private storage.
 * This keeps the APK usable without a custom model and preserves imports across app updates.
 */
@Composable
fun BobbyCharacterAssetImport() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bobby_character_asset", android.content.Context.MODE_PRIVATE) }
    var importedPath by remember { mutableStateOf(prefs.getString("glb_path", null)) }
    var status by remember { mutableStateOf("Default BOBBY avatar stays available. Import a rigged GLB when ready.") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        val result = runCatching {
            val targetDir = File(context.filesDir, "bobby-character").apply { mkdirs() }
            val target = File(targetDir, "custom-avatar.glb")
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Android could not open that file.")
            input.use { source ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 22L * 1024L * 1024L) { "GLB must be 22 MB or smaller for this phone." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(target.length() >= 20L) { "The selected file is empty or incomplete." }
            val header = ByteArray(12)
            target.inputStream().use { require(it.read(header) == 12) { "Could not read the GLB header." } }
            val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(data.int == 0x46546C67) { "That is not a GLB file. Choose a .glb avatar." }
            require(data.int == 2) { "Only glTF 2.0 binary (.glb) files are supported." }
            require(data.int.toLong() == target.length()) { "The GLB file is incomplete or damaged." }
            val bytes = target.readBytes()
            val jsonLength = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            require(jsonLength > 0 && jsonLength <= bytes.size - 20) { "The GLB has an invalid JSON section." }
            val json = String(bytes, 20, jsonLength, Charsets.UTF_8)
            require(json.contains("\"skins\"")) { "This GLB has no skeleton/skin. Export a rigged humanoid GLB." }
            target.absolutePath
        }
        result.onSuccess { path ->
            importedPath = path
            prefs.edit().putString("glb_path", path).apply()
            status = "Rigged GLB imported and saved on this phone."
        }.onFailure { status = it.message ?: "Import failed. Your current avatar is unchanged." }
        busy = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CHARACTER FILES")
        Text("Supported: rigged glTF 2.0 binary (.glb), up to 22 MB. The file must contain a skin/skeleton. STL is geometry only; FBX and BVH are not imported here.")
        Button(onClick = { picker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) }, enabled = !busy) {
            Text(if (busy) "IMPORTING…" else "IMPORT RIGGED GLB")
        }
        Text(status)
        importedPath?.let { path ->
            if (File(path).isFile) {
                Text("Imported character preview")
                BobbyRobot3DPreview(path = path, modifier = Modifier.fillMaxWidth().height(260.dp))
                Button(onClick = {
                    File(path).delete()
                    prefs.edit().remove("glb_path").apply()
                    importedPath = null
                    status = "Custom file removed. Default BOBBY avatar remains available."
                }) { Text("REMOVE IMPORTED FILE") }
            } else {
                importedPath = null
                prefs.edit().remove("glb_path").apply()
            }
        }
    }
}
