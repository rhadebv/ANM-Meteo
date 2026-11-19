package ro.anmmeteo.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import ro.anmmeteo.app.domain.model.AlertSeverity
import ro.anmmeteo.app.domain.model.DailyForecast
import ro.anmmeteo.app.domain.model.WeatherAlert
import ro.anmmeteo.app.domain.model.WeatherStation
import ro.anmmeteo.app.domain.util.WeatherMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnmApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val URL_STATIONS = "https://www.meteoromania.ro/wp-json/meteoapi/v2/starea-vremii"
        const val URL_FORECAST = "https://www.meteoromania.ro/wp-json/meteoapi/v2/prognoza-orase"
        const val URL_WARNINGS_GENERAL = "https://www.meteoromania.ro/wp-json/meteoapi/v2/avertizari-generale"
        const val URL_WARNINGS_NOWCASTING = "https://www.meteoromania.ro/wp-json/meteoapi/v2/avertizari-nowcasting"
    }

    fun fetchRaw(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 ANMMeteo/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Eroare HTTP ${response.code}")
            }
            return response.body?.string() ?: error("Răspuns gol de la serverul ANM")
        }
    }

    fun parseStationsJson(raw: String): List<WeatherStation> {
        val trimmed = raw.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("features")
                ?: root.optJSONArray("data")
                ?: JSONArray()
        }

        val out = mutableListOf<WeatherStation>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val props = entry.optJSONObject("properties") ?: entry

            val rawName = firstString(props, "nume", "name", "statie", "station", "denumire").uppercase(Locale.US)
            if (rawName == "—" || rawName.isBlank()) continue

            val tempRaw = firstString(props, "temperatura", "temp", "temperature")
            val windRaw = firstString(props, "vant", "wind", "viteza_vant")
            val humRaw = firstString(props, "umezeala", "umiditate", "humidity")
            val pressRaw = firstString(props, "presiune", "pressure")
            val condRaw = firstString(props, "nebulozitate", "cloud", "stare_vreme", "fenomen")
            val dateRaw = firstString(props, "data", "date", "timestamp", "ora")

            out += WeatherStation(
                name = rawName,
                displayName = WeatherMath.cleanStationDisplayName(rawName),
                tempRaw = tempRaw,
                tempCelsius = WeatherMath.parseNumber(tempRaw),
                windRaw = windRaw,
                windKmh = WeatherMath.parseNumber(windRaw),
                humidityRaw = humRaw,
                humidityPercent = WeatherMath.parseNumber(humRaw)?.toInt(),
                pressureRaw = pressRaw,
                pressureHpa = WeatherMath.parseNumber(pressRaw),
                condition = if (condRaw == "—") "Cer variabil" else condRaw.replaceFirstChar { it.uppercase() },
                observedAt = dateRaw
            )
        }
        return out.sortedBy { it.name }
    }

    fun parseForecastJson(raw: String, stationName: String): List<DailyForecast> {
        val allObjects = collectObjects(raw)
        val key = stationName.substringBefore(" ").lowercase(Locale.US)

        val filtered = allObjects.filter { obj ->
            val city = firstString(obj, "oras", "localitate", "nume", "name", "city").lowercase(Locale.US)
            city == "—" || city.contains(key) || key.contains(city)
        }

        val rows = filtered.mapNotNull { obj ->
            val minRaw = firstString(obj, "tmin", "temp_min", "min", "temperatura_minima")
            val maxRaw = firstString(obj, "tmax", "temp_max", "max", "temperatura_maxima")
            if (minRaw == "—" && maxRaw == "—") {
                null
            } else {
                val rawDate = firstString(obj, "data", "date", "zi", "day", "valabilitate")
                val cond = firstString(obj, "fenomen", "descriere", "description", "stare", "weather")
                DailyForecast(
                    dayLabel = if (rawDate == "—") "" else rawDate,
                    condition = if (cond == "—") "Prognoză ANM" else cond.replaceFirstChar { it.uppercase() },
                    minTemp = WeatherMath.parseNumber(minRaw),
                    maxTemp = WeatherMath.parseNumber(maxRaw),
                    minRaw = minRaw,
                    maxRaw = maxRaw
                )
            }
        }
            .distinctBy { "${it.minRaw}|${it.maxRaw}|${it.condition}" }
            .take(7)

        return rows.mapIndexed { index, item ->
            item.copy(dayLabel = formatDayLabel(item.dayLabel, index))
        }
    }

    fun parseWarningsJson(raw: String, source: String): List<WeatherAlert> {
        val allObjects = collectObjects(raw)
        return allObjects.mapNotNull { obj ->
            val title = firstString(obj, "titlu", "title", "headline", "fenomen", "mesaj")
            val body = firstString(obj, "descriere", "description", "text", "zone", "continut")
            val interval = firstString(obj, "valabilitate", "interval", "data_expirare", "expira")

            if (title == "—" && body == "—") {
                null
            } else {
                val resolvedTitle = if (title == "—") source else title
                val resolvedBody = if (body == "—") "" else body
                val combinedUpper = (resolvedTitle + " " + resolvedBody).uppercase(Locale.US)
                val severity = when {
                    combinedUpper.contains("ROȘU") || combinedUpper.contains("ROSU") -> AlertSeverity.RED
                    combinedUpper.contains("PORTOCALIU") -> AlertSeverity.ORANGE
                    combinedUpper.contains("GALBEN") -> AlertSeverity.YELLOW
                    else -> AlertSeverity.INFO
                }
                val hashId = "${source}_${resolvedTitle}_${resolvedBody.take(64)}".hashCode().toString()
                WeatherAlert(
                    id = hashId,
                    severity = severity,
                    title = resolvedTitle,
                    body = resolvedBody,
                    source = source,
                    validInterval = if (interval == "—") "" else interval
                )
            }
        }
            .distinctBy { it.title + it.body }
            .sortedByDescending { it.severity.priority }
            .take(30)
    }

    private fun collectObjects(raw: String): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    list += node
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        walk(node.opt(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))
                    }
                }
            }
        }
        runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                walk(JSONArray(trimmed))
            } else {
                walk(JSONObject(trimmed))
            }
        }
        return list
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = obj.opt(k)
            if (v != null && v !is JSONObject && v !is JSONArray) {
                val str = v.toString().trim()
                if (str.isNotEmpty() && str != "null") return str
            }
        }
        return "—"
    }

    private fun formatDayLabel(raw: String, index: Int): String {
        if (raw.isNotBlank() && raw.lowercase(Locale("ro", "RO")) != "prognoză") {
            return raw
        }
        val date = LocalDate.now().plusDays(index.toLong())
        return when (index) {
            0 -> "Astăzi"
            1 -> "Mâine"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("ro", "RO")))
                .replaceFirstChar { it.uppercase() }
        }
    }
}
