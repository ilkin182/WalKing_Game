package com.example.domain.usecase

import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.repository.WalkSessionRepository
import kotlinx.coroutines.flow.Flow

/** Opens a walk when tracking starts. */
class StartWalkSessionUseCase(private val repository: WalkSessionRepository) {
    suspend operator fun invoke(startedAt: Long = System.currentTimeMillis()) =
        repository.startSession(startedAt)
}

/** Adds metres already accepted by the odometer to the walk in progress. */
class AddWalkDistanceUseCase(private val repository: WalkSessionRepository) {
    suspend operator fun invoke(meters: Double, at: Long = System.currentTimeMillis()) {
        if (meters <= 0.0) return
        repository.addDistance(meters, at)
    }
}

/** Adds a position to the path of the walk in progress. */
class RecordRoutePointUseCase(private val repository: WalkSessionRepository) {
    suspend operator fun invoke(lat: Double, lng: Double, at: Long = System.currentTimeMillis()) {
        repository.recordPoint(lat, lng, at)
    }
}

/** The path of every walk, for the route-shape achievements. */
class ObserveWalkRoutesUseCase(private val repository: WalkSessionRepository) {
    operator fun invoke(): Flow<List<WalkRoute>> = repository.routes
}

/** Closes the open walk when tracking stops. */
class EndWalkSessionUseCase(private val repository: WalkSessionRepository) {
    suspend operator fun invoke() = repository.endSession()
}

/** Every walk the player has taken, for the per-walk and per-day distance achievements. */
class ObserveWalkSessionsUseCase(private val repository: WalkSessionRepository) {
    operator fun invoke(): Flow<List<WalkSession>> = repository.sessions
}
