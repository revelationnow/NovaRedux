package com.supernova.launcher.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// The conditions the search bar's weather button can show. NovaRedux has no
// internet permission: the reading comes from a weather app the user has
// installed and allowed (BreezyWeather).
enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, THUNDER, FOG, WIND }

data class WeatherNow(
    val temperature: Double,
    // "c", "f" or "k", as the weather app reports it in its own setting.
    val unit: String,
    val condition: WeatherCondition,
    val daylight: Boolean
) {
    // "21°", or "294 K" in kelvin.
    fun text(): String = if (unit == KELVIN) "${temperature.roundToInt()} K" else "${temperature.roundToInt()}°"

    private companion object {
        const val KELVIN = "k"
    }
}

// Breezy Weather's shared weather for one location, the JSON its content
// provider hands out (breezy-weather-data-sharing-lib, provider version 0.x),
// read down to what the bar shows: the current temperature and condition,
// and whether it is day there now.
internal object BreezyWeatherJson {

    @Serializable
    private class Weather(val current: Current? = null, val hourly: List<Hour>? = null)

    @Serializable
    private class Current(val weatherCode: String? = null, val temperature: Temperatures? = null)

    @Serializable
    private class Temperatures(val temperature: Measure? = null)

    @Serializable
    private class Measure(val value: Double? = null, val unit: String? = null)

    @Serializable
    private class Hour(val date: Long = 0, val isDaylight: Boolean = true)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Null when the text isn't the shape expected or holds no current
    // temperature. `now` is milliseconds since the epoch.
    fun parse(text: String, now: Long = System.currentTimeMillis()): WeatherNow? {
        val weather = runCatching { json.decodeFromString(Weather.serializer(), text) }.getOrNull()
        val current = weather?.current
        val temperature = current?.temperature?.temperature
        val value = temperature?.value ?: return null
        // The hour that has begun most recently; before the first, the first.
        val hours = weather?.hourly.orEmpty()
        val hour = hours.lastOrNull { it.date <= now } ?: hours.firstOrNull()
        return WeatherNow(
            temperature = value,
            unit = temperature?.unit?.lowercase() ?: "c",
            condition = current?.weatherCode?.lowercase()?.let(CODES::get) ?: WeatherCondition.CLOUDY,
            daylight = hour?.isDaylight ?: true
        )
    }

    // Breezy Weather's WeatherCode ids.
    private val CODES = mapOf(
        "clear" to WeatherCondition.CLEAR,
        "partly_cloudy" to WeatherCondition.PARTLY_CLOUDY,
        "cloudy" to WeatherCondition.CLOUDY,
        "rain" to WeatherCondition.RAIN,
        "sleet" to WeatherCondition.RAIN,
        "snow" to WeatherCondition.SNOW,
        "hail" to WeatherCondition.SNOW,
        "thunder" to WeatherCondition.THUNDER,
        "thunderstorm" to WeatherCondition.THUNDER,
        "fog" to WeatherCondition.FOG,
        "haze" to WeatherCondition.FOG,
        "wind" to WeatherCondition.WIND
    )
}
