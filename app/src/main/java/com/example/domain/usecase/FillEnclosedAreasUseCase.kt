package com.example.domain.usecase

import com.example.domain.engine.HexGridConfig
import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.repository.StompedHexRepository

/**
 * Flood-fills any pockets of unstomped cells that are fully surrounded by stomped cells,
 * so a player who walks a closed loop automatically claims the interior.
 *
 * The interior of a loop the size of a town is tens of thousands of cells, so the search is built
 * to answer "is this pocket sealed?" without ever walking the unbounded outside world:
 *
 *  - It only looks when the cell just claimed could actually have sealed something - when it split
 *    the unclaimed ground around it in two (see [isPinchPoint]). Almost every fix on a walk fails
 *    that test and costs seven neighbour lookups.
 *  - When it does look, it expands the candidate regions in lockstep rather than one after the
 *    other. A pinch produces an inside and an outside; the inside runs out of cells while the
 *    outside is still going, so the work is bounded by the *smaller* of the two, whichever that
 *    turns out to be, instead of by the size of the world.
 *
 * @param onAreasEnclosed told how many separate pockets a fill closed, so the "walked a closed loop"
 * achievement can count them. Reported from here rather than inferred later because the event leaves
 * no trace afterwards: once the interior is claimed it is indistinguishable from ground that was
 * walked over. A lambda rather than a repository so the flood fill stays pure enough to unit-test.
 */
class FillEnclosedAreasUseCase(
    private val repository: StompedHexRepository,
    private val hexGridEngine: HexGridEngine,
    private val onAreasEnclosed: suspend (Int) -> Unit = {}
) {
    /**
     * Claims whatever [newCell] just sealed off.
     *
     * @return how many separate enclosed pockets this call claimed - one per loop closed.
     */
    suspend operator fun invoke(
        newCell: String,
        neighborhood: String?,
        stompedAddresses: Set<String>
    ): Int {
        val stomped = if (newCell in stompedAddresses) stompedAddresses else stompedAddresses + newCell
        val seeds = neighborsOf(newCell).filterNot(stomped::contains)
        if (!isPinchPoint(seeds)) return 0

        return claim(enclosedPockets(seeds, stomped), neighborhood)
    }

    /**
     * Sweeps the whole claimed area for pockets that are already sealed but were never filled in.
     *
     * [invoke] only ever asks about the ground around the cell being claimed, which is all a walk in
     * progress needs. It cannot answer for a loop that was closed while the fill was capped, or by a
     * version of the app that filled nothing bigger than a courtyard - and the player who walked
     * that loop is still, correctly, waiting for their territory. This is the pass that hands it over.
     *
     * Rather than ask of each pocket "is this one sealed?", it works the other way round and maps
     * the outside once: flood the unclaimed ground inward from a rim drawn well clear of everything
     * the player has ever claimed, and whatever the flood never reaches is, by definition, walled in.
     * One pass over the area the player has covered, however many pockets are hiding in it.
     *
     * @return how many separate enclosed pockets it claimed.
     */
    suspend fun fillAll(stompedAddresses: Set<String>, neighborhood: String?): Int {
        if (stompedAddresses.isEmpty()) return 0

        val area = areaAround(stompedAddresses) ?: return 0
        val outside = floodFromRim(area, stompedAddresses) ?: return 0

        val pockets = mutableListOf<Set<String>>()
        val claimed = HashSet<String>()
        for (cell in stompedAddresses) {
            for (neighbor in neighborsOf(cell)) {
                if (neighbor in stompedAddresses || neighbor in outside || neighbor in claimed) continue

                // Nothing to work out here: the flood already proved this ground has no way out, so
                // the limit is only about how much can sensibly be handed over in one go.
                val pocket = regionAround(neighbor, stompedAddresses, MAX_SWEEP_POCKET_CELLS) ?: continue
                claimed.addAll(pocket)
                pockets.add(pocket)
            }
        }

        return claim(pockets, neighborhood)
    }

    /**
     * The box holding everything claimed so far, pushed out far enough on every side that the strip
     * it adds is guaranteed to be unclaimed ground - which is what makes it usable as a rim that is
     * definitely outside, and definitely joined up all the way round.
     */
    private fun areaAround(stomped: Set<String>): Box? {
        var north = -90.0
        var south = 90.0
        var east = -180.0
        var west = 180.0
        var found = false

        for (cell in stomped) {
            val corners = cornersOf(cell)
            for (corner in corners) {
                north = maxOf(north, corner.lat)
                south = minOf(south, corner.lat)
                east = maxOf(east, corner.lng)
                west = minOf(west, corner.lng)
                found = true
            }
        }
        if (!found) return null

        val latMargin = RIM_MARGIN_METERS / METERS_PER_DEGREE_LAT
        val cosLat = Math.cos(Math.toRadians((north + south) / 2)).coerceAtLeast(MIN_COS_LAT)
        val lngMargin = RIM_MARGIN_METERS / (METERS_PER_DEGREE_LAT * cosLat)

        return Box(
            north = (north + latMargin).coerceAtMost(MAX_MERCATOR_LAT),
            south = (south - latMargin).coerceAtLeast(-MAX_MERCATOR_LAT),
            east = (east + lngMargin).coerceAtMost(180.0),
            west = (west - lngMargin).coerceAtLeast(-180.0)
        )
    }

    /**
     * Every unclaimed cell inside [area] that can be reached from its edge.
     *
     * Null when that runs past [MAX_OUTSIDE_CELLS] - the player has covered too much ground for one
     * sweep. Nothing is filled in that case: a half-mapped outside would read as an enormous sealed
     * pocket, and claiming that would hand over the whole map.
     */
    private fun floodFromRim(area: Box, stomped: Set<String>): Set<String>? {
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()

        for (corner in area.corners()) {
            val cell = cellAt(corner) ?: continue
            if (cell in stomped || !visited.add(cell)) continue
            queue.add(cell)
        }
        if (queue.isEmpty()) return null

        while (queue.isNotEmpty()) {
            if (visited.size > MAX_OUTSIDE_CELLS) return null
            for (neighbor in neighborsOf(queue.removeFirst())) {
                if (neighbor in stomped || neighbor in visited) continue
                val center = centerOf(neighbor) ?: continue
                if (!area.contains(center)) continue
                visited.add(neighbor)
                queue.add(neighbor)
            }
        }
        return visited
    }

    /** The unclaimed cells joined to [start], or null if the region turns out to be too big to claim. */
    private fun regionAround(start: String, stomped: Set<String>, limit: Int): Set<String>? {
        val region = HashSet<String>()
        val queue = ArrayDeque<String>()
        region.add(start)
        queue.add(start)

        while (queue.isNotEmpty()) {
            if (region.size > limit) return null
            for (neighbor in neighborsOf(queue.removeFirst())) {
                if (neighbor in stomped || !region.add(neighbor)) continue
                queue.add(neighbor)
            }
        }
        return region
    }

    private suspend fun claim(pockets: List<Set<String>>, neighborhood: String?): Int {
        if (pockets.isEmpty()) return 0

        // Written in batches: the interior of a loop around a town runs to tens of thousands of
        // cells, and a single insert that size holds the database open for as long as it takes.
        val cells = pockets.flatMapTo(mutableListOf()) { it }
        cells.chunked(WRITE_BATCH_SIZE).forEach { repository.stompAll(it, neighborhood) }
        onAreasEnclosed(pockets.size)
        return pockets.size
    }

    /**
     * Whether claiming a cell could have sealed a pocket at all - the cheap test that keeps this
     * off the critical path of an ordinary walk.
     *
     * A pocket only becomes enclosed when the cell just claimed was the last way out of it, which
     * means the unclaimed cells around that one now fall into two or more separate arcs: the pocket
     * on one side, the rest of the world on the other. While the player walks along open ground
     * their unclaimed neighbours form a single arc, and there is nothing to look for.
     */
    private fun isPinchPoint(seeds: List<String>): Boolean {
        if (seeds.size < 2) return false

        // Connected components of the (at most six) unclaimed neighbours, joined where they touch
        // each other. Two arcs or more means the cell separated them.
        val group = HashMap<String, Int>(seeds.size * 2)
        var groups = 0
        for (seed in seeds) {
            if (seed in group) continue
            val id = groups++
            val queue = ArrayDeque<String>()
            queue.add(seed)
            group[seed] = id
            while (queue.isNotEmpty()) {
                val touching = neighborsOf(queue.removeFirst())
                for (other in seeds) {
                    if (other !in group && other in touching) {
                        group[other] = id
                        queue.add(other)
                    }
                }
            }
            if (groups > 1) return true
        }
        return false
    }

    /**
     * The sealed pockets among the regions of unstomped ground reachable from [seeds].
     *
     * All the regions grow a cell at a time in turn. A region whose queue empties is walled in on
     * every side and is a pocket; one that grows past [MAX_POCKET_CELLS] is treated as the outside
     * world and dropped. Regions that turn out to be joined are merged - and if everything merges
     * into one, nothing was sealed off and the search stops there.
     */
    private fun enclosedPockets(seeds: List<String>, stomped: Set<String>): List<Set<String>> {
        val cells = ArrayList<MutableSet<String>>(seeds.size)
        val queues = ArrayList<ArrayDeque<String>>(seeds.size)
        val mergedInto = IntArray(seeds.size) { it }
        val owner = HashMap<String, Int>()
        val active = ArrayDeque<Int>()

        fun rootOf(region: Int): Int {
            var root = region
            while (mergedInto[root] != root) {
                mergedInto[root] = mergedInto[mergedInto[root]]
                root = mergedInto[root]
            }
            return root
        }

        for (seed in seeds) {
            // Two seeds on the same cell would race themselves; the first one to claim it wins.
            if (owner.containsKey(seed)) continue
            val id = cells.size
            cells.add(mutableSetOf(seed))
            queues.add(ArrayDeque<String>().apply { add(seed) })
            owner[seed] = id
            active.add(id)
        }

        val enclosed = mutableListOf<Set<String>>()
        var undecided = active.size
        var budget = MAX_TOTAL_EXPANSIONS

        while (active.isNotEmpty() && budget-- > 0) {
            val region = active.removeFirst()
            if (rootOf(region) != region) continue

            val queue = queues[region]
            if (queue.isEmpty()) {
                // Nowhere left to go: every way out of this region is claimed ground.
                enclosed.add(cells[region])
                // With a pocket found and one region left, that region is the outside world, and
                // chasing it to the cap proves nothing. (One cell sealing off *two* pockets at once
                // would need three separate arcs around it; the sweep at launch catches those.)
                if (--undecided <= 1) break
                continue
            }

            var joinedEverything = false
            for (neighbor in neighborsOf(queue.removeFirst())) {
                if (neighbor in stomped) continue

                val existing = owner[neighbor]
                if (existing == null) {
                    owner[neighbor] = region
                    cells[region].add(neighbor)
                    queue.add(neighbor)
                    continue
                }

                val other = rootOf(existing)
                if (other == region) continue

                // The two regions are one after all - fold the other into this one. At most one
                // merge per seed can ever happen, so copying rather than book-keeping is cheapest.
                mergedInto[other] = region
                cells[region].addAll(cells[other])
                queue.addAll(queues[other])
                cells[other] = mutableSetOf()
                queues[other] = ArrayDeque()
                if (--undecided <= 1) {
                    // Everything still in the race is joined up, so this cell separated nothing.
                    joinedEverything = true
                    break
                }
            }
            if (joinedEverything) break

            if (cells[region].size > MAX_POCKET_CELLS) {
                // Too big to be the inside of anything a person walked around: this is the world.
                undecided--
                continue
            }
            active.add(region)
        }

        return enclosed
    }

    private fun neighborsOf(cell: String): List<String> = try {
        hexGridEngine.gridDisk(cell, 1).filter { it != cell }
    } catch (e: Exception) {
        emptyList()
    }

    private fun cornersOf(cell: String): List<Coordinate> = try {
        hexGridEngine.cellToBoundary(cell)
    } catch (e: Exception) {
        emptyList()
    }

    /** A cell's centre, averaged from its corners - the engine exposes an outline, not a centre. */
    private fun centerOf(cell: String): Coordinate? {
        val corners = cornersOf(cell)
        if (corners.isEmpty()) return null
        return Coordinate(
            lat = corners.sumOf { it.lat } / corners.size,
            lng = corners.sumOf { it.lng } / corners.size
        )
    }

    private fun cellAt(point: Coordinate): String? = try {
        hexGridEngine.latLngToCellAddress(point.lat, point.lng, HexGridConfig.RESOLUTION)
    } catch (e: Exception) {
        null
    }

    /** The area a sweep covers - see [areaAround]. */
    private data class Box(
        val north: Double,
        val south: Double,
        val east: Double,
        val west: Double
    ) {
        fun contains(point: Coordinate): Boolean =
            point.lat in south..north && point.lng in west..east

        fun corners(): List<Coordinate> = listOf(
            Coordinate(north, west),
            Coordinate(north, east),
            Coordinate(south, east),
            Coordinate(south, west)
        )
    }

    private companion object {
        /**
         * Where a region being grown stops being a pocket and starts being the outside world, in
         * cells - about 70 km², a loop some nine kilometres across.
         *
         * Generous on purpose: the point of the feature is the loop around a district or a town,
         * which the previous limit of two hundred cells (half a square kilometre) could never
         * close. Anything bigger still gets claimed, just not until the sweep at the next launch,
         * which knows where the outside is rather than having to guess from a size.
         */
        const val MAX_POCKET_CELLS = 30_000

        /**
         * Total cells any one search may look at, across all the regions racing in it.
         *
         * Comfortably more than twice [MAX_POCKET_CELLS], because the regions advance together and
         * the one that seals is only ever half the work. It is also what bounds the search that
         * finds nothing - closing a gap in a long line of claimed cells sets two regions off around
         * a wall they only meet at the far end of - so it is deliberately not open-ended.
         */
        const val MAX_TOTAL_EXPANSIONS = 70_000

        /**
         * How much unclaimed ground a full sweep may map before giving up - about 480 km² of box.
         *
         * The sweep walks every unclaimed cell around what the player has covered, so this is what
         * decides how spread out a history it can still answer for. Going higher costs memory for
         * the whole time it runs; failing here costs nothing but a sweep that fills nothing.
         */
        const val MAX_OUTSIDE_CELLS = 250_000

        /** The most a single sweep will hand over at once, however much of it is walled in. */
        const val MAX_SWEEP_POCKET_CELLS = 100_000

        /** How far outside everything claimed the sweep's rim is drawn - several cells clear. */
        const val RIM_MARGIN_METERS = 250.0

        /** Rows per insert when a fill claims a whole district at once. */
        const val WRITE_BATCH_SIZE = 2_000

        const val METERS_PER_DEGREE_LAT = 111_111.0
        const val MAX_MERCATOR_LAT = 85.0
        const val MIN_COS_LAT = 0.01
    }
}
