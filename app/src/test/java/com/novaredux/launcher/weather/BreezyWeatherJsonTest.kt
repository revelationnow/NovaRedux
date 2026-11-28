package com.novaredux.launcher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Breezy Weather's shared weather, in the shape its content provider hands
// out (breezy-weather-data-sharing-lib); the values are made up.
class BreezyWeatherJsonTest {

    private val hour = 3_600_000L

    private fun weather(code: String?, value: Double?, unit: String = "c", hours: String = "") = """
        {"refreshTime": 1, "current": {"weatherText": "Mostly clear", ${code?.let { "\"weatherCode\": \"$it\"," } ?: ""}
         "temperature": {"temperature": {"value": ${value ?: "null"}, "unit": "$unit"},
                         "computedApparent": {"value": 19.0, "unit": "$unit"}},
         "wind": {"speed": {"value": 3.0, "unit": "mps"}}},
         "hourly": [$hours], "sources": {"main": {"id": "openmeteo", "name": "Open-Meteo"}}}
    """.trimIndent()

    @Test
    fun readsTheCurrentTemperatureAndCondition() {
        val now = BreezyWeatherJson.parse(weather("partly_cloudy", 21.4))!!
        assertEquals(21.4, now.temperature, 0.001)
        assertEquals("c", now.unit)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, now.condition)
        assertEquals("21°", now.text())
    }

    @Test
    fun breezysOtherCodesFoldIntoTheIconsThereAre() {
        assertEquals(WeatherCondition.RAIN, BreezyWeatherJson.parse(weather("sleet", 1.0))?.condition)
        assertEquals(WeatherCondition.SNOW, BreezyWeatherJson.parse(weather("hail", 1.0))?.condition)
        assertEquals(WeatherCondition.THUNDER, BreezyWeatherJson.parse(weather("thunderstorm", 1.0))?.condition)
        assertEquals(WeatherCondition.FOG, BreezyWeatherJson.parse(weather("haze", 1.0))?.condition)
        assertEquals(WeatherCondition.CLOUDY, BreezyWeatherJson.parse(weather("volcanic_ash", 1.0))?.condition)
        assertEquals(WeatherCondition.CLOUDY, BreezyWeatherJson.parse(weather(null, 1.0))?.condition)
    }

    @Test
    fun dayOrNightComesFromTheHourUnderWay() {
        val hours = """{"date": 0, "isDaylight": true}, {"date": $hour, "isDaylight": false},
            {"date": ${2 * hour}, "isDaylight": true}"""
        assertFalse(BreezyWeatherJson.parse(weather("clear", 10.0, hours = hours), now = hour + 60_000)!!.daylight)
        assertTrue(BreezyWeatherJson.parse(weather("clear", 10.0, hours = hours), now = 2 * hour)!!.daylight)
        // Before the first hour: the first.
        assertTrue(BreezyWeatherJson.parse(weather("clear", 10.0, hours = hours), now = -hour)!!.daylight)
        // No hours at all: day.
        assertTrue(BreezyWeatherJson.parse(weather("clear", 10.0))!!.daylight)
    }

    @Test
    fun fahrenheitAndKelvinShowAsBreezyReportsThem() {
        assertEquals("70°", BreezyWeatherJson.parse(weather("clear", 70.4, unit = "f"))!!.text())
        assertEquals("294 K", BreezyWeatherJson.parse(weather("clear", 294.15, unit = "k"))!!.text())
    }

    @Test
    fun withoutATemperatureThereIsNoReading() {
        assertNull(BreezyWeatherJson.parse(weather("clear", null)))
        assertNull(BreezyWeatherJson.parse("not json"))
        assertNull(BreezyWeatherJson.parse("{}"))
    }
}
