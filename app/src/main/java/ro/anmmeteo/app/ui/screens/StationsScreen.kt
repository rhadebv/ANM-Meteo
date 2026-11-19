package ro.anmmeteo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.anmmeteo.app.data.local.VerifiedStations
import ro.anmmeteo.app.domain.model.WeatherStation
import ro.anmmeteo.app.domain.model.WeatherUiState
import ro.anmmeteo.app.domain.util.WeatherMath
import ro.anmmeteo.app.ui.components.AnimatedWeatherIcon
import ro.anmmeteo.app.ui.components.GlassCard
import ro.anmmeteo.app.ui.theme.LocalAtmosphericColors

@Composable
fun StationsScreen(
    state: WeatherUiState,
    onSelectStationAndGoHome: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val atm = LocalAtmosphericColors.current
    var query by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }

    val filteredStations = remember(state.stations, state.favoriteStations, query, onlyFavorites) {
        state.stations.filter { station ->
            val matchesFav = !onlyFavorites || state.favoriteStations.contains(station.name)
            val county = VerifiedStations.findByName(station.name)?.county.orEmpty()
            val matchesQuery = query.isBlank() ||
                station.name.contains(query, ignoreCase = true) ||
                station.displayName.contains(query, ignoreCase = true) ||
                county.contains(query, ignoreCase = true)
            matchesFav && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Stații Meteo ANM",
            style = MaterialTheme.typography.headlineMedium,
            color = atm.primaryText
        )
        Text(
            text = "${state.stations.size} stații monitorizate în România",
            style = MaterialTheme.typography.bodyMedium,
            color = atm.secondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Caută oraș, stație sau județ...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Șterge")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = atm.primaryText,
                unfocusedTextColor = atm.primaryText,
                focusedBorderColor = atm.primaryText,
                unfocusedBorderColor = atm.glassBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onlyFavorites,
                onClick = { onlyFavorites = false },
                label = { Text("Toate (${state.stations.size})") }
            )
            FilterChip(
                selected = onlyFavorites,
                onClick = { onlyFavorites = true },
                label = { Text("Favorite (${state.favoriteStations.size})") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredStations, key = { it.name }) { station ->
                StationListItem(
                    station = station,
                    isFavorite = state.favoriteStations.contains(station.name),
                    onSelect = { onSelectStationAndGoHome(station.name) },
                    onToggleFavorite = { onToggleFavorite(station.name) }
                )
            }
        }
    }
}

@Composable
private fun StationListItem(
    station: WeatherStation,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val atm = LocalAtmosphericColors.current
    val county = remember(station.name) { VerifiedStations.findByName(station.name)?.county }

    GlassCard(
        modifier = Modifier.clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedWeatherIcon(
                condition = station.condition,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = atm.primaryText
                )
                val sub = buildString {
                    if (!county.isNullOrBlank()) append("Jud. $county • ")
                    append(station.condition)
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = atm.secondaryText
                )
            }
            Text(
                text = WeatherMath.formatTemp(station.tempCelsius ?: WeatherMath.parseNumber(station.tempRaw)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = atm.primaryText
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) Color(0xFFFFD54F) else atm.secondaryText
                )
            }
        }
    }
}
