package com.example.domain.repository

import com.example.domain.model.CellContext
import com.example.domain.model.PlaceInfo
import com.example.domain.model.StompedHex
import kotlinx.coroutines.flow.Flow

interface StompedHexRepository {
    val stompedHexes: Flow<List<StompedHex>>

    suspend fun getAll(): List<StompedHex>
    suspend fun stomp(hexAddress: String, neighborhood: String? = null, context: CellContext? = null)
    suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String? = null,
        context: CellContext? = null
    )

    /** Cell ids with no elevation recorded yet, oldest first, for the batch enrichment pass. */
    suspend fun cellsMissingElevation(limit: Int): List<String>

    /** Stores an elevation looked up after the fact. */
    suspend fun setElevations(elevations: Map<String, Double>)

    /**
     * Cell ids with no place recorded yet, oldest first, ignoring any in [skip] - see the DAO for
     * why the caller gets to exclude ids.
     */
    suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String>

    /** Stores places looked up after the fact. */
    suspend fun setPlaces(places: Map<String, PlaceInfo>)

    /**
     * Records cells as *partially* explored - the vision ring around a cell the player lingered in.
     *
     * Never lowers a cell that is already more explored than [level], so walking through an area and
     * later glimpsing it from a neighbouring cell cannot re-fog it.
     */
    suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String? = null
    )
    suspend fun unstomp(hexAddress: String)
    suspend fun clearAll()
}
