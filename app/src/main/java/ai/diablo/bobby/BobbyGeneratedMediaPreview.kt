package ai.diablo.bobby

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Local preview for generated media. Playback stays inside BOBBY and is released
 * when the generated file changes or the preview leaves composition.
 */
@Composable
fun BobbyGeneratedMediaPreview(path: String, modifier: Modifier = Modifier) {
    val file = remember(path) { File(path) }
    if (!file.exists() || !file.isFile) return

    when (file.extension.lowercase()) {
        "mp4", "m4v", "3gp" -> {
            var videoView by remember(path) { mutableStateOf<VideoView?>(null) }
            AndroidView(
                factory = { context ->
                    VideoView(context).also { view ->
                        videoView = view
                        val controls = MediaController(context)
                        controls.setAnchorView(view)
                        view.setMediaController(controls)
                        view.setVideoPath(file.absolutePath)
                    }
                },
                modifier = modifier.fillMaxWidth().height(220.dp)
            )
            DisposableEffect(path) {
                onDispose {
                    runCatching { videoView?.stopPlayback() }
                    videoView = null
                }
            }
        }
        "mp3", "m4a", "aac", "wav", "ogg" -> {
            val context = LocalContext.current
            var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
            var prepared by remember(path) { mutableStateOf(false) }
            var playing by remember(path) { mutableStateOf(false) }
            var error by remember(path) { mutableStateOf("") }

            DisposableEffect(path) {
                val mediaPlayer = MediaPlayer()
                player = mediaPlayer
                mediaPlayer.setOnPreparedListener {
                    prepared = true
                    error = ""
                }
                mediaPlayer.setOnCompletionListener {
                    playing = false
                    runCatching { it.seekTo(0) }
                }
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    playing = false
                    error = "Audio playback failed ($what/$extra)."
                    true
                }
                try {
                    mediaPlayer.setDataSource(file.absolutePath)
                    mediaPlayer.prepareAsync()
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not open generated audio."
                }
                onDispose {
                    runCatching { mediaPlayer.stop() }
                    runCatching { mediaPlayer.release() }
                    player = null
                    prepared = false
                    playing = false
                }
            }

            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AUDIO PREVIEW • ${file.name}")
                if (!prepared && error.isBlank()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = prepared,
                    onClick = {
                        val active = player ?: return@Button
                        runCatching {
                            if (playing) {
                                active.pause()
                                playing = false
                            } else {
                                active.start()
                                playing = true
                            }
                        }.onFailure {
                            error = it.message ?: "Audio playback failed."
                            playing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (!prepared) "LOADING AUDIO…" else if (playing) "PAUSE AUDIO" else "PLAY AUDIO")
                }
                if (error.isNotBlank()) Text(error)
            }
        }
    }
}
