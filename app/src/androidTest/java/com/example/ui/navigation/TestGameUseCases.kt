package com.example.ui.navigation

import com.example.domain.engine.FallbackHexGridEngine
import com.example.domain.model.GeoLocation
import com.example.domain.model.StompedHex
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.StepCounterRepository
import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.FillEnclosedAreasUseCase
import com.example.domain.usecase.GetGridCellsInBoundsUseCase
import com.example.domain.usecase.ObserveLocationErrorsUseCase
import com.example.domain.usecase.ObserveLocationUpdatesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObserveRegionStatsUseCase
import com.example.domain.usecase.ObserveStatsStartTimestampUseCase
import com.example.domain.usecase.ObserveStepCountUseCase
import com.example.domain.usecase.ObserveStompedHexAddressesUseCase
import com.example.domain.usecase.ObserveTotalDistanceUseCase
import com.example.domain.usecase.RecordWalkedDistanceUseCase
import com.example.domain.usecase.StartLocationTrackingUseCase
import com.example.domain.usecase.StartStepCounterUseCase
import com.example.domain.usecase.StompCellUseCase
import com.example.domain.usecase.StopLocationTrackingUseCase
import com.example.domain.usecase.UpdateActiveNeighborhoodUseCase
import com.example.domain.usecase.UpdateNicknameUseCase
import com.example.ui.map.GameUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

private class FakeStompedHexRepository : StompedHexRepository {
    private val hexes = MutableStateFlow<List<StompedHex>>(emptyList())
    override val stompedHexes: Flow<List<StompedHex>> = hexes
    override suspend fun getAll(): List<StompedHex> = hexes.value
    override suspend fun stomp(hexAddress: String, neighborhood: String?) {}
    override suspend fun stompAll(hexAddresses: List<String>, neighborhood: String?) {}
    override suspend fun unstomp(hexAddress: String) {}
    override suspend fun clearAll() {
        hexes.value = emptyList()
    }
}

private class FakeUserStatsRepository : UserStatsRepository {
    override val nickname: Flow<String> = MutableStateFlow("Tester")
    override val totalDistanceWalked: Flow<Double> = MutableStateFlow(0.0)
    override val statsStartTimestamp: Flow<Long> = MutableStateFlow(0L)
    override fun updateNickname(name: String) {}
    override fun addDistance(deltaMeters: Double) {}
    override fun resetStats() {}
}

private class FakeLocationRepository : LocationRepository {
    override val locationUpdates: Flow<GeoLocation> = emptyFlow()
    override val errors: Flow<String> = emptyFlow()
    override fun startUpdates(intervalMs: Long) {}
    override fun stopUpdates() {}
}

private class FakeGeocodingRepository : GeocodingRepository {
    override suspend fun reverseGeocodePlaceName(lat: Double, lng: Double): String? = null
}

private class FakeStepCounterRepository : StepCounterRepository {
    override val stepCount: Flow<Int> = MutableStateFlow(0)
    override fun start() {}
}

/** A fully wired GameUseCases bundle backed by in-memory fakes, for UI/navigation tests. */
fun createTestGameUseCases(): GameUseCases {
    val stompedHexRepository = FakeStompedHexRepository()
    val userStatsRepository = FakeUserStatsRepository()
    val locationRepository = FakeLocationRepository()
    val geocodingRepository = FakeGeocodingRepository()
    val stepCounterRepository = FakeStepCounterRepository()
    val engine = FallbackHexGridEngine()
    val fillEnclosedAreas = FillEnclosedAreasUseCase(stompedHexRepository, engine)

    return GameUseCases(
        observeStompedHexAddresses = ObserveStompedHexAddressesUseCase(stompedHexRepository),
        observeRegionStats = ObserveRegionStatsUseCase(stompedHexRepository),
        stompCell = StompCellUseCase(stompedHexRepository, engine, fillEnclosedAreas),
        getGridCellsInBounds = GetGridCellsInBoundsUseCase(engine),
        clearProgress = ClearProgressUseCase(stompedHexRepository, userStatsRepository),
        updateNickname = UpdateNicknameUseCase(userStatsRepository),
        observeNickname = ObserveNicknameUseCase(userStatsRepository),
        observeTotalDistance = ObserveTotalDistanceUseCase(userStatsRepository),
        observeStatsStartTimestamp = ObserveStatsStartTimestampUseCase(userStatsRepository),
        recordWalkedDistance = RecordWalkedDistanceUseCase(userStatsRepository),
        observeLocationUpdates = ObserveLocationUpdatesUseCase(locationRepository),
        observeLocationErrors = ObserveLocationErrorsUseCase(locationRepository),
        startLocationTracking = StartLocationTrackingUseCase(locationRepository),
        stopLocationTracking = StopLocationTrackingUseCase(locationRepository),
        updateActiveNeighborhood = UpdateActiveNeighborhoodUseCase(geocodingRepository, engine),
        observeStepCount = ObserveStepCountUseCase(stepCounterRepository),
        startStepCounter = StartStepCounterUseCase(stepCounterRepository)
    )
}
