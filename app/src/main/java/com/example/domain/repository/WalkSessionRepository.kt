package com.example.domain.repository

import com.example.domain.model.WalkSession
import kotlinx.coroutines.flow.Flow

/**
 * The player's walks, kept apart from one another.
 *
 * A session runs from the moment tracking starts to the moment it stops. [addDistance] extends the
 * open one; calling it with no session open is a no-op rather than an error, because a stray
 * location update can arrive between stopping and the coroutine actually cancelling.
 */
interface WalkSessionRepository {
    val sessions: Flow<List<WalkSession>>

    suspend fun startSession(startedAt: Long)
    suspend fun addDistance(meters: Double, at: Long)
    suspend fun endSession()
    suspend fun clearAll()
}
