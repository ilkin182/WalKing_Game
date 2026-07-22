package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.GeocodingRepositoryImpl
import com.example.data.repository.LocalAuthRepository
import com.example.data.repository.LocationRepositoryImpl
import com.example.data.repository.StepCounterRepositoryImpl
import com.example.data.repository.StompedHexRepositoryImpl
import com.example.data.repository.UserStatsRepositoryImpl
import com.example.domain.engine.FallbackHexGridEngine
import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.StepCounterRepository
import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.FillEnclosedAreasUseCase
import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.GetGridCellsAroundUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.ObserveLocationErrorsUseCase
import com.example.domain.usecase.ObserveLocationUpdatesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObserveRegionStatsUseCase
import com.example.domain.usecase.ObserveStatsStartTimestampUseCase
import com.example.domain.usecase.ObserveStepCountUseCase
import com.example.domain.usecase.ObserveStompedHexAddressesUseCase
import com.example.domain.usecase.ObserveTotalDistanceUseCase
import com.example.domain.usecase.RecordWalkedDistanceUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase
import com.example.domain.usecase.StartLocationTrackingUseCase
import com.example.domain.usecase.StartStepCounterUseCase
import com.example.domain.usecase.StompCellUseCase
import com.example.domain.usecase.StopLocationTrackingUseCase
import com.example.domain.usecase.UpdateActiveNeighborhoodUseCase
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

    // No real google-services.json is configured yet, so auth runs entirely against local
    // SharedPreferences instead of Firebase. Once a real google-services.json is added, swap this
    // for `AuthRepositoryImpl(FirebaseAuth.getInstance())` -- same AuthRepository interface, so
    // nothing downstream (UseCases, AuthViewModel, screens) needs to change.
    private val authRepository: AuthRepository by lazy { LocalAuthRepository(appContext) }

    val authUseCases: AuthUseCases by lazy {
        AuthUseCases(
            login = LoginUseCase(authRepository),
            signUp = SignUpUseCase(authRepository),
            logout = LogoutUseCase(authRepository),
            getCurrentUser = GetCurrentUserUseCase(authRepository),
            sendPasswordReset = SendPasswordResetUseCase(authRepository)
        )
    }

    val gameUseCases: GameUseCases by lazy {
        val fillEnclosedAreas = FillEnclosedAreasUseCase(stompedHexRepository, hexGridEngine)
        GameUseCases(
            observeStompedHexAddresses = ObserveStompedHexAddressesUseCase(stompedHexRepository),
            observeRegionStats = ObserveRegionStatsUseCase(stompedHexRepository),
            stompCell = StompCellUseCase(stompedHexRepository, hexGridEngine, fillEnclosedAreas),
            getGridCellsAround = GetGridCellsAroundUseCase(hexGridEngine),
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
            updateActiveNeighborhood = UpdateActiveNeighborhoodUseCase(geocodingRepository, hexGridEngine),
            observeStepCount = ObserveStepCountUseCase(stepCounterRepository),
            startStepCounter = StartStepCounterUseCase(stepCounterRepository)
        )
    }
}
