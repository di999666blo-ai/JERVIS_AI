package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

data class OfflineModelStatus(val installed: Boolean, val modelPath: String, val note: String, val bytes: Long = 0L)

/** Installs only the pinned Qwen3 0.6B LiteRT-LM artifact. */
class BobbyOfflineModelStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "offline_models").apply { mkdirs() }
    private val target = File(dir, MODEL_FILE)
    private val temp = File(dir, "model.download.tmp")
    private val backup = File(dir, "model.previous.tmp")

    fun modelFile(): File = target

    fun status(): OfflineModelStatus {
        val valid = runCatching { isValidModel(target) }.getOrDefault(false)
        return OfflineModelStatus(
            valid,
            if (valid) target.absolutePath else "",
            if (valid) "Verified Qwen3 0.6B LiteRT-LM model installed. Runtime load and device performance are checked separately."
            else "No verified offline model installed. Download or import the exact pinned Qwen3 0.6B model.",
            if (valid) target.length() else 0L
        )
    }

    fun downloadRecommended(onProgress: (Long, Long) -> Unit, callback: (Result<OfflineModelStatus>) -> Unit) {
        thread(name = "bobby-model-download") {
            val result = runCatching {
                val available = StatFs(dir.absolutePath).availableBytes
                require(available > EXPECTED_BYTES + INSTALL_HEADROOM_BYTES) {
                    "Not enough free storage. At least " + ((EXPECTED_BYTES + INSTALL_HEADROOM_BYTES) / MIB) + " MB free is required."
                }
                temp.delete()
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "BOBBY-Android/1.0")
                }
                try {
                    val code = connection.responseCode
                    require(code in 200..299) { "Model download failed (HTTP " + code + ")." }
                    require(connection.contentLengthLong == EXPECTED_BYTES) { "Unexpected model size. Download cancelled." }
                    val digest = MessageDigest.getInstance("SHA-256")
                    temp.outputStream().buffered().use { output ->
                        connection.inputStream.buffered().use { input ->
                            val buffer = ByteArray(256 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= EXPECTED_BYTES) { "Model exceeded its pinned size." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                onProgress(total, EXPECTED_BYTES)
                            }
                            require(total == EXPECTED_BYTES) { "Download incomplete. Received " + total + " of " + EXPECTED_BYTES + " bytes." }
                        }
                    }
                    verifyDigest(digest.digest())
                    installVerifiedTemp()
                    status().also { check(it.installed) { "Installed model failed final verification." } }
                } finally {
                    connection.disconnect()
                }
            }
            if (result.isFailure) temp.delete()
            callback(result)
        }
    }

    /** Import is restricted to the exact pinned model; file extensions are not trusted. */
    fun importModel(uri: Uri): OfflineModelStatus {
        temp.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected model." }
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= EXPECTED_BYTES) { "Selected file is larger than the supported model." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total == EXPECTED_BYTES) { "Wrong model size. Select the official Qwen3 0.6B LiteRT-LM file." }
                }
            }
            verifyDigest(digest.digest())
            installVerifiedTemp()
            return status().also { check(it.installed) { "Imported model failed final verification." } }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    fun removeModel() {
        target.delete()
        temp.delete()
        backup.delete()
    }

    private fun isValidModel(file: File): Boolean {
        if (!file.isFile || file.length() != EXPECTED_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == EXPECTED_SHA256
    }

    private fun verifyDigest(bytes: ByteArray) {
        val actual = bytes.joinToString("") { "%02x".format(it) }
        require(actual == EXPECTED_SHA256) { "Model integrity check failed. File rejected; previous installation preserved." }
    }

    /** Keep the old installation until the replacement has passed validation. */
    private fun installVerifiedTemp() {
        check(isValidModel(temp)) { "Temporary model failed integrity verification." }
        backup.delete()
        if (target.exists()) check(target.renameTo(backup)) { "Could not preserve the previous model." }
        try {
            check(temp.renameTo(target)) { "Could not install verified model." }
            check(isValidModel(target)) { "Installed model failed integrity verification." }
            backup.delete()
        } catch (error: Throwable) {
            target.delete()
            if (backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    companion object {
        private const val MODEL_FILE = "bobby-assistant.litertlm"
        private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        private const val EXPECTED_BYTES = 614_000_000L
        private const val INSTALL_HEADROOM_BYTES = 100L * 1024L * 1024L
        private const val MIB = 1024L * 1024L
        private const val EXPECTED_SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    }
}
