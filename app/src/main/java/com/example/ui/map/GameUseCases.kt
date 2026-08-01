package com.example.ui.map

import com.example.domain.usecase.AddWalkDistanceUseCase
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.EndWalkSessionUseCase
import com.example.domain.usecase.EnrichCellElevationsUseCase
import com.example.domain.usecase.EnrichCellPlacesUseCase
import com.example.domain.usecase.GetGridCellsInBoundsUseCase
import com.example.domain.usecase.GetWeatherSnapshotUseCase
import com.example.domain.usecase.GetWeatherUseCase
import com.example.domain.usecase.GridCellLookupUseCase
import com.example.domain.usecase.MarkVisionRingUseCase
import com.example.domain.usecase.ObserveExploredCellsUseCase
import com.example.domain.usecase.ObserveLocationErrorsUseCase
import com.example.domain.usecase.ObserveLocationUpdatesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObserveRegionStatsUseCase
import com.example.domain.usecase.ObserveStatsStartTimestampUseCase
import com.example.domain.usecase.ObserveStepCountUseCase
import com.example.domain.usecase.ObserveTotalDistanceUseCase
import com.example.domain.usecase.ObserveWalkSessionsUseCase
import com.example.domain.usecase.RecordWalkedDistanceUseCase
import com.example.domain.usecase.ResolvePlaceUseCase
import com.example.domain.usecase.StartLocationTrackingUseCase
import com.example.domain.usecase.StartStepCounterUseCase
import com.example.domain.usecase.StartWalkSessionUseCase
import com.example.domain.usecase.StompCellUseCase
import com.example.domain.usecase.StopLocationTrackingUseCase
import com.example.domain.usecase.UpdateActiveNeighborhoodUseCase
import com.example.domain.usecase.UpdateNicknameUseCase

/**
 * Bundles the UseCases GameViewModel depends on. The ViewModel only ever talks to these
 * (never to a repository or DAO directly); this is just an ergonomic way to inject ~17 of them.
 */
data class GameUseCases(
    val observeExploredCells: ObserveExploredCellsUseCase,
    val observeRegionStats: ObserveRegionStatsUseCase,
    val stompCell: StompCellUseCase,
    val markVisionRing: MarkVisionRingUseCase,
    val gridCellLookup: GridCellLookupUseCase,
    val getGridCellsInBounds: GetGridCellsInBoundsUseCase,
    val clearProgress: ClearProgressUseCase,
    val updateNickname: UpdateNicknameUseCase,
    val observeNickname: ObserveNicknameUseCase,
    val observeTotalDistance: ObserveTotalDistanceUseCase,
    val observeStatsStartTimestamp: ObserveStatsStartTimestampUseCase,
    val recordWalkedDistance: RecordWalkedDistanceUseCase,
    val observeLocationUpdates: ObserveLocationUpdatesUseCase,
    val observeLocationErrors: ObserveLocationErrorsUseCase,
    val startLocationTracking: StartLocationTrackingUseCase,
    val stopLocationTracking: StopLocationTrackingUseCase,
    val updateActiveNeighborhood: UpdateActiveNeighborhoodUseCase,
    val observeStepCount: ObserveStepCountUseCase,
    val startStepCounter: StartStepCounterUseCase,
    val getWeather: GetWeatherUseCase,
    val resolvePlace: ResolvePlaceUseCase,
    val startWalkSession: StartWalkSessionUseCase,
    val endWalkSession: EndWalkSessionUseCase,
    val observeWalkSessions: ObserveWalkSessionsUseCase,
    val addWalkDistance: AddWalkDistanceUseCase,
    val enrichCellElevations: EnrichCellElevationsUseCase,
    val enrichCellPlaces: EnrichCellPlacesUseCase,
    val weatherSnapshot: GetWeatherSnapshotUseCase
)
