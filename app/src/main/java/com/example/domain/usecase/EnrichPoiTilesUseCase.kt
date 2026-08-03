package com.example.domain.usecase

import com.example.domain.engine.PoiTiles
import com.example.domain.model.Coordinate
import com.example.domain.repository.PoiRepository
import com.example.domain.repository.StompedHexRepository

/**
 * Fetches what is on the ground under the cells the player has claimed, one ~1 km tile at a time.
 *
 * The third of the enrichment passes, alongside [EnrichCellElevationsUseCase] and
 * [EnrichCellPlacesUseCase], and the slowest by a wide margin - which is the point. The queue is the
 * tiles the player's history covers that have not been asked about, oldest cell first, so a new
 * install works backwards through everything walked before this existed and a returning player picks
 * up wherever the last pass stopped.
 *
 * Only one tile per call by default. A player who walks a new route across a city can add fifty
 * tiles to the queue in an hour, and the repository holds those to one request every fifteen
 * seconds; taking them a few at a time here would only queue up behind that same limit while holding
 * a coroutine open. Returning the count lets the caller keep going while there is work, and stop the
 * moment there is not.
 */
class EnrichPoiTilesUseCase(
    private val cells: StompedHexRepository,
    private val pois: PoiRepository,
    private val resolveCenter: (String) -> Coordinate?
) {
    /**
     * The last computed tile list and the history size it was computed from.
     *
     * Draining a backlog means running every fifteen seconds for hours, and recomputing this each
     * time would resolve the geometry of every cell the player has ever claimed on every pass. The
     * history only ever grows or is wiped, so its size is enough to tell when the answer has gone
     * stale.
     */
    private var cachedFor = -1
    private var cachedTiles: List<String> = emptyList()

    suspend operator fun invoke(limit: Int = DEFAULT_BATCH): Int {
        val wanted = tilesOfHistory()
        if (wanted.isEmpty()) return 0

        val cached = pois.cachedTiles(wanted)
        val missing = wanted.filterNot { it in cached }.take(limit)

        // Each is counted only if it was actually stored: a tile the service could not answer for
        // stays in the queue, and reporting it as done would make the caller's drain loop spin.
        return missing.count { pois.fetchTile(it) }
    }

    /**
     * Every tile the exploration history touches, oldest cell first.
     *
     * Deduplicated as it goes: a walk through one square claims dozens of cells that all resolve to
     * the same tile, and the whole reason for the tile scheme is that they cost one request between
     * them. Cells whose geometry cannot be resolved are skipped - there is no square to ask about.
     */
    private suspend fun tilesOfHistory(): List<String> {
        val history = cells.getAll()
        if (history.size == cachedFor) return cachedTiles

        val keys = LinkedHashSet<String>()
        history
            .sortedBy { it.timestamp }
            .forEach { cell ->
                val center = runCatching { resolveCenter(cell.hexAddress) }.getOrNull()
                if (center != null) keys.add(PoiTiles.keyOf(center))
            }

        cachedFor = history.size
        cachedTiles = keys.toList()
        return cachedTiles
    }

    private companion object {
        const val DEFAULT_BATCH = 1
    }
}
