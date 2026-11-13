package ai.diablo.bobby

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.content.pm.PackageManager

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

    /** Resolves one unambiguous contact entry. Never guesses between similar names. */
    private fun findContact(context: Context, requestedName: String): Pair<String, String> {
        require(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            "BOBBY needs Contacts permission before I can find that person."
        }
        val query = requestedName.trim()
        require(query.isNotBlank()) { "Tell me the contact name you want to call." }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        val matches = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(uri, projection, selection, args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < 20) {
                val name = cursor.getString(nameIndex).orEmpty()
                val number = cursor.getString(numberIndex).orEmpty()
                if (name.isNotBlank() && number.isNotBlank() && matches.none { it.second == number }) matches.add(name to number)
            }
        }
        require(matches.isNotEmpty()) { "I couldn't find a phone number for '$query' in your contacts." }
        require(matches.size == 1) { "I found ${matches.size} matching contacts for '$query'. Please use the full contact name so I don't choose the wrong person." }
        return matches.single()
    }

    /** Places a call only after Android has granted CALL_PHONE for this app. */
    fun callContact(context: Context, requestedName: String): String {
        require(context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            "Android phone permission is required before BOBBY can place a call."
        }
        val (name, number) = findContact(context, requestedName)
        val call = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(call.resolveActivity(context.packageManager) != null) { "No phone calling app is available." }
        context.startActivity(call)
        return name
    }

    /** Fallback: opens the system dialer without placing the call. */
    fun dialContact(context: Context, requestedName: String): String {
        val (name, number) = findContact(context, requestedName)
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(dial.resolveActivity(context.packageManager) != null) { "No phone dialer is available." }
        context.startActivity(dial)
        return name
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
