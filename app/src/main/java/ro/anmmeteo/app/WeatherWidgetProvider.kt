package ro.anmmeteo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { render(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, manager, id)
    companion object {
        fun refreshAll(context: Context) {
            val m=AppWidgetManager.getInstance(context)
            m.getAppWidgetIds(ComponentName(context,WeatherWidgetProvider::class.java)).forEach { render(context,m,it) }
        }
        private fun render(c:Context,m:AppWidgetManager,id:Int) {
            val p=c.getSharedPreferences("cache",Context.MODE_PRIVATE)
            val o=m.getAppWidgetOptions(id); val compact=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,60)<110
            val layout=if(compact) R.layout.widget_compact else R.layout.widget_expanded
            val v=RemoteViews(c.packageName,layout)
            val condition=p.getString("condition","Deschide aplicația pentru actualizare")?:""
            v.setTextViewText(R.id.location,p.getString("location","ANM Meteo"))
            v.setTextViewText(R.id.temp,p.getString("temp","--°"))
            v.setTextViewText(R.id.condition,condition)
            v.setImageViewResource(R.id.icon,WeatherIcons.resource(condition))
            if(!compact){v.setTextViewText(R.id.updated,p.getString("updated","Date ANM"));v.setTextViewText(R.id.details,p.getString("details",""))}
            val pi=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.root,pi);m.updateAppWidget(id,v)
        }
    }
}

object WeatherIcons {
    fun resource(text:String):Int { val s=text.lowercase()
        return when { listOf("furtun","oraj","fulger").any{s.contains(it)}->R.drawable.ic_weather_storm
            listOf("ninso","zăpad","zapad").any{s.contains(it)}->R.drawable.ic_weather_snow
            listOf("ploa","averse","burni").any{s.contains(it)}->R.drawable.ic_weather_rain
            listOf("ceață","ceata","negură","negura").any{s.contains(it)}->R.drawable.ic_weather_fog
            listOf("senin","însorit","insorit").any{s.contains(it)}->R.drawable.ic_weather_sun
            else->R.drawable.ic_weather_cloud }
    }
}
