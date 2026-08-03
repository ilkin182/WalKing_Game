package com.example.domain.usecase

import com.example.domain.repository.PoiRepository
import com.example.domain.repository.StompedHexRepository

/**
 * Looks up the extent of the towns the player has walked in, one at a time.
 *
 * The two city achievements - fifty cells in the centre, and reaching the edge - need to know where
 * a town begins and ends, and the cells only carry its name. This turns the names
 * [EnrichCellPlacesUseCase] recorded into boundaries.
 *
 * The queue is naturally tiny: a name is looked up once and the answer is kept, including for names
 * that could not be placed, so this does nothing at all on all but a handful of runs in a player's
 * lifetime. It is a separate pass from [EnrichPoiTilesUseCase] rather than a step inside it because
 * the two drain at completely different rates - one town against thousands of tiles - and folding
 * them together would put the quick one behind the slow one forever.
 */
class EnrichCityBoundsUseCase(
    private val cells: StompedHexRepository,
    private val pois: PoiRepository
) {
    suspend operator fun invoke(limit: Int = DEFAULT_BATCH): Int {
        val known = pois.knownCities()

        // Oldest first, so the town the player started in - the one "reaching the edge" is most
        // likely to be about - is placed before anywhere they have since travelled to.
        val pending = cells.getAll()
            .sortedBy { it.timestamp }
            .mapNotNull { cell ->
                val city = cell.context?.city?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                city to cell.context?.countryCode
            }
            .distinctBy { it.first }
            .filterNot { (city, _) -> city in known }
            .take(limit)

        return pending.count { (city, country) -> pois.fetchCityBounds(city, country) }
    }

    private companion object {
        const val DEFAULT_BATCH = 1
    }
}
