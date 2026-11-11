package ro.anmmeteo.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

private val Navy = Color(0xFF071824)
private val Navy2 = Color(0xFF0C2535)
private val Blue = Color(0xFF2196F3)
private val Soft = Color(0xFFB6C7D4)

data class Station(
    val name: String,
    val temp: String,
    val wind: String,
    val humidity: String,
    val pressure: String,
    val cloud: String,
    val date: String,
    val lat: Double?,
    val lon: Double?
)

data class Alert(
    val level: String,
    val title: String,
    val interval: String,
    val body: String
)

data class UiState(
    val loading: Boolean = true,
    val stations: List<Station> = emptyList(),
    val selected: String = "BRASOV GHIMBAV",
    val alerts: List<Alert> = emptyList(),
    val error: String? = null
)

class WeatherViewModel : ViewModel() {
    var state by mutableStateOf(UiState())
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        refresh()
    }

    fun select(name: String) {
        state = state.copy(selected = name)
    }

    fun selectNearest(lat: Double, lon: Double) {
        state.stations
            .filter { it.lat != null && it.lon != null }
            .minByOrNull { distance(lat, lon, it.lat!!, it.lon!!) }
            ?.let { select(it.name) }
    }

    fun refresh() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)

            runCatching {
                withContext(Dispatchers.IO) { fetchStations() }
            }.onSuccess {
                state = state.copy(loading = false, stations = it)
            }.onFailure {
                state = state.copy(
                    loading = false,
                    error = "Datele ANM nu au putut fi încărcate: ${it.message}"
                )
            }

            launch {
                runCatching {
                    withContext(Dispatchers.IO) { fetchAlerts() }
                }.onSuccess {
                    state = state.copy(alerts = it)
                }
            }
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ANMMeteo/0.2 Android personal app")
            .build()

        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Răspuns gol")
        }
    }

    private fun fetchStations(): List<Station> {
        val raw = get("https://www.meteoromania.ro/wp-json/meteoapi/v2/starea-vremii")
        val root = if (raw.trim().startsWith("[")) {
            JSONArray(raw)
        } else {
            val obj = JSONObject(raw)
            obj.optJSONArray("features") ?: obj.optJSONArray("data") ?: JSONArray()
        }

        val result = mutableListOf<Station>()

        for (i in 0 until root.length()) {
            val base = root.optJSONObject(i) ?: continue
            val data = base.optJSONObject("properties") ?: base

            fun first(vararg keys: String): String {
                for (key in keys) {
                    val value = data.opt(key)
                    if (value != null && value.toString() != "null" && value.toString().isNotBlank()) {
                        return value.toString()
                    }
                }
                return "—"
            }

            fun number(vararg keys: String): Double? =
                first(*keys).replace(',', '.').toDoubleOrNull()

            val coords = base.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lon = coords?.optDouble(0)?.takeUnless { it.isNaN() }
                ?: number("lon", "lng", "longitude", "longitudine")
            val lat = coords?.optDouble(1)?.takeUnless { it.isNaN() }
                ?: number("lat", "latitude", "latitudine")

            val name = first("nume", "name", "statie", "station", "denumire")
                .uppercase(Locale.ROOT)

            if (name != "—") {
                result += Station(
                    name = name,
                    temp = first("temperatura", "temp", "temperature", "tempe"),
                    wind = first("vant", "wind", "viteza_vant"),
                    humidity = first("umezeala", "umiditate", "humidity", "umezeala_relativa"),
                    pressure = first("presiune", "pressure"),
                    cloud = first("nebulozitate", "cloud", "stare_vreme", "fenomen"),
                    date = first("data", "date", "timestamp", "ora"),
                    lat = lat,
                    lon = lon
                )
            }
        }

        if (result.isEmpty()) error("Format ANM necunoscut")
        return result.sortedBy { it.name }
    }

    private fun fetchAlerts(): List<Alert> {
        val xml = get("https://www.meteoromania.ro/avertizari-xml.php")

        fun tag(source: String, name: String): String {
            val regex = Regex(
                "<$name[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</$name>",
                RegexOption.IGNORE_CASE
            )
            return regex.find(source)
                ?.groupValues
                ?.get(1)
                ?.replace(Regex("<[^>]+>"), " ")
                ?.trim()
                .orEmpty()
        }

        return Regex("<item[\\s\\S]*?</item>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { it.value }
            .map { block ->
                val title = tag(block, "title")
                val description = tag(block, "description")
                val upper = "$title $description".uppercase(Locale.ROOT)
                val level = when {
                    "ROȘU" in upper || "ROSU" in upper -> "COD ROȘU"
                    "PORTOCALIU" in upper -> "COD PORTOCALIU"
                    "GALBEN" in upper -> "COD GALBEN"
                    else -> "INFORMARE"
                }
                Alert(
                    level = level,
                    title = title.ifBlank { "Avertizare meteorologică" },
                    interval = tag(block, "pubDate"),
                    body = description
                )
            }
            .take(20)
            .toList()
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

enum class Tab { HOME, STATIONS, RADAR, ALERTS, FAVORITES }

@Composable
fun App(vm: WeatherViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            background = Navy,
            surface = Navy2,
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF081D2A)) {
                    AppNavItem(Tab.HOME, tab, "Acasă", Icons.Default.Home) { tab = it }
                    AppNavItem(Tab.STATIONS, tab, "Stații", Icons.Default.Place) { tab = it }
                    AppNavItem(Tab.RADAR, tab, "Radar", Icons.Default.Map) { tab = it }
                    AppNavItem(Tab.ALERTS, tab, "Alerte", Icons.Default.Warning) { tab = it }
                    AppNavItem(Tab.FAVORITES, tab, "Favorite", Icons.Default.Star) { tab = it }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF123C56), Navy)))
            ) {
                when (tab) {
                    Tab.HOME -> Home(vm)
                    Tab.STATIONS -> Stations(vm)
                    Tab.RADAR -> Radar()
                    Tab.ALERTS -> Alerts(vm)
                    Tab.FAVORITES -> Favorites(vm)
                }
            }
        }
    }
}

@Composable
private fun RowScope.AppNavItem(
    tab: Tab,
    current: Tab,
    label: String,
    icon: ImageVector,
    onClick: (Tab) -> Unit
) {
    NavigationBarItem(
        selected = tab == current,
        onClick = { onClick(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(text = label, fontSize = 10.sp) }
    )
}

@Composable
fun Header(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp, 16.dp, 18.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = Soft)
    }
}

@Composable
fun Home(vm: WeatherViewModel) {
    val state = vm.state
    val current = state.stations.firstOrNull { it.name == state.selected }
        ?: state.stations.firstOrNull()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) locate(context, vm)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Header("ANM Meteo", "România, mereu la zi") }
        item {
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            locate(context, vm)
                        } else {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Locația mea")
                }
                OutlinedButton(onClick = vm::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reîncarcă")
                }
            }
        }
        item {
            if (current != null) CurrentWeather(current)
            else ErrorCard(state.error ?: "Se încarcă datele ANM", vm::refresh)
        }
        item {
            Text(
                text = "Stații meteo",
                modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 6.dp),
                fontWeight = FontWeight.Bold
            )
        }
        items(state.stations.take(6), key = { it.name }) { StationRow(it, vm) }
    }
}

@SuppressLint("MissingPermission")
private fun locate(context: Context, vm: WeatherViewModel) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    val last = providers
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    last?.let { vm.selectNearest(it.latitude, it.longitude) }

    providers.forEach { provider ->
        runCatching {
            locationManager.requestSingleUpdate(
                provider,
                object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        vm.selectNearest(location.latitude, location.longitude)
                    }
                },
                null
            )
        }
    }
}

@Composable
fun CurrentWeather(station: Station) {
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xB3123448))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = station.name.replace(" GHIMBAV", ""),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (station.date == "—") "Date oficiale ANM" else "Actualizat: ${station.date}",
                color = Soft
            )
            Icon(
                imageVector = Icons.Default.WbCloudy,
                contentDescription = null,
                tint = Color(0xFFFFD34D),
                modifier = Modifier.padding(16.dp).size(70.dp)
            )
            Text(text = formatTemperature(station.temp), fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(text = station.cloud, fontSize = 20.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Metric("Umiditate", station.humidity, "%")
                Metric("Vânt", station.wind, "")
                Metric("Presiune", station.pressure, " hPa")
            }
        }
    }
}

fun formatTemperature(value: String): String =
    Regex("-?\\d+(?:[.,]\\d+)?").find(value)?.value?.let { "${it.replace(',', '.')}°C" } ?: value

@Composable
fun Metric(label: String, value: String, suffix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Soft, fontSize = 12.sp)
        val shown = if (value == "—" || suffix.isBlank() || value.contains(suffix.trim(), ignoreCase = true)) {
            value
        } else {
            value + suffix
        }
        Text(text = shown, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ErrorCard(message: String, retry: () -> Unit) {
    Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = message)
            Button(onClick = retry, modifier = Modifier.padding(top = 10.dp)) {
                Text(text = "Reîncearcă")
            }
        }
    }
}

@Composable
fun StationRow(station: Station, vm: WeatherViewModel) {
    val context = LocalContext.current
    val favorites = rememberFavorites(context)

    Row(
        modifier = Modifier.fillMaxWidth().clickable { vm.select(station.name) }.padding(18.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF90CAF9))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = station.name, fontWeight = FontWeight.SemiBold)
            Text(text = station.cloud, color = Soft, fontSize = 12.sp)
        }
        Text(text = formatTemperature(station.temp), fontSize = 20.sp)
        IconButton(onClick = { favorites.toggle(station.name) }) {
            Icon(
                imageVector = if (favorites.names.contains(station.name)) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (favorites.names.contains(station.name)) Color(0xFFFFD54F) else Soft
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}

@Composable
fun Stations(vm: WeatherViewModel) {
    var query by remember { mutableStateOf("") }
    Column {
        Header("Stații meteo", "Observații oficiale ANM")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            label = { Text(text = "Caută localitate") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        LazyColumn {
            items(vm.state.stations.filter { it.name.contains(query, ignoreCase = true) }, key = { it.name }) {
                StationRow(it, vm)
            }
        }
    }
}

@Composable
fun Favorites(vm: WeatherViewModel) {
    val context = LocalContext.current
    val favorites = rememberFavorites(context)
    val list = vm.state.stations.filter { favorites.names.contains(it.name) }

    LazyColumn {
        item { Header("Localități favorite", "Salvate pe acest telefon") }
        if (list.isEmpty()) {
            item {
                Text(
                    text = "Adaugă localități apăsând steaua din lista de stații.",
                    modifier = Modifier.padding(24.dp),
                    color = Soft
                )
            }
        } else {
            items(list, key = { it.name }) { StationRow(it, vm) }
        }
    }
}

class FavoriteStore(private val context: Context) {
    var names by mutableStateOf(
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            .getStringSet("names", emptySet())
            ?.toSet()
            ?: emptySet()
    )
        private set

    fun toggle(name: String) {
        names = if (names.contains(name)) names - name else names + name
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("names", names)
            .apply()
    }
}

@Composable
fun rememberFavorites(context: Context): FavoriteStore = remember(context) { FavoriteStore(context) }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Radar() {
    Column {
        Header("Radar precipitații", "Harta radar națională ANM")
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl("https://www.meteoromania.ro/radarm/radar.index.php")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun Alerts(vm: WeatherViewModel) {
    LazyColumn {
        item { Header("Avertizări meteo", "Mesaje oficiale ANM") }
        if (vm.state.alerts.isEmpty()) {
            item { ErrorCard("Nu sunt avertizări disponibile sau fluxul ANM nu a răspuns.", vm::refresh) }
        } else {
            items(vm.state.alerts) { AlertCard(it) }
        }
    }
}

@Composable
fun AlertCard(alert: Alert) {
    val cardColor = when (alert.level) {
        "COD ROȘU" -> Color(0xFFD32F2F)
        "COD PORTOCALIU" -> Color(0xFFF57C00)
        "COD GALBEN" -> Color(0xFFFBC02D)
        else -> Color(0xFF1976D2)
    }
    val foreground = if (alert.level == "COD GALBEN") Color.Black else Color.White

    Card(
        modifier = Modifier.padding(16.dp, 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = alert.level, fontWeight = FontWeight.Black, fontSize = 19.sp, color = foreground)
            Text(text = alert.title, fontWeight = FontWeight.Bold, color = foreground)
            if (alert.interval.isNotBlank()) {
                Text(text = alert.interval, fontSize = 12.sp, color = foreground.copy(alpha = 0.8f))
            }
            if (alert.body.isNotBlank()) {
                Text(text = alert.body, maxLines = 6, color = foreground.copy(alpha = 0.9f))
            }
        }
    }
}
