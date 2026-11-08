package ai.diablo.bobby

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class BobbyReminder(val id: Int, val triggerAt: Long, val text: String)

/** Schedules local reminders; exact timing is used only when Android grants exact-alarm access. */
object BobbyReminderScheduler {
    private const val PREFS = "bobby_scheduled_reminders"
    private const val KEY = "items"
    private const val CHANNEL = "bobby_reminders"

    fun schedule(context: Context, delayMillis: Long, text: String): BobbyReminder {
        require(delayMillis >= 1000L) { "Reminder time must be at least one second in the future." }
        require(text.trim().isNotEmpty() && text.length <= 500) { "Enter a short reminder." }
        val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val trigger = System.currentTimeMillis() + delayMillis
        val reminder = BobbyReminder(id, trigger, text.trim())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rows = JSONArray(prefs.getString(KEY, "[]"))
        rows.put(JSONObject().put("id", id).put("at", trigger).put("text", reminder.text))
        prefs.edit().putString(KEY, rows.toString()).apply()
        scheduleAlarm(context, reminder)
        return reminder
    }

    private fun scheduleAlarm(context: Context, reminder: BobbyReminder) {
        val intent = Intent(context, BobbyReminderReceiver::class.java)
            .setAction("ai.diablo.bobby.REMINDER_" + reminder.id)
            .putExtra("reminder_id", reminder.id)
            .putExtra("reminder_text", reminder.text)
        val pending = PendingIntent.getBroadcast(context, reminder.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending)
        }
    }

    fun restore(context: Context) {
        val rows = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        val now = System.currentTimeMillis()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val item = BobbyReminder(row.optInt("id"), row.optLong("at"), row.optString("text"))
            if (item.triggerAt > now && item.text.isNotBlank()) scheduleAlarm(context, item)
        }
    }

    fun remove(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString(KEY, "[]"))
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val row = old.optJSONObject(i) ?: continue
            if (row.optInt("id") != id) next.put(row)
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun showNotification(context: Context, id: Int, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "BOBBY reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BOBBY REMINDER")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
        manager.notify(id, builder.build())
    }
}

class BobbyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminder_id", 0)
        val text = intent.getStringExtra("reminder_text").orEmpty()
        if (id != 0 && text.isNotBlank()) {
            BobbyReminderScheduler.showNotification(context, id, text)
            BobbyReminderScheduler.remove(context, id)
        }
    }
}

class BobbyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BobbyReminderScheduler.restore(context)
    }
}
