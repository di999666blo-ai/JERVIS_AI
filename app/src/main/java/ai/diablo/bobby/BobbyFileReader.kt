package ai.diablo.bobby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class BobbyFileContent(
    val name: String,
    val mimeType: String?,
    val text: String?,
    val note: String,
    val imagePath: String? = null,
    val mediaPrompt: String? = null
)

/** Bounded local extraction for text, PDF, Office Open XML, ZIP and raster images. */
class BobbyFileReader(private val context: Context) {
    fun read(uri: Uri): BobbyFileContent {
        val mime = context.contentResolver.getType(uri)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "selected-file"
        val lower = name.lowercase()

        if (mime?.startsWith("image/") == true || listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp").any { lower.endsWith(it) }) {
            val path = extractImage(uri, name)
            return BobbyFileContent(name, mime, null, "Image converted locally for vision analysis.", path)
        }
        if (mime?.startsWith("video/") == true || listOf(".mp4", ".m4v", ".3gp", ".webm", ".mkv").any { lower.endsWith(it) }) {
            val frame = extractVideoFrame(uri, name)
            return BobbyFileContent(name, mime, null,
                "A representative video frame was extracted locally. Full-motion/audio understanding is not available in this importer.",
                frame, "Please analyse this representative frame from the attached video named $name. Explain what is visible and note that this is only one frame, not a full video review.")
        }
        if (mime?.startsWith("audio/") == true || listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".flac", ".opus", ".3gp").any { lower.endsWith(it) }) {
            val retriever = MediaMetadataRetriever()
            val details = try {
                retriever.setDataSource(context, uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                buildList {
                    add("Name: $name")
                    mime?.let { add("Format: $it") }
                    title?.let { add("Title: $it") }
                    artist?.let { add("Artist: $it") }
                    album?.let { add("Album: $it") }
                    duration?.let { add("Duration: ${it / 60000} min ${(it / 1000) % 60} sec") }
                }.joinToString("\\n")
            } finally {
                runCatching { retriever.release() }
            }
            return BobbyFileContent(
                name, mime,
                "Audio attachment imported. Available local metadata:\\n$details\\nThe audio waveform has not been transcribed or listened to by AI.",
                "Audio file recognised and metadata extracted. Audio transcription/understanding is not yet implemented."
            )
        }
        if (mime == "application/pdf" || lower.endsWith(".pdf")) {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                PDFBoxResourceLoader.init(context)
                PDDocument.load(input).use { document ->
                    require(document.numberOfPages <= 500) { "PDF has more than 500 pages." }
                    PDFTextStripper().getText(document).take(MAX_TEXT_CHARS)
                }
            } ?: error("Could not open the selected PDF.")
            return BobbyFileContent(name, mime, text, "PDF text extracted locally. Scanned pages without text require OCR/vision.")
        }
        if (lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".pptx")) {
            val entries = readZipEntries(uri, 24, MAX_ARCHIVE_BYTES)
            val selected = when {
                lower.endsWith(".docx") -> entries.filterKeys { it == "word/document.xml" || it.startsWith("word/header") || it.startsWith("word/footer") }
                lower.endsWith(".xlsx") -> entries.filterKeys { it == "xl/sharedStrings.xml" || it.startsWith("xl/worksheets/sheet") }
                else -> entries.filterKeys { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }.toSortedMap()
            }
            val text = selected.entries.joinToString("\n\n") { (path, xml) -> "[$path]\n" + xmlToText(xml) }
                .take(MAX_TEXT_CHARS)
            require(text.isNotBlank()) { "No readable text was found in this Office document." }
            return BobbyFileContent(name, mime, text, "Office document text extracted locally.")
        }
        if (lower.endsWith(".zip") || mime == "application/zip" || mime == "application/x-zip-compressed") {
            val entries = readZipEntries(uri, 20, MAX_ARCHIVE_BYTES)
            val text = entries.entries.joinToString("\n\n") { (path, content) -> "[$path]\n$content" }.take(MAX_TEXT_CHARS)
            return BobbyFileContent(name, mime, text.ifBlank { null }, if (text.isBlank()) "ZIP opened, but no supported text files were found." else "Supported text files extracted safely from ZIP.")
        }
        val supported = listOf(".txt", ".md", ".csv", ".html", ".htm", ".json", ".xml", ".log", ".kt", ".py", ".js", ".css", ".java", ".kts", ".yaml", ".yml", ".sql")
            .any { lower.endsWith(it) } || mime?.startsWith("text/") == true || mime == "application/json"
        if (!supported) return BobbyFileContent(name, mime, null, "This file type is not supported by the current importer.")
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            readBounded(input, MAX_FILE_BYTES).toString(Charsets.UTF_8)
        } ?: error("Could not open the selected file.")
        return BobbyFileContent(name, mime, text.take(MAX_TEXT_CHARS), "Text extracted locally. No file was uploaded automatically.")
    }

    private fun extractVideoFrame(uri: Uri, name: String): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(durationMs > 0L) { "The selected video has no readable duration." }
            val frameTimeUs = (durationMs / 2L).coerceAtLeast(0L) * 1000L
            val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Could not extract a frame from this video.")
            val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
            val file = File(context.cacheDir, "bobby-video-frame-" + System.currentTimeMillis() + "-" + safeName + ".jpg")
            try {
                FileOutputStreamCompat.writeJpeg(bitmap, file, 78)
                require(file.length() in 1..(4L * 1024 * 1024)) { "Extracted video frame is too large." }
            } finally { bitmap.recycle() }
            return file.absolutePath
        } finally { runCatching { retriever.release() } }
    }

    private fun extractImage(uri: Uri, name: String): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image format could not be decoded." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode image.")
        val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val file = File(context.cacheDir, "bobby-image-" + System.currentTimeMillis() + "-" + safeName + ".jpg")
        try {
            FileOutputStreamCompat.writeJpeg(bitmap, file, 78)
            require(file.length() in 1..(4L * 1024 * 1024)) { "Image is too large after conversion. Choose a smaller image." }
        } finally { bitmap.recycle() }
        return file.absolutePath
    }

    private fun readZipEntries(uri: Uri, maxEntries: Int, maxBytes: Int): Map<String, String> {
        val output = linkedMapOf<String, String>()
        var total = 0
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open archive.")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && isExtractableEntry(entry.name) && output.size < maxEntries) {
                    val bytes = readBounded(zip, MAX_FILE_BYTES)
                    total += bytes.size
                    require(total <= maxBytes) { "Archive text exceeds the extraction limit." }
                    output[entry.name.take(180)] = bytes.toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return output
    }

    private fun isExtractableEntry(path: String): Boolean {
        if (path.startsWith("/") || path.split('/').any { it == ".." }) return false
        val p = path.lowercase()
        return p.endsWith(".xml") || listOf(".txt", ".md", ".csv", ".json", ".html", ".htm", ".kt", ".py", ".js", ".css", ".sql").any { p.endsWith(it) }
    }

    private fun xmlToText(xml: String): String {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) {
                val value = parser.text?.trim().orEmpty()
                if (value.isNotBlank()) out.append(value).append(' ')
            }
            event = parser.next()
        }
        return out.toString().trim()
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "File exceeds the local extraction limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 5 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 120_000
    }
}

private object FileOutputStreamCompat {
    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        java.io.FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Could not convert image." }
        }
    }
}
