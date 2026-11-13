package ai.diablo.bobby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Permission hub. Android controls the final grant decision; this screen only
 * requests permissions or opens the relevant system settings page.
 */
@Composable
fun BobbyPermissionCenter(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val runtimePermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY PERMISSION CENTRE") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Enable only the access you want Bobby to use. Android will show its own permission prompts. Some controls require you to switch them on manually in Settings.")
                Button(onClick = { runtimeLauncher.launch(runtimePermissions.toTypedArray()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("REQUEST AVAILABLE APP PERMISSIONS")
                }
                OutlinedButton(onClick = { openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("APP PERMISSIONS & OTHER ACCESS")
                }
                OutlinedButton(onClick = { openOverlaySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("ALLOW FLOATING ROBOT (OVERLAY)")
                }
                OutlinedButton(onClick = { openNotificationListenerSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("NOTIFICATION ACCESS (MANUAL)")
                }
                OutlinedButton(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("ACCESSIBILITY SERVICE (MANUAL)")
                }
                Text(
                    if (BobbyAccessibilityController.isEnabledAndConnected()) "PHONE INTERACTION: CONNECTED" else "PHONE INTERACTION: NOT CONNECTED",
                    color = if (BobbyAccessibilityController.isEnabledAndConnected()) androidx.compose.ui.graphics.Color(0xFF8BC34A) else androidx.compose.ui.graphics.Color(0xFFFFB74D)
                )
                Text("When enabled, Bobby can operate many visible app controls, enter text in supported fields, scroll and read accessible screen text. Some games and protected screens are not controllable. You remain responsible for sensitive actions.")
                OutlinedButton(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("BATTERY / BACKGROUND ACTIVITY")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = { openExactAlarmSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("EXACT ALARM ACCESS")
                    }
                }
                Text("Important: Android does not allow an app to force-grant every permission. You must approve each request. Some permissions are unavailable, restricted by Google Play policy, or require separate service implementation. Bobby will not bypass the phone's security controls.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}

private fun openAppDetails(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
private fun openOverlaySettings(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
}
private fun openNotificationListenerSettings(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
private fun openAccessibilitySettings(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
private fun openBatterySettings(context: Context) {
    launchSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        launchSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    }
}
private fun launchSettings(context: Context, intent: Intent) {
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
