package com.example.domain.usecase

import app.cash.turbine.test
import com.example.domain.model.GeoLocation
import com.example.domain.repository.LocationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationUseCasesTest {
    private val repository: LocationRepository = mockk()

    @Test
    fun `ObserveLocationUpdatesUseCase delegates to the repository flow`() = runTest {
        val location = GeoLocation(1.0, 2.0, 5f, 0L)
        every { repository.locationUpdates } returns flowOf(location)

        ObserveLocationUpdatesUseCase(repository)().test {
            assertEquals(location, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `ObserveLocationErrorsUseCase delegates to the repository flow`() = runTest {
        every { repository.errors } returns flowOf("GPS unavailable")

        ObserveLocationErrorsUseCase(repository)().test {
            assertEquals("GPS unavailable", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `StartLocationTrackingUseCase delegates the interval to the repository`() {
        every { repository.startUpdates(any()) } returns Unit

        StartLocationTrackingUseCase(repository)(3000L)

        verify(exactly = 1) { repository.startUpdates(3000L) }
    }

    @Test
    fun `StopLocationTrackingUseCase delegates to the repository`() {
        every { repository.stopUpdates() } returns Unit

        StopLocationTrackingUseCase(repository)()

        verify(exactly = 1) { repository.stopUpdates() }
    }
}
