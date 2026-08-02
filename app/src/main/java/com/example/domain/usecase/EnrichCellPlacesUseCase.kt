package com.example.domain.usecase

import com.example.domain.model.Coordinate
import com.example.domain.model.PlaceInfo
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.StompedHexRepository

/**
 * Fills in the town and country of cells that do not have them yet, a batch at a time.
 *
 * The counterpart to [EnrichCellElevationsUseCase], and it exists for the same reason: the queue is
 * "cells with no place, oldest first", so the pass that keeps up with new cells also works backwards
 * through everything claimed before the columns existed - the travel achievements then count the
 * player's whole history rather than only what they walk from today.
 *
 * Reverse geocoding is per point, not batched, so a run does far fewer cells than the elevation
 * pass. That is deliberate: on most devices this is a local lookup, but on some it goes over the
 * network, and a hundred back-to-back lookups is not something to do behind a walking player.
 */
class EnrichCellPlacesUseCase(
    private val cells: StompedHexRepository,
    private val geocoding: GeocodingRepository,
    private val resolveCenter: (String) -> Coordinate?
) {
    /**
     * Cells nothing can be learned about, skipped for the rest of the process.
     *
     * Without this, one cell the geocoder has no name for - a point out at sea - would sit at the
     * head of the queue forever and block every cell behind it.
     */
    private val unresolvable = mutableSetOf<String>()

    suspend operator fun invoke(limit: Int = DEFAULT_BATCH): Int {
        val pending = cells.cellsMissingPlace(limit, unresolvable)
        if (pending.isEmpty()) return 0

        val resolved = mutableMapOf<String, PlaceInfo>()
        val unnamed = mutableSetOf<String>()

        pending.forEach { id ->
            val center = runCatching { resolveCenter(id) }.getOrNull()
            if (center == null) {
                // No geometry means no point to ask about, and a retry cannot change that.
                unresolvable.add(id)
                return@forEach
            }

            val place = runCatching { geocoding.reverseGeocode(center.lat, center.lng) }.getOrNull()
            // Only a result that names something is worth storing: writing two nulls back over two
            // nulls would leave the cell in the queue and achieve nothing.
            if (place?.city != null || place?.countryCode != null) {
                resolved[id] = place
            } else {
                unnamed.add(id)
            }
        }

        // A batch where nothing resolved is far more likely to be the geocoder being unavailable
        // than a run of genuinely nameless ground, so those cells are left in the queue for a later
        // attempt instead of being written off. Cells only get written off once something else in
        // the same batch succeeded, which proves the geocoder was working at the time.
        if (resolved.isEmpty()) return 0

        unresolvable.addAll(unnamed)
        cells.setPlaces(resolved)
        return resolved.size
    }

    private companion object {
        /** Small: each cell is its own lookup, unlike the elevation batch. */
        const val DEFAULT_BATCH = 20
    }
}
