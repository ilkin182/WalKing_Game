package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.remote.NetworkModule
import com.example.data.repository.ElevationRepositoryImpl
import com.example.data.repository.GeocodingRepositoryImpl
import com.example.data.repository.LocalAuthRepository
import com.example.data.repository.LocalLeaderboardRepository
import com.example.data.repository.LocationRepositoryImpl
import com.example.data.repository.PoiRepositoryImpl
import com.example.data.repository.StepCounterRepositoryImpl
import com.example.data.repository.StompedHexRepositoryImpl
import com.example.data.repository.UserStatsRepositoryImpl
import com.example.data.repository.WalkSessionRepositoryImpl
import com.example.data.repository.WeatherRepositoryImpl
import com.example.domain.engine.FallbackHexGridEngine
import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ElevationRepository
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.LeaderboardRepository
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
import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.GetGridCellsInBoundsUseCase
import com.example.domain.usecase.GetWeatherSnapshotUseCase
import com.example.domain.usecase.GetWeatherUseCase
import com.example.domain.usecase.GridCellLookupUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.MarkVisionRingUseCase
import com.example.domain.usecase.ObserveCityBoundsUseCase
import com.example.domain.usecase.ObserveClosedLoopsUseCase
import com.example.domain.usecase.ObserveCountryUseCase
import com.example.domain.usecase.ObserveLeaderboardUseCase
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
import com.example.domain.usecase.PublishLeaderboardEntryUseCase
import com.example.domain.usecase.RecordRoutePointUseCase
import com.example.domain.usecase.RecordWalkedDistanceUseCase
import com.example.domain.usecase.ResolvePlaceUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase
import com.example.domain.usecase.StartLocationTrackingUseCase
import com.example.domain.usecase.StartStepCounterUseCase
import com.example.domain.usecase.StartWalkSessionUseCase
import com.example.domain.usecase.StompCellUseCase
import com.example.domain.usecase.StopLocationTrackingUseCase
import com.example.domain.usecase.UpdateActiveNeighborhoodUseCase
import com.example.domain.usecase.UpdateCountryUseCase
import com.example.domain.usecase.UpdateNicknameUseCase
import com.example.ui.auth.AuthUseCases
import com.example.ui.map.GameUseCases

/**
 * Minimal hand-rolled service locator. The app has no Hilt/Dagger dependency, and a single
 * screen's worth of wiring, so a small manual container keeps the dependency graph explicit
 * without adding a new build-time dependency (annotation processor, extra APK size) for it.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { AppDatabase.getDatabase(appContext) }

    private val hexGridEngine: HexGridEngine by lazy { FallbackHexGridEngine() }

    private val stompedHexRepository: StompedHexRepository by lazy {
        StompedHexRepositoryImpl(database.stompedHexDao())
    }
    private val locationRepository: LocationRepository by lazy { LocationRepositoryImpl(appContext) }
    private val stepCounterRepository: StepCounterRepository by lazy { StepCounterRepositoryImpl(appContext) }
    private val geocodingRepository: GeocodingRepository by lazy { GeocodingRepositoryImpl(appContext) }
    private val userStatsRepository: UserStatsRepository by lazy { UserStatsRepositoryImpl(appContext) }
    private val weatherRepository: WeatherRepository by lazy {
        WeatherRepositoryImpl(NetworkModule.openMeteoApi)
    }
    private val elevationRepository: ElevationRepository by lazy {
        ElevationRepositoryImpl(NetworkModule.elevationApi)
    }
    private val walkSessionRepository: WalkSessionRepository by lazy {
        WalkSessionRepositoryImpl(database.walkSessionDao(), database.routePointDao())
    }
    private val poiRepository: PoiRepository by lazy {
        PoiRepositoryImpl(database.poiDao(), NetworkModule.overpassApi, NetworkModule.nominatimApi)
    }

    // Country standings, on-device for now: the player's real figures ranked against a fixed
    // benchmark field, because nothing here can see another player's phone. Swap this for a
    // networked implementation of the same LeaderboardRepository interface once there is a backend
    // and every layer above keeps working unchanged. See LocalLeaderboardRepository.
    private val leaderboardRepository: LeaderboardRepository by lazy { LocalLeaderboardRepository() }

    // No real google-services.json is configured yet, so auth runs entirely against local
    // SharedPreferences instead of Firebase. Once a real google-services.json is added, swap this
    // for `AuthRepositoryImpl(FirebaseAuth.getInstance())` -- same AuthRepository interface, so
    // nothing downstream (UseCases, AuthViewModel, screens) needs to change.
    private val authRepository: AuthRepository by lazy { LocalAuthRepository(appContext) }

    val authUseCases: AuthUseCases by lazy {
        AuthUseCases(
            login = LoginUseCase(authRepository),
            signUp = SignUpUseCase(authRepository, userStatsRepository),
            logout = LogoutUseCase(authRepository),
            getCurrentUser = GetCurrentUserUseCase(authRepository),
            sendPasswordReset = SendPasswordResetUseCase(authRepository)
        )
    }

    val gameUseCases: GameUseCases by lazy {
        val fillEnclosedAreas = FillEnclosedAreasUseCase(
            repository = stompedHexRepository,
            hexGridEngine = hexGridEngine,
            onAreasEnclosed = { loops -> userStatsRepository.recordClosedLoops(loops) }
        )
        val gridCellLookup = GridCellLookupUseCase(hexGridEngine)
        GameUseCases(
            observeExploredCells = ObserveExploredCellsUseCase(stompedHexRepository),
            observeRegionStats = ObserveRegionStatsUseCase(stompedHexRepository),
            stompCell = StompCellUseCase(stompedHexRepository, hexGridEngine, fillEnclosedAreas),
            fillEnclosedAreas = fillEnclosedAreas,
            markVisionRing = MarkVisionRingUseCase(stompedHexRepository, hexGridEngine),
            gridCellLookup = gridCellLookup,
            getGridCellsInBounds = GetGridCellsInBoundsUseCase(hexGridEngine),
            clearProgress = ClearProgressUseCase(
                stompedHexRepository,
                userStatsRepository,
                walkSessionRepository
            ),
            updateNickname = UpdateNicknameUseCase(userStatsRepository),
            observeNickname = ObserveNicknameUseCase(userStatsRepository),
            observeCountry = ObserveCountryUseCase(userStatsRepository),
            updateCountry = UpdateCountryUseCase(userStatsRepository),
            observeLeaderboard = ObserveLeaderboardUseCase(leaderboardRepository),
            publishLeaderboardEntry = PublishLeaderboardEntryUseCase(leaderboardRepository),
            observeTotalDistance = ObserveTotalDistanceUseCase(userStatsRepository),
            observeStatsStartTimestamp = ObserveStatsStartTimestampUseCase(userStatsRepository),
            observeClosedLoops = ObserveClosedLoopsUseCase(userStatsRepository),
            recordWalkedDistance = RecordWalkedDistanceUseCase(userStatsRepository),
            observeLocationUpdates = ObserveLocationUpdatesUseCase(locationRepository),
            observeLocationErrors = ObserveLocationErrorsUseCase(locationRepository),
            startLocationTracking = StartLocationTrackingUseCase(locationRepository),
            stopLocationTracking = StopLocationTrackingUseCase(locationRepository),
            updateActiveNeighborhood = UpdateActiveNeighborhoodUseCase(hexGridEngine),
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
                elevations = elevationRepository,
                resolveCenter = { cellId -> gridCellLookup.centerOf(cellId) }
            ),
            enrichCellPlaces = EnrichCellPlacesUseCase(
                cells = stompedHexRepository,
                geocoding = geocodingRepository,
                resolveCenter = { cellId -> gridCellLookup.centerOf(cellId) }
            ),
            enrichPoiTiles = EnrichPoiTilesUseCase(
                cells = stompedHexRepository,
                pois = poiRepository,
                resolveCenter = { cellId -> gridCellLookup.centerOf(cellId) }
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
}
