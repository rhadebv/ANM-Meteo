package ro.anmmeteo.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.anmmeteo.app.domain.model.AlertSeverity
import ro.anmmeteo.app.domain.model.DailyForecast
import ro.anmmeteo.app.domain.model.HourlyPoint
import ro.anmmeteo.app.domain.model.SolarInfo
import ro.anmmeteo.app.domain.model.WeatherAlert
import ro.anmmeteo.app.domain.model.WeatherStation
import ro.anmmeteo.app.domain.util.WeatherMath
import ro.anmmeteo.app.ui.theme.AlertInfoBg
import ro.anmmeteo.app.ui.theme.AlertOrangeBg
import ro.anmmeteo.app.ui.theme.AlertRedBg
import ro.anmmeteo.app.ui.theme.AlertYellowBg
import ro.anmmeteo.app.ui.theme.AlertYellowText
import ro.anmmeteo.app.ui.theme.LocalAtmosphericColors
import ro.anmmeteo.app.ui.theme.ThermalCold
import ro.anmmeteo.app.ui.theme.ThermalHot
import ro.anmmeteo.app.ui.theme.ThermalMild
import ro.anmmeteo.app.ui.theme.ThermalWarm

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val atm = LocalAtmosphericColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = atm.glassSurface,
        border = BorderStroke(1.dp, atm.glassBorder)
    ) {
        content()
    }
}

@Composable
fun HeroWeatherSection(
    station: WeatherStation,
    todayForecast: DailyForecast?,
    solarInfo: SolarInfo,
    countyName: String?
) {
    val atm = LocalAtmosphericColors.current
    val feelsLike = WeatherMath.computeFeelsLike(
        station.tempCelsius,
        station.windKmh,
        station.humidityPercent
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!countyName.isNullOrBlank()) {
            Text(
                text = "Județul $countyName",
                style = MaterialTheme.typography.labelMedium,
                color = atm.secondaryText
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        AnimatedWeatherIcon(
            condition = station.condition,
            isNight = solarInfo.isNightNow,
            size = 84.dp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = WeatherMath.formatTemp(station.tempCelsius ?: WeatherMath.parseNumber(station.tempRaw)),
            style = MaterialTheme.typography.displayLarge,
            color = atm.primaryText
        )

        val minMaxPart = if (todayForecast != null) {
            " • Max ${WeatherMath.formatTemp(todayForecast.maxTemp)} / Min ${WeatherMath.formatTemp(todayForecast.minTemp)}"
        } else {
            ""
        }

        Text(
            text = "${station.condition}$minMaxPart",
            style = MaterialTheme.typography.titleMedium,
            color = atm.primaryText
        )

        if (feelsLike != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Se simte ca $feelsLike°",
                style = MaterialTheme.typography.bodyMedium,
                color = atm.secondaryText
            )
        }
    }
}

@Composable
fun ActiveAlertBanner(
    alert: WeatherAlert,
    onOpenAlertsTab: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (bgColor, textColor) = when (alert.severity) {
        AlertSeverity.YELLOW -> AlertYellowBg to AlertYellowText
        AlertSeverity.ORANGE -> AlertOrangeBg to Color.White
        AlertSeverity.RED -> AlertRedBg to Color.White
        AlertSeverity.INFO -> AlertInfoBg to Color.White
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = bgColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = alert.severity.label,
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${alert.severity.label}: ${alert.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = alert.source,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = textColor
                )
            }

            if (expanded) {
                if (alert.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = alert.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vezi toate avertizările ANM →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.clickable { onOpenAlertsTab() }
                )
            }
        }
    }
}

@Composable
fun HourlyForecastCard(points: List<HourlyPoint>) {
    val atm = LocalAtmosphericColors.current
    GlassCard {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = "Evoluție orară estimată",
                style = MaterialTheme.typography.labelMedium,
                color = atm.secondaryText,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(points) { point ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = point.hourLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = atm.secondaryText
                        )
                        AnimatedWeatherIcon(
                            condition = point.condition,
                            isNight = point.isNight,
                            size = 30.dp
                        )
                        Text(
                            text = "${point.tempCelsius}°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = atm.primaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastCard(forecasts: List<DailyForecast>) {
    val atm = LocalAtmosphericColors.current
    if (forecasts.isEmpty()) return

    val weekMin = forecasts.mapNotNull { it.minTemp }.minOrNull() ?: 0.0
    val weekMax = forecasts.mapNotNull { it.maxTemp }.maxOrNull() ?: 25.0

    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Prognoză pe ${forecasts.size} zile (ANM)",
                style = MaterialTheme.typography.labelMedium,
                color = atm.secondaryText
            )
            Spacer(modifier = Modifier.height(10.dp))

            forecasts.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = atm.glassBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val (startFrac, endFrac) = WeatherMath.normalizeThermalBar(
                    dayMin = item.minTemp,
                    dayMax = item.maxTemp,
                    weekMin = weekMin,
                    weekMax = weekMax
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.dayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                        color = atm.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.3f)
                    )

                    AnimatedWeatherIcon(
                        condition = item.condition,
                        isNight = false,
                        size = 28.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = WeatherMath.formatTemp(item.minTemp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = atm.secondaryText,
                        modifier = Modifier.width(34.dp)
                    )

                    ThermalRangeBar(
                        startFraction = startFrac,
                        endFraction = endFrac,
                        trackColor = atm.glassBorder,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(6.dp)
                            .padding(horizontal = 6.dp)
                    )

                    Text(
                        text = WeatherMath.formatTemp(item.maxTemp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = atm.primaryText,
                        modifier = Modifier.width(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalRangeBar(
    startFraction: Float,
    endFraction: Float,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = radius
        )
        val startX = size.width * startFraction
        val barWidth = (size.width * (endFraction - startFraction)).coerceAtLeast(size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ThermalCold, ThermalMild, ThermalWarm, ThermalHot)
            ),
            topLeft = Offset(startX, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = radius
        )
    }
}

@Composable
fun SecondaryMetricsGrid(
    station: WeatherStation,
    solarInfo: SolarInfo
) {
    val dewPoint = WeatherMath.computeDewPoint(station.tempCelsius, station.humidityPercent)
    val windDesc = WeatherMath.windBeaufortDescription(station.windKmh)
    val pressureDesc = WeatherMath.pressureTrendDescription(station.pressureHpa)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                icon = Icons.Default.Air,
                label = "Vânt",
                value = if (station.windKmh != null) "${station.windKmh.toInt()} km/h" else station.windRaw,
                subtitle = windDesc,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Default.WaterDrop,
                label = "Umiditate",
                value = if (station.humidityPercent != null) "${station.humidityPercent}%" else station.humidityRaw,
                subtitle = if (dewPoint != null) "Punct de rouă $dewPoint°" else "Umiditate relativă",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                icon = Icons.Default.Compress,
                label = "Presiune",
                value = if (station.pressureHpa != null) "${station.pressureHpa.toInt()} hPa" else station.pressureRaw,
                subtitle = pressureDesc,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Default.WbTwilight,
                label = "Soare & UV",
                value = "${solarInfo.sunrise} / ${solarInfo.sunset}",
                subtitle = "Indice UV ${solarInfo.uvIndexEstimate} • ${solarInfo.uvLabel}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val atm = LocalAtmosphericColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = atm.glassSurface,
        border = BorderStroke(1.dp, atm.glassBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = atm.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = atm.secondaryText
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = atm.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = atm.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
