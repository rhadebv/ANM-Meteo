package ro.anmmeteo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ro.anmmeteo.app.data.local.OfflineCacheStorage
import ro.anmmeteo.app.data.local.PreferencesStorage
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.data.repository.WeatherRepository
import ro.anmmeteo.app.domain.model.AlertSeverity
import ro.anmmeteo.app.notification.AlertNotificationHelper
import ro.anmmeteo.app.widget.WeatherWidgetProvider

class WeatherSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesStorage(applicationContext)
        val repo = WeatherRepository(cache = OfflineCacheStorage(applicationContext))

        return try {
            val selectedName = prefs.selectedStationFlow.first()
            val stationsRes = repo.getStations(forceRefresh = true)
            val forecastRes = runCatching {
                repo.getForecast(selectedName, forceRefresh = true).data
            }.getOrDefault(emptyList())

            val warnings = runCatching {
                repo.getWarnings(forceRefresh = true).data
            }.getOrDefault(emptyList())

            val activeStation = stationsRes.data.firstOrNull { it.name == selectedName }
                ?: stationsRes.data.firstOrNull()

            if (activeStation != null) {
                repo.syncWidgetCache(activeStation, forecastRes.firstOrNull(), warnings)
                WeatherWidgetProvider.refreshAll(applicationContext)
            }

            val notificationsEnabled = prefs.notificationsEnabledFlow.first()
            if (notificationsEnabled && warnings.isNotEmpty()) {
                val seenIds = prefs.seenAlertIdsFlow.first()
                val userCounty = VerifiedStations.findByName(selectedName)?.county?.lowercase()

                val newSevereAlerts = warnings.filter { alert ->
                    alert.id !in seenIds &&
                        alert.severity != AlertSeverity.INFO &&
                        (userCounty == null ||
                            alert.body.lowercase().contains(userCounty) ||
                            alert.title.lowercase().contains(userCounty) ||
                            alert.source.equals("Avertizare generală", ignoreCase = true))
                }

                newSevereAlerts.take(2).forEach { alert ->
                    AlertNotificationHelper.notifyNewAlert(applicationContext, alert)
                }
                prefs.markAlertsAsSeen(warnings.map { it.id }.toSet())
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
