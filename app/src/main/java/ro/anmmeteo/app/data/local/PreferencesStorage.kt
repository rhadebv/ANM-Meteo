package ro.anmmeteo.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ro.anmmeteo.app.domain.model.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anm_meteo_prefs")

class PreferencesStorage(private val context: Context) {

    private object Keys {
        val SELECTED_STATION = stringPreferencesKey("selected_station")
        val FAVORITE_STATIONS = stringSetPreferencesKey("favorite_stations")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SEEN_ALERT_IDS = stringSetPreferencesKey("seen_alert_ids")
    }

    val selectedStationFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_STATION] ?: "BRASOV GHIMBAV"
    }

    val favoriteStationsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_STATIONS] ?: emptySet()
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.AUTO.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.AUTO)
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val seenAlertIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEEN_ALERT_IDS] ?: emptySet()
    }

    suspend fun setSelectedStation(stationName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_STATION] = stationName
        }
    }

    suspend fun toggleFavoriteStation(stationName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_STATIONS] ?: emptySet()
            prefs[Keys.FAVORITE_STATIONS] = if (current.contains(stationName)) {
                current - stationName
            } else {
                current + stationName
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun markAlertsAsSeen(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SEEN_ALERT_IDS] ?: emptySet()
            prefs[Keys.SEEN_ALERT_IDS] = (current + ids).toList().takeLast(150).toSet()
        }
    }
}
