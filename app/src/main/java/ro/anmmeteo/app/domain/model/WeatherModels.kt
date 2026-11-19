package ro.anmmeteo.app.domain.model

enum class ThemeMode(val label: String) {
    AUTO("Automat"),
    DARK("Întunecat"),
    LIGHT("Luminos")
}

enum class AlertSeverity(val label: String, val priority: Int) {
    RED("COD ROȘU", 4),
    ORANGE("COD PORTOCALIU", 3),
    YELLOW("COD GALBEN", 2),
    INFO("INFORMARE", 1)
}

enum class AppTab(val label: String) {
    HOME("Acasă"),
    STATIONS("Stații"),
    RADAR("Radar"),
    ALERTS("Alerte")
}

data class StationCoordinates(
    val name: String,
    val county: String,
    val lat: Double,
    val lon: Double
)

data class WeatherStation(
    val name: String,
    val displayName: String,
    val tempRaw: String,
    val tempCelsius: Double?,
    val windRaw: String,
    val windKmh: Double?,
    val humidityRaw: String,
    val humidityPercent: Int?,
    val pressureRaw: String,
    val pressureHpa: Double?,
    val condition: String,
    val observedAt: String
)

data class DailyForecast(
    val dayLabel: String,
    val condition: String,
    val minTemp: Double?,
    val maxTemp: Double?,
    val minRaw: String,
    val maxRaw: String
)

data class HourlyPoint(
    val hourLabel: String,
    val tempCelsius: Int,
    val condition: String,
    val isNight: Boolean
)

data class WeatherAlert(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    val source: String,
    val validInterval: String = ""
)

data class SolarInfo(
    val sunrise: String,
    val sunset: String,
    val uvIndexEstimate: Int,
    val uvLabel: String,
    val isNightNow: Boolean
)

data class WeatherUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val stations: List<WeatherStation> = emptyList(),
    val selectedStationName: String = "BRASOV GHIMBAV",
    val favoriteStations: Set<String> = emptySet(),
    val forecastsByStation: Map<String, List<DailyForecast>> = emptyMap(),
    val warnings: List<WeatherAlert> = emptyList(),
    val warningError: String? = null,
    val warningLastChecked: String = "",
    val lastUpdatedText: String = "",
    val isOfflineData: Boolean = false,
    val gpsStatusText: String = "",
    val errorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val notificationsEnabled: Boolean = true
)
