package com.example.domain.repository

import com.example.domain.model.StompedHex
import kotlinx.coroutines.flow.Flow

interface StompedHexRepository {
    val stompedHexes: Flow<List<StompedHex>>

    suspend fun getAll(): List<StompedHex>
    suspend fun stomp(hexAddress: String, neighborhood: String? = null)
    suspend fun stompAll(hexAddresses: List<String>, neighborhood: String? = null)

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
