package ro.anmmeteo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.anmmeteo.app.data.remote.AnmApiService
import ro.anmmeteo.app.domain.model.AlertSeverity

class AnmParserTest {

    private val api = AnmApiService()

    @Test
    fun `parseStationsJson extracts GeoJSON station features accurately`() {
        val sampleJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "properties": {
                    "nume": "BRASOV GHIMBAV",
                    "temperatura": "24.2",
                    "vant": "18 km/h",
                    "umezeala": "54",
                    "presiune": "1015.4",
                    "nebulozitate": "Parțial noros",
                    "data": "2026-11-19 14:00"
                  }
                }
              ]
            }
        """.trimIndent()

        val stations = api.parseStationsJson(sampleJson)
        assertEquals(1, stations.size)
        val st = stations.first()
        assertEquals("BRASOV GHIMBAV", st.name)
        assertEquals("Brașov", st.displayName)
        assertEquals(24.2, st.tempCelsius!!, 0.01)
        assertEquals(54, st.humidityPercent)
    }

    @Test
    fun `parseWarningsJson detects Cod Galben and Cod Portocaliu severities`() {
        val sampleAlerts = """
            [
              {
                "titlu": "Atenționare Cod Galben de instabilitate atmosferică",
                "descriere": "În județul Brașov se vor semnala averse torențiale.",
                "valabilitate": "19.11.2026 14:00 - 21:00"
              },
              {
                "titlu": "Avertizare Cod Portocaliu de vijelii",
                "descriere": "Intensificări puternice ale vântului.",
                "valabilitate": "19.11.2026 15:00 - 18:00"
              }
            ]
        """.trimIndent()

        val warnings = api.parseWarningsJson(sampleAlerts, "Nowcasting")
        assertEquals(2, warnings.size)
        assertEquals(AlertSeverity.ORANGE, warnings[0].severity)
        assertEquals(AlertSeverity.YELLOW, warnings[1].severity)
        assertTrue(warnings[1].body.contains("Brașov"))
    }

    @Test
    fun `parseForecastJson maps daily temperatures and labels`() {
        val sampleForecast = """
            {
              "data": [
                {
                  "oras": "BRASOV",
                  "tmin": "14",
                  "tmax": "26",
                  "fenomen": "Parțial noros"
                },
                {
                  "oras": "BRASOV",
                  "tmin": "13",
                  "tmax": "22",
                  "fenomen": "Ploaie slabă"
                }
              ]
            }
        """.trimIndent()

        val forecast = api.parseForecastJson(sampleForecast, "BRASOV GHIMBAV")
        assertEquals(2, forecast.size)
        assertEquals("Astăzi", forecast[0].dayLabel)
        assertEquals("Mâine", forecast[1].dayLabel)
        assertEquals(14.0, forecast[0].minTemp!!, 0.01)
        assertEquals(26.0, forecast[0].maxTemp!!, 0.01)
    }
}
