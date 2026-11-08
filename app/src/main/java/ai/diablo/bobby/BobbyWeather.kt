package ai.diablo.bobby

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object BobbyWeather {
    fun today(city: String): String {
        require(city.isNotBlank()) { "Enter a city in your profile to check today's weather." }
        fun get(url: String): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            return try { connection.inputStream.bufferedReader().use { it.readText() } }
            finally { connection.disconnect() }
        }
        val encoded = URLEncoder.encode(city, "UTF-8")
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"))
        val place = geo.optJSONArray("results")?.optJSONObject(0)
            ?: return "I couldn't find $city. Check the city name or try a nearby larger city."
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val forecast = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto"))
        val current = forecast.getJSONObject("current")
        val temp = current.optDouble("temperature_2m")
        val feels = current.optDouble("apparent_temperature")
        val humidity = current.optInt("relative_humidity_2m")
        val wind = current.optDouble("wind_speed_10m")
        val rain = current.optDouble("precipitation")
        val code = current.optInt("weather_code")
        val sky = when (code) {
            0 -> "clear sky"; 1, 2 -> "mostly clear"; 3 -> "overcast"
            45, 48 -> "fog"; 51, 53, 55 -> "drizzle"; 61, 63, 65 -> "rain"
            71, 73, 75 -> "snow"; 80, 81, 82 -> "rain showers"
            95, 96, 99 -> "thunderstorm"; else -> "mixed conditions"
        }
        return "Today in ${place.optString("name")}: ${"%.1f".format(temp)}°C, $sky. Feels like ${"%.1f".format(feels)}°C • Humidity $humidity% • Wind ${"%.0f".format(wind)} km/h • Precipitation ${"%.1f".format(rain)} mm. Live conditions from Open-Meteo."
    }
}
