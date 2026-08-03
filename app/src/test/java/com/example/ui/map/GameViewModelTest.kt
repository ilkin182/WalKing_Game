package com.example.ui.map

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.RegionStat
import com.example.domain.usecase.AddWalkDistanceUseCase
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.EndWalkSessionUseCase
import com.example.domain.usecase.EnrichCellElevationsUseCase
import com.example.domain.usecase.EnrichCellPlacesUseCase
import com.example.domain.usecase.EnrichCityBoundsUseCase
import com.example.domain.usecase.EnrichPoiTilesUseCase
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val observeExploredCells: ObserveExploredCellsUseCase = mockk()
    private val observeRegionStats: ObserveRegionStatsUseCase = mockk()
    private val stompCell: StompCellUseCase = mockk(relaxed = true)
    private val markVisionRing: MarkVisionRingUseCase = mockk(relaxed = true)
    private val gridCellLookup: GridCellLookupUseCase = mockk(relaxed = true)
    private val getGridCellsInBounds: GetGridCellsInBoundsUseCase = mockk()
    private val clearProgress: ClearProgressUseCase = mockk(relaxed = true)
    private val updateNickname: UpdateNicknameUseCase = mockk(relaxed = true)
    private val observeNickname: ObserveNicknameUseCase = mockk()
    private val observeTotalDistance: ObserveTotalDistanceUseCase = mockk()
    private val observeStatsStartTimestamp: ObserveStatsStartTimestampUseCase = mockk()
    private val recordWalkedDistance: RecordWalkedDistanceUseCase = mockk(relaxed = true)
    private val observeLocationUpdates: ObserveLocationUpdatesUseCase = mockk()
    private val observeLocationErrors: ObserveLocationErrorsUseCase = mockk()
    private val startLocationTracking: StartLocationTrackingUseCase = mockk(relaxed = true)
    private val stopLocationTracking: StopLocationTrackingUseCase = mockk(relaxed = true)
    private val updateActiveNeighborhood: UpdateActiveNeighborhoodUseCase = mockk()
    private val observeStepCount: ObserveStepCountUseCase = mockk()
    private val startStepCounter: StartStepCounterUseCase = mockk(relaxed = true)
    private val getWeather: GetWeatherUseCase = mockk(relaxed = true)
    private val resolvePlace: ResolvePlaceUseCase = mockk(relaxed = true)
    private val startWalkSession: StartWalkSessionUseCase = mockk(relaxed = true)
    private val endWalkSession: EndWalkSessionUseCase = mockk(relaxed = true)
    private val observeWalkSessions: ObserveWalkSessionsUseCase = mockk(relaxed = true)
    private val observeWalkRoutes: ObserveWalkRoutesUseCase = mockk(relaxed = true)
    private val observeClosedLoops: ObserveClosedLoopsUseCase = mockk(relaxed = true)
    private val addWalkDistance: AddWalkDistanceUseCase = mockk(relaxed = true)
    private val recordRoutePoint: RecordRoutePointUseCase = mockk(relaxed = true)
    private val enrichCellElevations: EnrichCellElevationsUseCase = mockk(relaxed = true)
    private val enrichCellPlaces: EnrichCellPlacesUseCase = mockk(relaxed = true)
    private val enrichPoiTiles: EnrichPoiTilesUseCase = mockk(relaxed = true)
    private val enrichCityBounds: EnrichCityBoundsUseCase = mockk(relaxed = true)
    private val observePois: ObservePoisUseCase = mockk(relaxed = true)
    private val observeCityBounds: ObserveCityBoundsUseCase = mockk(relaxed = true)
    private val weatherSnapshot: GetWeatherSnapshotUseCase = mockk(relaxed = true)

    private val exploredCellsFlow = MutableStateFlow<List<ExploredCell>>(emptyList())
    private val regionStatsFlow = MutableStateFlow<List<RegionStat>>(emptyList())

    /** The explored set the ViewModel exposes is derived, so tests seed it as persisted cells. */
    private fun seedExplored(vararg cellIds: String) {
        exploredCellsFlow.value = cellIds.map { ExploredCell(it, 0L, ExploredCell.LEVEL_WALKED) }
    }

    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())

        every { observeNickname() } returns flowOf("Stomper")
        every { observeTotalDistance() } returns flowOf(0.0)
        every { observeStatsStartTimestamp() } returns flowOf(0L)
        every { observeStepCount() } returns flowOf(0)
        every { observeWalkSessions() } returns emptyFlow()
        every { observeWalkRoutes() } returns emptyFlow()
        every { observeClosedLoops() } returns flowOf(0)
        every { weatherSnapshot() } returns null
        every { observeExploredCells() } returns exploredCellsFlow
        every { observeRegionStats() } returns regionStatsFlow
        every { observePois() } returns flowOf(emptyList())
        every { observeCityBounds() } returns flowOf(emptyList())
        every { observeLocationUpdates() } returns emptyFlow()
        every { observeLocationErrors() } returns emptyFlow()
        every { gridCellLookup.cornersOf(any()) } returns emptyList()
        every { gridCellLookup.cellIdAt(any(), any()) } returns "cell"

        viewModel = GameViewModel(
            GameUseCases(
                observeExploredCells = observeExploredCells,
                observeRegionStats = observeRegionStats,
                stompCell = stompCell,
                markVisionRing = markVisionRing,
                gridCellLookup = gridCellLookup,
                getGridCellsInBounds = getGridCellsInBounds,
                clearProgress = clearProgress,
                updateNickname = updateNickname,
                observeNickname = observeNickname,
                observeTotalDistance = observeTotalDistance,
                observeStatsStartTimestamp = observeStatsStartTimestamp,
                observeClosedLoops = observeClosedLoops,
                recordWalkedDistance = recordWalkedDistance,
                observeLocationUpdates = observeLocationUpdates,
                observeLocationErrors = observeLocationErrors,
                startLocationTracking = startLocationTracking,
                stopLocationTracking = stopLocationTracking,
                updateActiveNeighborhood = updateActiveNeighborhood,
                observeStepCount = observeStepCount,
                startStepCounter = startStepCounter,
                getWeather = getWeather,
                resolvePlace = resolvePlace,
                startWalkSession = startWalkSession,
                endWalkSession = endWalkSession,
                observeWalkSessions = observeWalkSessions,
                observeWalkRoutes = observeWalkRoutes,
                addWalkDistance = addWalkDistance,
                recordRoutePoint = recordRoutePoint,
                enrichCellElevations = enrichCellElevations,
                enrichCellPlaces = enrichCellPlaces,
                enrichPoiTiles = enrichPoiTiles,
                enrichCityBounds = enrichCityBounds,
                observePois = observePois,
                observeCityBounds = observeCityBounds,
                weatherSnapshot = weatherSnapshot
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stompedHexes reflects the use case flow while collected`() = runTest {
        viewModel.stompedHexes.launchIn(backgroundScope)
        testScheduler.advanceUntilIdle()

        seedExplored("a", "b")
        testScheduler.advanceUntilIdle()

        assertEquals(setOf("a", "b"), viewModel.stompedHexes.value)
    }

    @Test
    fun `regionStats reflects the use case flow while collected`() = runTest {
        viewModel.regionStats.launchIn(backgroundScope)
        testScheduler.advanceUntilIdle()

        val stats = listOf(RegionStat("Downtown", 5, 100))
        regionStatsFlow.value = stats
        testScheduler.advanceUntilIdle()

        assertEquals(stats, viewModel.regionStats.value)
    }

    @Test
    fun `updateNickname delegates to the use case`() {
        viewModel.updateNickname("NewName")

        verify(exactly = 1) { updateNickname("NewName") }
    }

    @Test
    fun `clearAllStomps clears progress and resets the distance tracker`() = runTest {
        viewModel.clearAllStomps()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { clearProgress() }
        verify(exactly = 1) { recordWalkedDistance.reset() }
    }

    @Test
    fun `stompCellDirect forces a re-stomp using the current stomped set`() = runTest {
        viewModel.stompedHexes.launchIn(backgroundScope)
        testScheduler.advanceUntilIdle()
        seedExplored("existing")
        testScheduler.advanceUntilIdle()

        viewModel.stompCellDirect(1.0, 2.0)
        testScheduler.advanceUntilIdle()

        coVerify { stompCell(1.0, 2.0, null, setOf("existing"), forceRestomp = true) }
    }

    @Test
    fun `each fix stomps the trail back from the previous one`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null

        viewModel.simulateLocationUpdate(40.4093, 49.8671)
        testScheduler.advanceUntilIdle()
        viewModel.simulateLocationUpdate(40.4102, 49.8671)
        testScheduler.advanceUntilIdle()

        // The first fix has nothing to connect back to; the second one claims the way it came.
        coVerify { stompCell(40.4093, 49.8671, null, any(), any(), null, any()) }
        coVerify { stompCell(40.4102, 49.8671, null, any(), any(), Coordinate(40.4093, 49.8671), any()) }
    }

    @Test
    fun `resuming tracking does not stomp a trail across the pause`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null
        viewModel.simulateLocationUpdate(40.4093, 49.8671)
        testScheduler.advanceUntilIdle()

        viewModel.stopTracking()
        viewModel.simulateLocationUpdate(40.4102, 49.8671)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { stompCell(any(), any(), any(), any(), any(), Coordinate(40.4093, 49.8671), any()) }
    }

    @Test
    fun `getGridCellsInBounds delegates with the current stomped set`() = runTest {
        viewModel.stompedHexes.launchIn(backgroundScope)
        testScheduler.advanceUntilIdle()
        seedExplored("a")
        testScheduler.advanceUntilIdle()
        val bounds = listOf(Coordinate(1.0, 2.0), Coordinate(1.0, 3.0), Coordinate(0.0, 3.0), Coordinate(0.0, 2.0))
        every { getGridCellsInBounds(bounds, setOf("a"), null) } returns emptyList()

        viewModel.getGridCellsInBounds(bounds)

        verify { getGridCellsInBounds(bounds, setOf("a"), null) }
    }

    @Test
    fun `getGridCellsInBounds anchors the coverage area on the player's location`() = runTest {
        every { getGridCellsInBounds(any(), any(), any()) } returns emptyList()
        // A simulated fix also kicks off stomping/geocoding; stub it so nothing throws out of the
        // background coroutine it launches.
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null
        val bounds = listOf(Coordinate(1.0, 2.0), Coordinate(1.0, 3.0), Coordinate(0.0, 3.0), Coordinate(0.0, 2.0))
        viewModel.simulateLocationUpdate(40.4093, 49.8671)

        viewModel.getGridCellsInBounds(bounds)
        testScheduler.advanceUntilIdle()

        verify { getGridCellsInBounds(bounds, any(), Coordinate(40.4093, 49.8671)) }
    }

    @Test
    fun `a fix too vague to trust clears no fog`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null

        viewModel.simulateLocationUpdate(40.4093, 49.8671, accuracyMeters = 80f)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { stompCell(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a noisy fix still moves the blue dot`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null

        viewModel.simulateLocationUpdate(40.4093, 49.8671, accuracyMeters = 80f)
        testScheduler.advanceUntilIdle()

        // The player is still shown where the phone thinks they are - only the permanent act of
        // clearing fog is withheld.
        assertEquals(40.4093, viewModel.currentLocation.value?.latitude)
        verify { recordWalkedDistance(any()) }
    }

    @Test
    fun `a noisy fix breaks the trail so the next good one claims no corridor`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null

        viewModel.simulateLocationUpdate(40.4093, 49.8671)
        testScheduler.advanceUntilIdle()
        viewModel.simulateLocationUpdate(40.4200, 49.8700, accuracyMeters = 90f)
        testScheduler.advanceUntilIdle()
        viewModel.simulateLocationUpdate(40.4102, 49.8671)
        testScheduler.advanceUntilIdle()

        // The fix after the noisy one must start a fresh trail, not draw one back to the last
        // trusted position through ground the player may never have walked.
        coVerify { stompCell(40.4102, 49.8671, any(), any(), any(), null, any()) }
    }

    @Test
    fun `standing in one cell past the dwell threshold reveals the ring around it`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null
        every { gridCellLookup.cellIdAt(any(), any()) } returns "center"

        viewModel.simulateLocationUpdate(40.4093, 49.8671, timestampMillis = 0L)
        testScheduler.advanceUntilIdle()
        viewModel.simulateLocationUpdate(40.4093, 49.8671, timestampMillis = 30_000L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { markVisionRing("center", any(), any(), any()) }
    }

    @Test
    fun `a brief stop does not reveal a vision ring`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null
        every { gridCellLookup.cellIdAt(any(), any()) } returns "center"

        viewModel.simulateLocationUpdate(40.4093, 49.8671, timestampMillis = 0L)
        testScheduler.advanceUntilIdle()
        viewModel.simulateLocationUpdate(40.4093, 49.8671, timestampMillis = 10_000L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { markVisionRing(any(), any(), any(), any()) }
    }

    @Test
    fun `standing still forever still only writes the vision ring once`() = runTest {
        every { updateActiveNeighborhood(any(), any(), any(), any()) } returns null
        every { gridCellLookup.cellIdAt(any(), any()) } returns "center"

        listOf(0L, 30_000L, 60_000L, 120_000L, 600_000L).forEach { timestamp ->
            viewModel.simulateLocationUpdate(40.4093, 49.8671, timestampMillis = timestamp)
            testScheduler.advanceUntilIdle()
        }

        coVerify(exactly = 1) { markVisionRing(any(), any(), any(), any()) }
    }

    @Test
    fun `onStepPermissionGranted starts the step counter`() {
        viewModel.onStepPermissionGranted()

        verify(exactly = 1) { startStepCounter() }
    }
}
