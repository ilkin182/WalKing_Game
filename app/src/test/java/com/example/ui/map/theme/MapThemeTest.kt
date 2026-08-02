package com.example.ui.map.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the properties of the palettes that are easy to break by eye and impossible to notice in a
 * screenshot: label legibility, and the fog actually being translucent.
 */
class MapThemeTest {

    @Test
    fun `every theme keeps map labels above the 3 to 1 contrast floor`() {
        MapTheme.entries.forEach { theme ->
            val ratio = theme.palette.labelContrastRatio
            assertTrue(
                "$theme label #${theme.palette.labelFill.toString(16)} on fill " +
                    "#${theme.palette.geometryFill.toString(16)} is only ${"%.2f".format(ratio)}:1",
                ratio >= MapPalette.MIN_LABEL_CONTRAST
            )
        }
    }

    @Test
    fun `every theme keeps label halos above the 3 to 1 contrast floor`() {
        // The halo is what carries a label across water or a road, so it needs the same floor.
        MapTheme.entries.forEach { theme ->
            assertTrue(
                "$theme halo contrast is ${"%.2f".format(theme.palette.labelHaloContrastRatio)}:1",
                theme.palette.labelHaloContrastRatio >= MapPalette.MIN_LABEL_CONTRAST
            )
        }
    }

    @Test
    fun `the epic's specified label colour is what forced the deviation`() {
        // Documents the trade-off rather than silently disagreeing with the spec: #5C6470 on the
        // specified #1A1D21 fill genuinely cannot meet the 3:1 the same task asks for.
        val specified = ColorContrast.ratio(foreground = 0x5C6470, background = 0x1A1D21)

        assertTrue("expected the specified pair to fall short, got $specified", specified < 3.0)
        assertEquals(2.83, specified, 0.01)
        assertNotEquals(0x5C6470, MapTheme.DARK.palette.labelFill)
    }

    @Test
    fun `dark themes fog the map without hiding it completely`() {
        listOf(MapTheme.DARK, MapTheme.DARKER).forEach { theme ->
            val alpha = (theme.palette.fogArgb ushr 24) and 0xFF
            // Fully opaque fog would hide the coastlines and roads the epic wants to stay faintly
            // perceptible; fully transparent fog would not read as unexplored at all.
            assertTrue("$theme fog alpha $alpha is opaque", alpha < 255)
            assertTrue("$theme fog alpha $alpha is too faint", alpha >= 0xAA)
        }
    }

    @Test
    fun `dark themes level tiles into a dark band and classic leaves them alone`() {
        assertTrue(MapTheme.DARK.palette.geometryFill < MapTheme.DARK.palette.ink)
        // DARKER must actually be darker than DARK, or the enum is lying about its name.
        assertTrue(
            ColorContrast.relativeLuminance(MapTheme.DARKER.palette.geometryFill) <
                ColorContrast.relativeLuminance(MapTheme.DARK.palette.geometryFill)
        )
        assertEquals(1.0f, MapTheme.CLASSIC.palette.tileSaturation, 1e-6f)
    }

    @Test
    fun `the default theme is a light one and leaves the tiles unfiltered`() {
        // The whole point of the light themes: inverting raster cartography can only ever produce a
        // dark map, so a theme that wants to stay bright must not be styled at all.
        assertFalse(MapTheme.DEFAULT.stylesTiles)
        assertNull(MapTheme.DEFAULT.tileColorFilter())
        assertTrue(
            "the default base fill #${MapTheme.DEFAULT.palette.geometryFill.toString(16)} is not light",
            ColorContrast.relativeLuminance(MapTheme.DEFAULT.palette.geometryFill) > 0.7
        )
    }

    @Test
    fun `light themes fog the map with a pale veil, not a dark scrim`() {
        listOf(MapTheme.LIGHT).forEach { theme ->
            val fogRgb = theme.palette.fogArgb and 0x00FFFFFF
            // A dark scrim over light cartography is exactly what made the map unreadable; the veil
            // has to be lighter than the label ink it is drawn over.
            assertTrue(
                "$theme fog #${fogRgb.toString(16)} is darker than its own labels",
                ColorContrast.relativeLuminance(fogRgb) >
                    ColorContrast.relativeLuminance(theme.palette.labelFill)
            )
        }
    }

    @Test
    fun `contrast ratio is symmetric and bounded`() {
        assertEquals(21.0, ColorContrast.ratio(0xFFFFFF, 0x000000), 0.01)
        assertEquals(1.0, ColorContrast.ratio(0x123456, 0x123456), 1e-9)
        assertEquals(
            ColorContrast.ratio(0x6B7280, 0x1A1D21),
            ColorContrast.ratio(0x1A1D21, 0x6B7280),
            1e-9
        )
    }
}
