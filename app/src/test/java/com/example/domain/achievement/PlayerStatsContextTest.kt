package com.example.domain.achievement

import com.example.domain.model.CellContext
import com.example.domain.model.ExploredCell
import com.example.domain.model.WalkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The statistics that come from what was recorded *with* each cell - the weather, the town, the
 * height - plus the per-walk distances.
 *
 * The recurring theme is that a missing value must never read as a zero: a cell claimed before the
 * app recorded conditions has no temperature, and that is not the same as having been claimed at
 * 0 °C, which would hand out the cold-weather badge to everyone with an old save.
 */
class PlayerStatsContextTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Baku")

    private fun at(
        month: Int = Calendar.JUNE,
        day: Int = 1,
        hour: Int = 12,
        minute: Int = 0
    ): Long = GregorianCalendar(zone).apply {
        clear()
        set(2026, month, day, hour, minute, 0)
    }.timeInMillis

    private fun cell(id: String, at: Long, context: CellContext? = null) =
        ExploredCell(id, at, ExploredCell.LEVEL_WALKED, context)

    private fun calculate(
        cells: List<ExploredCell> = emptyList(),
        sessions: List<WalkSession> = emptyList()
    ) = PlayerStatsCalculator.calculate(
        cells = cells,
        totalDistanceMeters = 0.0,
        regionStats = emptyList(),
        statsStartMillis = 0L,
        sessions = sessions,
        zone = zone,
        now = at(month = Calendar.DECEMBER, day = 31)
    )

    // ---------------------------------------------------------------- walks

    @Test
    fun `the longest single walk and the biggest day are reported apart`() {
        val stats = calculate(
            cells = listOf(cell("a", at())),
            sessions = listOf(
                WalkSession(1, at(day = 1, hour = 8), at(day = 1, hour = 9), 6_000.0),
                WalkSession(2, at(day = 1, hour = 18), at(day = 1, hour = 19), 4_000.0),
                WalkSession(3, at(day = 2, hour = 8), at(day = 2, hour = 12), 9_000.0)
            )
        )

        // Two walks on day one add up to more than the single longest walk.
        assertEquals(10_000.0, stats.maxDayDistanceMeters, 1e-6)
        assertEquals(9_000.0, stats.maxSessionDistanceMeters, 1e-6)
    }

    @Test
    fun `a walk belongs to the day it set out on`() {
        val stats = calculate(
            cells = listOf(cell("a", at())),
            sessions = listOf(
                WalkSession(1, at(day = 1, hour = 23), at(day = 2, hour = 1), 5_000.0),
                WalkSession(2, at(day = 2, hour = 10), at(day = 2, hour = 11), 4_000.0)
            )
        )

        // The night walk counts towards the 1st, so no day totals 9 km.
        assertEquals(5_000.0, stats.maxDayDistanceMeters, 1e-6)
    }

    @Test
    fun `no walks recorded leaves both distances at zero`() {
        val stats = calculate(cells = listOf(cell("a", at())))

        assertEquals(0.0, stats.maxDayDistanceMeters, 1e-6)
        assertEquals(0.0, stats.maxSessionDistanceMeters, 1e-6)
    }

    // ---------------------------------------------------------------- weather

    @Test
    fun `rain is counted per cell and snow is a yes or no`() {
        val stats = calculate(
            listOf(
                cell("a", at(), CellContext(weatherCode = 61)),  // rain
                cell("b", at(), CellContext(weatherCode = 80)),  // showers
                cell("c", at(), CellContext(weatherCode = 95)),  // thunderstorm
                cell("d", at(), CellContext(weatherCode = 73)),  // snow
                cell("e", at(), CellContext(weatherCode = 0))    // clear
            )
        )

        assertEquals(3, stats.rainyCells)
        assertTrue(stats.hasSnowCell)
    }

    @Test
    fun `temperature and wind extremes are picked up`() {
        val stats = calculate(
            listOf(
                cell("a", at(), CellContext(temperatureCelsius = 36.5)),
                cell("b", at(), CellContext(temperatureCelsius = -3.0)),
                cell("c", at(), CellContext(windSpeedKmh = 55.0))
            )
        )

        assertTrue(stats.hasHotCell)
        assertTrue(stats.hasColdCell)
        assertTrue(stats.hasWindyCell)
    }

    @Test
    fun `cells with no recorded conditions are skipped, not read as zero`() {
        val stats = calculate(listOf(cell("a", at()), cell("b", at(), CellContext(city = "Bakı"))))

        assertEquals(0, stats.rainyCells)
        assertFalse(stats.hasColdCell)
        assertFalse(stats.hasHotCell)
        assertFalse(stats.hasWindyCell)
        assertFalse(stats.hasSnowCell)
    }

    @Test
    fun `a mild calm day unlocks neither extreme`() {
        val stats = calculate(
            listOf(cell("a", at(), CellContext(temperatureCelsius = 20.0, windSpeedKmh = 5.0)))
        )

        assertFalse(stats.hasHotCell)
        assertFalse(stats.hasColdCell)
        assertFalse(stats.hasWindyCell)
    }

    // ---------------------------------------------------------------- sun

    @Test
    fun `a cell claimed before sunrise and one at sunset are both recognised`() {
        val sun = CellContext(sunriseMinuteOfDay = 6 * 60, sunsetMinuteOfDay = 20 * 60)
        val stats = calculate(
            listOf(
                cell("dawn", at(hour = 5, minute = 30), sun),
                cell("dusk", at(hour = 20, minute = 5), sun)
            )
        )

        assertTrue(stats.hasBeforeSunriseCell)
        assertTrue(stats.hasAtSunsetCell)
    }

    @Test
    fun `midday is neither dawn nor dusk`() {
        val sun = CellContext(sunriseMinuteOfDay = 6 * 60, sunsetMinuteOfDay = 20 * 60)
        val stats = calculate(listOf(cell("noon", at(hour = 13), sun)))

        assertFalse(stats.hasBeforeSunriseCell)
        assertFalse(stats.hasAtSunsetCell)
    }

    @Test
    fun `an hour after sunset is too late to count as sunset`() {
        val sun = CellContext(sunriseMinuteOfDay = 6 * 60, sunsetMinuteOfDay = 20 * 60)
        val stats = calculate(listOf(cell("late", at(hour = 21), sun)))

        assertFalse(stats.hasAtSunsetCell)
    }

    // ---------------------------------------------------------------- places

    @Test
    fun `towns and countries are counted distinctly`() {
        val stats = calculate(
            listOf(
                cell("a", at(day = 1), CellContext(city = "Bakı", countryCode = "AZ")),
                cell("b", at(day = 2), CellContext(city = "Bakı", countryCode = "AZ")),
                cell("c", at(day = 3), CellContext(city = "Gəncə", countryCode = "AZ")),
                cell("d", at(day = 4), CellContext(city = "Tbilisi", countryCode = "GE"))
            )
        )

        assertEquals(3, stats.distinctCities)
        assertEquals(2, stats.distinctCountries)
    }

    @Test
    fun `coming home after being away counts as a homecoming`() {
        val stats = calculate(
            listOf(
                cell("a", at(day = 1), CellContext(city = "Bakı")),
                cell("b", at(day = 2), CellContext(city = "Gəncə")),
                cell("c", at(day = 3), CellContext(city = "Bakı"))
            )
        )

        assertTrue(stats.hasHomecoming)
    }

    @Test
    fun `never leaving home is not a homecoming`() {
        val stats = calculate(
            listOf(
                cell("a", at(day = 1), CellContext(city = "Bakı")),
                cell("b", at(day = 2), CellContext(city = "Bakı"))
            )
        )

        assertFalse(stats.hasHomecoming)
    }

    @Test
    fun `two towns on one day is an intercity day`() {
        val stats = calculate(
            listOf(
                cell("a", at(day = 5, hour = 9), CellContext(city = "Bakı")),
                cell("b", at(day = 5, hour = 17), CellContext(city = "Gəncə"))
            )
        )

        assertTrue(stats.hasIntercityDay)
    }

    @Test
    fun `two towns on different days is not an intercity day`() {
        val stats = calculate(
            listOf(
                cell("a", at(day = 5), CellContext(city = "Bakı")),
                cell("b", at(day = 6), CellContext(city = "Gəncə"))
            )
        )

        assertFalse(stats.hasIntercityDay)
    }

    // ---------------------------------------------------------------- elevation

    @Test
    fun `climb is the span between the lowest and highest ground claimed`() {
        val stats = calculate(
            listOf(
                cell("low", at(day = 1), CellContext(elevationMeters = -20.0)),
                cell("mid", at(day = 2), CellContext(elevationMeters = 140.0)),
                cell("high", at(day = 3), CellContext(elevationMeters = 320.0))
            )
        )

        assertEquals(320.0, stats.highestElevationMeters, 1e-6)
        assertEquals(340.0, stats.elevationGainMeters, 1e-6)
    }

    @Test
    fun `cells still waiting for their elevation do not drag the lowest point to zero`() {
        val stats = calculate(
            listOf(
                cell("pending", at(day = 1)),
                cell("known", at(day = 2), CellContext(elevationMeters = 500.0))
            )
        )

        assertEquals(500.0, stats.highestElevationMeters, 1e-6)
        assertEquals(0.0, stats.elevationGainMeters, 1e-6)
    }
}
