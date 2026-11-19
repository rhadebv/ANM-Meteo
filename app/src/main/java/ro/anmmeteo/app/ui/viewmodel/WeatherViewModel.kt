package ro.anmmeteo.app.ui.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.anmmeteo.app.data.local.OfflineCacheStorage
import ro.anmmeteo.app.data.local.PreferencesStorage
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.data.repository.WeatherRepository
import ro.anmmeteo.app.domain.model.ThemeMode
import ro.anmmeteo.app.domain.model.WeatherUiState
import ro.anmmeteo.app.widget.WeatherWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsStorage = PreferencesStorage(application)
    private val repository = WeatherRepository(cache = OfflineCacheStorage(application))

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefsStorage.selectedStationFlow,
                prefsStorage.favoriteStationsFlow,
                prefsStorage.themeModeFlow,
                prefsStorage.notificationsEnabledFlow
            ) { selected, favorites, theme, notifEnabled ->
                QuadPrefs(selected, favorites, theme, notifEnabled)
            }.collect { p ->
                val stationChanged = _uiState.value.selectedStationName != p.selected
                _uiState.update {
                    it.copy(
                        selectedStationName = p.selected,
                        favoriteStations = p.favorites,
                        themeMode = p.theme,
                        notificationsEnabled = p.notifEnabled
                    )
                }
                if (stationChanged) {
                    loadForecastFor(p.selected, forceRefresh = false)
                }
            }
        }
        refreshAll(forceRefresh = false)
    }

    fun refreshAll(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.stations.isEmpty(),
                    isRefreshing = it.stations.isNotEmpty(),
                    errorMessage = null
                )
            }

            runCatching {
                repository.getStations(forceRefresh)
            }.onSuccess { result ->
                val updatedLabel = repository.formatCacheTimestamp(result.timestampMs, result.isFromOfflineCache)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        stations = result.data,
                        lastUpdatedText = updatedLabel,
                        isOfflineData = result.isFromOfflineCache,
                        errorMessage = null
                    )
                }
                val currentStation = _uiState.value.selectedStationName
                loadForecastFor(currentStation, forceRefresh)
                loadWarnings(forceRefresh)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = err.message ?: "Eroare necunoscută de rețea"
                    )
                }
                loadWarnings(forceRefresh)
            }
        }
    }

    fun selectStation(stationName: String) {
        viewModelScope.launch {
            prefsStorage.setSelectedStation(stationName)
            loadForecastFor(stationName, forceRefresh = false)
        }
    }

    fun toggleFavorite(stationName: String) {
        viewModelScope.launch {
            prefsStorage.toggleFavoriteStation(stationName)
        }
    }

    fun cycleThemeMode() {
        viewModelScope.launch {
            val next = when (_uiState.value.themeMode) {
                ThemeMode.AUTO -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.AUTO
            }
            prefsStorage.setThemeMode(next)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsStorage.setNotificationsEnabled(enabled)
        }
    }

    fun onGpsLocationFound(location: Location) {
        val nearestPair = VerifiedStations.findNearest(location.latitude, location.longitude) ?: return
        val (station, distanceKm) = nearestPair
        if (distanceKm > 250.0) {
            _uiState.update {
                it.copy(
                    gpsStatusText = "Poziția GPS este în afara României (${distanceKm.roundToInt()} km până la ${station.name})."
                )
            }
            return
        }
        selectStation(station.name)
        _uiState.update {
            it.copy(
                gpsStatusText = "${station.name} • ${String.format(Locale.US, "%.1f", distanceKm)} km • precizie ${location.accuracy.roundToInt()} m"
            )
        }
    }

    fun setGpsStatus(status: String) {
        _uiState.update { it.copy(gpsStatusText = status) }
    }

    private fun loadForecastFor(stationName: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.getForecast(stationName, forceRefresh)
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        forecastsByStation = state.forecastsByStation + (stationName to result.data)
                    )
                }
                syncWidgetWithLatestState()
            }
        }
    }

    private fun loadWarnings(forceRefresh: Boolean) {
        viewModelScope.launch {
            val checkedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            runCatching {
                repository.getWarnings(forceRefresh)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        warnings = result.data,
                        warningError = null,
                        warningLastChecked = checkedAt
                    )
                }
                prefsStorage.markAlertsAsSeen(result.data.map { w -> w.id }.toSet())
                syncWidgetWithLatestState()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        warningError = "Conexiunea la fluxurile de avertizări ANM a eșuat: ${err.message}",
                        warningLastChecked = checkedAt
                    )
                }
            }
        }
    }

    private fun syncWidgetWithLatestState() {
        val st = _uiState.value
        val active = st.stations.firstOrNull { it.name == st.selectedStationName } ?: return
        val todayForecast = st.forecastsByStation[active.name]?.firstOrNull()
        repository.syncWidgetCache(active, todayForecast, st.warnings)
        WeatherWidgetProvider.refreshAll(getApplication())
    }

    private data class QuadPrefs(
        val selected: String,
        val favorites: Set<String>,
        val theme: ThemeMode,
        val notifEnabled: Boolean
    )
}
