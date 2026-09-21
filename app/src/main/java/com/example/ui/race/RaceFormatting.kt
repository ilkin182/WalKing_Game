package com.example.ui.race

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The races tab's palette and its handful of time strings.
 *
 * The colours are the leaderboard's, restated here rather than shared: both tabs are drawn against
 * the same dark map chrome, and a single palette object would be the start of a design system this
 * app does not have. The one addition is [RaceDanger], for leaving and cancelling - the two actions
 * that take something away from other people.
 */
internal val RaceBackground = Color(0xFF0F1A1B)
internal val RaceCardSurface = Color(0xFF0F2624)
internal val RaceCardBorder = Color(0xFF1B3D3A)
internal val RaceAccent = Color(0xFF5DF2D6)
internal val RaceOnAccent = Color(0xFF0A1F1C)
internal val RaceMuted = Color(0xFF98BCB6)
internal val RaceDim = Color(0xFF5A7C77)
internal val RaceGold = Color(0xFFFFD700)
internal val RaceSilver = Color(0xFFC0C0C0)
internal val RaceBronze = Color(0xFFE27D60)
internal val RaceDanger = Color(0xFFFF6B6B)

private const val MINUTE = 60 * 1000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Azerbaijani, because every other string the player reads in this app is. */
private val displayLocale: Locale = Locale.forLanguageTag("az")

/**
 * How long is left, at the coarsest unit that still says something useful.
 *
 * Days and hours while there is more than a day, hours and minutes below that, minutes in the last
 * hour: a race with four days to run does not need its seconds counted, and one with four minutes
 * left needs nothing else.
 */
internal fun formatRemaining(millis: Long): String {
    if (millis <= 0L) return "Bitdi"

    val days = millis / DAY
    val hours = (millis % DAY) / HOUR
    val minutes = (millis % HOUR) / MINUTE

    return when {
        days > 0 -> "$days gün $hours saat"
        hours > 0 -> "$hours saat $minutes dəq"
        minutes > 0 -> "$minutes dəq"
        else -> "1 dəqdən az"
    }
}

/** The deadline as a date somebody can put in a calendar. */
internal fun formatDeadline(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", displayLocale).format(Date(millis))

/** The short form, for a list row where the date is one line among several. */
internal fun formatShortDate(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", displayLocale).format(Date(millis))

/**
 * How long ago a runner's figure was last updated.
 *
 * On the screen next to every score that is not the player's own, because without a server those
 * figures are as old as the last time that phone was in reach - and a stale number that looks live
 * is the one genuinely misleading thing this screen could do.
 */
internal fun formatAgo(millis: Long, now: Long): String {
    val elapsed = (now - millis).coerceAtLeast(0L)
    return when {
        elapsed < 2 * MINUTE -> "indi"
        elapsed < HOUR -> "${elapsed / MINUTE} dəq əvvəl"
        elapsed < DAY -> "${elapsed / HOUR} saat əvvəl"
        else -> "${elapsed / DAY} gün əvvəl"
    }
}
