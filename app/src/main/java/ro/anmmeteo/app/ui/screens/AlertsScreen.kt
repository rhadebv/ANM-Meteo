package ro.anmmeteo.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.anmmeteo.app.domain.model.AlertSeverity
import ro.anmmeteo.app.domain.model.WeatherAlert
import ro.anmmeteo.app.domain.model.WeatherUiState
import ro.anmmeteo.app.ui.components.GlassCard
import ro.anmmeteo.app.ui.theme.AlertInfoBg
import ro.anmmeteo.app.ui.theme.AlertOrangeBg
import ro.anmmeteo.app.ui.theme.AlertRedBg
import ro.anmmeteo.app.ui.theme.AlertYellowBg
import ro.anmmeteo.app.ui.theme.AlertYellowText
import ro.anmmeteo.app.ui.theme.LocalAtmosphericColors
import ro.anmmeteo.app.ui.theme.SafeGreenBg

@Composable
fun AlertsScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit
) {
    val atm = LocalAtmosphericColors.current
    var filterSource by remember { mutableStateOf("TOATE") }

    val visibleAlerts = remember(state.warnings, filterSource) {
        when (filterSource) {
            "NOWCASTING" -> state.warnings.filter { it.source.contains("Nowcasting", ignoreCase = true) }
            "GENERALE" -> state.warnings.filter { it.source.contains("general", ignoreCase = true) }
            else -> state.warnings
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Avertizări ANM",
                    style = MaterialTheme.typography.headlineMedium,
                    color = atm.primaryText
                )
                Text(
                    text = if (state.warningLastChecked.isNotBlank()) {
                        "Verificat la ${state.warningLastChecked}"
                    } else {
                        "Fluxuri oficiale generale și nowcasting"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = atm.secondaryText
                )
            }

            IconButton(onClick = { onToggleNotifications(!state.notificationsEnabled) }) {
                Icon(
                    imageVector = if (state.notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = "Notificări alerte",
                    tint = atm.primaryText
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reîncarcă alertele",
                    tint = atm.primaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterSource == "TOATE",
                onClick = { filterSource = "TOATE" },
                label = { Text("Toate (${state.warnings.size})") }
            )
            FilterChip(
                selected = filterSource == "NOWCASTING",
                onClick = { filterSource = "NOWCASTING" },
                label = { Text("Nowcasting") }
            )
            FilterChip(
                selected = filterSource == "GENERALE",
                onClick = { filterSource = "GENERALE" },
                label = { Text("Generale") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.warningError != null) {
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = state.warningError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = atm.primaryText
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = onRefresh) {
                                Text("Reîncearcă")
                            }
                        }
                    }
                }
            } else if (visibleAlerts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SafeGreenBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Column {
                                Text(
                                    text = "Nicio avertizare activă",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Fluxurile ANM nu raportează fenomene periculoase în acest moment.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(visibleAlerts, key = { it.id }) { alert ->
                    AlertDetailCard(alert = alert)
                }
            }
        }
    }
}

@Composable
private fun AlertDetailCard(alert: WeatherAlert) {
    val context = LocalContext.current
    val (bgColor, textColor) = when (alert.severity) {
        AlertSeverity.RED -> AlertRedBg to Color.White
        AlertSeverity.ORANGE -> AlertOrangeBg to Color.White
        AlertSeverity.YELLOW -> AlertYellowBg to AlertYellowText
        AlertSeverity.INFO -> AlertInfoBg to Color.White
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${alert.severity.label} • ${alert.source}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "⚠ ${alert.severity.label} (ANM - ${alert.source}): ${alert.title}
${alert.body}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Distribuie avertizarea ANM"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Distribuie",
                        tint = textColor
                    )
                }
            }

            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            if (alert.validInterval.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Valabilitate: ${alert.validInterval}",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.85f)
                )
            }

            if (alert.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = alert.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}
