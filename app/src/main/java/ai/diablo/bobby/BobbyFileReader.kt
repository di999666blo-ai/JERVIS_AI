package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

data class BobbyFileContent(val name: String, val mimeType: String?, val text: String?, val note: String)

/** Safe first-stage importer. Reads text formats only and caps memory use. */
class BobbyFileReader(private val context: Context) {
    fun read(uri: Uri): BobbyFileContent {
        val mime = context.contentResolver.getType(uri)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "selected-file"
        val lower = name.lowercase()
        if (mime?.startsWith("image/") == true) return BobbyFileContent(name, mime, null, "Image selected. Image understanding requires a configured vision-capable AI provider.")
        if (mime == "application/pdf" || lower.endsWith(".pdf")) return BobbyFileContent(name, mime, null, "PDF selected. PDF text extraction is not installed in this build yet.")
        if (lower.endsWith(".zip") || mime == "application/zip") return BobbyFileContent(name, mime, null, "ZIP archives are not unpacked in this build.")
        val supported = listOf(".txt", ".md", ".csv", ".html", ".htm", ".json", ".xml", ".log", ".kt", ".py", ".js", ".css")
            .any { lower.endsWith(it) } || mime?.startsWith("text/") == true || mime == "application/json"
        if (!supported) return BobbyFileContent(name, mime, null, "This file type is not supported by the current text importer.")
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val stream = BufferedInputStream(input)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "File is larger than the 2 MB import limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        } ?: error("Could not open the selected file.")
        return BobbyFileContent(name, mime, text, "Text extracted locally. No file was uploaded automatically.")
    }
    companion object { private const val MAX_BYTES = 2 * 1024 * 1024 }
}
