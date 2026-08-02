package com.example.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.example.domain.model.Weather
import com.example.domain.model.WeatherCondition
import com.example.domain.model.WindDirection
import com.example.ui.map.WeatherUiState
import java.util.Locale
import kotlin.math.roundToInt

private val CardBackground = Color(0xFF102B28)
private val CardBorder = Color(0xFF1E4642)
private val Accent = Color(0xFF5DF2D6)
private val Muted = Color(0xFF98BCB6)
private val SunColor = Color(0xFFF9C74F)

/**
 * Current conditions where the player is standing.
 *
 * Every state the fetch can be in gets a card of its own rather than an empty gap, so the section
 * does not appear and disappear as the player walks in and out of signal.
 */
@Composable
fun WeatherCard(
    state: WeatherUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_card")
    ) {
        when (state) {
            is WeatherUiState.Loaded -> WeatherDetails(state.weather)
            WeatherUiState.Loading, WeatherUiState.Idle -> WeatherPlaceholder(
                message = "Hava məlumatı yüklənir...",
                showSpinner = true
            )
            WeatherUiState.NoLocation -> WeatherPlaceholder(
                message = "Məkan təyin olunduqdan sonra hava göstəriləcək",
                showSpinner = false
            )
            WeatherUiState.Unavailable -> WeatherPlaceholder(
                message = "Hava məlumatı alınmadı",
                showSpinner = false,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun WeatherPlaceholder(
    message: String,
    showSpinner: Boolean,
    onRetry: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(text = message, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("weather_retry_button")) {
                Text("Yenilə", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeatherDetails(weather: Weather) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = weather.condition.icon(),
                    contentDescription = weather.condition.label,
                    tint = if (weather.condition == WeatherCondition.CLEAR) SunColor else Accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${weather.temperatureCelsius.roundToInt()}°",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("weather_temperature")
                )
                Text(
                    text = weather.condition.label,
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Hiss olunur ${weather.feelsLikeCelsius.roundToInt()}°",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherMetric(
                icon = Icons.Default.Air,
                label = "Külək",
                // km/saat, not km/s - the forecast reports kilometres per hour, and "km/s" reads as
                // per second.
                value = String.format(Locale.US, "%.0f km/saat", weather.windSpeedKmh),
                detail = WindDirection.fromDegrees(weather.windDirectionDegrees).label,
                modifier = Modifier.weight(1f)
            )
            WeatherMetric(
                icon = Icons.Default.WaterDrop,
                label = "Rütubət",
                value = "${weather.humidityPercent}%",
                detail = humidityComfort(weather.humidityPercent),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SunArc(weather)
    }
}

@Composable
private fun WeatherMetric(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0C1F1D), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = detail, color = Muted, fontSize = 11.sp)
    }
}

/**
 * Sunrise to sunset as an arc, with the sun placed where it currently is along it.
 *
 * A pair of times tells the player when the sun rises and sets; the arc tells them how much of the
 * daylight is left, which is the thing that decides whether there is time for another walk.
 */
@Composable
private fun SunArc(weather: Weather) {
    val progress = weather.daylightProgress

    Column(modifier = Modifier.fillMaxWidth().testTag("sun_arc")) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            val arc = Path().apply {
                moveTo(0f, size.height)
                quadraticTo(size.width / 2f, -size.height * 0.55f, size.width, size.height)
            }

            drawPath(
                path = arc,
                color = Muted.copy(alpha = 0.25f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            if (progress != null) {
                // The travelled part of the arc, drawn by measuring the same curve rather than
                // recomputing it - so the highlight and the sun can never drift apart.
                val measure = PathMeasure().apply { setPath(arc, forceClosed = false) }
                val travelled = Path()
                measure.getSegment(0f, measure.length * progress, travelled, startWithMoveTo = true)
                drawPath(
                    path = travelled,
                    color = SunColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val sun = measure.getPosition(measure.length * progress)
                drawCircle(color = SunColor.copy(alpha = 0.28f), radius = 14.dp.toPx(), center = sun)
                drawCircle(color = SunColor, radius = 7.dp.toPx(), center = sun)
            }

            // Horizon line the arc rises from and returns to.
            drawLine(
                color = Muted.copy(alpha = 0.3f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SunTime(
                label = "Gündoğumu",
                time = Weather.formatMinuteOfDay(weather.sunriseMinuteOfDay),
                alignEnd = false
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (weather.isDaytime) "Gündüz" else "Gecə",
                    color = Muted,
                    fontSize = 11.sp
                )
                Text(
                    text = formatDuration(weather.daylightMinutes),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            SunTime(
                label = "Günbatımı",
                time = Weather.formatMinuteOfDay(weather.sunsetMinuteOfDay),
                alignEnd = true
            )
        }
    }
}

@Composable
private fun SunTime(label: String, time: String, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WbTwilight,
                contentDescription = null,
                tint = SunColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = Muted, fontSize = 11.sp)
        }
        Text(
            text = time,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun WeatherCondition.icon(): ImageVector = when (this) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> Icons.Default.LightMode
    WeatherCondition.CLOUDY, WeatherCondition.FOG -> Icons.Default.Cloud
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> Icons.Default.Umbrella
    WeatherCondition.SHOWERS -> Icons.Default.Grain
    WeatherCondition.SNOW -> Icons.Default.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Default.Bolt
    WeatherCondition.UNKNOWN -> Icons.Default.HelpOutline
}

private fun humidityComfort(percent: Int): String = when {
    percent < 30 -> "Quru"
    percent <= 60 -> "Rahat"
    else -> "Nəm"
}

private fun formatDuration(minutes: Int): String = "${minutes / 60}s ${minutes % 60}d"
