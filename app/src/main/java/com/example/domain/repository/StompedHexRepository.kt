package com.example.domain.repository

import com.example.domain.model.StompedHex
import kotlinx.coroutines.flow.Flow

interface StompedHexRepository {
    val stompedHexes: Flow<List<StompedHex>>

    suspend fun getAll(): List<StompedHex>
    suspend fun stomp(hexAddress: String, neighborhood: String? = null)
    suspend fun stompAll(hexAddresses: List<String>, neighborhood: String? = null)
    suspend fun unstomp(hexAddress: String)
    suspend fun clearAll()
}
