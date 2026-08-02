package com.example.ui.map.fog

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.LruCache
import com.example.domain.model.ExploredCell
import com.example.domain.model.FogIntensity
import com.example.domain.model.GeoBounds

/**
 * The result of asking for one fog tile.
 *
 * The epic's Google Maps formulation is "a bitmap, or `TileProvider.NO_TILE` when the tile is fully
 * explored". Naming the three outcomes instead lets the fully-*fogged* case skip a bitmap too: a
 * tile nobody has walked in yet is a flat rectangle of one colour, and the overlay can draw that
 * with `drawRect` for free. Between them, [FullyExplored] and [FullyFogged] mean panning across
 * either extreme - untouched wilderness or a completely cleared city - allocates nothing at all.
 */
sealed interface FogTile {
    /** Nothing explored here: fill the tile's rectangle with flat fog. */
    data object FullyFogged : FogTile

    /** Everything here is explored - the `NO_TILE` case. Draw nothing and let the map show through. */
    data object FullyExplored : FogTile

    /** A partially cleared tile that had to be rendered. */
    data class Rendered(val bitmap: Bitmap) : FogTile
}

/**
 * Renders and caches the fog layer, one tile at a time.
 *
 * Each tile starts as a solid rectangle of fog and then has every explored cell punched out of it,
 * feathered so the reveal reads as a soft clearing rather than a stack of pixel-sharp hexagons.
 *
 * Tiles are cached under `"z/x/y/exploredVersion"`, exactly as the epic specifies: bumping the
 * version (via [setExploredCells]) invalidates every tile at once, and [clearTileCache] forces the
 * redraw. Eviction hands bitmaps back to a [BitmapPool] rather than to the GC.
 *
 * Confined to the thread that draws the map (the UI thread) - see [BitmapPool].
 */
class FogTileProvider(
    fogArgb: Int,
    intensity: FogIntensity = FogIntensity.DEFAULT,
    private val tileSize: Int = TileMath.TILE_SIZE_PX,
    cacheBytes: Int = defaultCacheBytes()
) {

    /** The flat fog colour, alpha included. The overlay uses this for the [FogTile.FullyFogged] case. */
    var fogColor: Int = applyIntensity(fogArgb, intensity)
        private set

    var intensity: FogIntensity = intensity
        private set

    private val baseFogRgb: Int = fogArgb and 0x00FFFFFF

    private val pool = BitmapPool(
        width = tileSize,
        height = tileSize,
        maxEntries = (cacheBytes / bytesPerTile(tileSize)).coerceIn(2, 8)
    )

    private val cache = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, old: Bitmap, new: Bitmap?) {
            if (old !== new) pool.release(old)
        }
    }

    /** Tiles known to need no bitmap at all, so repeat visits skip even the geometry query. */
    private val flatTiles = HashMap<String, FogTile>()

    private var index: ExploredCellIndex = ExploredCellIndex.EMPTY

    /** Bumped on every explored-set change; part of every cache key. */
    var exploredVersion: Long = 0L
        private set

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // DST_OUT rather than the epic's CLEAR: at full alpha the two are identical, but DST_OUT
        // also honours the paint's alpha, which is what lets a partially explored cell (a dwell's
        // vision ring, explorationLevel 0.5) thin the fog instead of removing it outright.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        style = Paint.Style.FILL
    }

    private val scratchPath = Path()

    /** Rendering stats, for the frame-timing comparison in TASK 13. */
    var tilesRendered: Int = 0
        private set
    val bitmapAllocations: Int get() = pool.allocations

    /** Number of tiles currently cached. [cache].size() is a byte total, not a count. */
    val cachedTiles: Int get() = cache.snapshot().size
    val cachedBytes: Int get() = cache.size()

    /**
     * Swaps in a new explored set. Bumps [exploredVersion] and drops the cache, so the next draw
     * repaints the affected tiles.
     */
    fun setExploredCells(index: ExploredCellIndex) {
        this.index = index
        exploredVersion++
        clearTileCache()
    }

    fun setIntensity(intensity: FogIntensity) {
        if (this.intensity == intensity) return
        this.intensity = intensity
        fogColor = applyIntensity(baseFogRgb, intensity)
        exploredVersion++
        clearTileCache()
    }

    /** Drops every cached tile, returning their bitmaps to the pool for reuse. */
    fun clearTileCache() {
        cache.evictAll()
        flatTiles.clear()
    }

    fun destroy() {
        cache.evictAll()
        flatTiles.clear()
        pool.clear()
    }

    /**
     * The fog for one tile.
     *
     * When [renderBudget] is exhausted the tile is *not* rendered; the caller gets
     * [FogTile.FullyFogged] for this frame and can ask again on the next one. That caps how much
     * tile painting a single frame can absorb, so flinging across unrendered ground degrades to
     * briefly-heavier fog instead of dropped frames.
     */
    fun getTile(tile: TileId, renderBudget: RenderBudget = RenderBudget.UNLIMITED): FogTile {
        val key = cacheKey(tile)

        flatTiles[key]?.let { return it }
        cache.get(key)?.let { return FogTile.Rendered(it) }

        val bounds = TileMath.tileBounds(tile.zoom, tile.x, tile.y)

        if (index.isEmpty()) return remember(key, FogTile.FullyFogged)

        // Query a ring wider than the tile: a cell centred just outside it still bleeds fog-clearing
        // (and its feather) across the seam, and leaving it out would leave a visible edge.
        val margin = cellMarginDegrees(bounds)
        val candidates = index.query(bounds.expand(margin.first, margin.second))
        if (candidates.isEmpty()) return remember(key, FogTile.FullyFogged)

        if (isFullyExplored(candidates, bounds, tile)) {
            return remember(key, FogTile.FullyExplored)
        }

        if (!renderBudget.tryConsume()) return FogTile.FullyFogged

        val bitmap = render(tile, bounds, candidates)
        cache.put(key, bitmap)
        tilesRendered++
        return FogTile.Rendered(bitmap)
    }

    private fun remember(key: String, tile: FogTile): FogTile {
        flatTiles[key] = tile
        return tile
    }

    private fun cacheKey(tile: TileId): String = "${tile.zoom}/${tile.x}/${tile.y}/$exploredVersion"

    private fun render(
        tile: TileId,
        bounds: GeoBounds,
        cells: List<ExploredCellGeometry>
    ): Bitmap {
        val bitmap = pool.acquire()
        val canvas = Canvas(bitmap)
        canvas.drawColor(fogColor)

        // Feather radius in pixels, from a fixed ground distance, so the softness of the reveal
        // looks the same at every zoom instead of blurring away entirely when zoomed out.
        val metersPerPixel = TileMath.metersPerPixel(tile.zoom, bounds.centerLat, tileSize)
        val featherPx = (FEATHER_METERS / metersPerPixel)
            .toFloat()
            .coerceIn(MIN_FEATHER_PX, MAX_FEATHER_PX)
        clearPaint.maskFilter = BlurMaskFilter(featherPx, BlurMaskFilter.Blur.NORMAL)

        // Cells are grouped by how cleared they are and each group punched as ONE path. Clearing
        // them individually would double-darken every shared hex edge, printing the grid's seams
        // into the fog; a single path fills the union of the group and leaves no seams.
        cells.groupBy { quantiseLevel(it.explorationLevel) }
            .forEach { (level, group) ->
                if (level <= 0f) return@forEach
                scratchPath.rewind()
                group.forEach { cell -> appendCellPath(scratchPath, cell, tile) }
                clearPaint.alpha = Math.round(level * 255f).coerceIn(0, 255)
                canvas.drawPath(scratchPath, clearPaint)
            }

        return bitmap
    }

    private fun appendCellPath(path: Path, cell: ExploredCellGeometry, tile: TileId) {
        val corners = cell.corners
        if (corners.size < 3) return

        corners.forEachIndexed { i, corner ->
            val px = TileMath.pixelXInTile(corner.lng, tile.zoom, tile.x, tileSize)
            val py = TileMath.pixelYInTile(corner.lat, tile.zoom, tile.y, tileSize)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    /**
     * Whether every scrap of this tile is walked-on ground, in which case it needs no bitmap.
     *
     * Exact coverage would mean unioning hundreds of hexagons and comparing the area to the tile's,
     * per tile, per frame. Instead: hexagons tile the plane without gaps, so "no partially explored
     * cells nearby, and at least as many fully explored ones as the tile has room for" means the
     * tile is covered. The expected count comes from the cells' own footprint, so it stays correct
     * whichever grid engine is in use.
     */
    private fun isFullyExplored(
        cells: List<ExploredCellGeometry>,
        bounds: GeoBounds,
        tile: TileId
    ): Boolean {
        if (cells.any { it.explorationLevel < ExploredCell.LEVEL_WALKED }) return false

        val meanFootprint = cells.sumOf { it.bounds.areaDegrees() } / cells.size
        if (meanFootprint <= 0.0) return false

        // A hexagon fills ~3/4 of its bounding box.
        val cellArea = meanFootprint * HEX_TO_BBOX_AREA_RATIO
        val expected = bounds.areaDegrees() / cellArea

        return cells.size >= expected * FULL_COVERAGE_MARGIN
    }

    /** Half a cell's width/height, so a query ring picks up cells centred just off the tile. */
    private fun cellMarginDegrees(bounds: GeoBounds): Pair<Double, Double> {
        val sample = index.query(bounds).firstOrNull()
            ?: return DEFAULT_MARGIN_DEGREES to DEFAULT_MARGIN_DEGREES
        return (sample.bounds.north - sample.bounds.south) to (sample.bounds.east - sample.bounds.west)
    }

    /** Snaps levels to quarter steps so a tile needs at most a handful of path groups. */
    private fun quantiseLevel(level: Float): Float =
        (Math.round(level * LEVEL_STEPS) / LEVEL_STEPS.toFloat()).coerceIn(0f, 1f)

    private fun applyIntensity(argb: Int, intensity: FogIntensity): Int =
        Color.argb(
            intensity.alpha255,
            Color.red(argb),
            Color.green(argb),
            Color.blue(argb)
        )

    private companion object {
        /** How far the reveal fades out, on the ground. */
        const val FEATHER_METERS = 12.0
        const val MIN_FEATHER_PX = 2f
        const val MAX_FEATHER_PX = 4f

        const val HEX_TO_BBOX_AREA_RATIO = 0.75
        /** Allows a little slack so floating-point edges do not keep a covered tile alive. */
        const val FULL_COVERAGE_MARGIN = 0.98

        const val LEVEL_STEPS = 4
        const val DEFAULT_MARGIN_DEGREES = 0.0005

        fun bytesPerTile(tileSize: Int): Int = tileSize * tileSize * 4

        /**
         * A 512 px tile is 1 MB, and a phone viewport is ~15 of them, so the budget has to hold a
         * screenful plus a margin for panning without ever becoming a meaningful share of the heap.
         */
        fun defaultCacheBytes(): Int {
            val eighthOfHeap = (Runtime.getRuntime().maxMemory() / 8).toInt()
            return eighthOfHeap.coerceIn(8 * 1024 * 1024, 24 * 1024 * 1024)
        }
    }
}

/** Area in square degrees - only ever compared against another box at the same latitude. */
private fun GeoBounds.areaDegrees(): Double = (north - south) * (east - west)

/**
 * Caps how many tiles may be rendered in one frame. [UNLIMITED] is for tests and for off-frame
 * pre-rendering.
 */
class RenderBudget(private var remaining: Int) {
    fun tryConsume(): Boolean {
        if (remaining <= 0) return false
        remaining--
        return true
    }

    companion object {
        val UNLIMITED get() = RenderBudget(Int.MAX_VALUE)
    }
}
