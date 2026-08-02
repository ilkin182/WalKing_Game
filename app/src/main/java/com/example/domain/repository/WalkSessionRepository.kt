package com.example.domain.repository

import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import kotlinx.coroutines.flow.Flow

/**
 * The player's walks, kept apart from one another, and the line each of them traced.
 *
 * A session runs from the moment tracking starts to the moment it stops. [addDistance] and
 * [recordPoint] extend the open one; calling either with no session open is a no-op rather than an
 * error, because a stray location update can arrive between stopping and the coroutine actually
 * cancelling.
 */
interface WalkSessionRepository {
    val sessions: Flow<List<WalkSession>>

    /** The path of every walk, for the route-shape achievements. */
    val routes: Flow<List<WalkRoute>>

    suspend fun startSession(startedAt: Long)
    suspend fun addDistance(meters: Double, at: Long)

    /**
     * Adds a position to the open walk's path, if it is far enough from the last one recorded.
     *
     * The thinning is the repository's job rather than the caller's: it is what keeps the table to a
     * few hundred rows a walk instead of a few thousand, and it needs the last point kept, which
     * only this side has.
     */
    suspend fun recordPoint(lat: Double, lng: Double, at: Long)
    suspend fun endSession()
    suspend fun clearAll()
}
