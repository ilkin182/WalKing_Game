package com.example.ui.navigation

import com.example.domain.engine.FallbackHexGridEngine
import com.example.domain.model.CellContext
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoLocation
import com.example.domain.model.PlaceInfo
import com.example.domain.model.PointOfInterest
import com.example.domain.model.StompedHex
import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.model.Weather
import com.example.domain.repository.ElevationRepository
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.PoiRepository
import com.example.domain.repository.StepCounterRepository
import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.repository.WalkSessionRepository
import com.example.domain.repository.WeatherRepository
import com.example.domain.usecase.AddWalkDistanceUseCase
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.EndWalkSessionUseCase
import com.example.domain.usecase.EnrichCellElevationsUseCase
import com.example.domain.usecase.EnrichCellPlacesUseCase
import com.example.domain.usecase.EnrichCityBoundsUseCase
import com.example.domain.usecase.EnrichPoiTilesUseCase
import com.example.domain.usecase.FillEnclosedAreasUseCase
import com.example.domain.usecase.GetGridCellsInBoundsUseCase
import com.example.domain.usecase.GetWeatherSnapshotUseCase
import com.example.domain.usecase.GetWeatherUseCase
import com.example.domain.usecase.GridCellLookupUseCase
import com.example.domain.usecase.MarkVisionRingUseCase
import com.example.domain.usecase.ObserveCityBoundsUseCase
import com.example.domain.usecase.ObserveClosedLoopsUseCase
import com.example.domain.usecase.ObserveExploredCellsUseCase
import com.example.domain.usecase.ObserveLocationErrorsUseCase
import com.example.domain.usecase.ObserveLocationUpdatesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObservePoisUseCase
import com.example.domain.usecase.ObserveRegionStatsUseCase
import com.example.domain.usecase.ObserveStatsStartTimestampUseCase
import com.example.domain.usecase.ObserveStepCountUseCase
import com.example.domain.usecase.ObserveTotalDistanceUseCase
import com.example.domain.usecase.ObserveWalkRoutesUseCase
import com.example.domain.usecase.ObserveWalkSessionsUseCase
import com.example.domain.usecase.RecordRoutePointUseCase
import com.example.domain.usecase.RecordWalkedDistanceUseCase
import com.example.domain.usecase.ResolvePlaceUseCase
import com.example.domain.usecase.StartLocationTrackingUseCase
import com.example.domain.usecase.StartStepCounterUseCase
import com.example.domain.usecase.StartWalkSessionUseCase
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
    override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {}
    override suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String?,
        context: CellContext?
    ) {}
    override suspend fun cellsMissingElevation(limit: Int): List<String> = emptyList()
    override suspend fun setElevations(elevations: Map<String, Double>) {}
    override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> = emptyList()
    override suspend fun setPlaces(places: Map<String, PlaceInfo>) {}
    override suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String?
    ) {}
    override suspend fun unstomp(hexAddress: String) {}
    override suspend fun clearAll() {
        hexes.value = emptyList()
    }
}

private class FakeUserStatsRepository : UserStatsRepository {
    override val nickname: Flow<String> = MutableStateFlow("Tester")
    override val totalDistanceWalked: Flow<Double> = MutableStateFlow(0.0)
    override val statsStartTimestamp: Flow<Long> = MutableStateFlow(0L)
    override val closedLoops: Flow<Int> = MutableStateFlow(0)
    override fun updateNickname(name: String) {}
    override fun addDistance(deltaMeters: Double) {}
    override fun recordClosedLoops(count: Int) {}
    override fun resetStats() {}
}

private class FakeLocationRepository : LocationRepository {
    override val locationUpdates: Flow<GeoLocation> = emptyFlow()
    override val errors: Flow<String> = emptyFlow()
    override fun startUpdates(intervalMs: Long) {}
    override fun stopUpdates() {}
}

private class FakeGeocodingRepository : GeocodingRepository {
    override suspend fun reverseGeocode(lat: Double, lng: Double): PlaceInfo? = null
}

private class FakeStepCounterRepository : StepCounterRepository {
    override val stepCount: Flow<Int> = MutableStateFlow(0)
    override fun start() {}
}

/** No network in UI tests: the profile's weather card renders its "unavailable" state instead. */
private class FakeWeatherRepository : WeatherRepository {
    override suspend fun currentWeather(lat: Double, lng: Double): Weather? = null
    override fun lastKnown(): Weather? = null
}

/** No walks recorded in UI tests; the distance and route-shape achievements stay at zero. */
private class FakeWalkSessionRepository : WalkSessionRepository {
    override val sessions: Flow<List<WalkSession>> = MutableStateFlow(emptyList())
    override val routes: Flow<List<WalkRoute>> = MutableStateFlow(emptyList())
    override suspend fun startSession(startedAt: Long) {}
    override suspend fun addDistance(meters: Double, at: Long) {}
    override suspend fun recordPoint(lat: Double, lng: Double, at: Long) {}
    override suspend fun endSession() {}
    override suspend fun clearAll() {}
}

/** No network in UI tests, so nothing ever gets an elevation. */
private class FakeElevationRepository : ElevationRepository {
    override suspend fun elevations(points: List<Coordinate>): List<Double> = emptyList()
}

/**
 * No network in UI tests, so no places are ever cached and the geography achievements stay at zero.
 *
 * `cachedTiles` returns every key it is asked about rather than none: that is what says "nothing
 * left to fetch", and the alternative would have the enrichment pass calling `fetchTile` on a
 * thousand squares in a loop behind every UI test.
 */
private class FakePoiRepository : PoiRepository {
    override val pois: Flow<List<PointOfInterest>> = MutableStateFlow(emptyList())
    override val cityBounds: Flow<List<CityBounds>> = MutableStateFlow(emptyList())
    override suspend fun cachedTiles(tileKeys: List<String>): Set<String> = tileKeys.toSet()
    override suspend fun fetchTile(tileKey: String): Boolean = false
    override suspend fun knownCities(): Set<String> = emptySet()
    override suspend fun fetchCityBounds(city: String, countryCode: String?): Boolean = false
    override suspend fun clearAll() {}
}

/** A fully wired GameUseCases bundle backed by in-memory fakes, for UI/navigation tests. */
fun createTestGameUseCases(): GameUseCases {
    val stompedHexRepository = FakeStompedHexRepository()
    val userStatsRepository = FakeUserStatsRepository()
    val locationRepository = FakeLocationRepository()
    val geocodingRepository = FakeGeocodingRepository()
    val stepCounterRepository = FakeStepCounterRepository()
    val weatherRepository = FakeWeatherRepository()
    val walkSessionRepository = FakeWalkSessionRepository()
    val poiRepository = FakePoiRepository()
    val engine = FallbackHexGridEngine()
    val fillEnclosedAreas = FillEnclosedAreasUseCase(stompedHexRepository, engine)

    return GameUseCases(
        observeExploredCells = ObserveExploredCellsUseCase(stompedHexRepository),
        observeRegionStats = ObserveRegionStatsUseCase(stompedHexRepository),
        stompCell = StompCellUseCase(stompedHexRepository, engine, fillEnclosedAreas),
        markVisionRing = MarkVisionRingUseCase(stompedHexRepository, engine),
        gridCellLookup = GridCellLookupUseCase(engine),
        getGridCellsInBounds = GetGridCellsInBoundsUseCase(engine),
        clearProgress = ClearProgressUseCase(
            stompedHexRepository,
            userStatsRepository,
            walkSessionRepository
        ),
        updateNickname = UpdateNicknameUseCase(userStatsRepository),
        observeNickname = ObserveNicknameUseCase(userStatsRepository),
        observeTotalDistance = ObserveTotalDistanceUseCase(userStatsRepository),
        observeStatsStartTimestamp = ObserveStatsStartTimestampUseCase(userStatsRepository),
        observeClosedLoops = ObserveClosedLoopsUseCase(userStatsRepository),
        recordWalkedDistance = RecordWalkedDistanceUseCase(userStatsRepository),
        observeLocationUpdates = ObserveLocationUpdatesUseCase(locationRepository),
        observeLocationErrors = ObserveLocationErrorsUseCase(locationRepository),
        startLocationTracking = StartLocationTrackingUseCase(locationRepository),
        stopLocationTracking = StopLocationTrackingUseCase(locationRepository),
        updateActiveNeighborhood = UpdateActiveNeighborhoodUseCase(engine),
        observeStepCount = ObserveStepCountUseCase(stepCounterRepository),
        startStepCounter = StartStepCounterUseCase(stepCounterRepository),
        getWeather = GetWeatherUseCase(weatherRepository),
        resolvePlace = ResolvePlaceUseCase(geocodingRepository),
        startWalkSession = StartWalkSessionUseCase(walkSessionRepository),
        endWalkSession = EndWalkSessionUseCase(walkSessionRepository),
        observeWalkSessions = ObserveWalkSessionsUseCase(walkSessionRepository),
        observeWalkRoutes = ObserveWalkRoutesUseCase(walkSessionRepository),
        addWalkDistance = AddWalkDistanceUseCase(walkSessionRepository),
        recordRoutePoint = RecordRoutePointUseCase(walkSessionRepository),
        enrichCellElevations = EnrichCellElevationsUseCase(
            cells = stompedHexRepository,
            elevations = FakeElevationRepository(),
            resolveCenter = { null }
        ),
        enrichCellPlaces = EnrichCellPlacesUseCase(
            cells = stompedHexRepository,
            geocoding = geocodingRepository,
            resolveCenter = { null }
        ),
        enrichPoiTiles = EnrichPoiTilesUseCase(
            cells = stompedHexRepository,
            pois = poiRepository,
            resolveCenter = { null }
        ),
        enrichCityBounds = EnrichCityBoundsUseCase(
            cells = stompedHexRepository,
            pois = poiRepository
        ),
        observePois = ObservePoisUseCase(poiRepository),
        observeCityBounds = ObserveCityBoundsUseCase(poiRepository),
        weatherSnapshot = GetWeatherSnapshotUseCase(weatherRepository)
    )
}
