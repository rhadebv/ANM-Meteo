package ro.anmmeteo.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.anmmeteo.app.data.local.CachePayload
import ro.anmmeteo.app.data.local.OfflineCacheStorage
import ro.anmmeteo.app.data.remote.AnmApiService
import ro.anmmeteo.app.domain.model.DailyForecast
import ro.anmmeteo.app.domain.model.WeatherAlert
import ro.anmmeteo.app.domain.model.WeatherStation
import ro.anmmeteo.app.domain.util.WeatherMath

data class RepoResult<T>(
    val data: T,
    val timestampMs: Long,
    val isFromOfflineCache: Boolean
)

class WeatherRepository(
    private val api: AnmApiService = AnmApiService(),
    private val cache: OfflineCacheStorage
) {
    suspend fun getStations(forceRefresh: Boolean): RepoResult<List<WeatherStation>> {
        val payload = fetchWithCache("stations", maxAgeMinutes = 30, forceRefresh = forceRefresh) {
            api.fetchRaw(AnmApiService.URL_STATIONS)
        }
        val parsed = api.parseStationsJson(payload.json)
        return RepoResult(parsed, payload.timestampMs, payload.isFromCache)
    }

    suspend fun getForecast(stationName: String, forceRefresh: Boolean): RepoResult<List<DailyForecast>> {
        val payload = fetchWithCache("forecast", maxAgeMinutes = 30, forceRefresh = forceRefresh) {
            api.fetchRaw(AnmApiService.URL_FORECAST)
        }
        val parsed = api.parseForecastJson(payload.json, stationName)
        return RepoResult(parsed, payload.timestampMs, payload.isFromCache)
    }

    suspend fun getWarnings(forceRefresh: Boolean): RepoResult<List<WeatherAlert>> {
        val general = fetchWithCache("warn_general", maxAgeMinutes = 15, forceRefresh = forceRefresh) {
            api.fetchRaw(AnmApiService.URL_WARNINGS_GENERAL)
        }
        val nowcasting = fetchWithCache("warn_now", maxAgeMinutes = 15, forceRefresh = forceRefresh) {
            api.fetchRaw(AnmApiService.URL_WARNINGS_NOWCASTING)
        }
        val combined = (
            api.parseWarningsJson(general.json, "Avertizare generală") +
                api.parseWarningsJson(nowcasting.json, "Nowcasting")
            )
            .distinctBy { it.title + it.body }
            .sortedByDescending { it.severity.priority }

        val oldestTs = minOf(general.timestampMs, nowcasting.timestampMs)
        val offline = general.isFromCache || nowcasting.isFromCache
        return RepoResult(combined, oldestTs, offline)
    }

    fun formatCacheTimestamp(timestampMs: Long, isOffline: Boolean): String {
        return cache.formatAgeLabel(timestampMs, isOffline)
    }

    fun syncWidgetCache(
        station: WeatherStation,
        todayForecast: DailyForecast?,
        activeAlerts: List<WeatherAlert>
    ) {
        val details = if (todayForecast != null) {
            "${todayForecast.dayLabel}: ${WeatherMath.formatTemp(todayForecast.minTemp)} / ${WeatherMath.formatTemp(todayForecast.maxTemp)} • Umiditate ${station.humidityRaw}%"
        } else {
            "Umiditate ${station.humidityRaw}% • Vânt ${station.windRaw}"
        }
        val topAlert = activeAlerts.maxByOrNull { it.severity.priority }
        val badge = topAlert?.let { "${it.severity.label}: ${it.title}" }

        cache.saveWidgetSnapshot(
            location = station.displayName,
            temp = WeatherMath.formatTemp(station.tempCelsius ?: WeatherMath.parseNumber(station.tempRaw)),
            condition = station.condition,
            details = details,
            warningBadge = badge
        )
    }

    private suspend fun fetchWithCache(
        key: String,
        maxAgeMinutes: Int,
        forceRefresh: Boolean,
        fetcher: () -> String
    ): CachePayload = withContext(Dispatchers.IO) {
        val cached = cache.read(key)
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && (now - cached.second) < maxAgeMinutes * 60_000L) {
            return@withContext CachePayload(cached.first, cached.second, isFromCache = false)
        }
        try {
            val fresh = fetcher()
            cache.write(key, fresh, now)
            CachePayload(fresh, now, isFromCache = false)
        } catch (e: Exception) {
            if (cached != null) {
                CachePayload(cached.first, cached.second, isFromCache = true)
            } else {
                throw e
            }
        }
    }
}
