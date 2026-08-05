package com.example.domain.stats

import com.example.domain.model.ExploredCell
import com.example.domain.model.WalkSession
import java.util.TimeZone

/**
 * Splits the exploration history into the last seven local days, for the breakdown the profile's
 * stat cards open onto.
 *
 * Pure and dependency-free, like [com.example.domain.achievement.PlayerStatsCalculator], so the whole
 * thing runs in a unit test with no Android and no database.
 *
 * Distance is attributed to the day a walk *started* on, matching how the achievement rules count a
 * day's distance: a walk that runs past midnight belongs to the evening the player set out on, not
 * split across two days neither of which they would recognise.
 */
object WeeklyStatsCalculator {

    /** How many days the breakdown covers, today included. */
    const val WINDOW_DAYS = 7

    /**
     * The last [WINDOW_DAYS] days, oldest first, always exactly that many entries.
     *
     * @param cells the full exploration history; only the cells claimed inside the window count.
     * @param sessions every walk taken, for the per-day distance.
     * @param zone the calendar to interpret timestamps in; injectable so tests are not at the mercy
     *   of the machine they run on.
     * @param now the instant "today" is measured from.
     */
    fun lastSevenDays(
        cells: List<ExploredCell>,
        sessions: List<WalkSession>,
        zone: TimeZone = TimeZone.getDefault(),
        now: Long = System.currentTimeMillis()
    ): List<DailyStat> {
        val today = CalendarDays.epochDay(now, zone)
        val window = (today - (WINDOW_DAYS - 1))..today

        val cellsPerDay = HashMap<Long, Int>(WINDOW_DAYS)
        for (cell in cells) {
            val day = CalendarDays.epochDay(cell.exploredAt, zone)
            if (day in window) cellsPerDay[day] = (cellsPerDay[day] ?: 0) + 1
        }

        val metersPerDay = HashMap<Long, Double>(WINDOW_DAYS)
        for (session in sessions) {
            val day = CalendarDays.epochDay(session.startedAt, zone)
            if (day in window) metersPerDay[day] = (metersPerDay[day] ?: 0.0) + session.distanceMeters
        }

        return window.map { day ->
            DailyStat(
                epochDay = day,
                distanceMeters = metersPerDay[day] ?: 0.0,
                cellCount = cellsPerDay[day] ?: 0
            )
        }
    }
}
