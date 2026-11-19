package ro.anmmeteo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ro.anmmeteo.app.domain.model.ThemeMode
import java.util.Locale

data class AtmosphericColors(
    val backgroundBrush: Brush,
    val glassSurface: Color,
    val glassBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val isLightSurface: Boolean
)

val LocalAtmosphericColors = staticCompositionLocalOf {
    AtmosphericColors(
        backgroundBrush = Brush.verticalGradient(listOf(SkyDayTop, SkyDayBottom)),
        glassSurface = Color.White.copy(alpha = 0.13f),
        glassBorder = Color.White.copy(alpha = 0.22f),
        primaryText = Color.White,
        secondaryText = Color.White.copy(alpha = 0.78f),
        isLightSurface = false
    )
}

@Composable
fun AnMMeteoTheme(
    themeMode: ThemeMode,
    condition: String,
    isNight: Boolean,
    content: @Composable () -> Unit
) {
    val cond = condition.lowercase(Locale("ro", "RO"))
    val useLight = themeMode == ThemeMode.LIGHT

    val gradientColors = when {
        useLight -> listOf(SkyLightTop, SkyLightBottom)
        themeMode == ThemeMode.DARK -> listOf(SkyNightTop, SkyNightBottom)
        cond.contains("furtun") || cond.contains("oraj") -> listOf(SkyStormTop, SkyStormBottom)
        cond.contains("ploa") || cond.contains("averse") || cond.contains("cea") -> listOf(SkyRainTop, SkyRainBottom)
        isNight -> listOf(SkyNightTop, SkyNightBottom)
        else -> listOf(SkyDayTop, SkyDayBottom)
    }

    val atmospheric = if (useLight) {
        AtmosphericColors(
            backgroundBrush = Brush.verticalGradient(gradientColors),
            glassSurface = Color.White.copy(alpha = 0.80f),
            glassBorder = Color(0xFF1A3A60).copy(alpha = 0.12f),
            primaryText = Color(0xFF0E2238),
            secondaryText = Color(0xFF3E5875),
            isLightSurface = true
        )
    } else {
        AtmosphericColors(
            backgroundBrush = Brush.verticalGradient(gradientColors),
            glassSurface = Color.White.copy(alpha = 0.13f),
            glassBorder = Color.White.copy(alpha = 0.22f),
            primaryText = Color.White,
            secondaryText = Color.White.copy(alpha = 0.78f),
            isLightSurface = false
        )
    }

    val materialScheme = if (useLight) {
        lightColorScheme(
            primary = AccentBlueDeep,
            background = SkyLightBottom,
            surface = Color.White
        )
    } else {
        darkColorScheme(
            primary = AccentBlue,
            background = SkyNightBottom,
            surface = SkyNightTop
        )
    }

    CompositionLocalProvider(LocalAtmosphericColors provides atmospheric) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AppTypography,
            content = content
        )
    }
}
