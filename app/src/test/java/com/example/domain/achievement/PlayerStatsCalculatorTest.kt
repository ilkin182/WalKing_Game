package com.example.domain.achievement

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.RegionStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The achievement rules are only as good as this snapshot, and most of them turn on calendar
 * arithmetic that is easy to get subtly wrong (a UTC day boundary, a weekend that spans a week
 * number, a streak that should not count its own first run as a comeback).
 *
 * Every case fixes the timezone explicitly, so the suite means the same thing on any machine.
 */
class PlayerStatsCalculatorTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Baku")

    /** A local timestamp in [zone], which is what every rule here is expressed in. */
    private fun at(
        year: Int = 2026,
        month: Int = Calendar.JUNE,
        day: Int = 1,
        hour: Int = 12,
        minute: Int = 0
    ): Long = GregorianCalendar(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    private fun cells(vararg times: Long): List<ExploredCell> =
        times.mapIndexed { i, t -> ExploredCell("cell$i", t, ExploredCell.LEVEL_WALKED) }

    private fun calculate(
        cells: List<ExploredCell>,
        distanceMeters: Double = 0.0,
        regions: List<RegionStat> = emptyList(),
        startMillis: Long = 0L,
        resolveCenter: (String) -> Coordinate? = { null },
        now: Long = at(month = Calendar.DECEMBER, day = 31)
    ) = PlayerStatsCalculator.calculate(
        cells = cells,
        totalDistanceMeters = distanceMeters,
        regionStats = regions,
        statsStartMillis = startMillis,
        resolveCenter = resolveCenter,
        zone = zone,
        now = now
    )

    @Test
    fun `an empty history still reports the odometer`() {
        val stats = calculate(emptyList(), distanceMeters = 4200.0)

        assertEquals(0, stats.totalCells)
        assertEquals(4200.0, stats.totalDistanceMeters, 1e-6)
        assertEquals(0, stats.longestDayStreak)
    }

    @Test
    fun `cells in an hour use a sliding window, not clock hours`() {
        // 13:50, 13:55, 14:05, 14:20 - four within any 60 minutes, though they straddle the hour.
        val stats = calculate(
            cells(
                at(hour = 13, minute = 50),
                at(hour = 13, minute = 55),
                at(hour = 14, minute = 5),
                at(hour = 14, minute = 20)
            )
        )

        assertEquals(4, stats.maxCellsInOneHour)
    }

    @Test
    fun `a cell more than an hour later starts a new window`() {
        val stats = calculate(
            cells(at(hour = 8), at(hour = 8, minute = 30), at(hour = 11))
        )

        assertEquals(2, stats.maxCellsInOneHour)
    }

    @Test
    fun `days and streaks are counted in the player's own calendar`() {
        // 23:30 local on the 1st and 00:30 local on the 2nd are two days here, but the same UTC day.
        val stats = calculate(
            cells(at(day = 1, hour = 23, minute = 30), at(day = 2, hour = 0, minute = 30))
        )

        assertEquals(2, stats.activeDays)
        assertEquals(2, stats.longestDayStreak)
    }

    @Test
    fun `a missed day breaks the streak`() {
        val stats = calculate(cells(at(day = 1), at(day = 2), at(day = 4), at(day = 5), at(day = 6)))

        assertEquals(3, stats.longestDayStreak)
        assertEquals(5, stats.activeDays)
    }

    @Test
    fun `the busiest day is the one reported`() {
        val stats = calculate(
            cells(
                at(day = 1, hour = 9), at(day = 1, hour = 10),
                at(day = 2, hour = 9), at(day = 2, hour = 10), at(day = 2, hour = 11)
            )
        )

        assertEquals(3, stats.maxCellsInOneDay)
    }

    @Test
    fun `a morning streak only counts days walked before nine`() {
        val stats = calculate(
            cells(at(day = 1, hour = 7), at(day = 2, hour = 8), at(day = 3, hour = 18))
        )

        assertEquals(2, stats.longestMorningStreak)
    }

    @Test
    fun `a weekend in the middle does not break the workday streak`() {
        // 2026-06-04 is a Thursday: Thu, Fri, then Mon and Tue after the weekend.
        val stats = calculate(
            cells(
                at(day = 4, hour = 10), at(day = 5, hour = 10),
                at(day = 8, hour = 10), at(day = 9, hour = 10)
            )
        )

        assertEquals(4, stats.longestWorkdayStreak)
    }

    @Test
    fun `a walk outside working hours does not count towards the workday streak`() {
        val stats = calculate(cells(at(day = 4, hour = 22), at(day = 5, hour = 23)))

        assertEquals(0, stats.longestWorkdayStreak)
    }

    @Test
    fun `both days of one weekend count as a single weekend`() {
        // 2026-06-06 and 06-07 are Saturday and Sunday of the same weekend.
        val stats = calculate(cells(at(day = 6), at(day = 7)))

        assertEquals(1, stats.longestWeekendStreak)
    }

    @Test
    fun `consecutive weekends run, a skipped one resets`() {
        // Weekends of 6-7, 13-14 June, then a gap, then 27-28.
        val stats = calculate(cells(at(day = 6), at(day = 13), at(day = 27), at(day = 28)))

        assertEquals(2, stats.longestWeekendStreak)
    }

    @Test
    fun `a first streak is not a comeback`() {
        val week = (1..7).map { at(day = it) }

        assertFalse(calculate(cells(*week.toLongArray())).hasComebackStreak)
    }

    @Test
    fun `a full week after a broken streak is a comeback`() {
        val first = (1..3).map { at(day = it) }
        val after = (10..16).map { at(day = it) }

        assertTrue(calculate(cells(*(first + after).toLongArray())).hasComebackStreak)
    }

    @Test
    fun `a gap of over a month counts as coming back`() {
        val stats = calculate(cells(at(month = Calendar.APRIL, day = 1), at(month = Calendar.JUNE, day = 1)))

        assertTrue(stats.returnedAfterLongBreak)
    }

    @Test
    fun `a fortnight away is a break in the streak but not a comeback from quitting`() {
        val stats = calculate(cells(at(day = 1), at(day = 15)))

        assertFalse(stats.returnedAfterLongBreak)
    }

    @Test
    fun `times of day are picked out`() {
        val stats = calculate(
            cells(
                at(hour = 2),                       // night
                at(hour = 0, minute = 0),           // exactly midnight, also night
                at(hour = 12, minute = 30),         // midday window
                at(hour = 13, minute = 30),         // midday window
                at(hour = 19)
            )
        )

        assertEquals(2, stats.nightCells)
        assertTrue(stats.hasExactMidnightCell)
        assertEquals(2, stats.maxMiddayCellsInOneDay)
    }

    @Test
    fun `two o'clock is not exactly midnight`() {
        assertFalse(calculate(cells(at(hour = 0, minute = 1))).hasExactMidnightCell)
    }

    @Test
    fun `the four six-hour blocks are counted`() {
        val stats = calculate(cells(at(hour = 1), at(hour = 7), at(hour = 13), at(hour = 20)))

        assertEquals(4, stats.sixHourBlocksCovered)
    }

    @Test
    fun `holidays are recognised by their date`() {
        val stats = calculate(
            cells(at(month = Calendar.MARCH, day = 21), at(month = Calendar.JANUARY, day = 1))
        )

        assertTrue(stats.hasNowruzCell)
        assertTrue(stats.hasNewYearCell)
    }

    @Test
    fun `seasons and their months are counted`() {
        val stats = calculate(
            cells(
                at(month = Calendar.JANUARY, day = 10),
                at(month = Calendar.JANUARY, day = 11),
                at(month = Calendar.MARCH, day = 5),
                at(month = Calendar.JULY, day = 5),
                at(month = Calendar.OCTOBER, day = 5)
            )
        )

        assertEquals(4, stats.seasonsCovered)
        assertEquals(1, stats.marchCells)
        assertEquals(2, stats.winterActiveDays)
    }

    @Test
    fun `zone coverage comes from the region stats`() {
        val stats = calculate(
            cells(at()),
            regions = listOf(
                RegionStat("Nərimanov", exploredHexes = 600, totalEstimatedHexes = 600),
                RegionStat("Yasamal", exploredHexes = 300, totalEstimatedHexes = 1000),
                RegionStat("Nəsimi", exploredHexes = 100, totalEstimatedHexes = 1000)
            )
        )

        assertEquals(3, stats.distinctZones)
        assertEquals(100.0, stats.bestZonePercentage, 1e-6)
        assertEquals(2, stats.zonesAtQuarter)
        assertEquals(1, stats.zonesAtHalf)
        assertEquals(1, stats.zonesComplete)
        assertEquals(600, stats.largestCompleteZoneCells)
    }

    @Test
    fun `the farthest cell is measured from the first one claimed`() {
        val centres = mapOf(
            "cell0" to Coordinate(40.4093, 49.8671),   // Baku
            "cell1" to Coordinate(40.4200, 49.8800),   // a couple of km away
            "cell2" to Coordinate(40.6828, 46.3606)    // Ganja, ~298 km great-circle
        )
        val stats = calculate(
            cells(at(day = 1), at(day = 2), at(day = 3)),
            resolveCenter = { centres[it] }
        )

        assertTrue(
            "expected roughly 298 km, got ${stats.farthestCellMeters}",
            stats.farthestCellMeters in 290_000.0..305_000.0
        )
    }

    @Test
    fun `cells the grid cannot resolve do not break the distance calculation`() {
        val stats = calculate(cells(at(day = 1), at(day = 2)), resolveCenter = { null })

        assertEquals(0.0, stats.farthestCellMeters, 1e-6)
    }

    @Test
    fun `walking on an anniversary of the first day counts`() {
        val start = at(year = 2024, month = Calendar.JUNE, day = 1)
        val stats = calculate(
            cells(at(year = 2025, month = Calendar.JUNE, day = 1, hour = 9)),
            startMillis = start,
            now = at(year = 2026, month = Calendar.JUNE, day = 2)
        )

        assertTrue(stats.activeOnAppAnniversary)
    }

    @Test
    fun `walking on an ordinary day is not an anniversary`() {
        val start = at(year = 2024, month = Calendar.JUNE, day = 1)
        val stats = calculate(
            cells(at(year = 2025, month = Calendar.JUNE, day = 3)),
            startMillis = start,
            now = at(year = 2026, month = Calendar.JUNE, day = 2)
        )

        assertFalse(stats.activeOnAppAnniversary)
    }
}
