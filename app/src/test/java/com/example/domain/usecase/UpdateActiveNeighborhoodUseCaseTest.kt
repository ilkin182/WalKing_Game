package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.repository.GeocodingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateActiveNeighborhoodUseCaseTest {
    private val geocodingRepository: GeocodingRepository = mockk()
    private val engine: HexGridEngine = mockk()
    private lateinit var useCase: UpdateActiveNeighborhoodUseCase

    @Before
    fun setUp() {
        every { engine.polygonToCells(any(), any()) } returns setOf("c1", "c2")
        useCase = UpdateActiveNeighborhoodUseCase(geocodingRepository, engine)
    }

    @Test
    fun `a new neighborhood name produces a new ActiveNeighborhood`() = runTest {
        coEvery { geocodingRepository.reverseGeocodePlaceName(1.0, 2.0) } returns "Downtown"

        val result = useCase(1.0, 2.0, current = null)

        assertEquals("Downtown", result?.name)
        assertEquals(setOf("c1", "c2"), result?.totalCells)
    }

    @Test
    fun `same neighborhood name as current returns null (unchanged)`() = runTest {
        coEvery { geocodingRepository.reverseGeocodePlaceName(1.0, 2.0) } returns "Downtown"
        val current = ActiveNeighborhood("Downtown", 1.0, 2.0, setOf("c1"))

        val result = useCase(1.0, 2.0, current)

        assertNull(result)
    }

    @Test
    fun `null place name falls back to Active Zone`() = runTest {
        coEvery { geocodingRepository.reverseGeocodePlaceName(1.0, 2.0) } returns null

        val result = useCase(1.0, 2.0, current = null)

        assertEquals("Active Zone", result?.name)
    }

    @Test
    fun `geocoder failure with no current neighborhood falls back to Active Zone`() = runTest {
        coEvery { geocodingRepository.reverseGeocodePlaceName(any(), any()) } throws RuntimeException("offline")

        val result = useCase(1.0, 2.0, current = null)

        assertEquals("Active Zone", result?.name)
    }

    @Test
    fun `geocoder failure with an existing neighborhood keeps it unchanged`() = runTest {
        coEvery { geocodingRepository.reverseGeocodePlaceName(any(), any()) } throws RuntimeException("offline")
        val current = ActiveNeighborhood("Downtown", 1.0, 2.0, setOf("c1"))

        val result = useCase(1.0, 2.0, current)

        assertNull(result)
    }
}
