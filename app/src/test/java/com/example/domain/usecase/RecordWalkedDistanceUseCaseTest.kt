package com.example.domain.usecase

import com.example.domain.model.GeoLocation
import com.example.domain.repository.UserStatsRepository
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordWalkedDistanceUseCaseTest {
    private val repository: UserStatsRepository = mockk(relaxed = true)
    private val useCase = RecordWalkedDistanceUseCase(repository)

    private fun locationAt(lat: Double, lng: Double, accuracy: Float = 5f) =
        GeoLocation(latitude = lat, longitude = lng, accuracyMeters = accuracy, timestampMillis = 0L)

    @Test
    fun `first ever reading records no distance but is remembered`() {
        useCase(locationAt(40.0, -73.0))

        verify(exactly = 0) { repository.addDistance(any()) }
    }

    @Test
    fun `a plausible walking step between two fixes adds distance`() {
        useCase(locationAt(40.0000, -73.0000))
        // roughly 11m north
        useCase(locationAt(40.0001, -73.0000))

        val delta = slot<Double>()
        verify(exactly = 1) { repository.addDistance(capture(delta)) }
        assertEquals(11.1, delta.captured, 1.0)
    }

    @Test
    fun `a GPS jump beyond 250m is ignored`() {
        useCase(locationAt(40.0, -73.0))
        useCase(locationAt(41.0, -73.0)) // ~111km away

        verify(exactly = 0) { repository.addDistance(any()) }
    }

    @Test
    fun `sub-2m drift while stationary is ignored`() {
        useCase(locationAt(40.00000, -73.00000))
        useCase(locationAt(40.00000, -73.000001))

        verify(exactly = 0) { repository.addDistance(any()) }
    }

    @Test
    fun `low accuracy fixes are neither counted nor remembered as the reference point`() {
        useCase(locationAt(40.0000, -73.0000))
        useCase(locationAt(40.0001, -73.0000, accuracy = 30f)) // too inaccurate, distance skipped
        useCase(locationAt(40.0002, -73.0000)) // measured from the last *good* fix, not the skipped one

        // Only the final call (relative to the first fix) should register.
        verify(exactly = 1) { repository.addDistance(any()) }
    }

    @Test
    fun `reset forgets the last known location`() {
        useCase(locationAt(40.0000, -73.0000))
        useCase.reset()
        useCase(locationAt(40.0001, -73.0000))

        verify(exactly = 0) { repository.addDistance(any()) }
    }
}
