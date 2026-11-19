package ro.anmmeteo.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ro.anmmeteo.app.notification.AlertNotificationHelper
import ro.anmmeteo.app.worker.WeatherSyncWorker
import java.util.concurrent.TimeUnit

class AnMMeteoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AlertNotificationHelper.createNotificationChannel(this)
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "anm_meteo_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
