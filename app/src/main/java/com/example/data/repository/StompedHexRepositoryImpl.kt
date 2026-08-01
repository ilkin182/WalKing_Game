package com.example.data.repository

import com.example.data.local.dao.StompedHexDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.CellContext
import com.example.domain.model.PlaceInfo
import com.example.domain.model.StompedHex
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StompedHexRepositoryImpl(private val dao: StompedHexDao) : StompedHexRepository {
    override val stompedHexes: Flow<List<StompedHex>> =
        dao.getAllStompedHexesFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<StompedHex> =
        dao.getAllStompedHexes().map { it.toDomain() }

    override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {
        dao.insertHex(
            StompedHex(
                hexAddress = hexAddress,
                neighborhood = neighborhood,
                timestamp = System.currentTimeMillis(),
                context = context
            ).toEntity()
        )
    }

    override suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String?,
        context: CellContext?
    ) {
        val now = System.currentTimeMillis()
        dao.insertHexes(
            hexAddresses.map {
                StompedHex(
                    hexAddress = it,
                    neighborhood = neighborhood,
                    timestamp = now,
                    context = context
                ).toEntity()
            }
        )
    }

    override suspend fun cellsMissingElevation(limit: Int): List<String> =
        dao.cellsMissingElevation(limit)

    override suspend fun setElevations(elevations: Map<String, Double>) {
        elevations.forEach { (cellId, meters) -> dao.setElevation(cellId, meters) }
    }

    override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> =
        // SQLite has no `NOT IN ()`, so an empty exclusion list is sent as one id nothing can match.
        dao.cellsMissingPlace(limit, skip.ifEmpty { setOf("") }.toList())

    override suspend fun setPlaces(places: Map<String, PlaceInfo>) {
        places.forEach { (cellId, place) -> dao.setPlace(cellId, place.city, place.countryCode) }
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
