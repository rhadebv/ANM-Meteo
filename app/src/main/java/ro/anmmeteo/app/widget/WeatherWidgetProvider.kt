package ro.anmmeteo.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ro.anmmeteo.app.MainActivity
import ro.anmmeteo.app.R
import ro.anmmeteo.app.ui.components.WeatherIconMapper
import ro.anmmeteo.app.worker.WeatherSyncWorker

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle
    ) {
        render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val prefs = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
            prefs.edit().putString("updated", "Se actualizează...").apply()
            refreshAll(context)

            val syncRequest = OneTimeWorkRequestBuilder<WeatherSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_manual_refresh",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "ro.anmmeteo.app.ACTION_REFRESH_WIDGET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
            val options = manager.getAppWidgetOptions(widgetId)
            val isCompact = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64) < 115

            val layoutId = if (isCompact) R.layout.widget_compact else R.layout.widget_expanded
            val views = RemoteViews(context.packageName, layoutId)

            val condition = prefs.getString("condition", "Deschide aplicația pentru actualizare") ?: ""
            views.setTextViewText(R.id.location, prefs.getString("location", "ANM Meteo"))
            views.setTextViewText(R.id.temp, prefs.getString("temp", "--°"))
            views.setTextViewText(R.id.condition, condition)
            views.setImageViewResource(R.id.icon, WeatherIconMapper.drawableFor(condition, isNight = false))

            if (!isCompact) {
                views.setTextViewText(R.id.updated, prefs.getString("updated", "Date oficiale ANM"))
                views.setTextViewText(R.id.details, prefs.getString("details", ""))

                val badge = prefs.getString("warning_badge", "") ?: ""
                if (badge.isNotBlank()) {
                    views.setViewVisibility(R.id.alert_badge, View.VISIBLE)
                    views.setTextViewText(R.id.alert_badge, "⚠ $badge")
                } else {
                    views.setViewVisibility(R.id.alert_badge, View.GONE)
                }
            }

            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.root, openAppIntent)

            val refreshIntent = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
