package ai.diablo.bobby

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.net.Uri

private val VoiceNeon = Color(0xFFFF1744)
private val VoiceGreen = Color(0xFF8BC34A)

/** Uses the Android system TTS engine selected by the user. */
@Composable
fun BobbyOfflineVoiceLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val currentEngine = remember(refresh) {
        Settings.Secure.getString(context.contentResolver, "tts_default_synth")
            ?.takeIf { it.isNotBlank() } ?: "Android default (not reported)"
    }
    val installedEngines = remember(refresh) { findInstalledTtsEngines(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY • OFFLINE VOICE LIBRARY", color = VoiceNeon) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("BOBBY speaks through Android's currently selected Text-to-Speech engine. Install a compatible voice app, download its voice/model once, then select it as the phone's preferred engine. Speech can then work offline.")
                Text("CURRENT SYSTEM ENGINE", color = VoiceGreen)
                Text(currentEngine)
                if (installedEngines.isNotEmpty()) {
                    Text("DETECTED TTS SERVICES")
                    installedEngines.forEach { Text("• $it") }
                } else {
                    Text("No installed TTS service was detected. Check the phone's Text-to-speech settings.")
                }

                Text("1. NEKOSPEAK — RECOMMENDED FOR THIS PHONE", color = VoiceGreen)
                Text("Free, open-source offline Android TTS. Its APK supports Android 8 (minimum API 26), includes a Piper English voice and can download more voices. Pocket-TTS supports voice enrollment/cloning. Model downloads need internet once. This is a separate app, not bundled inside BOBBY.")
                OutlinedButton(onClick = { openUrl(context, "https://github.com/DetentionHall/NekroSpeak/releases/latest") }, modifier = Modifier.fillMaxWidth()) {
                    Text("GET NEKOSPEAK APK")
                }

                Text("2. RHVOICE — LIGHTWEIGHT OFFLINE OPTION", color = VoiceGreen)
                Text("Free Android TTS engine. Install RHVoice, open it, download a language voice, then select RHVoice as the phone's preferred TTS engine.")
                OutlinedButton(onClick = { openUrl(context, "https://play.google.com/store/apps/details?id=com.github.olga_yakovleva.rhvoice.android") }, modifier = Modifier.fillMaxWidth()) {
                    Text("GET RHVOICE")
                }
                OutlinedButton(onClick = { openUrl(context, "https://rhvoice.com/android_support") }, modifier = Modifier.fillMaxWidth()) {
                    Text("RHVOICE SETUP GUIDE")
                }

                Text("3. MARMALADE TTS — NOT COMPATIBLE WITH ANDROID 8", color = VoiceNeon)
                Text("It has an offline built-in voice and is free/open source, but its current release requires Android 9 (API 28) or newer. Do not install it on this phone.")
                OutlinedButton(onClick = { openUrl(context, "https://f-droid.org/en/packages/app.marmalade.tts/") }, modifier = Modifier.fillMaxWidth(), enabled = false) {
                    Text("MARMALADE • ANDROID 9+ ONLY")
                }

                Text("After installing a compatible engine, choose it in Android Settings → Accessibility → Text-to-speech output → Preferred engine. Menu names vary by phone. Return here and tap REFRESH.")
            }
        },
        confirmButton = {
            Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) {
                Text("OPEN PHONE SETTINGS")
            }
        },
        dismissButton = {
            TextButton(onClick = { refresh++ }) { Text("REFRESH") }
            TextButton(onClick = onDismiss) { Text("DONE", color = VoiceNeon) }
        }
    )
}

private fun findInstalledTtsEngines(context: Context): List<String> {
    return runCatching {
        val intent = Intent("android.intent.action.TTS_SERVICE")
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { resolveInfo ->
                val service = resolveInfo.serviceInfo ?: return@mapNotNull null
                val label = runCatching { service.loadLabel(context.packageManager).toString() }
                    .getOrDefault(service.name)
                "$label (${service.packageName})"
            }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
