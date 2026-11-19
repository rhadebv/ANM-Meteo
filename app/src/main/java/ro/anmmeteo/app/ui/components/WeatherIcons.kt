package ro.anmmeteo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ro.anmmeteo.app.R
import java.util.Locale

object WeatherIconMapper {

    @DrawableRes
    fun drawableFor(condition: String, isNight: Boolean = false): Int {
        val s = condition.lowercase(Locale("ro", "RO"))
        return when {
            listOf("furtun", "oraj", "fulger", "tunet").any { s.contains(it) } ->
                R.drawable.ic_weather_storm

            listOf("lapovi", "măzăriche", "mazari").any { s.contains(it) } ->
                R.drawable.ic_weather_sleet

            listOf("ninso", "zăpad", "zapad", "fulgu").any { s.contains(it) } ->
                R.drawable.ic_weather_snow

            listOf("ploa", "averse", "burni").any { s.contains(it) } ->
                R.drawable.ic_weather_rain

            listOf("ceață", "ceata", "negură", "negura", "pâcl").any { s.contains(it) } ->
                R.drawable.ic_weather_fog

            listOf("vânt", "vant", "vijel").any { s.contains(it) } ->
                R.drawable.ic_weather_wind

            listOf("parțial", "partial", "variabil", "puțin noros", "putin noros").any { s.contains(it) } ->
                if (isNight) R.drawable.ic_weather_partly_cloudy_night else R.drawable.ic_weather_partly_cloudy

            listOf("senin", "însorit", "insorit", "clear").any { s.contains(it) } ->
                if (isNight) R.drawable.ic_weather_moon else R.drawable.ic_weather_sun

            else -> R.drawable.ic_weather_cloud
        }
    }
}

@Composable
fun AnimatedWeatherIcon(
    condition: String,
    isNight: Boolean = false,
    size: Dp = 76.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "weather_icon_motion")
    val floatOffset by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobbing"
    )
    val scalePulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Image(
        painter = painterResource(id = WeatherIconMapper.drawableFor(condition, isNight)),
        contentDescription = condition,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatOffset
                scaleX = scalePulse
                scaleY = scalePulse
            }
    )
}
