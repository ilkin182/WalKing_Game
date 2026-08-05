package com.example.domain.stats

import com.example.domain.engine.HexGridConfig

/**
 * One local day of the player's activity, as the weekly breakdown behind each stat card shows it.
 *
 * A day with nothing on it is still a [DailyStat] with zeroes rather than a missing entry - the
 * chart is seven days wide whether the player walked on all of them or none, and a gap would read
 * as "no data" when what it means is "you did not go out".
 */
data class DailyStat(
    val epochDay: Long,
    val distanceMeters: Double,
    val cellCount: Int
) {
    /**
     * How much ground the day's cells cover, on the same footing as the profile's total: cell count
     * times the area of one cell.
     */
    val areaSquareMeters: Double get() = cellCount * HexGridConfig.CELL_AREA_SQUARE_METERS
}
