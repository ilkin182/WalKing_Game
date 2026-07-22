package com.example.ui.map

import com.example.domain.model.RegionStat
import com.example.domain.usecase.ClearProgressUseCase
import com.example.domain.usecase.GetGridCellsAroundUseCase
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
    private val observeStompedHexAddresses: ObserveStompedHexAddressesUseCase = mockk()
    private val observeRegionStats: ObserveRegionStatsUseCase = mockk()
    private val stompCell: StompCellUseCase = mockk(relaxed = true)
    private val getGridCellsAround: GetGridCellsAroundUseCase = mockk()
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

    private val stompedHexesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val regionStatsFlow = MutableStateFlow<List<RegionStat>>(emptyList())

    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())

        every { observeNickname() } returns flowOf("Stomper")
        every { observeTotalDistance() } returns flowOf(0.0)
        every { observeStatsStartTimestamp() } returns flowOf(0L)
        every { observeStepCount() } returns flowOf(0)
        every { observeStompedHexAddresses() } returns stompedHexesFlow
        every { observeRegionStats() } returns regionStatsFlow
        every { observeLocationUpdates() } returns emptyFlow()
        every { observeLocationErrors() } returns emptyFlow()

        viewModel = GameViewModel(
            GameUseCases(
                observeStompedHexAddresses = observeStompedHexAddresses,
                observeRegionStats = observeRegionStats,
                stompCell = stompCell,
                getGridCellsAround = getGridCellsAround,
                clearProgress = clearProgress,
                updateNickname = updateNickname,
                observeNickname = observeNickname,
                observeTotalDistance = observeTotalDistance,
                observeStatsStartTimestamp = observeStatsStartTimestamp,
                recordWalkedDistance = recordWalkedDistance,
                observeLocationUpdates = observeLocationUpdates,
                observeLocationErrors = observeLocationErrors,
                startLocationTracking = startLocationTracking,
                stopLocationTracking = stopLocationTracking,
                updateActiveNeighborhood = updateActiveNeighborhood,
                observeStepCount = observeStepCount,
                startStepCounter = startStepCounter
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

        stompedHexesFlow.value = setOf("a", "b")
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
        stompedHexesFlow.value = setOf("existing")
        testScheduler.advanceUntilIdle()

        viewModel.stompCellDirect(1.0, 2.0)
        testScheduler.advanceUntilIdle()

        coVerify { stompCell(1.0, 2.0, null, setOf("existing"), forceRestomp = true) }
    }

    @Test
    fun `getGridCellsAround delegates with the current stomped set`() = runTest {
        viewModel.stompedHexes.launchIn(backgroundScope)
        testScheduler.advanceUntilIdle()
        stompedHexesFlow.value = setOf("a")
        testScheduler.advanceUntilIdle()
        every { getGridCellsAround(1.0, 2.0, setOf("a"), 5) } returns emptyList()

        viewModel.getGridCellsAround(1.0, 2.0)

        verify { getGridCellsAround(1.0, 2.0, setOf("a"), 5) }
    }

    @Test
    fun `onStepPermissionGranted starts the step counter`() {
        viewModel.onStepPermissionGranted()

        verify(exactly = 1) { startStepCounter() }
    }
}
