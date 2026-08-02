package com.example.data.repository

import com.example.data.local.dao.WalkSessionDao
import com.example.data.local.entity.WalkSessionEntity
import com.example.domain.model.WalkSession
import com.example.domain.repository.WalkSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the open walk's row up to date as the player moves.
 *
 * The running total is held in memory and flushed on every accepted fix, so the row on disk is never
 * more than one fix behind: if the process is killed mid-walk - which is normal for a foreground
 * service that has been running for an hour - the walk is still there, a few metres short, instead
 * of missing entirely.
 */
class WalkSessionRepositoryImpl(private val dao: WalkSessionDao) : WalkSessionRepository {

    private val mutex = Mutex()
    private var openSessionId: Long? = null
    private var openDistanceMeters: Double = 0.0

    override val sessions: Flow<List<WalkSession>> =
        dao.observeAll().map { rows ->
            rows.map { WalkSession(it.id, it.startedAt, it.endedAt, it.distanceMeters) }
        }

    override suspend fun startSession(startedAt: Long) = mutex.withLock {
        // Starting twice without stopping (a restart while tracking is already running) closes the
        // first one where it stood rather than merging two walks into one.
        openSessionId = dao.insert(
            WalkSessionEntity(startedAt = startedAt, endedAt = startedAt, distanceMeters = 0.0)
        )
        openDistanceMeters = 0.0
    }

    override suspend fun addDistance(meters: Double, at: Long) = mutex.withLock {
        val id = openSessionId ?: return@withLock
        openDistanceMeters += meters
        dao.update(id, openDistanceMeters, at)
    }

    override suspend fun endSession() = mutex.withLock {
        openSessionId = null
        openDistanceMeters = 0.0
    }

    override suspend fun clearAll() = mutex.withLock {
        openSessionId = null
        openDistanceMeters = 0.0
        dao.clearAll()
    }
}
