package com.example.ui.map.theme

import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

/**
 * The map's base style, swappable without touching any map code: [applyTo] is the single place
 * that knows how a theme reaches the [MapView].
 *
 * ## Why this is a colour filter and not a style JSON
 *
 * The epic specifies `GoogleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(R.raw.map_style_dark))`,
 * which restyles *vector* features individually - land, water, roads and labels each get their own
 * colour, and POI labels can be switched off. This app renders with osmdroid over **raster** OSM
 * tiles: the tiles arrive as finished bitmaps, so there are no features left to address by name.
 * The only restyling hook is a per-pixel [ColorFilter] over the whole tile.
 *
 * So the palette below is the honest target, and [tileColorFilter] is the closest a raster pipeline
 * can get to it: invert (light cartography becomes dark), desaturate, then level the result into the
 * band between [MapPalette.geometryFill] and [MapPalette.ink]. Land, water and roads land in the
 * right tonal *order* and the right overall colour family, but they cannot be placed on their exact
 * individual hexes, and POI labels cannot be selectively hidden.
 *
 * For pixel-exact fidelity to the palette, point [tileSource] at a tile server that ships dark
 * cartography (CARTO dark-matter, Stadia Alidade Dark, a self-hosted style, ...). That is a one-line
 * change here and needs no change at the call site - which is the point of this enum.
 *
 * Themes with [stylesTiles] = false skip that transform entirely and draw the raster tiles as they
 * arrive - which is the only way to get a genuinely *light* map out of a raster pipeline, since any
 * inversion of light cartography is dark by construction.
 *
 * @property stylesTiles whether the raster tiles get [tileColorMatrix] applied to them.
 */
enum class MapTheme(val palette: MapPalette, val stylesTiles: Boolean = true) {

    /**
     * The default: OSM's own light cartography, untouched, with a pale haze over unexplored ground.
     *
     * Streets, water and place names all keep their normal daylight colours, so the map underneath
     * the game stays readable at a glance - the fog is the only thing that dims anything.
     */
    LIGHT(
        MapPalette(
            geometryFill = 0xF4F1EA,
            water = 0xA8D2E0,
            road = 0xFFFFFF,
            roadStroke = 0xCFCABF,
            labelFill = 0x2E3338,
            labelStroke = 0xFFFFFF,
            // Tiles are drawn unfiltered, so ink/geometryFill are not a levelling band here - they
            // just describe the tonal range the cartography already occupies.
            ink = 0x1B1E21,
            // A cool grey haze rather than a dark scrim: over light cartography a *bright* veil is
            // what keeps unexplored streets faintly perceptible instead of blacking them out.
            fogArgb = 0xB3C3CDD6.toInt(),
            tileSaturation = 1.0f
        ),
        stylesTiles = false
    ),

    /** A dark, desaturated slate base that reads as "unknown territory". */
    DARK(
        MapPalette(
            geometryFill = 0x1A1D21,
            water = 0x0E1013,
            road = 0x2A2E33,
            roadStroke = 0x23272B,
            // The epic specifies #5C6470 here, but that is only 2.83:1 against the #1A1D21 fill -
            // under the 3:1 floor the same task sets. Lifted to the nearest tone that clears it
            // (3.46:1); see MapThemeTest, which fails if a future edit drops back below 3:1.
            labelFill = 0x6B7280,
            labelStroke = 0x101214,
            ink = 0x96A0AC,
            fogArgb = 0xCC0B0D10.toInt(),
            tileSaturation = 0.18f
        )
    ),

    /** Higher contrast between explored and unexplored: a deeper base and heavier fog. */
    DARKER(
        MapPalette(
            geometryFill = 0x0B0D10,
            water = 0x05070A,
            road = 0x1B1F24,
            roadStroke = 0x15181C,
            labelFill = 0x5A626D,
            labelStroke = 0x000000,
            ink = 0x6E7681,
            fogArgb = 0xE6050609.toInt(),
            tileSaturation = 0.08f
        )
    ),

    /** Unstyled OSM cartography, for users who want the map to look like a normal map. */
    CLASSIC(
        MapPalette(
            geometryFill = 0xF2EFE9,
            water = 0xAAD3DF,
            road = 0xFFFFFF,
            roadStroke = 0xC8C8C8,
            labelFill = 0x2B2B2B,
            labelStroke = 0xFFFFFF,
            ink = 0x000000,
            fogArgb = 0x99202428.toInt(),
            tileSaturation = 1.0f
        ),
        stylesTiles = false
    );

    /** The base fill as an opaque 0xAARRGGBB colour, for the view background behind the tiles. */
    val backgroundArgb: Int get() = 0xFF000000.toInt() or palette.geometryFill

    /** Raster source for this theme. Same OSM tiles for every theme today - see the class docs. */
    val tileSource: ITileSource get() = TileSourceFactory.MAPNIK

    /**
     * The per-pixel transform that turns the raster tiles into this theme, or null when the tiles
     * should be drawn untouched (the light themes - see [stylesTiles]).
     */
    fun tileColorFilter(): ColorFilter? {
        if (!stylesTiles) return null
        return ColorMatrixColorFilter(tileColorMatrix())
    }

    /**
     * Applies the theme to [mapView]: background, tile source and colour filter together.
     *
     * Call this *before* the first camera move (osmdroid's equivalent of doing it in `onMapReady`).
     * The background colour is what removes the white flash: it is painted immediately, while the
     * first tiles are still being fetched, so the map is never briefly white.
     */
    fun applyTo(mapView: MapView) {
        mapView.setBackgroundColor(backgroundArgb)
        mapView.setTileSource(tileSource)
        // overlayManager.tilesOverlay is osmdroid's base-map layer; its filter runs on every tile,
        // including ones already in the disk cache, so a config change restyles instantly too.
        mapView.overlayManager.tilesOverlay.setColorFilter(tileColorFilter())
        mapView.overlayManager.tilesOverlay.loadingBackgroundColor = backgroundArgb
        mapView.overlayManager.tilesOverlay.loadingLineColor = palette.roadStroke or 0xFF000000.toInt()
    }

    /**
     * invert -> desaturate -> level into [geometryFill]..[ink].
     *
     * Split out from [tileColorFilter] so the matrix itself can be asserted on in tests without
     * constructing a [ColorFilter].
     */
    internal fun tileColorMatrix(): ColorMatrix {
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(ColorMatrix().apply { setSaturation(palette.tileSaturation) })
        matrix.postConcat(levelsMatrix(palette.geometryFill, palette.ink))
        return matrix
    }

    private fun levelsMatrix(floor: Int, ceiling: Int): ColorMatrix {
        fun gain(shift: Int) = (((ceiling shr shift) and 0xFF) - ((floor shr shift) and 0xFF)) / 255f
        fun bias(shift: Int) = ((floor shr shift) and 0xFF).toFloat()

        return ColorMatrix(
            floatArrayOf(
                gain(16), 0f, 0f, 0f, bias(16),
                0f, gain(8), 0f, 0f, bias(8),
                0f, 0f, gain(0), 0f, bias(0),
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    companion object {
        val DEFAULT = LIGHT
    }
}
