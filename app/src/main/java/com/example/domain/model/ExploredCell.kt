package com.example.domain.model

/**
 * A grid cell the player has revealed, and how completely.
 *
 * [explorationLevel] drives the fog: 0.0 leaves the cell under full fog, 1.0 clears it entirely.
 * Cells the player actually walked into are recorded at [LEVEL_WALKED]; the ring around a cell they
 * lingered in is recorded at [LEVEL_VISION], so a dwell reveals a soft halo rather than a hard disk.
 */
data class ExploredCell(
    val cellId: String,
    val exploredAt: Long,
    val explorationLevel: Float,
    /**
     * The weather, place and elevation captured when this cell was claimed, where they are known.
     * The fog layer ignores it; the achievements are what it exists for.
     */
    val context: CellContext? = null
) {
    companion object {
        /** The player's own position: fully cleared. */
        const val LEVEL_WALKED = 1.0f

        /** Seen from a neighbouring cell during a dwell - partially cleared. */
        const val LEVEL_VISION = 0.5f

        fun clampLevel(level: Float): Float = level.coerceIn(0f, 1f)
    }
}
