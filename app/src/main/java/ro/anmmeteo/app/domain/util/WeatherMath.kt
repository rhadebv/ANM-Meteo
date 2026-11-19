package ro.anmmeteo.app.domain.util

import ro.anmmeteo.app.domain.model.HourlyPoint
import ro.anmmeteo.app.domain.model.SolarInfo
import java.util.Locale
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object WeatherMath {

    private val numberRegex = Regex("-?\d+(?:[.,]\d+)?")

    fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return numberRegex.find(raw)?.value?.replace(',', '.')?.toDoubleOrNull()
    }

    fun formatTemp(raw: String): String {
        val parsed = parseNumber(raw) ?: return raw
        return "${parsed.roundToInt()}°"
    }

    fun formatTemp(value: Double?): String {
        if (value == null) return "--°"
        return "${value.roundToInt()}°"
    }

    fun cleanStationDisplayName(rawName: String): String {
        val cleaned = rawName
            .replace(" GHIMBAV", "")
            .replace(" BANEASA", "")
            .trim()
        return cleaned.lowercase(Locale("ro", "RO"))
            .split(" ", "-")
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch -> ch.titlecase(Locale("ro", "RO")) }
            }
            .replace("Targu ", "Târgu ")
            .replace("Brasov", "Brașov")
            .replace("Bucuresti", "București")
            .replace("Iasi", "Iași")
            .replace("Timisoara", "Timișoara")
            .replace("Constanta", "Constanța")
            .replace("Galati", "Galați")
            .replace("Ploiesti", "Ploiești")
            .replace("Fagaras", "Făgăraș")
            .replace("Sfantu ", "Sfântu ")
            .replace("Calarasi", "Călărași")
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun computeFeelsLike(tempC: Double?, windKmh: Double?, humidityPct: Int?): Int? {
        if (tempC == null) return null
        val wind = windKmh ?: 0.0
        val rh = (humidityPct ?: 50).toDouble()

        if (tempC <= 10.0 && wind > 4.8) {
            val vPow = wind.pow(0.16)
            val wc = 13.12 + 0.6215 * tempC - 11.37 * vPow + 0.3965 * tempC * vPow
            return wc.roundToInt()
        }

        if (tempC >= 26.0 && rh >= 40.0) {
            val hi = -8.78469475556 +
                1.61139411 * tempC +
                2.33854883889 * rh -
                0.14611605 * tempC * rh -
                0.012308094 * tempC.pow(2) -
                0.0164248277778 * rh.pow(2) +
                0.002211732 * tempC.pow(2) * rh +
                0.00072546 * tempC * rh.pow(2) -
                0.000003582 * tempC.pow(2) * rh.pow(2)
            return hi.roundToInt()
        }

        return tempC.roundToInt()
    }

    fun computeDewPoint(tempC: Double?, humidityPct: Int?): Int? {
        if (tempC == null || humidityPct == null || humidityPct <= 0) return null
        val a = 17.27
        val b = 237.7
        val alpha = ((a * tempC) / (b + tempC)) + ln(humidityPct / 100.0)
        val dew = (b * alpha) / (a - alpha)
        return dew.roundToInt()
    }

    fun windBeaufortDescription(windKmh: Double?): String {
        if (windKmh == null) return "Date ANM"
        return when {
            windKmh < 2.0 -> "Calm atmosferic"
            windKmh < 12.0 -> "Adiere ușoară"
            windKmh < 29.0 -> "Vânt slab spre moderat"
            windKmh < 50.0 -> "Vânt moderat spre tare"
            windKmh < 75.0 -> "Intensificări puternice"
            else -> "Vijelie / Furtună"
        }
    }

    fun pressureTrendDescription(pressureHpa: Double?): String {
        if (pressureHpa == null) return "Presiune la nivelul stației"
        return when {
            pressureHpa < 1005.0 -> "Scăzută • Instabilitate"
            pressureHpa <= 1022.0 -> "Normală • Stabil"
            else -> "Ridicată • Timp frumos"
        }
    }

    fun calculateSolarInfo(
        lat: Double,
        lon: Double,
        dayOfYear: Int,
        currentHour: Int,
        currentMinute: Int,
        cloudCondition: String
    ): SolarInfo {
        val latRad = Math.toRadians(lat)
        val decl = Math.toRadians(23.44) * sin(Math.toRadians((360.0 / 365.0) * (dayOfYear - 81)))
        val cosOmega = (-sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl))
        val clampedCos = cosOmega.coerceIn(-1.0, 1.0)
        val omegaHours = Math.toDegrees(acos(clampedCos)) / 15.0

        val tzOffset = if (dayOfYear in 86..299) 3.0 else 2.0
        val solarNoonUtc = 12.0 - (lon / 15.0)
        val sunriseLocal = (solarNoonUtc - omegaHours + tzOffset).coerceIn(4.0, 9.5)
        val sunsetLocal = (solarNoonUtc + omegaHours + tzOffset).coerceIn(15.5, 22.0)

        fun fmtHours(h: Double): String {
            val totalMins = (h * 60.0).roundToInt()
            val hh = (totalMins / 60).coerceIn(0, 23)
            val mm = (totalMins % 60).coerceIn(0, 59)
            return String.format(Locale.US, "%02d:%02d", hh, mm)
        }

        val nowDecimal = currentHour + (currentMinute / 60.0)
        val isNight = nowDecimal < sunriseLocal || nowDecimal >= sunsetLocal

        val uv = if (isNight) {
            0
        } else {
            val solarElevationFactor = sin(
                ((nowDecimal - sunriseLocal) / (sunsetLocal - sunriseLocal)).coerceIn(0.0, 1.0) * Math.PI
            )
            val seasonFactor = 0.55 + 0.45 * sin(Math.toRadians(((dayOfYear - 80) / 365.0) * 360.0)).coerceAtLeast(0.0)
            val cond = cloudCondition.lowercase(Locale("ro", "RO"))
            val cloudFactor = when {
                cond.contains("furtun") || cond.contains("ploa") || cond.contains("ninso") -> 0.35
                cond.contains("acoperit") || cond.contains("noros") || cond.contains("cea") -> 0.55
                cond.contains("parțial") || cond.contains("variabil") -> 0.80
                else -> 1.0
            }
            (9.0 * solarElevationFactor * seasonFactor * cloudFactor).roundToInt().coerceIn(1, 10)
        }

        val uvLabel = when (uv) {
            0 -> "Noapte (UV 0)"
            in 1..2 -> "Scăzut"
            in 3..5 -> "Moderat"
            in 6..7 -> "Ridicat"
            else -> "Foarte ridicat"
        }

        return SolarInfo(
            sunrise = fmtHours(sunriseLocal),
            sunset = fmtHours(sunsetLocal),
            uvIndexEstimate = uv,
            uvLabel = uvLabel,
            isNightNow = isNight
        )
    }

    fun buildHourlyForecast(
        currentTemp: Double?,
        todayMin: Double?,
        todayMax: Double?,
        condition: String,
        currentHour: Int
    ): List<HourlyPoint> {
        val base = currentTemp ?: 18.0
        val minT = todayMin ?: (base - 5.0)
        val maxT = todayMax ?: (base + 4.0)
        val mid = (minT + maxT) / 2.0
        val amp = max(1.5, (maxT - minT) / 2.0)

        fun diurnal(h: Int): Double {
            return mid + amp * cos(Math.toRadians((h - 15) * 15.0))
        }

        val offset = base - diurnal(currentHour)

        return (0 until 12).map { step ->
            val hour = (currentHour + step) % 24
            val interpolated = if (step == 0) {
                base.roundToInt()
            } else {
                (diurnal(hour) + offset * (1.0 - step / 14.0)).roundToInt()
            }
            val isNight = hour < 6 || hour >= 20
            HourlyPoint(
                hourLabel = String.format(Locale.US, "%02d:00", hour),
                tempCelsius = interpolated,
                condition = condition,
                isNight = isNight
            )
        }
    }

    fun normalizeThermalBar(
        dayMin: Double?,
        dayMax: Double?,
        weekMin: Double,
        weekMax: Double
    ): Pair<Float, Float> {
        val span = (weekMax - weekMin).coerceAtLeast(1.0)
        val lo = ((dayMin ?: weekMin) - weekMin) / span
        val hi = ((dayMax ?: weekMax) - weekMin) / span
        val start = lo.toFloat().coerceIn(0f, 0.92f)
        val end = max(start + 0.08f, hi.toFloat().coerceIn(0.08f, 1f))
        return start to end
    }
}
