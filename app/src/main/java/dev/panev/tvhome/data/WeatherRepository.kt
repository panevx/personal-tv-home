package dev.panev.tvhome.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Weather(val tempC: Double, val code: Int, val isDay: Boolean, val place: String) {
    val condition: String
        get() = when (code) {
            0 -> if (isDay) "Clear" else "Clear night"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            85, 86 -> "Snow showers"
            in 95..99 -> "Thunderstorms"
            else -> ""
        }
}

/** Open-Meteo forecast (no key). Location comes from a named city, or from the network's IP when none is set. */
class WeatherRepository {

    suspend fun fetch(city: String?): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon, place) = if (city.isNullOrBlank()) locateByIp() else geocode(city) ?: return@runCatching null
            val o = getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day"
            ).getJSONObject("current")
            Weather(
                tempC = o.getDouble("temperature_2m"),
                code = o.getInt("weather_code"),
                isDay = o.optInt("is_day", 1) == 1,
                place = place,
            )
        }.getOrNull()
    }

    private fun locateByIp(): Triple<String, String, String> {
        val o = getJson("https://get.geojs.io/v1/ip/geo.json")
        return Triple(o.getString("latitude"), o.getString("longitude"), o.optString("city"))
    }

    private fun geocode(city: String): Triple<String, String, String>? {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val results = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1").optJSONArray("results")
            ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return Triple(r.getDouble("latitude").toString(), r.getDouble("longitude").toString(), r.optString("name", city))
    }

    private fun getJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
