package ro.anmmeteo.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.domain.model.ThemeMode
import ro.anmmeteo.app.domain.model.WeatherStation
import ro.anmmeteo.app.domain.model.WeatherUiState
import ro.anmmeteo.app.domain.util.WeatherMath
import ro.anmmeteo.app.ui.components.ActiveAlertBanner
import ro.anmmeteo.app.ui.components.DailyForecastCard
import ro.anmmeteo.app.ui.components.GlassCard
import ro.anmmeteo.app.ui.components.HeroWeatherSection
import ro.anmmeteo.app.ui.components.HourlyForecastCard
import ro.anmmeteo.app.ui.components.SecondaryMetricsGrid
import ro.anmmeteo.app.ui.theme.LocalAtmosphericColors
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    state: WeatherUiState,
    onSelectStation: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onGpsLocation: (Location) -> Unit,
    onGpsStatus: (String) -> Unit,
    onCycleTheme: () -> Unit,
    onOpenStationsTab: () -> Unit,
    onOpenAlertsTab: () -> Unit
) {
    val atm = LocalAtmosphericColors.current
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            requestFreshGps(context, onGpsLocation, onGpsStatus)
        } else {
            onGpsStatus("Permisiunea de localizare a fost refuzată.")
        }
    }

    val pagerStationNames = remember(state.selectedStationName, state.favoriteStations) {
        val others = state.favoriteStations.filter { it != state.selectedStationName }.sorted()
        listOf(state.selectedStationName) + others
    }

    val pagerState = rememberPagerState(pageCount = { pagerStationNames.size })

    LaunchedEffect(state.selectedStationName) {
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    LaunchedEffect(pagerState, pagerStationNames) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settledStation = pagerStationNames.getOrNull(page)
            if (settledStation != null && settledStation != state.selectedStationName) {
                onSelectStation(settledStation)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted) {
                        requestFreshGps(context, onGpsLocation, onGpsStatus)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Localizare GPS",
                    tint = atm.primaryText
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val activeName = pagerStationNames.getOrNull(pagerState.currentPage) ?: state.selectedStationName
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = atm.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = WeatherMath.cleanStationDisplayName(activeName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = atm.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (pagerStationNames.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(pagerStationNames.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) atm.primaryText
                                        else atm.primaryText.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onOpenStationsTab) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Caută stație",
                    tint = atm.primaryText
                )
            }

            IconButton(onClick = onCycleTheme) {
                val themeIcon = when (state.themeMode) {
                    ThemeMode.AUTO -> Icons.Default.BrightnessAuto
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                }
                Icon(
                    imageVector = themeIcon,
                    contentDescription = "Temă: ${state.themeMode.label}",
                    tint = atm.primaryText
                )
            }

            IconButton(onClick = onRefresh) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = atm.primaryText
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizează",
                        tint = atm.primaryText
                    )
                }
            }
        }

        if (state.gpsStatusText.isNotBlank()) {
            Text(
                text = state.gpsStatusText,
                style = MaterialTheme.typography.labelMedium,
                color = atm.secondaryText,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        if (state.isLoading && state.stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = atm.primaryText)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Se încarcă datele oficiale ANM...", color = atm.primaryText)
                }
            }
        } else if (state.stations.isEmpty() && state.errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(modifier = Modifier.padding(20.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nu s-au putut prelua datele ANM",
                            style = MaterialTheme.typography.titleMedium,
                            color = atm.primaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = atm.secondaryText
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onRefresh) {
                            Text("Reîncearcă")
                        }
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val stationName = pagerStationNames[page]
                val station = state.stations.firstOrNull { it.name == stationName }
                    ?: state.stations.firstOrNull()

                if (station != null) {
                    StationWeatherPage(
                        station = station,
                        isFavorite = state.favoriteStations.contains(station.name),
                        forecasts = state.forecastsByStation[station.name].orEmpty(),
                        warnings = state.warnings,
                        lastUpdatedText = state.lastUpdatedText,
                        isOffline = state.isOfflineData,
                        onToggleFavorite = { onToggleFavorite(station.name) },
                        onOpenAlertsTab = onOpenAlertsTab
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun requestFreshGps(
    context: Context,
    onLocationFound: (Location) -> Unit,
    onStatus: (String) -> Unit
) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    onStatus("Se caută poziția GPS...")
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.time > System.currentTimeMillis() - 120_000L &&
                (!location.hasAccuracy() || location.accuracy <= 500f)
            ) {
                onLocationFound(location)
                manager.removeUpdates(this)
            }
        }

        override fun onProviderDisabled(provider: String) {
            onStatus("Activează localizarea (GPS) a telefonului.")
        }
    }

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { manager.isProviderEnabled(it) }

    if (providers.isEmpty()) {
        onStatus("Serviciile de localizare sunt oprite.")
        return
    }

    providers.forEach { provider ->
        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
    }
}

@Composable
private fun StationWeatherPage(
    station: WeatherStation,
    isFavorite: Boolean,
    forecasts: List<ro.anmmeteo.app.domain.model.DailyForecast>,
    warnings: List<ro.anmmeteo.app.domain.model.WeatherAlert>,
    lastUpdatedText: String,
    isOffline: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAlertsTab: () -> Unit
) {
    val atm = LocalAtmosphericColors.current
    val coords = remember(station.name) { VerifiedStations.findByName(station.name) }
    val nowDate = remember { LocalDate.now() }
    val nowTime = remember { LocalTime.now() }

    val solarInfo = remember(station.name, station.condition) {
        WeatherMath.calculateSolarInfo(
            lat = coords?.lat ?: 45.7,
            lon = coords?.lon ?: 25.5,
            dayOfYear = nowDate.dayOfYear,
            currentHour = nowTime.hour,
            currentMinute = nowTime.minute,
            cloudCondition = station.condition
        )
    }

    val hourlyPoints = remember(station.tempCelsius, forecasts, station.condition) {
        val today = forecasts.firstOrNull()
        WeatherMath.buildHourlyForecast(
            currentTemp = station.tempCelsius,
            todayMin = today?.minTemp,
            todayMax = today?.maxTemp,
            condition = station.condition,
            currentHour = nowTime.hour
        )
    }

    val relevantWarning = remember(warnings, coords) {
        val countyLower = coords?.county?.lowercase()
        warnings.firstOrNull { w ->
            countyLower != null && (w.body.lowercase().contains(countyLower) || w.title.lowercase().contains(countyLower))
        } ?: warnings.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isOffline) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFA000).copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mod offline • Se afișează ultimele date ANM salvate",
                        style = MaterialTheme.typography.labelMedium,
                        color = atm.primaryText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        item {
            HeroWeatherSection(
                station = station,
                todayForecast = forecasts.firstOrNull(),
                solarInfo = solarInfo,
                countyName = coords?.county
            )
        }

        if (relevantWarning != null) {
            item {
                ActiveAlertBanner(
                    alert = relevantWarning,
                    onOpenAlertsTab = onOpenAlertsTab
                )
            }
        }

        item {
            HourlyForecastCard(points = hourlyPoints)
        }

        item {
            DailyForecastCard(forecasts = forecasts)
        }

        item {
            SecondaryMetricsGrid(
                station = station,
                solarInfo = solarInfo
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lastUpdatedText.ifBlank { "Sursă: meteoromania.ro" },
                    style = MaterialTheme.typography.labelMedium,
                    color = atm.secondaryText
                )

                Surface(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(16.dp),
                    color = atm.glassSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFFD54F) else atm.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isFavorite) "În favorite" else "Adaugă la favorite",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = atm.primaryText
                        )
                    }
                }
            }
        }
    }
}
