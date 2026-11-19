package ro.anmmeteo.app.data.local

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CachePayload(
    val json: String,
    val timestampMs: Long,
    val isFromCache: Boolean
)

class OfflineCacheStorage(context: Context) {

    private val prefs = context.getSharedPreferences("cache", Context.MODE_PRIVATE)

    fun read(key: String): Pair<String, Long>? {
        val data = prefs.getString(key, null) ?: return null
        val ts = prefs.getLong("${key}_t", 0L)
        return data to ts
    }

    fun write(key: String, json: String, timestampMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(key, json)
            .putLong("${key}_t", timestampMs)
            .apply()
    }

    fun saveWidgetSnapshot(
        location: String,
        temp: String,
        condition: String,
        details: String,
        warningBadge: String?
    ) {
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        prefs.edit()
            .putString("location", location)
            .putString("temp", temp)
            .putString("condition", condition)
            .putString("updated", "Actualizat $timeText")
            .putString("details", details)
            .putString("warning_badge", warningBadge ?: "")
            .apply()
    }

    fun formatAgeLabel(timestampMs: Long, isOffline: Boolean): String {
        if (timestampMs <= 0L) return ""
        val diffMins = ((System.currentTimeMillis() - timestampMs) / 60_000L).coerceAtLeast(0L)
        val base = when {
            diffMins == 0L -> "Actualizat chiar acum"
            diffMins < 60L -> "Actualizat acum $diffMins min"
            else -> {
                val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
                "Actualizat la $formatted"
            }
        }
        return if (isOffline) "$base (date salvate offline)" else base
    }
}
