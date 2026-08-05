package com.example.domain.achievement

import com.example.domain.engine.CellChains
import com.example.domain.engine.PoiIndex
import com.example.domain.engine.RouteShapes
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest
import com.example.domain.model.RegionStat
import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.stats.CalendarDays
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the raw exploration history into the one [PlayerStats] snapshot the achievement rules read.
 *
 * Everything here is in the player's *local* calendar, not UTC: "walked on two days in a row" and
 * "walked at midnight" mean what the clock on their wall said, and a UTC day boundary would break
 * both for anyone east or west of Greenwich.
 *
 * Pure and dependency-free apart from the two things it cannot derive itself - the odometer reading
 * and the per-zone totals - so the whole thing runs in a unit test with no Android and no database.
 */
object PlayerStatsCalculator {

    /** A cell counts as a "morning" walk before this hour. */
    private const val MORNING_END_HOUR = 9

    /** The working day, for the weekday-habit achievement. */
    private const val WORK_START_HOUR = 9
    private const val WORK_END_HOUR = 18

    private const val MIDDAY_START_HOUR = 12
    private const val MIDDAY_END_HOUR = 14

    private const val NIGHT_END_HOUR = 4

    /** Days away that count as having stopped playing rather than having missed a day. */
    private const val LONG_BREAK_DAYS = 30

    /** Length of the streak that counts as a comeback once a previous streak was broken. */
    private const val COMEBACK_STREAK_DAYS = 7

    /** WMO codes for drizzle, rain, rain showers and thunderstorms - anything that falls as water. */
    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

    /** WMO codes for snow, snow grains and snow showers. */
    private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)

    private const val HOT_CELSIUS = 35.0
    private const val COLD_CELSIUS = 0.0

    /** Roughly a strong breeze on the Beaufort scale - the kind Baku is known for. */
    private const val STRONG_WIND_KMH = 40.0

    /** How close to the exact minute of sunset still counts as "at sunset". */
    private const val SUNSET_WINDOW_MINUTES = 15

    /**
     * How far into a town, as a fraction of its half-width, still counts as its centre.
     *
     * Measured against the town's own bounding box rather than in metres, so it means the same thing
     * in a capital and in a village: the middle seventh or so of the place, which is what anyone
     * living there would point at if asked where the centre is.
     */
    private const val CITY_CENTRE_FRACTION = 0.15

    /** And how far out counts as having reached the edge - the outer tenth of the same box. */
    private const val CITY_EDGE_FRACTION = 0.9

    private const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Day-of-week for epoch day 0 (1 January 1970 was a Thursday), counting Monday as 0. */
    private const val EPOCH_DAY_OF_WEEK_OFFSET = 3

    /**
     * @param cells the full exploration history.
     * @param totalDistanceMeters the odometer, which is tracked separately from the cells.
     * @param regionStats per-zone coverage, for the zone achievements.
     * @param statsStartMillis when the player started, for the anniversary achievement.
     * @param routes the path each walk traced, for the route-shape achievements.
     * @param closedLoops how many loops the player has closed, counted as they happened - the one
     *   figure here that cannot be re-derived from the history, see [PlayerStats.closedLoops].
     * @param pois the parks, monuments, metro, bridges, squares and coastline cached for the ground
     *   the player has covered, for the geography achievements. Empty until the background pass has
     *   fetched them, which reads as "not started" rather than as zero of something.
     * @param cityBounds the extent of the towns walked in, for the two city achievements.
     * @param resolveCenter cell id to its centre, for the "how far from home" achievement, for
     *   matching cells against places, and for telling the grid's six directions apart. Cells it
     *   cannot resolve are skipped rather than failing the whole calculation.
     * @param neighborsOf the cells touching one cell, for the longest-straight-line achievement.
     * @param zone the calendar to interpret timestamps in; injectable so tests are not at the mercy
     *   of the machine they run on.
     */
    fun calculate(
        cells: List<ExploredCell>,
        totalDistanceMeters: Double,
        regionStats: List<RegionStat>,
        statsStartMillis: Long,
        sessions: List<WalkSession> = emptyList(),
        routes: List<WalkRoute> = emptyList(),
        closedLoops: Int = 0,
        pois: List<PointOfInterest> = emptyList(),
        cityBounds: List<CityBounds> = emptyList(),
        resolveCenter: (String) -> Coordinate? = { null },
        neighborsOf: (String) -> List<String> = { emptyList() },
        zone: TimeZone = TimeZone.getDefault(),
        now: Long = System.currentTimeMillis()
    ): PlayerStats {
        if (cells.isEmpty()) {
            return PlayerStats(
                totalDistanceMeters = totalDistanceMeters,
                closedLoops = closedLoops
            )
                .withZones(regionStats)
                .withSessions(sessions, zone)
                .withRouteShapes(routes)
        }

        // Resolving a cell id into a coordinate goes through the grid engine and is the most
        // expensive thing in here; three separate passes below want the same few thousand answers,
        // so they are worked out once each. Failures are memoised as null too - a cell id from an
        // older grid will not start resolving because it was asked about a second time.
        val centers = HashMap<String, Coordinate?>(cells.size)
        val centerOf: (String) -> Coordinate? = { cellId ->
            centers.getOrPut(cellId) { runCatching { resolveCenter(cellId) }.getOrNull() }
        }

        val sorted = cells.sortedBy { it.exploredAt }
        val stamps = sorted.map { LocalStamp.of(it.exploredAt, zone) }

        val byDay = stamps.groupBy { it.epochDay }
        val activeDays = byDay.keys.sorted()

        return PlayerStats(
            totalCells = cells.size,
            totalDistanceMeters = totalDistanceMeters,

            maxCellsInOneHour = maxCellsWithin(sorted.map { it.exploredAt }, MILLIS_PER_HOUR),
            maxCellsInOneDay = byDay.values.maxOf { it.size },

            activeDays = activeDays.size,
            longestDayStreak = longestRun(activeDays),
            longestMorningStreak = longestRun(
                byDay.filterValues { day -> day.any { it.hour < MORNING_END_HOUR } }.keys.sorted()
            ),
            longestWorkdayStreak = longestWorkdayRun(byDay),
            longestWeekendStreak = longestWeekendRun(activeDays),
            hasComebackStreak = hasComebackStreak(activeDays),
            returnedAfterLongBreak = activeDays.zipWithNext().any { (a, b) -> b - a > LONG_BREAK_DAYS },

            nightCells = stamps.count { it.hour < NIGHT_END_HOUR },
            maxMiddayCellsInOneDay = byDay.values.maxOf { day ->
                day.count { it.hour in MIDDAY_START_HOUR until MIDDAY_END_HOUR }
            },
            hasExactMidnightCell = stamps.any { it.hour == 0 && it.minute == 0 },
            hasNowruzCell = stamps.any { it.month == Calendar.MARCH && it.dayOfMonth == 21 },
            hasNewYearCell = stamps.any { it.month == Calendar.JANUARY && it.dayOfMonth == 1 },
            sixHourBlocksCovered = stamps.map { it.hour / 6 }.distinct().size,

            seasonsCovered = stamps.map { seasonOf(it.month) }.distinct().size,
            marchCells = stamps.count { it.month == Calendar.MARCH },
            winterActiveDays = byDay.filterValues { day -> day.any { isWinter(it.month) } }.size,

            farthestCellMeters = farthestFromStart(sorted, centerOf),

            closedLoops = closedLoops,
            longestCellLine = CellChains.longestStraightRun(
                cells = cells.mapTo(HashSet(cells.size)) { it.cellId },
                neighborsOf = neighborsOf,
                centerOf = centerOf
            ),

            activeOnAppAnniversary = activeOnAnniversary(activeDays, statsStartMillis, zone, now)
        )
            .withZones(regionStats)
            .withSessions(sessions, zone)
            .withRouteShapes(routes)
            .withConditions(sorted, stamps)
            .withPlaces(sorted, stamps)
            .withElevation(sorted)
            .withGeography(sorted, pois, centerOf)
            .withCities(sorted, cityBounds, centerOf)
    }

    /**
     * What the player has walked past: parks, monuments, metro stations, bridges, squares, and how
     * much of the coast.
     *
     * Each cell is matched against the places near it and every hit recorded by
     * [PointOfInterest.identity], so the counts are of *places* rather than of encounters - walking
     * the length of one park a hundred times is one park. The two percentages are measured against
     * what the app actually knows about: the places in the tiles the player's own walking has caused
     * to be fetched. "Half the parks" therefore means half the parks around the ground they have
     * covered, not half the parks in the country, which is the only version of the question the app
     * can honestly answer - and the only one a player could act on.
     *
     * Everything stays at zero while [pois] is empty, which is what a player sees before the
     * background pass has caught up. That is deliberately indistinguishable from having visited
     * nothing: the alternative is a percentage over an empty set, which would read as either 0% or
     * 100% and both would be lies.
     */
    private fun PlayerStats.withGeography(
        cells: List<ExploredCell>,
        pois: List<PointOfInterest>,
        centerOf: (String) -> Coordinate?
    ): PlayerStats {
        if (pois.isEmpty()) return this
        val index = PoiIndex.build(pois)

        val visited = HashMap<PoiKind, MutableSet<String>>()
        var seaside = 0

        cells.forEach { cell ->
            val center = centerOf(cell.cellId) ?: return@forEach
            val near = index.matching(center.lat, center.lng)
            near.forEach { poi -> visited.getOrPut(poi.kind) { HashSet() }.add(poi.identity) }
            // Counted in cells rather than in places: "twenty cells by the sea" asks how much
            // seafront was walked, where the others ask how many separate things were visited.
            if (near.any { it.kind == PoiKind.COAST }) seaside++
        }

        fun seen(kind: PoiKind) = visited[kind]?.size ?: 0
        fun coverage(kind: PoiKind): Double {
            val known = index.countOf(kind)
            return if (known == 0) 0.0 else seen(kind) * 100.0 / known
        }

        return copy(
            distinctParks = seen(PoiKind.PARK),
            distinctMonuments = seen(PoiKind.MONUMENT),
            distinctMetroStations = seen(PoiKind.METRO),
            distinctBridges = seen(PoiKind.BRIDGE),
            distinctSquares = seen(PoiKind.SQUARE),
            seasideCells = seaside,
            parkCoveragePercent = coverage(PoiKind.PARK),
            coastlineCoveragePercent = coverage(PoiKind.COAST)
        )
    }

    /**
     * Whereabouts in their own town the player has been.
     *
     * A cell is placed against the bounding box of whichever known town contains it, and its
     * distance from that town's centre is expressed as a fraction of the box - 0 at the middle, 1 at
     * the boundary. Normalising this way is what lets one rule work for a capital and a village at
     * once; a radius in metres would put the whole of a small town inside its own centre.
     *
     * The centre is Nominatim's point for the place, not the middle of the box, because for a
     * coastal city those are a long way apart - half of Baku's bounding box is the Caspian.
     */
    private fun PlayerStats.withCities(
        cells: List<ExploredCell>,
        cityBounds: List<CityBounds>,
        centerOf: (String) -> Coordinate?
    ): PlayerStats {
        val known = cityBounds.filter { it.found }
        if (known.isEmpty()) return this

        var centreCells = 0
        var reachedEdge = false

        cells.forEach { cell ->
            val point = centerOf(cell.cellId) ?: return@forEach
            val city = known.firstOrNull { it.bounds?.contains(point.lat, point.lng) == true }
                ?: return@forEach
            val box = city.bounds ?: return@forEach
            val middle = city.center ?: return@forEach

            // Chebyshev distance in the box's own units: the larger of the two axes, so a point is
            // "at the edge" as soon as it is near any side rather than only near a corner.
            val halfHeight = (box.north - box.south) / 2.0
            val halfWidth = (box.east - box.west) / 2.0
            if (halfHeight <= 0.0 || halfWidth <= 0.0) return@forEach

            val offset = maxOf(
                abs(point.lat - middle.lat) / halfHeight,
                abs(point.lng - middle.lng) / halfWidth
            )

            if (offset <= CITY_CENTRE_FRACTION) centreCells++
            if (offset >= CITY_EDGE_FRACTION) reachedEdge = true
        }

        return copy(cityCentreCells = centreCells, hasReachedCityEdge = reachedEdge)
    }

    /**
     * What the player has drawn on the ground with their walks.
     *
     * Each walk is read on its own and the best of each shape kept: a boomerang is one walk that
     * came back to its start, not the player happening to end today where they began last Tuesday.
     */
    private fun PlayerStats.withRouteShapes(routes: List<WalkRoute>): PlayerStats {
        if (routes.isEmpty()) return this
        val shapes = routes.map { RouteShapes.analyze(it.points) }

        return copy(
            hasBoomerangRoute = shapes.any { it.isBoomerang },
            longestStraightRouteMeters = shapes.maxOf { it.longestStraightMeters },
            maxRouteTurns = shapes.maxOf { it.turns },
            hasSquareRoute = shapes.any { it.isSquare }
        )
    }

    private fun PlayerStats.withZones(regionStats: List<RegionStat>): PlayerStats {
        if (regionStats.isEmpty()) return this
        val complete = regionStats.filter { it.percentage >= 100.0 }
        return copy(
            distinctZones = regionStats.size,
            bestZonePercentage = regionStats.maxOf { it.percentage },
            zonesAtQuarter = regionStats.count { it.percentage >= 25.0 },
            zonesAtHalf = regionStats.count { it.percentage >= 50.0 },
            zonesComplete = complete.size,
            largestCompleteZoneCells = complete.maxOfOrNull { it.exploredHexes } ?: 0
        )
    }

    /**
     * The longest single walk, and the most walked in one day.
     *
     * A day's total sums the walks that *started* that day: a walk that runs past midnight belongs
     * to the day it set out on, which is how a player would describe it.
     */
    private fun PlayerStats.withSessions(sessions: List<WalkSession>, zone: TimeZone): PlayerStats {
        if (sessions.isEmpty()) return this
        val perDay = sessions
            .groupBy { epochDay(it.startedAt, zone) }
            .mapValues { (_, day) -> day.sumOf { it.distanceMeters } }

        return copy(
            maxSessionDistanceMeters = sessions.maxOf { it.distanceMeters },
            maxDayDistanceMeters = perDay.values.max()
        )
    }

    /**
     * What the weather was where cells were claimed.
     *
     * Only cells that actually carry a reading count - everything claimed before the app recorded
     * conditions, or while offline, is silently skipped rather than counted as a mild, calm day.
     */
    private fun PlayerStats.withConditions(
        cells: List<ExploredCell>,
        stamps: List<LocalStamp>
    ): PlayerStats {
        var rainy = 0
        var snow = false
        var hot = false
        var cold = false
        var windy = false
        var beforeSunrise = false
        var atSunset = false

        cells.forEachIndexed { i, cell ->
            val context = cell.context ?: return@forEachIndexed
            context.weatherCode?.let { code ->
                if (code in RAIN_CODES) rainy++
                if (code in SNOW_CODES) snow = true
            }
            context.temperatureCelsius?.let {
                if (it > HOT_CELSIUS) hot = true
                if (it < COLD_CELSIUS) cold = true
            }
            context.windSpeedKmh?.let { if (it >= STRONG_WIND_KMH) windy = true }

            val minuteOfDay = stamps[i].hour * 60 + stamps[i].minute
            context.sunriseMinuteOfDay?.let { if (minuteOfDay < it) beforeSunrise = true }
            context.sunsetMinuteOfDay?.let {
                if (abs(minuteOfDay - it) <= SUNSET_WINDOW_MINUTES) atSunset = true
            }
        }

        return copy(
            rainyCells = rainy,
            hasSnowCell = snow,
            hasHotCell = hot,
            hasColdCell = cold,
            hasWindyCell = windy,
            hasBeforeSunriseCell = beforeSunrise,
            hasAtSunsetCell = atSunset
        )
    }

    /**
     * Which towns and countries the player has claimed ground in.
     *
     * "Home" is the town of the earliest cell that has one; a homecoming is any later cell back in
     * it after having claimed one somewhere else.
     */
    private fun PlayerStats.withPlaces(
        cells: List<ExploredCell>,
        stamps: List<LocalStamp>
    ): PlayerStats {
        val cities = cells.mapNotNull { it.context?.city }
        if (cities.isEmpty()) return this

        val home = cities.first()
        var leftHome = false
        var cameBack = false
        cells.forEach { cell ->
            when (cell.context?.city) {
                null -> Unit
                home -> if (leftHome) cameBack = true
                else -> leftHome = true
            }
        }

        // Two towns claimed on one day is the closest the app can get to "an intercity route"
        // without tying cells to individual walks.
        val citiesPerDay = cells.indices
            .mapNotNull { i -> cells[i].context?.city?.let { stamps[i].epochDay to it } }
            .groupBy({ it.first }, { it.second })

        return copy(
            distinctCities = cities.distinct().size,
            distinctCountries = cells.mapNotNull { it.context?.countryCode }.distinct().size,
            hasHomecoming = cameBack,
            hasIntercityDay = citiesPerDay.values.any { it.distinct().size >= 2 }
        )
    }

    /** How high the player has been, and how much they climbed between their lowest and highest. */
    private fun PlayerStats.withElevation(cells: List<ExploredCell>): PlayerStats {
        val heights = cells.mapNotNull { it.context?.elevationMeters }
        if (heights.isEmpty()) return this
        return copy(
            highestElevationMeters = heights.max(),
            elevationGainMeters = heights.max() - heights.min()
        )
    }

    /**
     * The most cells that fall inside any [windowMillis] window, over timestamps already sorted
     * ascending. A sliding window rather than fixed clock hours: claiming 25 cells between 13:50 and
     * 14:20 is the same burst of walking whether or not it straddles the top of an hour.
     */
    private fun maxCellsWithin(sortedTimestamps: List<Long>, windowMillis: Long): Int {
        var best = 0
        var start = 0
        sortedTimestamps.indices.forEach { end ->
            while (sortedTimestamps[end] - sortedTimestamps[start] >= windowMillis) start++
            best = maxOf(best, end - start + 1)
        }
        return best
    }

    /** Longest run of consecutive values in an ascending, duplicate-free list of day numbers. */
    private fun longestRun(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        days.zipWithNext { previous, next ->
            run = if (next - previous == 1L) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    /**
     * Longest run of consecutive *working* days with a walk inside working hours.
     *
     * A weekend in the middle does not break the run - nobody expects a work-hours walk on Sunday -
     * but a missed Wednesday does.
     */
    private fun longestWorkdayRun(byDay: Map<Long, List<LocalStamp>>): Int {
        val qualifying = byDay
            .filterValues { day -> day.any { it.hour in WORK_START_HOUR until WORK_END_HOUR } }
            .keys
            .filter { !isWeekend(it) }
            .sorted()
        if (qualifying.isEmpty()) return 0

        var best = 1
        var run = 1
        qualifying.zipWithNext { previous, next ->
            run = if (noWorkdaysBetween(previous, next)) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    /** True when [later] is the next working day after [earlier], skipping over a weekend. */
    private fun noWorkdaysBetween(earlier: Long, later: Long): Boolean =
        ((earlier + 1)until later).none { !isWeekend(it) }

    /**
     * Longest run of consecutive calendar weekends with at least one walk in them.
     *
     * Weekends are keyed by the week they belong to, so Saturday and Sunday of the same weekend
     * count once, and two walks a fortnight apart do not read as a run of two.
     */
    private fun longestWeekendRun(activeDays: List<Long>): Int {
        val weekends = activeDays.filter { isWeekend(it) }.map { weekIndex(it) }.distinct().sorted()
        return longestRun(weekends)
    }

    /**
     * Whether the player ever came back from a broken streak and put together another full week.
     *
     * The first streak of the player's life does not count - a comeback needs something to come back
     * from, so this only looks at runs that begin after a gap.
     */
    private fun hasComebackStreak(activeDays: List<Long>): Boolean {
        if (activeDays.isEmpty()) return false
        var run = 1
        var startedAfterGap = false
        activeDays.zipWithNext { previous, next ->
            if (next - previous == 1L) {
                run++
            } else {
                run = 1
                startedAfterGap = true
            }
            if (startedAfterGap && run >= COMEBACK_STREAK_DAYS) return true
        }
        return false
    }

    private fun farthestFromStart(
        sorted: List<ExploredCell>,
        resolveCenter: (String) -> Coordinate?
    ): Double {
        val start = sorted.firstNotNullOfOrNull { runCatching { resolveCenter(it.cellId) }.getOrNull() }
            ?: return 0.0
        return sorted.maxOf { cell ->
            val point = runCatching { resolveCenter(cell.cellId) }.getOrNull() ?: return@maxOf 0.0
            haversineMeters(start, point)
        }
    }

    private fun activeOnAnniversary(
        activeDays: List<Long>,
        statsStartMillis: Long,
        zone: TimeZone,
        now: Long
    ): Boolean {
        if (statsStartMillis <= 0L || statsStartMillis > now) return false
        val start = GregorianCalendar(zone).apply { timeInMillis = statsStartMillis }
        val active = activeDays.toHashSet()

        // Any anniversary counts, not only the first: a player who has been walking for three years
        // should not be told they missed their chance in year one.
        var year = 1
        while (true) {
            val anniversary = (start.clone() as GregorianCalendar).apply { add(Calendar.YEAR, year) }
            if (anniversary.timeInMillis > now) return false
            if (epochDay(anniversary.timeInMillis, zone) in active) return true
            year++
        }
    }

    private fun seasonOf(month: Int): Int = when (month) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> 0
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> 1
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> 2
        else -> 3
    }

    private fun isWinter(month: Int): Boolean = seasonOf(month) == 0

    /** Monday is 0, so Saturday and Sunday are 5 and 6. */
    private fun dayOfWeek(epochDay: Long): Int =
        (((epochDay + EPOCH_DAY_OF_WEEK_OFFSET) % 7) + 7).toInt() % 7

    private fun isWeekend(epochDay: Long): Boolean = dayOfWeek(epochDay) >= 5

    /** Which week a day belongs to, so both days of one weekend share an index. */
    private fun weekIndex(epochDay: Long): Long =
        Math.floorDiv(epochDay + EPOCH_DAY_OF_WEEK_OFFSET, 7L)

    private fun epochDay(millis: Long, zone: TimeZone): Long = CalendarDays.epochDay(millis, zone)

    private fun haversineMeters(from: Coordinate, to: Coordinate): Double {
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(from.lat)) * cos(Math.toRadians(to.lat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(abs(1 - a)))
    }

    /** One timestamp, resolved once into every calendar field the rules ask about. */
    private data class LocalStamp(
        val epochDay: Long,
        val hour: Int,
        val minute: Int,
        val month: Int,
        val dayOfMonth: Int
    ) {
        companion object {
            fun of(millis: Long, zone: TimeZone): LocalStamp {
                val calendar = GregorianCalendar(zone).apply { timeInMillis = millis }
                return LocalStamp(
                    epochDay = CalendarDays.epochDay(millis, zone),
                    hour = calendar.get(Calendar.HOUR_OF_DAY),
                    minute = calendar.get(Calendar.MINUTE),
                    month = calendar.get(Calendar.MONTH),
                    dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                )
            }
        }
    }
}
