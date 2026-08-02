package com.example.ui.map.grid

/**
 * How much of the hex grid is worth drawing at a given zoom.
 *
 * The grid's cells are ~40 m across ([com.example.domain.engine.HexGridConfig.RESOLUTION]). Drawn
 * unconditionally that is fine at street zoom and ruinous below it: at zoom 15 a phone viewport
 * covers a few kilometres, so the "grid" becomes tens of thousands of outlines packed tighter than
 * one line per pixel - a flat grey mesh that hides the cartography underneath it and costs a frame
 * to draw. The level of detail here is what keeps the map readable: the honeycomb over unwalked
 * ground only appears once a cell is actually big enough on screen to read as a cell, and it fades
 * in rather than snapping on.
 *
 * Claimed territory is treated separately - it is the game state, so it stays visible far longer
 * than the guide lines do, and below [MIN_TERRITORY_ZOOM] the fog layer already shows it as cleared
 * ground for free.
 *
 * Pure zoom arithmetic, so the thresholds are unit-testable without a map.
 *
 * @property drawsTerritory whether explored cells are painted at all.
 * @property drawsEmptyCells whether the honeycomb over unwalked ground is painted.
 * @property emptyCellAlpha 0..255 alpha for those guide lines, ramped in over the fade band.
 * @property emptyStrokeDp guide-line width.
 * @property territoryStrokeDp width of the outline around the claimed region.
 */
data class GridLod(
    val drawsTerritory: Boolean,
    val drawsEmptyCells: Boolean,
    val emptyCellAlpha: Int,
    val emptyStrokeDp: Float,
    val territoryStrokeDp: Float
) {
    companion object {
        /** Below this, a cell is a couple of pixels wide and the fog carries the explored area. */
        const val MIN_TERRITORY_ZOOM = 13.0

        /** Where the honeycomb starts fading in - roughly a cell per 25 px. */
        const val MIN_EMPTY_GRID_ZOOM = 16.0

        /** Where it reaches full strength: cells are ~60 px across and read individually. */
        const val FULL_EMPTY_GRID_ZOOM = 17.5

        /**
         * Guide lines top out well below opaque. They exist to show where the next cell begins,
         * not to compete with the roads the player is meant to be walking down.
         */
        const val MAX_EMPTY_CELL_ALPHA = 0x59

        private const val MIN_EMPTY_STROKE_DP = 0.6f
        private const val MAX_EMPTY_STROKE_DP = 1.0f

        /** The claimed region's outline thins out when the region itself is small on screen. */
        private const val NEAR_TERRITORY_STROKE_DP = 2.0f
        private const val FAR_TERRITORY_STROKE_DP = 1.25f
        private const val TERRITORY_STROKE_ZOOM = 15.0

        val HIDDEN = GridLod(
            drawsTerritory = false,
            drawsEmptyCells = false,
            emptyCellAlpha = 0,
            emptyStrokeDp = 0f,
            territoryStrokeDp = 0f
        )

        fun forZoom(zoom: Double): GridLod {
            if (zoom < MIN_TERRITORY_ZOOM) return HIDDEN

            val fade = ((zoom - MIN_EMPTY_GRID_ZOOM) / (FULL_EMPTY_GRID_ZOOM - MIN_EMPTY_GRID_ZOOM))
                .coerceIn(0.0, 1.0)

            return GridLod(
                drawsTerritory = true,
                drawsEmptyCells = fade > 0.0,
                emptyCellAlpha = Math.round(fade * MAX_EMPTY_CELL_ALPHA).toInt(),
                emptyStrokeDp = lerp(MIN_EMPTY_STROKE_DP, MAX_EMPTY_STROKE_DP, fade),
                territoryStrokeDp = if (zoom < TERRITORY_STROKE_ZOOM) {
                    FAR_TERRITORY_STROKE_DP
                } else {
                    NEAR_TERRITORY_STROKE_DP
                }
            )
        }

        private fun lerp(from: Float, to: Float, t: Double): Float = from + (to - from) * t.toFloat()
    }
}
