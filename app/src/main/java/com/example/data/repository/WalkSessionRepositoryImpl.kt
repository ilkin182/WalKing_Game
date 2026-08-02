package com.example.data.repository

import com.example.data.local.dao.RoutePointDao
import com.example.data.local.dao.WalkSessionDao
import com.example.data.local.entity.RoutePointEntity
import com.example.data.local.entity.WalkSessionEntity
import com.example.domain.engine.GeoPath
import com.example.domain.model.Coordinate
import com.example.domain.model.RoutePoint
import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.repository.WalkSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the open walk's row and its path up to date as the player moves.
 *
 * The running total is held in memory and flushed on every accepted fix, so the row on disk is never
 * more than one fix behind: if the process is killed mid-walk - which is normal for a foreground
 * service that has been running for an hour - the walk is still there, a few metres short, instead
 * of missing entirely.
 */
class WalkSessionRepositoryImpl(
    private val dao: WalkSessionDao,
    private val routePointDao: RoutePointDao
) : WalkSessionRepository {

    private val mutex = Mutex()
    private var openSessionId: Long? = null
    private var openDistanceMeters: Double = 0.0
    private var lastRecordedPoint: Coordinate? = null

    override val sessions: Flow<List<WalkSession>> =
        dao.observeAll().map { rows ->
            rows.map { WalkSession(it.id, it.startedAt, it.endedAt, it.distanceMeters) }
        }

    override val routes: Flow<List<WalkRoute>> =
        routePointDao.observeAll().map { rows ->
            // The query already orders by session and then by time, so grouping preserves the order
            // the points were walked in - which is the only thing that makes them a route.
            rows.groupBy { it.sessionId }
                .map { (sessionId, points) ->
                    WalkRoute(sessionId, points.map { RoutePoint(it.lat, it.lng, it.at) })
                }
        }

    override suspend fun startSession(startedAt: Long) = mutex.withLock {
        // Starting twice without stopping (a restart while tracking is already running) closes the
        // first one where it stood rather than merging two walks into one.
        openSessionId = dao.insert(
            WalkSessionEntity(startedAt = startedAt, endedAt = startedAt, distanceMeters = 0.0)
        )
        openDistanceMeters = 0.0
        lastRecordedPoint = null
    }

    override suspend fun addDistance(meters: Double, at: Long) = mutex.withLock {
        val id = openSessionId ?: return@withLock
        openDistanceMeters += meters
        dao.update(id, openDistanceMeters, at)
    }

    override suspend fun recordPoint(lat: Double, lng: Double, at: Long) = mutex.withLock {
        val id = openSessionId ?: return@withLock
        val point = Coordinate(lat, lng)

        // A player standing at a crossing keeps producing fixes in the same spot. Storing them all
        // would bury the shape of the walk under hundreds of identical points and, worse, read as a
        // pile of zero-length segments the turn count cannot make sense of.
        val previous = lastRecordedPoint
        if (previous != null && GeoPath.distanceMeters(previous, point) < MIN_POINT_SPACING_METERS) {
            return@withLock
        }

        lastRecordedPoint = point
        routePointDao.insert(RoutePointEntity(sessionId = id, lat = lat, lng = lng, at = at))
    }

    override suspend fun endSession() = mutex.withLock {
        openSessionId = null
        openDistanceMeters = 0.0
        lastRecordedPoint = null
    }

    override suspend fun clearAll() = mutex.withLock {
        openSessionId = null
        openDistanceMeters = 0.0
        lastRecordedPoint = null
        routePointDao.clearAll()
        dao.clearAll()
    }

    private companion object {
        /**
         * How far the player must move before another point is written down.
         *
         * Roughly a cell across: close enough that a corner is still a corner and a curve still
         * curves, far enough that an hour's walk is a few hundred rows rather than a few thousand.
         */
        const val MIN_POINT_SPACING_METERS = 20.0
    }
}
