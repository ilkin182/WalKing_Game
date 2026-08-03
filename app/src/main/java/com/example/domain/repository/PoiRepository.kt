package com.example.domain.repository

import com.example.domain.model.CityBounds
import com.example.domain.model.PointOfInterest
import kotlinx.coroutines.flow.Flow

/**
 * What is on the ground where the player has walked: parks, monuments, metro, bridges, squares, the
 * coastline, and the extent of the towns themselves.
 *
 * Everything here reads from a local cache. The two `fetch` calls are the only ones that touch the
 * network, they are the only ones that are slow, and they are meant to be called from a background
 * pass rather than from anything the player is waiting on - see the implementation for why asking
 * the upstream services more often than this is not an option.
 */
interface PoiRepository {

    /** Every place cached so far. Changes when a background fetch brings a new tile in. */
    val pois: Flow<List<PointOfInterest>>

    /** Every town looked up so far, including the ones that could not be found. */
    val cityBounds: Flow<List<CityBounds>>

    /** Which of [tileKeys] have already been asked about and are still current. */
    suspend fun cachedTiles(tileKeys: List<String>): Set<String>

    /**
     * Asks about one tile and caches the answer.
     *
     * @return true when the tile was fetched and stored - including when it turned out to contain
     * nothing, which is a result worth remembering. False when the request could not be made or
     * failed, leaving the tile unmarked so a later pass tries again.
     */
    suspend fun fetchTile(tileKey: String): Boolean

    /** Town names already looked up, whether or not they were found. */
    suspend fun knownCities(): Set<String>

    /** Looks a town up and caches its extent, or the fact that it has none. */
    suspend fun fetchCityBounds(city: String, countryCode: String?): Boolean

    /** Drops the whole cache. Not part of clearing the player's progress - see ClearProgressUseCase. */
    suspend fun clearAll()
}
