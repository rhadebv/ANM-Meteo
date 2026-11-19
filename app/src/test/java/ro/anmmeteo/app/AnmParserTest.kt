package ro.anmmeteo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.anmmeteo.app.data.remote.AnmApiService
import ro.anmmeteo.app.domain.model.AlertSeverity

class AnmParserTest {

    private val api = AnmApiService()

    @Test
    fun `parseStationsJson extracts ANM tempe wind and humidity accurately`() {
        val sampleJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "properties": {
                    "nume": "BRASOV GHIMBAV",
                    "tempe": "14.2",
                    "vant": "slab, 2.5 m/s",
                    "umezeala": "54",
                    "presiune": "1015.4 mb",
                    "nebulozitate": "Cer partial noros",
                    "actualizat": "19-11-2026 14:00"
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
        assertEquals(14.2, st.tempCelsius!!, 0.01)
        assertEquals(9.0, st.windKmh!!, 0.01)
        assertEquals(54, st.humidityPercent)
        assertEquals(1015.4, st.pressureHpa!!, 0.01)
        assertEquals("19-11-2026 14:00", st.observedAt)
    }

    @Test
    fun `parseWarningsJson strips HTML tags decodes entities and detects severity`() {
        val sampleAlerts = """
            [
              {
                "mesaj": "<p style=\"text-align: center;\"><strong>MESAJ 4/5</strong></p><p><strong>AVERTIZARE METEOROLOGICĂ</strong></p><p><img src=\"../wp-content/plugins/meteo/admin/images/galben.gif\" /><strong>COD GALBEN<br /> Interval de valabilitate: 20 noiembrie, ora 1 &ndash; 20 noiembrie, ora 10<br /> Fenomene vizate: intensificări ale v&acirc;ntului &icirc;n Banat</strong></p>"
              }
            ]
        """.trimIndent()

        val warnings = api.parseWarningsJson(sampleAlerts, "Avertizare generală")
        assertEquals(1, warnings.size)
        val w = warnings.first()
        assertEquals(AlertSeverity.YELLOW, w.severity)
        assertEquals("MESAJ 4/5 — AVERTIZARE METEOROLOGICĂ", w.title)
        assertFalse(w.body.contains("<p"))
        assertFalse(w.body.contains("&ndash;"))
        assertTrue(w.body.contains("1 – 20 noiembrie"))
        assertTrue(w.body.contains("vântului în Banat"))
    }

    @Test
    fun `parseForecastJson maps daily temperatures and labels`() {
        val sampleForecast = """
            {
              "data": [
                {
                  "oras": "BRASOV",
                  "tmin": "6",
                  "tmax": "16",
                  "fenomen": "Parțial noros"
                },
                {
                  "oras": "BRASOV",
                  "tmin": "7",
                  "tmax": "15",
                  "fenomen": "Ploaie slabă"
                }
              ]
            }
        """.trimIndent()

        val forecast = api.parseForecastJson(sampleForecast, "BRASOV GHIMBAV")
        assertEquals(2, forecast.size)
        assertEquals("Astăzi", forecast[0].dayLabel)
        assertEquals("Mâine", forecast[1].dayLabel)
        assertEquals(6.0, forecast[0].minTemp!!, 0.01)
        assertEquals(16.0, forecast[0].maxTemp!!, 0.01)
    }
}
