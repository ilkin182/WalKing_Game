package com.example.ui.map.grid

import com.example.ui.map.theme.MapTheme

/**
 * The colours the hex grid is drawn in, as 0xAARRGGBB.
 *
 * Chosen against the basemap the theme puts underneath: a wash that reads as "claimed" over pale OSM
 * cartography would vanish over an inverted dark one, and the deep border that carries the shape on
 * light tiles would disappear into them. Free of Android graphics types so the choices stay
 * unit-testable on a plain JVM.
 *
 * @property walkedFill ground the player actually walked over.
 * @property seenFill ground revealed from a distance by dwelling - claimed, but not as strongly.
 * @property territoryBorder the outline around the claimed region as a whole.
 * @property emptyCellStroke guide lines over unwalked ground; alpha comes from [GridLod].
 */
data class HexGridStyle(
    val walkedFill: Int,
    val seenFill: Int,
    val territoryBorder: Int,
    val emptyCellStroke: Int
) {
    companion object {
        /**
         * Over daylight OSM tiles. The fill is kept translucent enough to read street names through -
         * claimed ground still has to be navigable - and the shape is carried by the border instead.
         */
        val OVER_LIGHT = HexGridStyle(
            walkedFill = 0x3812B394,
            seenFill = 0x1C12B394,
            territoryBorder = 0xFF0B8C72.toInt(),
            emptyCellStroke = 0x37564F
        )

        /** Over the inverted dark basemap, where the app's mint accent is what stands out. */
        val OVER_DARK = HexGridStyle(
            walkedFill = 0x425DF2D6,
            seenFill = 0x1F5DF2D6,
            territoryBorder = 0xFF5DF2D6.toInt(),
            emptyCellStroke = 0x7FA79B
        )

        fun forTheme(theme: MapTheme): HexGridStyle =
            if (theme.stylesTiles) OVER_DARK else OVER_LIGHT
    }
}
