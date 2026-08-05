package com.example.domain.stats

import com.example.domain.engine.HexGridConfig
import com.example.domain.model.ExploredCell
import com.example.domain.model.WalkSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The window is the whole point of this calculator, and every way of getting it wrong is invisible
 * on the screen: a day boundary taken in UTC puts an evening walk on tomorrow, a missing day
 * silently shortens the chart, and a walk from last month quietly inflates the week's total.
 *
 * Every case fixes the timezone explicitly, so the suite means the same thing on any machine.
 */
class WeeklyStatsCalculatorTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Baku")

    /** A local timestamp in [zone], which is what the whole breakdown is expressed in. */
    private fun at(
        year: Int = 2026,
        month: Int = Calendar.JUNE,
        day: Int = 10,
        hour: Int = 12,
        minute: Int = 0
    ): Long = GregorianCalendar(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    private val now = at(day = 10, hour = 15)

    private fun cells(vararg times: Long): List<ExploredCell> =
        times.mapIndexed { i, t -> ExploredCell("cell$i", t, ExploredCell.LEVEL_WALKED) }

    private fun session(startedAt: Long, meters: Double) =
        WalkSession(id = 0, startedAt = startedAt, endedAt = startedAt, distanceMeters = meters)

    private fun week(
        cells: List<ExploredCell> = emptyList(),
        sessions: List<WalkSession> = emptyList()
    ) = WeeklyStatsCalculator.lastSevenDays(cells, sessions, zone, now)

    @Test
    fun `an empty history is still seven days`() {
        val days = week()

        assertEquals(WeeklyStatsCalculator.WINDOW_DAYS, days.size)
        assertEquals(0, days.sumOf { it.cellCount })
        assertEquals(0.0, days.sumOf { it.distanceMeters }, 0.0)
    }

    @Test
    fun `days run oldest first and end with today`() {
        val days = week()

        assertEquals(days.map { it.epochDay }.sorted(), days.map { it.epochDay })
        assertEquals(CalendarDays.epochDay(now, zone), days.last().epochDay)
        assertEquals(CalendarDays.epochDay(now, zone) - 6, days.first().epochDay)
    }

    @Test
    fun `cells are counted on the local day they were claimed`() {
        val days = week(cells = cells(at(day = 10), at(day = 10, hour = 20), at(day = 8)))

        assertEquals(2, days.last().cellCount)
        assertEquals(1, days[days.size - 3].cellCount)
        assertEquals(3, days.sumOf { it.cellCount })
    }

    @Test
    fun `a cell claimed just after midnight counts on the new day`() {
        // 00:30 local in Baku is 20:30 UTC the *previous* day, so grouping by a UTC boundary would
        // file this one under yesterday - which is exactly what the player would not recognise.
        val days = week(cells = cells(at(day = 10, hour = 0, minute = 30)))

        assertEquals(1, days.last().cellCount)
        assertEquals(0, days[days.size - 2].cellCount)
    }

    @Test
    fun `history from outside the window is left out`() {
        val days = week(
            cells = cells(at(day = 3), at(month = Calendar.MAY, day = 1)),
            sessions = listOf(session(at(day = 1), 5000.0))
        )

        assertEquals(0, days.sumOf { it.cellCount })
        assertEquals(0.0, days.sumOf { it.distanceMeters }, 0.0)
    }

    @Test
    fun `the day exactly seven days back is the first one in`() {
        val days = week(cells = cells(at(day = 4, hour = 23), at(day = 3, hour = 23)))

        assertEquals(1, days.first().cellCount)
        assertEquals(1, days.sumOf { it.cellCount })
    }

    @Test
    fun `several walks on one day add up`() {
        val days = week(
            sessions = listOf(
                session(at(day = 9, hour = 8), 1200.0),
                session(at(day = 9, hour = 18), 800.0),
                session(at(day = 10, hour = 9), 500.0)
            )
        )

        assertEquals(2000.0, days[days.size - 2].distanceMeters, 0.001)
        assertEquals(500.0, days.last().distanceMeters, 0.001)
    }

    @Test
    fun `a walk that runs past midnight belongs to the evening it started on`() {
        val days = week(sessions = listOf(session(at(day = 9, hour = 23, minute = 30), 3000.0)))

        assertEquals(3000.0, days[days.size - 2].distanceMeters, 0.001)
        assertEquals(0.0, days.last().distanceMeters, 0.001)
    }

    @Test
    fun `a day's area follows its cell count`() {
        val days = week(cells = cells(at(day = 10), at(day = 10)))

        assertEquals(2 * HexGridConfig.CELL_AREA_SQUARE_METERS, days.last().areaSquareMeters, 0.001)
        assertEquals(0.0, days.first().areaSquareMeters, 0.001)
    }
}
