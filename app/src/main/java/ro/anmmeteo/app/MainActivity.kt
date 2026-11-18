package ro.anmmeteo.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

private val Navy = Color(0xFF071824)
private val Navy2 = Color(0xFF0C2535)
private val Blue = Color(0xFF2196F3)
private val Soft = Color(0xFFB6C7D4)
private const val WEATHER_TTL = 30L * 60L * 1000L
private const val ALERT_TTL = 15L * 60L * 1000L
private const val FORECAST_TTL = 30L * 60L * 1000L

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

data class Alert(val level: String, val title: String, val interval: String, val body: String)
data class ForecastItem(val label: String, val condition: String, val min: String, val max: String, val hour: String = "")
data class UiState(
    val loading: Boolean = true,
    val stations: List<Station> = emptyList(),
    val selected: String = "BRASOV GHIMBAV",
    val alerts: List<Alert> = emptyList(),
    val daily: List<ForecastItem> = emptyList(),
    val hourly: List<ForecastItem> = emptyList(),
    val locationStatus: String = "",
    val error: String? = null,
    val fromCache: Boolean = false
)

class WeatherViewModel(application: android.app.Application) : AndroidViewModel(application) {
    var state by mutableStateOf(UiState())
        private set

    private val prefs = application.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    init {
        val favoriteStation = prefs.getString("selected_station", "BRASOV GHIMBAV") ?: "BRASOV GHIMBAV"
        state = state.copy(selected = favoriteStation)
        refresh(force = false)
    }

    fun select(name: String) {
        state = state.copy(selected = name)
        prefs.edit().putString("selected_station", name).apply()
        loadForecast(force = false)
        updateWidget()
    }

    fun setLocationStatus(message: String) {
        state = state.copy(locationStatus = message)
    }

    fun selectNearest(location: Location) {
        if (location.time < System.currentTimeMillis() - 5 * 60 * 1000L) {
            setLocationStatus("Locația primită este prea veche. Încearcă din nou în aer liber.")
            return
        }
        if (location.hasAccuracy() && location.accuracy > 1000f) {
            setLocationStatus("Precizie GPS insuficientă: ${location.accuracy.roundToInt()} m")
            return
        }
        val nearest = state.stations
            .filter { it.lat != null && it.lon != null }
            .minByOrNull { distance(location.latitude, location.longitude, it.lat!!, it.lon!!) }
        if (nearest == null) {
            setLocationStatus("Stațiile ANM nu conțin coordonate utilizabile.")
            return
        }
        select(nearest.name)
        setLocationStatus("Stație apropiată: ${nearest.name} • precizie ${location.accuracy.roundToInt()} m")
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val weather = loadCachedOrRemote("stations_json", "stations_time", WEATHER_TTL, force) {
                get("https://www.meteoromania.ro/wp-json/meteoapi/v2/starea-vremii")
            }
            runCatching { parseStations(weather.first) }
                .onSuccess {
                    state = state.copy(loading = false, stations = it, fromCache = weather.second)
                    updateWidget()
                }
                .onFailure {
                    state = state.copy(loading = false, error = "Datele ANM nu au putut fi citite: ${it.message}")
                }

            launch { loadAlerts(force) }
            launch { loadForecast(force) }
        }
    }

    private fun loadAlerts(force: Boolean) {
        viewModelScope.launch {
            runCatching {
                loadCachedOrRemote("alerts_xml", "alerts_time", ALERT_TTL, force) {
                    get("https://www.meteoromania.ro/avertizari-xml.php")
                }.first
            }.mapCatching { parseAlerts(it) }
                .onSuccess { state = state.copy(alerts = it) }
        }
    }

    private fun loadForecast(force: Boolean) {
        viewModelScope.launch {
            runCatching {
                loadCachedOrRemote("forecast_json", "forecast_time", FORECAST_TTL, force) {
                    get("https://www.meteoromania.ro/wp-json/meteoapi/v2/prognoza-orase")
                }.first
            }.mapCatching { parseForecast(it, state.selected) }
                .onSuccess { result -> state = state.copy(daily = result.first, hourly = result.second) }
        }
    }

    private suspend fun loadCachedOrRemote(
        dataKey: String,
        timeKey: String,
        ttl: Long,
        force: Boolean,
        remote: () -> String
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val cached = prefs.getString(dataKey, null)
        val savedAt = prefs.getLong(timeKey, 0L)
        val fresh = cached != null && System.currentTimeMillis() - savedAt < ttl
        if (!force && fresh) return@withContext cached!! to true
        try {
            val value = remote()
            prefs.edit().putString(dataKey, value).putLong(timeKey, System.currentTimeMillis()).apply()
            value to false
        } catch (error: Exception) {
            if (cached != null) cached to true else throw error
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "ANMMeteo/0.3 Android personal app")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Răspuns gol")
        }
    }

    private fun parseStations(raw: String): List<Station> {
        val root = if (raw.trim().startsWith("[")) JSONArray(raw) else {
            val obj = JSONObject(raw)
            obj.optJSONArray("features") ?: obj.optJSONArray("data") ?: JSONArray()
        }
        val result = mutableListOf<Station>()
        for (i in 0 until root.length()) {
            val base = root.optJSONObject(i) ?: continue
            val data = base.optJSONObject("properties") ?: base
            fun first(vararg keys: String): String {
                keys.forEach { key ->
                    val value = data.opt(key)
                    if (value != null && value.toString() != "null" && value.toString().isNotBlank()) return value.toString()
                }
                return "—"
            }
            fun number(vararg keys: String) = first(*keys).replace(',', '.').toDoubleOrNull()
            val coords = base.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lon = coords?.optDouble(0)?.takeUnless { it.isNaN() } ?: number("lon", "lng", "longitude", "longitudine")
            val lat = coords?.optDouble(1)?.takeUnless { it.isNaN() } ?: number("lat", "latitude", "latitudine")
            val name = first("nume", "name", "statie", "station", "denumire").uppercase(Locale.ROOT)
            if (name != "—") result += Station(
                name, first("temperatura", "temp", "temperature", "tempe"),
                first("vant", "wind", "viteza_vant"),
                first("umezeala", "umiditate", "humidity", "umezeala_relativa"),
                first("presiune", "pressure"),
                first("nebulozitate", "cloud", "stare_vreme", "fenomen"),
                first("data", "date", "timestamp", "ora"), lat, lon
            )
        }
        if (result.isEmpty()) error("Format ANM necunoscut")
        return result.sortedBy { it.name }
    }

    private fun parseAlerts(xml: String): List<Alert> {
        fun tag(source: String, name: String): String = Regex(
            "<$name[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</$name>", RegexOption.IGNORE_CASE
        ).find(source)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.trim().orEmpty()
        return Regex("<item[\\s\\S]*?</item>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.map { block ->
            val title = tag(block, "title")
            val description = tag(block, "description")
            val upper = "$title $description".uppercase(Locale.ROOT)
            val level = when {
                "ROȘU" in upper || "ROSU" in upper -> "COD ROȘU"
                "PORTOCALIU" in upper -> "COD PORTOCALIU"
                "GALBEN" in upper -> "COD GALBEN"
                else -> "INFORMARE"
            }
            Alert(level, title.ifBlank { "Avertizare meteorologică" }, tag(block, "pubDate"), description)
        }.take(20).toList()
    }

    private fun parseForecast(raw: String, station: String): Pair<List<ForecastItem>, List<ForecastItem>> {
        val normalized = station.substringBefore(" ").lowercase(Locale.ROOT)
        val objects = mutableListOf<JSONObject>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    objects += value
                    value.keys().forEach { walk(value.opt(it)) }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }
        walk(if (raw.trim().startsWith("[")) JSONArray(raw) else JSONObject(raw))
        fun text(o: JSONObject, vararg keys: String): String {
            keys.forEach { key ->
                val v = o.opt(key)
                if (v != null && v !is JSONObject && v !is JSONArray && v.toString() != "null" && v.toString().isNotBlank()) return v.toString()
            }
            return ""
        }
        val cityObjects = objects.filter { obj ->
            val city = text(obj, "oras", "localitate", "nume", "name", "city").lowercase(Locale.ROOT)
            city.isBlank() || city.contains(normalized) || normalized.contains(city)
        }
        val source = if (cityObjects.any { text(it, "oras", "localitate", "nume", "name", "city").contains(normalized, true) }) {
            cityObjects.filter { text(it, "oras", "localitate", "nume", "name", "city").contains(normalized, true) }
        } else objects
        val all = source.mapNotNull { o ->
            val label = text(o, "data", "date", "zi", "day", "valabilitate", "interval")
            val condition = text(o, "fenomen", "descriere", "description", "stare", "weather")
            val min = text(o, "tmin", "temp_min", "min", "temperatura_minima")
            val max = text(o, "tmax", "temp_max", "max", "temperatura_maxima", "temperatura")
            val hour = text(o, "ora", "hour", "time")
            if (label.isBlank() && condition.isBlank() && min.isBlank() && max.isBlank()) null
            else ForecastItem(label.ifBlank { hour.ifBlank { "Prognoză" } }, condition.ifBlank { "Date ANM" }, min, max, hour)
        }.distinctBy { "${it.label}|${it.min}|${it.max}|${it.condition}" }
        val hourly = all.filter { it.hour.isNotBlank() || Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b").containsMatchIn(it.label) }.take(12)
        val daily = all.filterNot { hourly.contains(it) }.filter { it.min.isNotBlank() || it.max.isNotBlank() }.take(7)
        return daily to hourly
    }

    private fun updateWidget() {
        val current = state.stations.firstOrNull { it.name == state.selected } ?: return
        prefs.edit()
            .putString("widget_location", current.name.replace(" GHIMBAV", ""))
            .putString("widget_temp", formatTemperature(current.temp))
            .putString("widget_condition", current.cloud)
            .putString("widget_updated", "Actualizat ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
            .apply()
        WeatherWidgetProvider.refreshAll(getApplication())
    }

    private fun distance(a: Double, b: Double, c: Double, d: Double): Double {
        val r = 6371.0
        val p1 = Math.toRadians(a)
        val p2 = Math.toRadians(c)
        val dp = Math.toRadians(c - a)
        val dl = Math.toRadians(d - b)
        val x = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(x), sqrt(1 - x))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherApp() }
    }
}

enum class Tab { HOME, STATIONS, RADAR, ALERTS, FAVORITES }

@Composable
fun WeatherApp(vm: WeatherViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Navy2)) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF081D2A)) {
                    NavItem(Tab.HOME, tab, "Acasă", Icons.Default.Home) { tab = it }
                    NavItem(Tab.STATIONS, tab, "Stații", Icons.Default.Place) { tab = it }
                    NavItem(Tab.RADAR, tab, "Radar", Icons.Default.Map) { tab = it }
                    NavItem(Tab.ALERTS, tab, "Alerte", Icons.Default.Warning) { tab = it }
                    NavItem(Tab.FAVORITES, tab, "Favorite", Icons.Default.Star) { tab = it }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF123C56), Navy)))) {
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
private fun RowScope.NavItem(tab: Tab, current: Tab, label: String, icon: ImageVector, change: (Tab) -> Unit) {
    NavigationBarItem(selected = tab == current, onClick = { change(tab) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) })
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(18.dp, 16.dp, 18.dp, 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, color = Soft)
    }
}

@Composable
private fun Home(vm: WeatherViewModel) {
    val state = vm.state
    val current = state.stations.firstOrNull { it.name == state.selected } ?: state.stations.firstOrNull()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) requestFreshLocation(context, vm) else vm.setLocationStatus("Permisiunea de localizare a fost refuzată.")
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Header("ANM Meteo", if (state.fromCache) "Date ANM din cache" else "România, mereu la zi") }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        requestFreshLocation(context, vm)
                    } else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Locația mea")
                }
                OutlinedButton(onClick = { vm.refresh(true) }) { Icon(Icons.Default.Refresh, "Actualizează") }
            }
            if (state.locationStatus.isNotBlank()) Text(state.locationStatus, Modifier.padding(18.dp, 8.dp), color = Soft, fontSize = 12.sp)
        }
        item {
            if (current != null) CurrentCard(current) else ErrorCard(state.error ?: "Se încarcă datele ANM") { vm.refresh(true) }
        }
        if (state.hourly.isNotEmpty()) item { ForecastStrip("Următoarele ore", state.hourly) }
        item { ForecastStrip("Prognoză pe zile", state.daily) }
        item { Text("Stații meteo", Modifier.padding(20.dp, 16.dp, 20.dp, 6.dp), fontWeight = FontWeight.Bold, color = Color.White) }
        items(state.stations.take(6), key = { it.name }) { StationRow(it, vm) }
    }
}

@SuppressLint("MissingPermission")
private fun requestFreshLocation(context: Context, vm: WeatherViewModel) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    vm.setLocationStatus("Se caută o poziție GPS proaspătă…")
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if ((!location.hasAccuracy() || location.accuracy <= 1000f) && location.time >= System.currentTimeMillis() - 5 * 60 * 1000L) {
                vm.selectNearest(location)
                runCatching { manager.removeUpdates(this) }
            } else vm.setLocationStatus("GPS găsit, dar precizia este ${location.accuracy.roundToInt()} m. Se așteaptă o poziție mai bună…")
        }
        override fun onProviderDisabled(provider: String) { vm.setLocationStatus("Activează GPS-ul și încearcă din nou.") }
    }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
    if (providers.isEmpty()) vm.setLocationStatus("Localizarea este dezactivată pe telefon.")
    providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
}

@Composable
private fun CurrentCard(station: Station) {
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xB3123448))) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(station.name.replace(" GHIMBAV", ""), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (station.date == "—") "Date oficiale ANM" else "Actualizat: ${station.date}", color = Soft)
            AnimatedWeatherIcon(station.cloud, Modifier.padding(12.dp).size(90.dp))
            Text(formatTemperature(station.temp), fontSize = 64.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(station.cloud, fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Metric("Umiditate", station.humidity, "%")
                Metric("Vânt", station.wind, "")
                Metric("Presiune", station.pressure, " hPa")
            }
        }
    }
}

@Composable
private fun AnimatedWeatherIcon(condition: String, modifier: Modifier = Modifier) {
    val lower = condition.lowercase(Locale.ROOT)
    val infinite = rememberInfiniteTransition(label = "weather")
    val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "sun")
    val drift by infinite.animateFloat(-4f, 4f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "cloud")
    val pulse by infinite.animateFloat(0.86f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "rain")
    Box(modifier, contentAlignment = Alignment.Center) {
        if ("senin" in lower || "soare" in lower || "noros" !in lower) {
            Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFFD34D), modifier = Modifier.fillMaxSize(0.72f).rotate(rotation))
        }
        if ("nor" in lower || "ploi" in lower || "ploa" in lower || "ninso" in lower) {
            Icon(Icons.Default.Cloud, null, tint = Color(0xFFB8D9F5), modifier = Modifier.fillMaxSize(0.82f).offset(x = drift.dp))
        }
        if ("ploi" in lower || "ploa" in lower) {
            Icon(Icons.Default.Grain, null, tint = Color(0xFF28C7FA), modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(0.42f).scale(pulse))
        }
        if ("ninso" in lower) {
            Icon(Icons.Default.AcUnit, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(0.38f).rotate(-rotation))
        }
        if ("furtun" in lower || "oraje" in lower) {
            Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(0.42f).alpha(pulse))
        }
    }
}

@Composable
private fun ForecastStrip(title: String, items: List<ForecastItem>) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (items.isEmpty()) {
            Text("ANM nu a furnizat încă elemente de prognoză compatibile pentru stația selectată.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Soft, fontSize = 12.sp)
        } else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0x99123448)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.width(118.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.hour.ifBlank { item.label.take(10) }, color = Soft, fontSize = 12.sp)
                        AnimatedWeatherIcon(item.condition, Modifier.size(48.dp))
                        Text(listOf(item.min, item.max).filter { it.isNotBlank() }.joinToString(" / ") { formatTemperature(it) }.ifBlank { "—" }, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(item.condition, color = Soft, fontSize = 10.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

fun formatTemperature(value: String): String = Regex("-?\\d+(?:[.,]\\d+)?").find(value)?.value?.let { "${it.replace(',', '.')}°C" } ?: value

@Composable
private fun Metric(label: String, value: String, suffix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Soft, fontSize = 12.sp)
        val shown = if (value == "—" || suffix.isBlank() || value.contains(suffix.trim(), true)) value else value + suffix
        Text(shown, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Card(Modifier.padding(16.dp).fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(message, color = Color.White)
            Button(retry, Modifier.padding(top = 10.dp)) { Text("Reîncearcă") }
        }
    }
}

@Composable
private fun StationRow(station: Station, vm: WeatherViewModel) {
    val context = LocalContext.current
    val favorites = rememberFavorites(context)
    Row(Modifier.fillMaxWidth().clickable { vm.select(station.name) }.padding(18.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedWeatherIcon(station.cloud, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(station.cloud, color = Soft, fontSize = 12.sp)
        }
        Text(formatTemperature(station.temp), fontSize = 20.sp, color = Color.White)
        IconButton(onClick = { favorites.toggle(station.name) }) {
            Icon(if (favorites.names.contains(station.name)) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (favorites.names.contains(station.name)) Color(0xFFFFD54F) else Soft)
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}

@Composable
private fun Stations(vm: WeatherViewModel) {
    var query by remember { mutableStateOf("") }
    Column {
        Header("Stații meteo", "Observații oficiale ANM")
        OutlinedTextField(query, { query = it }, Modifier.padding(16.dp).fillMaxWidth(), label = { Text("Caută localitate") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        LazyColumn { items(vm.state.stations.filter { it.name.contains(query, true) }, key = { it.name }) { StationRow(it, vm) } }
    }
}

@Composable
private fun Favorites(vm: WeatherViewModel) {
    val store = rememberFavorites(LocalContext.current)
    val list = vm.state.stations.filter { store.names.contains(it.name) }
    LazyColumn {
        item { Header("Localități favorite", "Salvate pe acest telefon") }
        if (list.isEmpty()) item { Text("Adaugă localități apăsând steaua din lista de stații.", Modifier.padding(24.dp), color = Soft) }
        else items(list, key = { it.name }) { StationRow(it, vm) }
    }
}

class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    var names by mutableStateOf(prefs.getStringSet("names", emptySet())?.toSet() ?: emptySet())
        private set
    fun toggle(name: String) {
        names = if (names.contains(name)) names - name else names + name
        prefs.edit().putStringSet("names", names).apply()
    }
}

@Composable
private fun rememberFavorites(context: Context) = remember(context) { FavoriteStore(context) }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Radar() {
    var loading by remember { mutableStateOf(true) }
    val webView = remember { mutableStateOf<WebView?>(null) }
    Column {
        Header("Radar precipitații", "Harta radar națională ANM")
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl("https://www.meteoromania.ro/radarm/radar.index.php")
                        webView.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            FloatingActionButton(onClick = { loading = true; webView.value?.reload() }, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Blue) {
                Icon(Icons.Default.Refresh, "Actualizează radarul")
            }
        }
    }
}

@Composable
private fun Alerts(vm: WeatherViewModel) {
    LazyColumn {
        item { Header("Avertizări meteo", "Mesaje oficiale ANM") }
        if (vm.state.alerts.isEmpty()) item { ErrorCard("Nu sunt avertizări active sau fluxul ANM nu a răspuns.") { vm.refresh(true) } }
        else items(vm.state.alerts) { AlertCard(it) }
    }
}

@Composable
private fun AlertCard(alert: Alert) {
    val bg = when (alert.level) {
        "COD ROȘU" -> Color(0xFFD32F2F)
        "COD PORTOCALIU" -> Color(0xFFF57C00)
        "COD GALBEN" -> Color(0xFFFBC02D)
        else -> Color(0xFF1976D2)
    }
    val fg = if (alert.level == "COD GALBEN") Color.Black else Color.White
    Card(Modifier.padding(16.dp, 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(alert.level, fontWeight = FontWeight.Black, fontSize = 19.sp, color = fg)
            Text(alert.title, fontWeight = FontWeight.Bold, color = fg)
            if (alert.interval.isNotBlank()) Text(alert.interval, fontSize = 12.sp, color = fg.copy(alpha = .8f))
            if (alert.body.isNotBlank()) Text(alert.body, maxLines = 7, color = fg.copy(alpha = .9f))
        }
    }
}
