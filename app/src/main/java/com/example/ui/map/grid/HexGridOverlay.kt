package com.example.ui.map.grid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import com.example.domain.model.Coordinate
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Draws the hex grid: claimed territory as one filled region under a single outline, and - only when
 * the zoom makes it legible - the honeycomb of guide lines over ground nobody has walked yet.
 *
 * ## Why one overlay instead of one osmdroid Polygon per cell
 *
 * A [org.osmdroid.views.overlay.Polygon] per cell draws each hexagon in isolation, which is what
 * made a walked neighbourhood read as a honeycomb of separate tiles: every shared edge got two
 * strokes and every translucent fill met its neighbour at an antialiased seam, so the claimed area
 * came out as a mesh of outlined cells rather than as one piece of ground. Filling all the cells as
 * a single [Path] merges them (non-zero winding fills the union, so abutting cells leave no seam)
 * and [HexEdges.boundary] reduces their outlines to the border of the region itself.
 *
 * It is also what makes the grid affordable: hundreds of overlay objects, each re-projecting and
 * re-styling itself, become three path builds per frame.
 *
 * Geometry is set from outside ([geometry]) already reduced; the overlay only projects and paints.
 * The level of detail is read from the live projection on every frame rather than from the geometry,
 * so pinching to zoom out drops the guide lines immediately instead of after the next rebuild.
 */
class HexGridOverlay(
    private val style: HexGridStyle,
    private val density: Float
) : Overlay() {

    var geometry: HexGridGeometry = HexGridGeometry.EMPTY

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scratchPath = Path()
    private val scratchPoint = Point()
    private val scratchGeoPoint = GeoPoint(0.0, 0.0)

    // Where the last [project] call landed. A viewport can hold thousands of vertices, so projecting
    // into fields rather than returning a pair keeps a pan from allocating one object per corner
    // per frame.
    private var projectedX = 0f
    private var projectedY = 0f

    override fun draw(canvas: Canvas, projection: Projection) {
        val lod = GridLod.forZoom(projection.zoomLevel.toDouble())
        if (!lod.drawsTerritory) return

        val current = geometry
        if (current.isEmpty) return

        // Faintly revealed ground first, then walked ground over it: a cell that is both (the player
        // later walked into a cell they had only seen) ends up at the stronger of the two.
        fillRegion(canvas, projection, current.seen, style.seenFill)
        fillRegion(canvas, projection, current.walked, style.walkedFill)

        if (lod.drawsEmptyCells && current.emptyEdges.isNotEmpty()) {
            strokePaint.color = withAlpha(style.emptyCellStroke, lod.emptyCellAlpha)
            strokePaint.strokeWidth = lod.emptyStrokeDp * density
            strokeEdges(canvas, projection, current.emptyEdges)
        }

        if (current.territoryBorder.isNotEmpty()) {
            strokePaint.color = style.territoryBorder
            strokePaint.strokeWidth = lod.territoryStrokeDp * density
            strokeEdges(canvas, projection, current.territoryBorder)
        }
    }

    private fun fillRegion(
        canvas: Canvas,
        projection: Projection,
        rings: List<List<Coordinate>>,
        color: Int
    ) {
        if (rings.isEmpty()) return

        scratchPath.rewind()
        rings.forEach { ring ->
            ring.forEachIndexed { i, corner ->
                project(projection, corner)
                if (i == 0) {
                    scratchPath.moveTo(projectedX, projectedY)
                } else {
                    scratchPath.lineTo(projectedX, projectedY)
                }
            }
            scratchPath.close()
        }

        fillPaint.color = color
        canvas.drawPath(scratchPath, fillPaint)
    }

    private fun strokeEdges(canvas: Canvas, projection: Projection, edges: List<GridEdge>) {
        scratchPath.rewind()
        edges.forEach { edge ->
            project(projection, edge.from)
            scratchPath.moveTo(projectedX, projectedY)
            project(projection, edge.to)
            scratchPath.lineTo(projectedX, projectedY)
        }
        canvas.drawPath(scratchPath, strokePaint)
    }

    private fun project(projection: Projection, coordinate: Coordinate) {
        scratchGeoPoint.setCoords(coordinate.lat, coordinate.lng)
        val point = projection.toPixels(scratchGeoPoint, scratchPoint)
        projectedX = point.x.toFloat()
        projectedY = point.y.toFloat()
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
}
