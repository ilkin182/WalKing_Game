package com.example.ui.map.theme

/**
 * The colour targets a map theme renders to, as plain 0xRRGGBB ints.
 *
 * This is deliberately free of Android graphics types so the palette (and the contrast guarantees
 * that go with it) can be unit-tested on a plain JVM.
 *
 * @property geometryFill the base land colour, and the darkest tone styled tiles are levelled to.
 * @property water open water.
 * @property road road fill.
 * @property roadStroke road casing.
 * @property labelFill place-label text.
 * @property labelStroke the halo drawn behind label text so it stays readable over any feature.
 * @property ink the brightest tone styled tiles are levelled to - the top of the raster's range.
 * @property fogArgb fog colour for unexplored cells, as 0xAARRGGBB (alpha carries the density).
 * @property tileSaturation how much of the raster tiles' original colour survives styling, 0..1.
 */
data class MapPalette(
    val geometryFill: Int,
    val water: Int,
    val road: Int,
    val roadStroke: Int,
    val labelFill: Int,
    val labelStroke: Int,
    val ink: Int,
    val fogArgb: Int,
    val tileSaturation: Float
) {
    /**
     * Contrast ratio of label text against the base fill. The map is only useful if you can still
     * read the place names on it, so this is asserted against [MIN_LABEL_CONTRAST] in tests rather
     * than left to the eye.
     */
    val labelContrastRatio: Double get() = ColorContrast.ratio(labelFill, geometryFill)

    /** Contrast of label text against its own halo, which is what carries the text over water/roads. */
    val labelHaloContrastRatio: Double get() = ColorContrast.ratio(labelFill, labelStroke)

    companion object {
        /** WCAG's minimum for large/graphical text, and the floor the epic sets for map labels. */
        const val MIN_LABEL_CONTRAST = 3.0
    }
}
