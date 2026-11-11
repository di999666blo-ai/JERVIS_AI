package ai.diablo.bobby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Restores the explicitly enabled wake listener after device restart. */
class BobbyWakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences(BobbyWakeService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BobbyWakeService.KEY_ENABLED, false)
        if (!enabled) return
        val serviceIntent = Intent(context, BobbyWakeService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        }
    }
}
