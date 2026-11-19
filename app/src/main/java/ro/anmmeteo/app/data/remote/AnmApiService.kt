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

            val tempRaw = firstStringMatching(
                props,
                exactKeys = listOf("tempe", "temperatura", "temp", "temperature"),
                prefixes = listOf("temp")
            )
            val windRaw = firstStringMatching(
                props,
                exactKeys = listOf("vant", "viteza_vant", "wind", "vânt"),
                prefixes = listOf("vant", "wind")
            )
            val humRaw = firstStringMatching(
                props,
                exactKeys = listOf("umezeala", "umiditate", "humidity", "ur"),
                prefixes = listOf("umez", "umid", "humid")
            )
            val pressRaw = firstStringMatching(
                props,
                exactKeys = listOf("presiune", "pressure", "pres"),
                prefixes = listOf("presi", "press")
            )
            val condRaw = firstStringMatching(
                props,
                exactKeys = listOf("nebulozitate", "cloud", "stare_vreme", "fenomen", "fenomen_e"),
                prefixes = listOf("nebul", "fenomen", "stare")
            )
            val dateRaw = firstStringMatching(
                props,
                exactKeys = listOf("actualizat", "data", "date", "timestamp", "ora"),
                prefixes = listOf("actualiz", "data", "ora")
            )

            out += WeatherStation(
                name = rawName,
                displayName = WeatherMath.cleanStationDisplayName(rawName),
                tempRaw = tempRaw,
                tempCelsius = WeatherMath.parseNumber(tempRaw),
                windRaw = windRaw,
                windKmh = WeatherMath.parseWindKmh(windRaw),
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
                val cond = firstString(obj, "fenomen", "prognoza", "descriere", "description", "stare", "weather")
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
            val rawTitle = firstString(obj, "titlu", "title", "headline", "fenomen", "mesaj")
            val rawBody = firstString(obj, "descriere", "description", "text", "zone", "continut", "mesaj")
            val rawInterval = firstString(obj, "valabilitate", "interval", "data_expirare", "expira")
            val rawColor = firstString(obj, "culoare", "cod", "nivel", "severity", "tip")

            if (rawTitle == "—" && rawBody == "—") {
                null
            } else {
                val cleanTitleAll = WeatherMath.cleanHtmlText(if (rawTitle == "—") "" else rawTitle)
                val cleanBodyAll = WeatherMath.cleanHtmlText(if (rawBody == "—") "" else rawBody)
                val cleanInterval = WeatherMath.cleanHtmlText(if (rawInterval == "—") "" else rawInterval)

                val (resolvedTitle, resolvedBody) = splitCleanTitleAndBody(
                    cleanTitle = cleanTitleAll,
                    cleanBody = cleanBodyAll,
                    fallbackSource = source
                )

                val severitySourceText = buildString {
                    if (rawColor != "—") append(rawColor).append(' ')
                    append(resolvedTitle).append(' ').append(resolvedBody)
                }.uppercase(Locale.US)

                val severity = when {
                    severitySourceText.contains("ROȘU") || severitySourceText.contains("ROSU") -> AlertSeverity.RED
                    severitySourceText.contains("PORTOCALIU") -> AlertSeverity.ORANGE
                    severitySourceText.contains("GALBEN") -> AlertSeverity.YELLOW
                    else -> AlertSeverity.INFO
                }
                val hashId = "${source}_${resolvedTitle}_${resolvedBody.take(64)}".hashCode().toString()
                WeatherAlert(
                    id = hashId,
                    severity = severity,
                    title = resolvedTitle,
                    body = resolvedBody,
                    source = source,
                    validInterval = cleanInterval
                )
            }
        }
            .distinctBy { it.title + it.body }
            .sortedByDescending { it.severity.priority }
            .take(30)
    }

    private fun splitCleanTitleAndBody(
        cleanTitle: String,
        cleanBody: String,
        fallbackSource: String
    ): Pair<String, String> {
        val primary = cleanTitle.ifBlank { cleanBody }
        if (primary.isBlank()) return fallbackSource to ""

        val lines = primary.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTitle.isNotBlank() && cleanBody.isNotBlank() && cleanTitle != cleanBody) {
            val shortTitle = if (cleanTitle.length <= 120 && !cleanTitle.contains('\n')) {
                cleanTitle
            } else {
                lines.take(2).joinToString(" — ").take(120)
            }
            return shortTitle to cleanBody
        }

        if (lines.size == 1) {
            val single = lines.first()
            return if (single.length <= 120) {
                single to ""
            } else {
                single.take(117) + "..." to single
            }
        }

        val headerLines = if (lines.size >= 2 && lines[0].length + lines[1].length <= 110) {
            lines.take(2).joinToString(" — ")
        } else {
            lines.first().take(120)
        }
        val bodyStartIndex = if (lines.size >= 2 && lines[0].length + lines[1].length <= 110) 2 else 1
        val remainingBody = lines.drop(bodyStartIndex).joinToString("\n")
        return headerLines to remainingBody
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

    private fun firstStringMatching(
        obj: JSONObject,
        exactKeys: List<String>,
        prefixes: List<String>
    ): String {
        val exact = firstString(obj, *exactKeys.toTypedArray())
        if (exact != "—") return exact

        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            val lower = key.lowercase(Locale.US)
            if (prefixes.any { lower.startsWith(it) }) {
                val v = obj.opt(key)
                if (v != null && v !is JSONObject && v !is JSONArray) {
                    val str = v.toString().trim()
                    if (str.isNotEmpty() && str != "null") return str
                }
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
