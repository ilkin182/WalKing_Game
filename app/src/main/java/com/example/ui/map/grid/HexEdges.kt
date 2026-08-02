package com.example.ui.map.grid

import com.example.domain.model.Coordinate

/** One straight segment of the grid, in geographic coordinates. */
data class GridEdge(val from: Coordinate, val to: Coordinate)

/**
 * Turns a pile of hexagon rings into the lines that should actually be drawn.
 *
 * Hexagons tile the plane, so every interior edge belongs to two cells. Drawing each cell's own ring
 * therefore paints every interior line twice - which is what made the old grid look muddy (doubled
 * stroke weight on every seam, and doubled alpha where the strokes were translucent) and cost twice
 * the drawing it needed to. Both helpers here collapse that: [unique] keeps one line per seam, and
 * [boundary] keeps only the seams that separate the region from the outside, which is what turns a
 * few hundred claimed cells into a single outlined territory instead of a honeycomb.
 *
 * Vertices are matched at ~1 cm, so two neighbouring cells whose shared corners come back from the
 * grid engine differing in the last floating-point bits still count as the same vertex.
 */
object HexEdges {

    /** Every distinct edge, once. The honeycomb mesh over unwalked ground. */
    fun unique(rings: List<List<Coordinate>>): List<GridEdge> = collect(rings, onlyBoundary = false)

    /**
     * The edges exactly one cell owns: the outline of [rings] taken as a single region. Edges shared
     * by two cells are interior seams and are dropped.
     */
    fun boundary(rings: List<List<Coordinate>>): List<GridEdge> = collect(rings, onlyBoundary = true)

    private fun collect(rings: List<List<Coordinate>>, onlyBoundary: Boolean): List<GridEdge> {
        if (rings.isEmpty()) return emptyList()

        // Insertion-ordered so the output is stable across runs; shared holds the keys seen more
        // than once, which is all "interior" means here.
        val first = LinkedHashMap<EdgeKey, GridEdge>()
        val shared = HashSet<EdgeKey>()

        rings.forEach { ring ->
            if (ring.size < 3) return@forEach
            ring.forEachIndexed { i, corner ->
                val next = ring[(i + 1) % ring.size]
                val key = EdgeKey.of(corner, next)
                if (first.putIfAbsent(key, GridEdge(corner, next)) != null) shared.add(key)
            }
        }

        if (!onlyBoundary) return first.values.toList()
        return first.entries.mapNotNull { (key, edge) -> edge.takeIf { key !in shared } }
    }

    /**
     * An edge identified by its two endpoints, independent of which way round the owning cell walks
     * it - the two cells sharing a seam traverse it in opposite directions.
     */
    private data class EdgeKey(val low: Long, val high: Long) {
        companion object {
            /** ~1 cm at the equator: far below the grid's resolution, far above the engine's noise. */
            private const val VERTEX_SCALE = 1e7

            fun of(a: Coordinate, b: Coordinate): EdgeKey {
                val ka = vertex(a)
                val kb = vertex(b)
                return if (ka <= kb) EdgeKey(ka, kb) else EdgeKey(kb, ka)
            }

            // Both scaled coordinates fit an Int (lat <= 9e8, lng <= 1.8e9), so a vertex packs into
            // one Long and the map never has to hash a string.
            private fun vertex(c: Coordinate): Long {
                val lat = Math.round(c.lat * VERTEX_SCALE)
                val lng = Math.round(c.lng * VERTEX_SCALE)
                return (lat shl 32) or (lng and 0xFFFFFFFFL)
            }
        }
    }
}
