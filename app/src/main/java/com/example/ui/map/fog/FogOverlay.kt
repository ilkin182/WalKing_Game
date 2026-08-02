package com.example.ui.map.fog

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Draws the fog of war over the base map.
 *
 * Sits directly above the base tiles and below everything else - district borders and the user
 * marker are added after it, so osmdroid paints them on top (its overlay list order *is* the
 * z-index).
 *
 * The overlay owns no geometry of its own: it asks [provider] for the tiles covering the viewport
 * and blits them. Tile placement goes through the map's own [Projection], so the fog stays glued to
 * the map when the user rotates it with the on-screen rotate buttons.
 */
class FogOverlay(
    private val provider: FogTileProvider,
    private val tileSize: Int = TileMath.TILE_SIZE_PX
) : Overlay() {

    /** Cells currently playing their 300 ms reveal fade, and when each started. */
    private val revealing = ArrayList<Reveal>()

    private val fogPaint = Paint().apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val revealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val matrix = Matrix()
    private val srcPoly = FloatArray(6)
    private val dstPoly = FloatArray(6)
    private val scratchPoint = Point()
    private val scratchPath = Path()
    private val scratchGeoPoint = GeoPoint(0.0, 0.0)

    /** Set while a reveal is in flight, so the map knows to keep drawing frames. */
    var isAnimating: Boolean = false
        private set

    /** Frames drawn and tiles blitted, for the TASK 13 frame-timing comparison. */
    var framesDrawn: Long = 0L
        private set

    /**
     * Starts the reveal fade for cells that have just been explored. The fog over them is painted
     * back on at full strength and fades out over [REVEAL_DURATION_MS], so ground appears rather
     * than pops.
     */
    fun revealCells(cells: List<ExploredCellGeometry>, nowMs: Long = System.currentTimeMillis()) {
        if (cells.isEmpty()) return
        cells.forEach { revealing.add(Reveal(it, nowMs)) }
        isAnimating = true
    }

    fun cancelReveals() {
        revealing.clear()
        isAnimating = false
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        framesDrawn++

        val zoom = projection.zoomLevel
        val viewport = projection.boundingBox ?: return
        val bounds = GeoBounds(
            north = viewport.latNorth,
            south = viewport.latSouth,
            east = viewport.lonEast,
            west = viewport.lonWest
        )

        // A 512 px fog tile covers the same ground as two 256 px map tiles, so it is one zoom level
        // coarser than the map's - that keeps one fog pixel to one screen pixel.
        val fogZoom = (Math.round(zoom).toInt() - 1).coerceIn(MIN_FOG_ZOOM, MAX_FOG_ZOOM)

        val budget = RenderBudget(MAX_TILE_RENDERS_PER_FRAME)
        fogPaint.color = provider.fogColor

        TileMath.tilesCovering(bounds, fogZoom).forEach { tileId ->
            drawTile(canvas, projection, tileId, budget)
        }

        drawReveals(canvas, projection)
    }

    private fun drawTile(canvas: Canvas, projection: Projection, tileId: TileId, budget: RenderBudget) {
        val tile = provider.getTile(tileId, budget)
        if (tile is FogTile.FullyExplored) return

        val tileBounds = TileMath.tileBounds(tileId.zoom, tileId.x, tileId.y)

        when (tile) {
            is FogTile.FullyFogged -> {
                buildTilePath(projection, tileBounds, scratchPath)
                canvas.drawPath(scratchPath, fogPaint)
            }

            is FogTile.Rendered -> {
                if (!buildTileMatrix(projection, tileBounds)) return
                canvas.drawBitmap(tile.bitmap, matrix, bitmapPaint)
            }

            FogTile.FullyExplored -> Unit
        }
    }

    /**
     * Maps the tile bitmap onto the screen via three of its corners.
     *
     * Three points define the affine transform exactly, which is what makes this correct under map
     * rotation - a plain `drawBitmap(bitmap, left, top, paint)` would leave the fog upright while
     * the map turned underneath it.
     */
    private fun buildTileMatrix(projection: Projection, bounds: GeoBounds): Boolean {
        val nw = project(projection, bounds.north, bounds.west) ?: return false
        val ne = project(projection, bounds.north, bounds.east) ?: return false
        val sw = project(projection, bounds.south, bounds.west) ?: return false

        srcPoly[0] = 0f; srcPoly[1] = 0f
        srcPoly[2] = tileSize.toFloat(); srcPoly[3] = 0f
        srcPoly[4] = 0f; srcPoly[5] = tileSize.toFloat()

        dstPoly[0] = nw.first; dstPoly[1] = nw.second
        dstPoly[2] = ne.first; dstPoly[3] = ne.second
        dstPoly[4] = sw.first; dstPoly[5] = sw.second

        return matrix.setPolyToPoly(srcPoly, 0, dstPoly, 0, 3)
    }

    private fun buildTilePath(projection: Projection, bounds: GeoBounds, path: Path) {
        path.rewind()
        bounds.toCorners().forEachIndexed { i, corner ->
            val p = project(projection, corner.lat, corner.lng) ?: return@forEachIndexed
            if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
        }
        path.close()
    }

    private fun drawReveals(canvas: Canvas, projection: Projection) {
        if (revealing.isEmpty()) {
            isAnimating = false
            return
        }

        val now = System.currentTimeMillis()
        val iterator = revealing.iterator()
        var stillAnimating = false

        while (iterator.hasNext()) {
            val reveal = iterator.next()
            val progress = (now - reveal.startedAt).toFloat() / REVEAL_DURATION_MS
            if (progress >= 1f) {
                iterator.remove()
                continue
            }
            stillAnimating = true

            // Fog painted back over the cell at a strength that decays to nothing: the underlying
            // tile is already cleared, so this is what makes the clearing fade in over 300 ms.
            val alpha = ((1f - progress) * Color.alpha(provider.fogColor)).toInt().coerceIn(0, 255)
            revealPaint.color = provider.fogColor
            revealPaint.alpha = alpha

            buildCellPath(projection, reveal.cell.corners, scratchPath)
            canvas.drawPath(scratchPath, revealPaint)
        }

        isAnimating = stillAnimating
    }

    private fun buildCellPath(projection: Projection, corners: List<Coordinate>, path: Path) {
        path.rewind()
        corners.forEachIndexed { i, corner ->
            val p = project(projection, corner.lat, corner.lng) ?: return@forEachIndexed
            if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
        }
        path.close()
    }

    private fun project(projection: Projection, lat: Double, lng: Double): Pair<Float, Float>? {
        scratchGeoPoint.setCoords(lat, lng)
        val point = projection.toPixels(scratchGeoPoint, scratchPoint) ?: return null
        return point.x.toFloat() to point.y.toFloat()
    }

    override fun onDetach(mapView: MapView?) {
        cancelReveals()
        provider.destroy()
        super.onDetach(mapView)
    }

    private data class Reveal(val cell: ExploredCellGeometry, val startedAt: Long)

    private companion object {
        /** The epic's reveal timing. */
        const val REVEAL_DURATION_MS = 300f

        /**
         * At most this many *new* tiles get painted in one frame. Anything over budget stays flat
         * fog for that frame and is picked up on the next one, so a fast fling can never turn into
         * a multi-tile paint stall inside a single 16 ms budget.
         */
        const val MAX_TILE_RENDERS_PER_FRAME = 3

        const val MIN_FOG_ZOOM = 1
        const val MAX_FOG_ZOOM = 21
    }
}
