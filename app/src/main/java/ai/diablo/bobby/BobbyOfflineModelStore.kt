package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import android.os.StatFs
import kotlin.concurrent.thread

data class OfflineModelStatus(val installed: Boolean, val modelPath: String, val note: String, val bytes: Long = 0L)

/** Keeps the optional model in app-private storage; it is never bundled into the APK. */
class BobbyOfflineModelStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "offline_models").apply { mkdirs() }
    private val target = File(dir, "bobby-assistant.litertlm")
    private val temp = File(dir, "model.download")

    fun modelFile(): File = target
    fun status(): OfflineModelStatus {
        val ready = target.isFile && target.length() > 0
        return OfflineModelStatus(ready, if (ready) target.absolutePath else "",
            if (ready) "Model downloaded. Runtime load and device performance must still be verified."
            else "No offline model installed. Download the recommended Qwen3 0.6B model (about 614 MB).",
            if (ready) target.length() else 0L)
    }

    fun downloadRecommended(onProgress: (Long, Long) -> Unit, callback: (Result<OfflineModelStatus>) -> Unit) {
        thread(name = "bobby-model-download") {
            val result = runCatching {
                val url = URL("https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "BOBBY-Android/1.0")
                }
                try {
                    val code = connection.responseCode
                    require(code in 200..299) { "Model download failed (HTTP $code)." }
                    val expected = connection.contentLengthLong
                    require(expected in 600_000_000L..630_000_000L) {
                        "Unexpected model size. Download cancelled for safety."
                    }
                    val available = StatFs(dir.absolutePath).availableBytes
                    require(available > expected + 100_000_000L) {
                        "Not enough free storage. At least ${(expected + 100_000_000L) / (1024 * 1024)} MB free is required."
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    temp.outputStream().buffered().use { output ->
                        connection.inputStream.buffered().use { input ->
                            val buffer = ByteArray(1024 * 256)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= expected) { "Model exceeded the advertised download size." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                onProgress(total, expected)
                            }
                            require(total == expected) { "Download incomplete. Received $total of $expected bytes." }
                        }
                    }
                    require(temp.length() == expected) { "Downloaded model size check failed." }
                    val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    require(sha256 == "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108") {
                        "Model integrity check failed. The incomplete or altered download was rejected."
                    }
                    if (target.exists()) target.delete()
                    check(temp.renameTo(target)) { "Could not install model in app storage." }
                    status()
                } finally {
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }

    fun importModel(uri: Uri): OfflineModelStatus {
        val incoming = File(dir, "model.import")
        app.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected model." }
            incoming.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        require(incoming.length() in 1..(750L * 1024 * 1024)) { "Model is empty or larger than 750 MB." }
        require(incoming.name.endsWith(".import") && incoming.isFile)
        if (target.exists()) target.delete()
        check(incoming.renameTo(target)) { "Could not install model." }
        return status()
    }

    fun removeModel() {
        target.delete()
        temp.delete()
        File(dir, "model.import").delete()
    }
}
