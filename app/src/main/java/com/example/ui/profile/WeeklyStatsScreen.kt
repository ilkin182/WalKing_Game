package com.example.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.stats.CalendarDays
import com.example.domain.stats.DailyStat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Which of the profile's headline numbers a weekly breakdown is showing.
 *
 * The three cards open onto the same screen rather than three near-identical ones: the shape of the
 * answer - seven days, a total, an average, a best day - is the same for all of them, and only the
 * number pulled out of each day and how it is written differ.
 */
enum class StatMetric(val title: String, val accent: Color) {
    DISTANCE("Gəzilən Məsafə", Color(0xFF5DF2D6)),
    AREA("Kəşf Edilən Ərazi", Color(0xFFF9A825)),
    CELLS("Fəth Edilən Hüceyrələr", Color(0xFFE27D60));

    /** The day's raw figure, in whatever unit [format] then writes it in. */
    fun valueOf(day: DailyStat): Double = when (this) {
        DISTANCE -> day.distanceMeters
        AREA -> day.areaSquareMeters
        CELLS -> day.cellCount.toDouble()
    }

    fun format(value: Double): String = when (this) {
        DISTANCE -> formatDistance(value)
        AREA -> formatArea(value)
        CELLS -> "${value.toInt()} hüceyrə"
    }
}

/**
 * The last seven days of one statistic: a bar per day, the week's total and average, and the
 * exact figure for each day underneath.
 *
 * Presentational only - [days] is expected to be exactly
 * [com.example.domain.stats.WeeklyStatsCalculator.WINDOW_DAYS] entries, oldest first, zeroes
 * included. Days the player did not go out are shown as zero rather than left out, so the week reads
 * as a habit rather than as a list of achievements.
 */
@Composable
fun WeeklyStatsScreen(
    metric: StatMetric,
    days: List<DailyStat>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val values = days.map { metric.valueOf(it) }
    val total = values.sum()
    val activeDays = values.count { it > 0.0 }
    val best = values.maxOrNull() ?: 0.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1A1B))
            // Swallows whatever this screen's own controls did not handle. The profile is still
            // composed underneath - that is what makes coming back to it instant - and its close and
            // logout buttons sit exactly where this toolbar has empty space. Consumed on the main
            // pass, so the back button above still gets first refusal.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("weekly_stats_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(48.dp)
                        .testTag("weekly_stats_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri qayıt",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = metric.title.uppercase(AZ),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Son 7 gün",
                        color = Color(0xFF98BCB6),
                        fontSize = 12.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Headline: the week as one number, with the two figures that give it meaning.
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2624)),
                    border = BorderStroke(1.dp, Color(0xFF1B3D3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "HƏFTƏLİK CƏM",
                            color = Color(0xFF98BCB6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = metric.format(total),
                            color = metric.accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("weekly_stats_total")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryFigure(
                                label = "Gündəlik orta",
                                value = metric.format(total / days.size.coerceAtLeast(1)),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryFigure(
                                label = "Ən yaxşı gün",
                                value = metric.format(best),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryFigure(
                                label = "Aktiv gün",
                                value = "$activeDays / ${days.size}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "GÜNLƏR ÜZRƏ",
                    color = Color(0xFF98BCB6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(16.dp))

                WeeklyBarChart(
                    days = days,
                    values = values,
                    best = best,
                    accent = metric.accent,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Newest first here, the opposite of the chart: a chart reads left to right
                    // through time, a list reads top-down from what just happened.
                    days.asReversed().forEach { day ->
                        DailyRow(
                            day = day,
                            value = metric.format(metric.valueOf(day)),
                            isEmpty = metric.valueOf(day) <= 0.0,
                            accent = metric.accent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFF5A7C77),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Seven bars, scaled against the best day of the week rather than against a fixed ceiling - the
 * point of the chart is which days were the big ones, and a fixed scale would flatten a quiet week
 * into seven stubs.
 */
@Composable
private fun WeeklyBarChart(
    days: List<DailyStat>,
    values: List<Double>,
    best: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEachIndexed { index, day ->
            val value = values[index]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // The track behind the bar keeps every day the same visible height, so a week
                    // with one big day still shows seven columns rather than one bar and six gaps.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14201F), RoundedCornerShape(8.dp))
                    )
                    val fraction = if (best > 0.0) (value / best).toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // A day with something on it never rounds away to an invisible sliver.
                            .fillMaxHeight(if (value > 0.0) fraction.coerceIn(0.04f, 1f) else 0f)
                            .background(
                                if (value >= best && value > 0.0) accent else accent.copy(alpha = 0.45f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = weekdayLabel(day.epochDay),
                    color = if (value > 0.0) Color(0xFF98BCB6) else Color(0xFF3F5754),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DailyRow(day: DailyStat, value: String, isEmpty: Boolean, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F2624), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = dateLabel(day.epochDay),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = fullWeekdayLabel(day.epochDay),
                color = Color(0xFF5A7C77),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = value,
            color = if (isEmpty) Color(0xFF3F5754) else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val AZ: Locale = Locale.Builder().setLanguage("az").build()

// Written out here rather than read from the platform's locale data: Azerbaijani day names are not
// present on every device the app runs on, and a week labelled half in Azerbaijani and half in
// English would be worse than one that is simply always right.
private val SHORT_WEEKDAYS = listOf("B.e", "Ç.a", "Ç", "C.a", "C", "Ş", "B")
private val FULL_WEEKDAYS = listOf(
    "Bazar ertəsi",
    "Çərşənbə axşamı",
    "Çərşənbə",
    "Cümə axşamı",
    "Cümə",
    "Şənbə",
    "Bazar"
)

private fun weekdayLabel(epochDay: Long): String = SHORT_WEEKDAYS[CalendarDays.dayOfWeek(epochDay)]

private fun fullWeekdayLabel(epochDay: Long): String = FULL_WEEKDAYS[CalendarDays.dayOfWeek(epochDay)]

/**
 * The day's date, e.g. "04 avqust".
 *
 * Formatted in UTC against [CalendarDays.utcInstantOf] because a day here is a local calendar date
 * with no time attached; applying the device's offset again would slide half of them onto the
 * neighbouring date.
 */
private fun dateLabel(epochDay: Long): String {
    val formatter = SimpleDateFormat("dd MMMM", AZ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(CalendarDays.utcInstantOf(epochDay)))
}

internal fun formatDistance(meters: Double): String {
    return if (meters < 1000.0) {
        "${meters.toInt()} metr"
    } else {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    }
}

internal fun formatArea(sqMeters: Double): String {
    return if (sqMeters < 10000.0) {
        String.format(Locale.US, "%,.0f m²", sqMeters)
    } else {
        String.format(Locale.US, "%.2f ha", sqMeters / 10000.0)
    }
}
