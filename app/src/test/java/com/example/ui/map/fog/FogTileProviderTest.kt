package com.example.ui.map.fog

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.FogIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the fog cache's behaviour - which tiles need a bitmap, which are cached, and when bitmaps
 * are allocated. Runs under Robolectric because [FogTileProvider] builds real `Bitmap`s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FogTileProviderTest {

    private val zoom = 16
    private val tile = TileId(zoom, 44710, 25674)
    private val tileBounds = TileMath.tileBounds(tile.zoom, tile.x, tile.y)

    private fun provider() = FogTileProvider(fogArgb = 0xCC0B0D10.toInt())

    /** A square cell of [size] degrees centred on (lat, lng). */
    private fun squareCell(id: String, lat: Double, lng: Double, size: Double) = Pair(
        ExploredCell(id, 0L, ExploredCell.LEVEL_WALKED),
        listOf(
            Coordinate(lat - size / 2, lng - size / 2),
            Coordinate(lat - size / 2, lng + size / 2),
            Coordinate(lat + size / 2, lng + size / 2),
            Coordinate(lat + size / 2, lng - size / 2)
        )
    )

    private fun indexOf(vararg cells: Pair<ExploredCell, List<Coordinate>>): ExploredCellIndex {
        val geometry = cells.associate { it.first.cellId to it.second }
        return ExploredCellIndex.build(cells.map { it.first }, 1L) { geometry[it].orEmpty() }
    }

    /** One small cell inside the tile: enough to need a bitmap, nowhere near covering it. */
    private fun partiallyExplored() = indexOf(
        squareCell("a", tileBounds.centerLat, tileBounds.centerLng, size = 0.0004)
    )

    /** A single cell large enough to swallow the whole tile. */
    private fun fullyExplored() = indexOf(
        squareCell(
            "big",
            tileBounds.centerLat,
            tileBounds.centerLng,
            size = (tileBounds.north - tileBounds.south) * 4
        )
    )

    @Test
    fun `with nothing explored a tile is flat fog and needs no bitmap`() {
        val provider = provider()

        assertSame(FogTile.FullyFogged, provider.getTile(tile))
        assertEquals(0, provider.bitmapAllocations)
    }

    @Test
    fun `a tile nowhere near explored ground is flat fog`() {
        val provider = provider()
        provider.setExploredCells(indexOf(squareCell("far", 10.0, 10.0, size = 0.0004)))

        assertSame(FogTile.FullyFogged, provider.getTile(tile))
        assertEquals(0, provider.bitmapAllocations)
    }

    @Test
    fun `a partly explored tile is rendered to a bitmap`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())

        val result = provider.getTile(tile)

        assertTrue("expected a rendered tile, got $result", result is FogTile.Rendered)
        assertEquals(TileMath.TILE_SIZE_PX, (result as FogTile.Rendered).bitmap.width)
        assertEquals(1, provider.bitmapAllocations)
    }

    @Test
    fun `a fully explored tile needs no tile at all`() {
        val provider = provider()
        provider.setExploredCells(fullyExplored())

        assertSame(FogTile.FullyExplored, provider.getTile(tile))
        assertEquals(0, provider.bitmapAllocations)
    }

    @Test
    fun `panning across fully explored ground allocates nothing`() {
        // The epic's performance criterion, stated directly.
        val provider = provider()
        provider.setExploredCells(fullyExplored())

        (0 until 40).forEach { step ->
            provider.getTile(TileId(zoom, tile.x, tile.y).copy(x = tile.x + step % 3))
        }

        assertEquals(0, provider.bitmapAllocations)
    }

    @Test
    fun `a tile is rendered once and then served from the cache`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())

        val first = provider.getTile(tile) as FogTile.Rendered
        val second = provider.getTile(tile) as FogTile.Rendered

        assertSame(first.bitmap, second.bitmap)
        assertEquals(1, provider.tilesRendered)
        assertEquals(1, provider.bitmapAllocations)
    }

    @Test
    fun `exploring a new cell bumps the version and invalidates the cached tiles`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)
        val versionBefore = provider.exploredVersion

        provider.setExploredCells(partiallyExplored())

        assertNotEquals(versionBefore, provider.exploredVersion)
        assertEquals(0, provider.cachedTiles)
    }

    @Test
    fun `redrawing after a cache clear reuses the pooled bitmap instead of allocating`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)

        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)

        // Two renders, one bitmap: the evicted tile went back to the pool and was refilled.
        assertEquals(2, provider.tilesRendered)
        assertEquals(1, provider.bitmapAllocations)
    }

    @Test
    fun `changing fog intensity restyles the fog and drops the cache`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)
        val before = provider.fogColor

        provider.setIntensity(FogIntensity.HEAVY)

        assertNotEquals(before, provider.fogColor)
        assertEquals(FogIntensity.HEAVY.alpha255, android.graphics.Color.alpha(provider.fogColor))
        assertEquals(0, provider.cachedTiles)
    }

    @Test
    fun `setting the same intensity twice changes nothing`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)
        val version = provider.exploredVersion

        provider.setIntensity(provider.intensity)

        assertEquals(version, provider.exploredVersion)
        assertEquals(1, provider.cachedTiles)
    }

    @Test
    fun `fog stays translucent at every intensity so the map shows through`() {
        val provider = provider()

        FogIntensity.entries.forEach { intensity ->
            provider.setIntensity(intensity)
            assertTrue(
                "$intensity fog is opaque",
                android.graphics.Color.alpha(provider.fogColor) < 255
            )
        }
    }

    @Test
    fun `an exhausted render budget defers the tile instead of blowing the frame`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())

        val budget = RenderBudget(0)
        val result = provider.getTile(tile, budget)

        assertSame(FogTile.FullyFogged, result)
        assertEquals(0, provider.tilesRendered)
    }

    @Test
    fun `a deferred tile is rendered on the next frame`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile, RenderBudget(0))

        val result = provider.getTile(tile, RenderBudget(1))

        assertTrue(result is FogTile.Rendered)
    }

    @Test
    fun `a render budget caps how many tiles one frame paints`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        val budget = RenderBudget(2)

        // Three tiles that all straddle the explored cell, so each genuinely needs rendering.
        listOf(tile, tile.copy(x = tile.x - 1), tile.copy(y = tile.y - 1))
            .forEach { provider.getTile(it, budget) }

        assertTrue("rendered ${provider.tilesRendered} tiles on a budget of 2", provider.tilesRendered <= 2)
    }

    @Test
    fun `destroy releases everything it is holding`() {
        val provider = provider()
        provider.setExploredCells(partiallyExplored())
        provider.getTile(tile)

        provider.destroy()

        assertEquals(0, provider.cachedTiles)
    }
}
