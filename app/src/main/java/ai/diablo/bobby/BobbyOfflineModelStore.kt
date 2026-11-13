package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

data class OfflineModelStatus(
    val installed: Boolean,
    val modelPath: String,
    val note: String,
    val bytes: Long = 0L,
    val verified: Boolean = false
)

/** Installs only the pinned Qwen3 0.6B LiteRT-LM artifact. */
class BobbyOfflineModelStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "offline_models").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("bobby_offline_model", Context.MODE_PRIVATE)
    private val target = File(dir, MODEL_FILE)
    private val temp = File(dir, "model.download.tmp")
    private val backup = File(dir, "model.previous.tmp")

    fun modelFile(): File = target

    /**
     * Fast status check: never hashes a 614 MB file on the UI thread.
     * Full checksum verification happens on the inference/download worker.
     */
    fun status(): OfflineModelStatus {
        val candidate = target.isFile && target.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES
        val verified = candidate &&
            prefs.getString(KEY_VERIFIED_SHA, null) == EXPECTED_SHA256 &&
            prefs.getLong(KEY_VERIFIED_SIZE, -1L) == target.length() &&
            prefs.getLong(KEY_VERIFIED_MTIME, -1L) == target.lastModified()
        val note = when {
            !candidate -> "No offline model installed. Download or import the pinned Qwen3 0.6B model."
            verified -> "Checksum-verified Qwen3 0.6B LiteRT-LM model installed. Runtime readiness is checked when loaded."
            else -> "Model file detected. Its checksum will be verified in the background before inference."
        }
        return OfflineModelStatus(candidate, if (candidate) target.absolutePath else "", note,
            if (candidate) target.length() else 0L, verified)
    }

    /** Performs checksum verification off the UI thread before the model is loaded. */
    fun verifyInstalledModel(): Boolean {
        if (!target.isFile || target.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return false
        val cached = prefs.getString(KEY_VERIFIED_SHA, null) == EXPECTED_SHA256 &&
            prefs.getLong(KEY_VERIFIED_SIZE, -1L) == target.length() &&
            prefs.getLong(KEY_VERIFIED_MTIME, -1L) == target.lastModified()
        if (cached) return true
        if (!isValidModel(target)) return false
        markVerified(target)
        return true
    }

    fun downloadRecommended(onProgress: (Long, Long) -> Unit, callback: (Result<OfflineModelStatus>) -> Unit) {
        thread(name = "bobby-model-download") {
            val result = runCatching {
                val available = StatFs(dir.absolutePath).availableBytes
                require(available > MAX_MODEL_BYTES + INSTALL_HEADROOM_BYTES) {
                    "Not enough free storage. At least " + ((MAX_MODEL_BYTES + INSTALL_HEADROOM_BYTES) / MIB) + " MB free is required."
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
                    val expected = connection.contentLengthLong
                    require(expected in MIN_MODEL_BYTES..MAX_MODEL_BYTES) { "Unexpected model size. Download cancelled." }
                    val digest = MessageDigest.getInstance("SHA-256")
                    temp.outputStream().buffered().use { output ->
                        connection.inputStream.buffered().use { input ->
                            val buffer = ByteArray(256 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_MODEL_BYTES) { "Model exceeded the supported size window." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                onProgress(total, expected)
                            }
                            require(total == expected && total in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                                "Download incomplete or unexpected size. Received " + total + " of " + expected + " bytes."
                            }
                        }
                    }
                    verifyDigest(digest.digest())
                    installVerifiedTemp()
                    status().also { check(it.installed && it.verified) { "Installed model failed final verification." } }
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
                        require(total <= MAX_MODEL_BYTES) { "Selected file is larger than the supported model." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                        "Wrong model size. Select the official Qwen3 0.6B LiteRT-LM file."
                    }
                }
            }
            verifyDigest(digest.digest())
            installVerifiedTemp()
            return status().also { check(it.installed && it.verified) { "Imported model failed final verification." } }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    fun removeModel() {
        target.delete()
        temp.delete()
        backup.delete()
        prefs.edit().remove(KEY_VERIFIED_SHA).remove(KEY_VERIFIED_SIZE).remove(KEY_VERIFIED_MTIME).apply()
    }

    private fun isValidModel(file: File): Boolean {
        if (!file.isFile || file.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return false
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

    private fun markVerified(file: File) {
        prefs.edit()
            .putString(KEY_VERIFIED_SHA, EXPECTED_SHA256)
            .putLong(KEY_VERIFIED_SIZE, file.length())
            .putLong(KEY_VERIFIED_MTIME, file.lastModified())
            .apply()
    }

    /** Keep the old installation until the replacement has passed validation. */
    private fun installVerifiedTemp() {
        check(isValidModel(temp)) { "Temporary model failed integrity verification." }
        backup.delete()
        if (target.exists()) check(target.renameTo(backup)) { "Could not preserve the previous model." }
        try {
            check(temp.renameTo(target)) { "Could not install verified model." }
            check(isValidModel(target)) { "Installed model failed integrity verification." }
            markVerified(target)
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
        private const val MIN_MODEL_BYTES = 600_000_000L
        private const val MAX_MODEL_BYTES = 630_000_000L
        private const val INSTALL_HEADROOM_BYTES = 100L * 1024L * 1024L
        private const val MIB = 1024L * 1024L
        private const val EXPECTED_SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
        private const val KEY_VERIFIED_SHA = "verified_sha256"
        private const val KEY_VERIFIED_SIZE = "verified_size"
        private const val KEY_VERIFIED_MTIME = "verified_mtime"
    }
}
