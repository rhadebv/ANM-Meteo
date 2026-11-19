package ro.anmmeteo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.domain.model.AppTab
import ro.anmmeteo.app.domain.util.WeatherMath
import ro.anmmeteo.app.ui.screens.AlertsScreen
import ro.anmmeteo.app.ui.screens.HomeScreen
import ro.anmmeteo.app.ui.screens.RadarScreen
import ro.anmmeteo.app.ui.screens.StationsScreen
import ro.anmmeteo.app.ui.theme.AnMMeteoTheme
import ro.anmmeteo.app.ui.theme.LocalAtmosphericColors
import ro.anmmeteo.app.ui.viewmodel.WeatherViewModel
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnMMeteoApp()
        }
    }
}

@Composable
fun AnMMeteoApp(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val currentStation = remember(state.stations, state.selectedStationName) {
        state.stations.firstOrNull { it.name == state.selectedStationName }
    }

    val isNightNow = remember(state.selectedStationName, currentStation?.condition) {
        val coords = VerifiedStations.findByName(state.selectedStationName)
        val nowDate = LocalDate.now()
        val nowTime = LocalTime.now()
        WeatherMath.calculateSolarInfo(
            lat = coords?.lat ?: 45.7,
            lon = coords?.lon ?: 25.5,
            dayOfYear = nowDate.dayOfYear,
            currentHour = nowTime.hour,
            currentMinute = nowTime.minute,
            cloudCondition = currentStation?.condition.orEmpty()
        ).isNightNow
    }

    AnMMeteoTheme(
        themeMode = state.themeMode,
        condition = currentStation?.condition.orEmpty(),
        isNight = isNightNow
    ) {
        val atm = LocalAtmosphericColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(atm.backgroundBrush)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        containerColor = if (atm.isLightSurface) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            Color(0xFF0A1424).copy(alpha = 0.85f)
                        }
                    ) {
                        NavigationBarItem(
                            selected = currentTab == AppTab.HOME,
                            onClick = { currentTab = AppTab.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = AppTab.HOME.label) },
                            label = { Text(AppTab.HOME.label) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.STATIONS,
                            onClick = { currentTab = AppTab.STATIONS },
                            icon = { Icon(Icons.Default.Place, contentDescription = AppTab.STATIONS.label) },
                            label = { Text(AppTab.STATIONS.label) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.RADAR,
                            onClick = { currentTab = AppTab.RADAR },
                            icon = { Icon(Icons.Default.Map, contentDescription = AppTab.RADAR.label) },
                            label = { Text(AppTab.RADAR.label) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.ALERTS,
                            onClick = { currentTab = AppTab.ALERTS },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (state.warnings.isNotEmpty()) {
                                            Badge {
                                                Text(state.warnings.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = AppTab.ALERTS.label)
                                }
                            },
                            label = { Text(AppTab.ALERTS.label) }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        AppTab.HOME -> HomeScreen(
                            state = state,
                            onSelectStation = viewModel::selectStation,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onRefresh = { viewModel.refreshAll(forceRefresh = true) },
                            onGpsLocation = viewModel::onGpsLocationFound,
                            onGpsStatus = viewModel::setGpsStatus,
                            onCycleTheme = viewModel::cycleThemeMode,
                            onOpenStationsTab = { currentTab = AppTab.STATIONS },
                            onOpenAlertsTab = { currentTab = AppTab.ALERTS }
                        )

                        AppTab.STATIONS -> StationsScreen(
                            state = state,
                            onSelectStationAndGoHome = { name ->
                                viewModel.selectStation(name)
                                currentTab = AppTab.HOME
                            },
                            onToggleFavorite = viewModel::toggleFavorite
                        )

                        AppTab.RADAR -> RadarScreen()

                        AppTab.ALERTS -> AlertsScreen(
                            state = state,
                            onRefresh = { viewModel.refreshAll(forceRefresh = true) },
                            onToggleNotifications = viewModel::setNotificationsEnabled
                        )
                    }
                }
            }
        }
    }
}
