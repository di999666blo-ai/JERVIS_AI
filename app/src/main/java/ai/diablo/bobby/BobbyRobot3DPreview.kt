package ai.diablo.bobby

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Base64

/** Interactive WebGL/3D preview for generated GLB assets. */
@Composable
fun BobbyRobot3DPreview(path: String, modifier: Modifier = Modifier) {
    var html by remember(path) { mutableStateOf<String?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        html = null
        failed = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                require(file.isFile && file.length() in 1024..(22L * 1024L * 1024L)) {
                    "Preview supports GLB files up to 22 MB."
                }
                val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                <style>
                html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#080609}
                model-viewer{width:100%;height:100%;background:radial-gradient(ellipse at 50% 35%,#35101e 0%,#12090f 48%,#050505 100%);--poster-color:transparent}
                </style>
                <script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/4.0.0/model-viewer.min.js"></script>
                </head><body>
                <model-viewer src="data:model/gltf-binary;base64,$encoded" camera-controls auto-rotate auto-rotate-delay="0"
                rotation-per-second="18deg" shadow-intensity="1.1" exposure="1.15" interaction-prompt="none"
                environment-image="neutral" camera-orbit="0deg 75deg 2.4m" min-camera-orbit="auto auto 0.6m"
                max-camera-orbit="auto auto 6m" ar="false"></model-viewer>
                </body></html>
                """.trimIndent()
            }
        }
        result.onSuccess { html = it }.onFailure { failed = true }
    }

    Box(modifier.background(Color(0xFF080609)), contentAlignment = Alignment.Center) {
        val page = html
        when {
            page != null -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.rgb(8, 6, 9))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadDataWithBaseURL("https://modelviewer.dev/", page, "text/html", "UTF-8", null)
                    }
                },
                update = { view -> if (view.tag != path) { view.tag = path; view.loadDataWithBaseURL("https://modelviewer.dev/", page, "text/html", "UTF-8", null) } }
            )
            failed -> Text("Could not prepare this GLB for preview. Export it and try a smaller model.", color = Color.LightGray)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = Color(0xFFFF1744))
                Text("Preparing BOBBY's 3D model…", color = Color.LightGray)
            }
        }
    }
}
