package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import java.io.File

data class OfflineModelStatus(val installed: Boolean, val modelPath: String, val note: String)

/** Imports and manages LiteRT-LM model files in app-private storage. */
class BobbyOfflineModelStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "offline_models").apply { mkdirs() }
    fun status(): OfflineModelStatus {
        val model = File(dir, "bobby-assistant.litertlm")
        val ready = model.exists() && model.length() > 0
        return OfflineModelStatus(ready, if (ready) model.absolutePath else "",
            if (ready) "Model file installed. Runtime compatibility and inference still need validation."
            else "No local model installed. Import a compatible .litertlm file.")
    }
    fun importModel(uri: Uri): OfflineModelStatus {
        val target = File(dir, "bobby-assistant.litertlm"); val temp = File(dir, "model.tmp")
        app.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected model." }
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        require(temp.length() in 1..(2L * 1024 * 1024 * 1024)) { "Model is empty or larger than 2 GB." }
        require(temp.extension == "tmp" && temp.isFile)
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not install model." }
        return status()
    }
    fun removeModel() {
        File(dir, "bobby-assistant.litertlm").delete()
        File(dir, "model.tmp").delete()
    }
}
