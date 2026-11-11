package ai.diablo.bobby

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Explicit, user-started microphone recording saved inside BOBBY's private app storage. */
class BobbyAudioRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    val isRecording: Boolean get() = recorder != null

    @Suppress("DEPRECATION")
    @Synchronized
    fun start(): File {
        check(recorder == null) { "A recording is already in progress." }
        val folder = File(appContext.filesDir, "recordings")
        check(folder.exists() || folder.mkdirs()) { "Could not create the recordings folder." }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(folder, "BOBBY_RECORDING_$stamp.m4a")
        val media = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext) else MediaRecorder()
        try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(128000)
            media.setAudioSamplingRate(44100)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
            recorder = media
            currentFile = file
            return file
        } catch (error: Exception) {
            runCatching { media.reset() }
            runCatching { media.release() }
            file.delete()
            throw error
        }
    }

    @Synchronized
    fun stop(): File {
        val media = recorder ?: error("No recording is active.")
        val file = currentFile ?: error("Recording file is missing.")
        try {
            media.stop()
            check(file.exists() && file.length() > 1024L) { "Recording was too short or empty." }
            return file
        } catch (error: Exception) {
            file.delete()
            throw IllegalStateException("Recording could not be finalised. Try recording for at least one second.", error)
        } finally {
            runCatching { media.reset() }
            runCatching { media.release() }
            recorder = null
            currentFile = null
        }
    }

    @Synchronized
    fun cancel() {
        val media = recorder ?: return
        runCatching { media.stop() }
        runCatching { media.reset() }
        runCatching { media.release() }
        currentFile?.delete()
        recorder = null
        currentFile = null
    }
}
