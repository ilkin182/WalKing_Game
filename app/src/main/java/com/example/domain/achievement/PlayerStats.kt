package com.example.domain.achievement

/**
 * Everything the achievement catalogue can currently measure about a player, computed once from the
 * exploration history rather than re-derived per achievement.
 *
 * Roughly ninety achievements ask ninety questions of the same few hundred timestamps. Answering
 * them one at a time would walk the history ninety times on every recomposition; this is a single
 * pass whose result every rule reads from.
 *
 * See [PlayerStatsCalculator] for how each field is derived, and [AchievementCatalog] for the
 * achievements that are defined but not yet measurable - those need data the app does not collect
 * yet (the letters a route spelled out), and are deliberately absent here rather than approximated
 * into something that would unlock wrongly.
 */
data class PlayerStats(
    // Totals
    val totalCells: Int = 0,
    val totalDistanceMeters: Double = 0.0,

    // Bursts
    val maxCellsInOneHour: Int = 0,
    val maxCellsInOneDay: Int = 0,

    // Days and streaks, all in the device's local calendar
    val activeDays: Int = 0,
    val longestDayStreak: Int = 0,
    val longestMorningStreak: Int = 0,
    val longestWorkdayStreak: Int = 0,
    val longestWeekendStreak: Int = 0,
    val hasComebackStreak: Boolean = false,
    val returnedAfterLongBreak: Boolean = false,

    // Times of day and dates
    val nightCells: Int = 0,
    val maxMiddayCellsInOneDay: Int = 0,
    val hasExactMidnightCell: Boolean = false,
    val hasNowruzCell: Boolean = false,
    val hasNewYearCell: Boolean = false,
    val sixHourBlocksCovered: Int = 0,

    // Seasons
    val seasonsCovered: Int = 0,
    val marchCells: Int = 0,
    val winterActiveDays: Int = 0,

    // Zones
    val distinctZones: Int = 0,
    val bestZonePercentage: Double = 0.0,
    val zonesAtQuarter: Int = 0,
    val zonesAtHalf: Int = 0,
    val zonesComplete: Int = 0,
    val largestCompleteZoneCells: Int = 0,

    // Reach
    val farthestCellMeters: Double = 0.0,

    // The shape of what was claimed, rather than how much of it
    val closedLoops: Int = 0,
    val longestCellLine: Int = 0,

    // The shape of the walks themselves, from the recorded routes
    val hasBoomerangRoute: Boolean = false,
    val longestStraightRouteMeters: Double = 0.0,
    val maxRouteTurns: Int = 0,
    val hasSquareRoute: Boolean = false,

    // Per-walk and per-day distance, from the walk sessions
    val maxDayDistanceMeters: Double = 0.0,
    val maxSessionDistanceMeters: Double = 0.0,

    // Conditions recorded with the cells, where they are known
    val rainyCells: Int = 0,
    val hasSnowCell: Boolean = false,
    val hasHotCell: Boolean = false,
    val hasColdCell: Boolean = false,
    val hasWindyCell: Boolean = false,
    val hasBeforeSunriseCell: Boolean = false,
    val hasAtSunsetCell: Boolean = false,

    // Places, from the city and country recorded with the cells
    val distinctCities: Int = 0,
    val distinctCountries: Int = 0,
    val hasHomecoming: Boolean = false,
    val hasIntercityDay: Boolean = false,

    // Elevation, filled in by the batch enrichment pass
    val elevationGainMeters: Double = 0.0,
    val highestElevationMeters: Double = 0.0,

    // What the player has walked past, from the cached OpenStreetMap places. All zero until the
    // background pass has fetched the tiles the history covers - the geography achievements simply
    // read as not-yet-started rather than as failed while that happens.
    val distinctParks: Int = 0,
    val distinctMonuments: Int = 0,
    val distinctMetroStations: Int = 0,
    val distinctBridges: Int = 0,
    val distinctSquares: Int = 0,
    val seasideCells: Int = 0,

    /** Share of the known parks the player has set foot in, 0-100. */
    val parkCoveragePercent: Double = 0.0,

    /** Share of the known coastline the player has walked, 0-100. */
    val coastlineCoveragePercent: Double = 0.0,

    // Where in their town the player has been, from the cached town extents
    val cityCentreCells: Int = 0,
    val hasReachedCityEdge: Boolean = false,

    // Loyalty
    val activeOnAppAnniversary: Boolean = false
) {
    companion object {
        val EMPTY = PlayerStats()
    }
}
