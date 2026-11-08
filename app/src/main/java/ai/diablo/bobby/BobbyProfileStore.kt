package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

data class BobbyImportantDay(val title: String, val date: String)
data class BobbyProfile(
    val name: String,
    val age: Int,
    val birthday: String,
    val city: String,
    val importantDays: List<BobbyImportantDay>
) {
    fun ageOn(date: LocalDate = LocalDate.now()): Int =
        runCatching { Period.between(LocalDate.parse(birthday), date).years }.getOrDefault(age)
    fun reminders(date: LocalDate = LocalDate.now()): List<String> {
        val today = date.toString().substring(5)
        return buildList {
            if (birthday.length >= 10 && birthday.substring(5) == today) add("🎂 Today is ${name}'s birthday! Happy birthday!")
            importantDays.filter { it.date.length >= 10 && it.date.substring(5) == today }
                .forEach { add("📅 Today: ${it.title}") }
        }
    }
}

/** Stores profile and dates locally. This is personal profile data, not an AI credential store. */
class BobbyProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_profile_v1", Context.MODE_PRIVATE)

    fun load(): BobbyProfile? = runCatching {
        val raw = prefs.getString("profile", null) ?: return null
        val o = JSONObject(raw)
        val days = o.optJSONArray("importantDays") ?: JSONArray()
        BobbyProfile(
            o.optString("name"), o.optInt("age"), o.optString("birthday"), o.optString("city"),
            (0 until days.length()).mapNotNull { i -> days.optJSONObject(i)?.let {
                BobbyImportantDay(it.optString("title"), it.optString("date"))
            } }
        )
    }.getOrNull()

    fun save(profile: BobbyProfile) {
        require(profile.name.isNotBlank()) { "Enter your name." }
        require(profile.age in 1..120) { "Enter a valid age." }
        require(runCatching { LocalDate.parse(profile.birthday) }.isSuccess) { "Birthday must use YYYY-MM-DD." }
        val days = JSONArray()
        profile.importantDays.forEach { day ->
            if (day.title.isNotBlank() && runCatching { LocalDate.parse(day.date) }.isSuccess) {
                days.put(JSONObject().put("title", day.title.trim()).put("date", day.date))
            }
        }
        prefs.edit().putString("profile", JSONObject()
            .put("name", profile.name.trim()).put("age", profile.age)
            .put("birthday", profile.birthday).put("city", profile.city.trim())
            .put("importantDays", days).toString()).apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
