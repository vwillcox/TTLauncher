package app.ttlauncher.weather

import android.content.Context
import app.ttlauncher.data.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale

/** One day of the forecast. [date] is as the service gives it, such as 2026-10-11. */
@Serializable
data class DayForecast(val date: String, val code: Int, val high: Double, val low: Double)

/** The weather somewhere, in Celsius and km/h whatever the widget goes on to show. */
@Serializable
data class Weather(
    /** When it was fetched, in epoch millis. */
    val at: Long,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    /** A WMO weather code. */
    val code: Int,
    val day: Boolean = true,
    /** Today first. */
    val days: List<DayForecast> = emptyList(),
)

/** A place a search turned up, with enough of where it is to tell it from others of the same name. */
class PlaceMatch(val place: Place, val region: String)

/** What a WMO weather code means, in a couple of words. */
fun conditionOf(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    in 61..65 -> "Rain"
    66, 67 -> "Freezing rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunder and hail"
    else -> "Unknown"
}

@Serializable
private class ForecastReply(val current: Current, val daily: Daily? = null) {
    @Serializable
    class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("apparent_temperature") val feelsLike: Double,
        @SerialName("relative_humidity_2m") val humidity: Double = 0.0,
        @SerialName("wind_speed_10m") val wind: Double = 0.0,
        @SerialName("weather_code") val code: Int = -1,
        @SerialName("is_day") val day: Int = 1,
    )

    @Serializable
    class Daily(
        val time: List<String> = emptyList(),
        @SerialName("weather_code") val code: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val high: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val low: List<Double?> = emptyList(),
    )
}

@Serializable
private class PlacesReply(val results: List<Result> = emptyList()) {
    @Serializable
    class Result(val name: String, val latitude: Double, val longitude: Double, val admin1: String? = null, val country: String? = null)
}

private val lenient = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Reads an Open-Meteo forecast. Throws if it isn't one. */
fun parseWeather(body: String, at: Long): Weather {
    val reply = lenient.decodeFromString<ForecastReply>(body)
    val daily = reply.daily
    // A day the service has no figures for is left out rather than shown as zeroes.
    val days = daily?.time.orEmpty().mapIndexedNotNull { i, date ->
        val high = daily?.high?.getOrNull(i) ?: return@mapIndexedNotNull null
        val low = daily.low.getOrNull(i) ?: return@mapIndexedNotNull null
        DayForecast(date, daily.code.getOrNull(i) ?: -1, high, low)
    }
    val now = reply.current
    return Weather(at, now.temperature, now.feelsLike, now.humidity.toInt(), now.wind, now.code, now.day != 0, days)
}

/** Reads an Open-Meteo place search, which leaves the list out altogether when nothing matched. */
fun parsePlaces(body: String): List<PlaceMatch> = lenient.decodeFromString<PlacesReply>(body).results.map {
    PlaceMatch(Place(it.name, it.latitude, it.longitude), listOfNotNull(it.admin1, it.country).filter { part -> part != it.name }.joinToString(", "))
}

/** Two widgets set to the same place share one report. A hundredth of a degree is about a kilometre. */
fun Place.key(): String = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)

/**
 * Weather from Open-Meteo, which needs no account or key. Like the news, it is only fetched while a
 * widget showing it is on screen and its last report has gone stale. Nothing but the chosen place is sent.
 */
class WeatherRepo(context: Context, private val http: OkHttpClient, scope: CoroutineScope) {
    private val file = File(context.cacheDir, "weather.json")
    private val fetching = HashSet<String>()

    private val _reports = MutableStateFlow<Map<String, Weather>>(emptyMap())

    /** The last report for each place, by [Place.key]. */
    val reports: StateFlow<Map<String, Weather>> = _reports.asStateFlow()

    private val restored = scope.launch(Dispatchers.IO) {
        runCatching { lenient.decodeFromString<Map<String, Weather>>(file.readText()) }.getOrNull()?.let { saved -> _reports.update { saved + it } }
    }

    /** Returns false when a report was wanted and couldn't be had. */
    suspend fun refresh(place: Place, force: Boolean = false): Boolean {
        restored.join()
        val key = place.key()
        val fresh = System.currentTimeMillis() - (_reports.value[key]?.at ?: 0) < STALE_AFTER_MS
        if (fresh && !force) return true
        if (!synchronized(fetching) { fetching.add(key) }) return true
        try {
            val url = FORECAST.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", place.latitude.toString())
                .addQueryParameter("longitude", place.longitude.toString())
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day")
                .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "5")
                .build()
            val report = withContext(Dispatchers.IO) { runCatching { parseWeather(get(url), System.currentTimeMillis()) }.getOrNull() } ?: return false
            val all = _reports.updateAndGet { it + (key to report) }
            withContext(Dispatchers.IO) { runCatching { file.writeText(lenient.encodeToString(all)) } }
            return true
        } finally {
            synchronized(fetching) { fetching.remove(key) }
        }
    }

    /** Places by this name, likeliest first. Throws when the search couldn't be made. */
    suspend fun search(name: String): List<PlaceMatch> = withContext(Dispatchers.IO) {
        val url = PLACES.toHttpUrl().newBuilder()
            .addQueryParameter("name", name.trim())
            .addQueryParameter("count", "6")
            .addQueryParameter("language", Locale.getDefault().language)
            .addQueryParameter("format", "json")
            .build()
        parsePlaces(get(url))
    }

    private fun get(url: HttpUrl): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", "TTLauncher/0.1 (Android; weather widget)").build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.string()
        }

    private companion object {
        const val FORECAST = "https://api.open-meteo.com/v1/forecast"
        const val PLACES = "https://geocoding-api.open-meteo.com/v1/search"
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
