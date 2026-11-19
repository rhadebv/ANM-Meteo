package ro.anmmeteo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.domain.util.WeatherMath

class WeatherMathTest {

    @Test
    fun `haversine distance between Brasov and Predeal is around 21 km`() {
        val brasov = VerifiedStations.findByName("BRASOV GHIMBAV")!!
        val predeal = VerifiedStations.findByName("PREDEAL")!!
        val km = WeatherMath.haversineKm(brasov.lat, brasov.lon, predeal.lat, predeal.lon)
        assertTrue("Expected ~21 km, got $km", km in 18.0..25.0)
    }

    @Test
    fun `feels like applies wind chill in freezing windy weather`() {
        val feels = WeatherMath.computeFeelsLike(tempC = -5.0, windKmh = 30.0, humidityPct = 70)
        assertNotNull(feels)
        assertTrue("Expected wind chill < -5C, got $feels", feels!! < -5)
    }

    @Test
    fun `feels like applies heat index in hot humid weather`() {
        val feels = WeatherMath.computeFeelsLike(tempC = 33.0, windKmh = 5.0, humidityPct = 65)
        assertNotNull(feels)
        assertTrue("Expected heat index > 33C, got $feels", feels!! > 33)
    }

    @Test
    fun `solar info calculates valid sunrise sunset and UV`() {
        val solar = WeatherMath.calculateSolarInfo(
            lat = 45.696,
            lon = 25.526,
            dayOfYear = 172,
            currentHour = 13,
            currentMinute = 0,
            cloudCondition = "Senin"
        )
        assertTrue(solar.sunrise.startsWith("05:"))
        assertTrue(solar.sunset.startsWith("21:"))
        assertTrue(solar.uvIndexEstimate >= 6)
    }

    @Test
    fun `hourly forecast generates 12 sequential hourly slots`() {
        val points = WeatherMath.buildHourlyForecast(
            currentTemp = 24.0,
            todayMin = 14.0,
            todayMax = 26.0,
            condition = "Parțial noros",
            currentHour = 14
        )
        assertEquals(12, points.size)
        assertEquals("14:00", points.first().hourLabel)
        assertEquals(24, points.first().tempCelsius)
    }

    @Test
    fun `thermal range bar stays within normalized bounds`() {
        val (start, end) = WeatherMath.normalizeThermalBar(12.0, 22.0, 10.0, 26.0)
        assertTrue(start in 0f..1f)
        assertTrue(end in 0f..1f)
        assertTrue(end > start)
    }
}
