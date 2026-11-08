package ai.diablo.bobby

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/** Platform-mediated actions. Android/target apps present their own confirmation UI. */
object BobbyAndroidActions {
    fun setAlarm(context: Context, hour: Int, minute: Int, label: String = "BOBBY alarm") {
        require(hour in 0..23 && minute in 0..59) { "Invalid alarm time." }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        require(intent.resolveActivity(context.packageManager) != null) { "No compatible Clock app is installed." }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openApp(context: Context, requestedName: String): String {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(launcher, 0)
        val match = matches.firstOrNull { info ->
            info.loadLabel(context.packageManager).toString().contains(requestedName.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("I couldn't find an installed app matching '$requestedName'.")
        val packageName = match.activityInfo.packageName
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("That app does not provide a launch activity.")
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return match.loadLabel(context.packageManager).toString()
    }

    fun composeSms(context: Context, number: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(intent.resolveActivity(context.packageManager) != null) { "No SMS app is available." }
        context.startActivity(intent)
    }

    fun composeEmail(context: Context, address: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address)))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(intent.resolveActivity(context.packageManager) != null) { "No email app is available." }
        context.startActivity(intent)
    }
}
