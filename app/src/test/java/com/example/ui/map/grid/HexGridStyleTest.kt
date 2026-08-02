package com.example.ui.map.grid

import com.example.ui.map.theme.MapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexGridStyleTest {

    private val styles = listOf(HexGridStyle.OVER_LIGHT, HexGridStyle.OVER_DARK)

    @Test
    fun `claimed ground stays translucent enough to navigate`() {
        styles.forEach { style ->
            val walked = (style.walkedFill ushr 24) and 0xFF
            assertTrue(
                "walked fill alpha $walked hides the streets underneath it",
                walked in 1..0x66
            )
        }
    }

    @Test
    fun `ground only seen is shaded more faintly than ground walked`() {
        styles.forEach { style ->
            assertTrue(
                ((style.seenFill ushr 24) and 0xFF) < ((style.walkedFill ushr 24) and 0xFF)
            )
        }
    }

    @Test
    fun `the region's border is opaque, because it alone carries the shape`() {
        styles.forEach { style ->
            assertEquals(0xFF, (style.territoryBorder ushr 24) and 0xFF)
        }
    }

    @Test
    fun `each theme gets the style built for the basemap it puts underneath`() {
        assertEquals(HexGridStyle.OVER_LIGHT, HexGridStyle.forTheme(MapTheme.LIGHT))
        assertEquals(HexGridStyle.OVER_LIGHT, HexGridStyle.forTheme(MapTheme.CLASSIC))
        assertEquals(HexGridStyle.OVER_DARK, HexGridStyle.forTheme(MapTheme.DARK))
        assertEquals(HexGridStyle.OVER_DARK, HexGridStyle.forTheme(MapTheme.DARKER))
    }
}
