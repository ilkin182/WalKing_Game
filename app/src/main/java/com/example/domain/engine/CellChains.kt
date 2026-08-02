package com.example.domain.engine

import com.example.domain.model.Coordinate
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Finds straight lines in the set of cells a player has claimed.
 *
 * A hexagon has six neighbours, which are three axes taken in both directions. A "wall" is the
 * longest unbroken run of claimed cells along one of those axes - the shape of a player who walked
 * one street end to end, as opposed to the same number of cells scattered over a neighbourhood.
 *
 * Pure and engine-agnostic: it is handed the adjacency and the geometry as functions, so it works
 * against whichever [HexGridEngine] is wired up and runs in a unit test with neither.
 */
object CellChains {

    /** The six neighbour directions, which are the three axes taken forwards and backwards. */
    private const val DIRECTIONS = 6
    private const val DEGREES_PER_DIRECTION = 360.0 / DIRECTIONS

    /**
     * The longest unbroken straight line of claimed cells, counted in cells.
     *
     * @param cells every claimed cell id.
     * @param neighborsOf the cells touching one cell - the centre itself may be included and is
     *   ignored.
     * @param centerOf a cell's centre, used only to tell the six directions apart. Cells whose
     *   centre cannot be resolved take no part in any line rather than breaking the whole count.
     */
    fun longestStraightRun(
        cells: Set<String>,
        neighborsOf: (String) -> List<String>,
        centerOf: (String) -> Coordinate?
    ): Int {
        if (cells.isEmpty()) return 0

        val centers = HashMap<String, Coordinate>(cells.size)
        cells.forEach { cell ->
            runCatching { centerOf(cell) }.getOrNull()?.let { centers[cell] = it }
        }
        if (centers.isEmpty()) return 0

        // For every cell, which claimed cell (if any) lies in each of the six directions.
        val next = HashMap<String, Array<String?>>(centers.size)
        centers.forEach { (cell, center) ->
            val slots = arrayOfNulls<String>(DIRECTIONS)
            val neighbors = runCatching { neighborsOf(cell) }.getOrDefault(emptyList())
            neighbors.forEach { neighbor ->
                if (neighbor == cell) return@forEach
                val neighborCenter = centers[neighbor] ?: return@forEach
                slots[directionOf(center, neighborCenter)] = neighbor
            }
            next[cell] = slots
        }

        // How far the line continues from each cell in each single direction...
        val runs = Array(DIRECTIONS) { direction -> runLengths(next, direction) }

        // ...so a line through a cell is what continues one way plus what continues the other,
        // minus the cell itself, which both halves counted.
        var longest = 0
        next.keys.forEach { cell ->
            for (axis in 0 until DIRECTIONS / 2) {
                val through = runs[axis].getValue(cell) + runs[axis + DIRECTIONS / 2].getValue(cell) - 1
                if (through > longest) longest = through
            }
        }
        return longest
    }

    /**
     * How many cells the line continues for from each cell, following [direction] only.
     *
     * Walked iteratively rather than by recursing down the chain: a player who has covered a long
     * straight road has a run thousands of cells long, which is a stack overflow waiting to happen.
     */
    private fun runLengths(next: Map<String, Array<String?>>, direction: Int): Map<String, Int> {
        val lengths = HashMap<String, Int>(next.size)
        val path = ArrayList<String>()

        next.keys.forEach { start ->
            if (lengths.containsKey(start)) return@forEach

            // Follow the direction until the line ends or reaches a cell already measured. Cells
            // cannot repeat - each step moves strictly further along one direction - so this
            // terminates without needing a visited set.
            path.clear()
            var cell: String? = start
            while (cell != null && !lengths.containsKey(cell)) {
                path.add(cell)
                cell = next[cell]?.get(direction)
            }

            var lengthAhead = if (cell == null) 0 else lengths.getValue(cell)
            for (index in path.indices.reversed()) {
                lengthAhead += 1
                lengths[path[index]] = lengthAhead
            }
        }
        return lengths
    }

    /**
     * Which of the six directions [to] lies in, seen from [from].
     *
     * The bearing is taken in metres rather than in degrees of latitude and longitude, so a hexagon
     * is not read as squashed the further the player is from the equator. The grid's six directions
     * fall in the middle of the six sectors, which is what keeps a cell and its neighbour agreeing
     * on which direction connects them.
     */
    private fun directionOf(from: Coordinate, to: Coordinate): Int {
        val east = (to.lng - from.lng) * cos(Math.toRadians(from.lat))
        val north = to.lat - from.lat
        val bearing = (Math.toDegrees(atan2(north, east)) + 360.0) % 360.0
        return (bearing / DEGREES_PER_DIRECTION).toInt().coerceIn(0, DIRECTIONS - 1)
    }
}
