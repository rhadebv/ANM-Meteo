package ro.anmmeteo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            val location = prefs.getString("widget_location", "ANM Meteo") ?: "ANM Meteo"
            val temperature = prefs.getString("widget_temp", "--°") ?: "--°"
            val condition = prefs.getString("widget_condition", "Deschide aplicația pentru actualizare")
                ?: "Deschide aplicația pentru actualizare"
            val updated = prefs.getString("widget_updated", "Date ANM") ?: "Date ANM"

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.weather_widget)
            views.setTextViewText(R.id.widget_location, location)
            views.setTextViewText(R.id.widget_temperature, temperature)
            views.setTextViewText(R.id.widget_condition, condition)
            views.setTextViewText(R.id.widget_updated, updated)
            views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
