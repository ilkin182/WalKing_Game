package com.example.data.repository

import com.example.data.local.dao.StompedHexDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.StompedHex
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StompedHexRepositoryImpl(private val dao: StompedHexDao) : StompedHexRepository {
    override val stompedHexes: Flow<List<StompedHex>> =
        dao.getAllStompedHexesFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<StompedHex> =
        dao.getAllStompedHexes().map { it.toDomain() }

    override suspend fun stomp(hexAddress: String, neighborhood: String?) {
        dao.insertHex(StompedHex(hexAddress, neighborhood, System.currentTimeMillis()).toEntity())
    }

    override suspend fun stompAll(hexAddresses: List<String>, neighborhood: String?) {
        val now = System.currentTimeMillis()
        dao.insertHexes(hexAddresses.map { StompedHex(it, neighborhood, now).toEntity() })
    }

    override suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String?
    ) {
        if (hexAddresses.isEmpty()) return
        val now = System.currentTimeMillis()

        // Insert first (ignoring cells that already exist), then raise the ones that exist but are
        // less explored. The UPDATE's `explorationLevel < :level` guard is what makes the pair safe
        // to run in either order and idempotent when nothing has changed.
        dao.insertHexesIfAbsent(
            hexAddresses.map { StompedHex(it, neighborhood, now, level).toEntity() }
        )
        dao.raiseExplorationLevel(hexAddresses, level, now)
    }

    override suspend fun unstomp(hexAddress: String) {
        dao.deleteHex(hexAddress)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}
