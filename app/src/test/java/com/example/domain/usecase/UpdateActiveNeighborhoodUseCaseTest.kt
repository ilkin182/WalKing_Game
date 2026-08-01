package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.model.PlaceInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The reverse geocoding itself now lives in ResolvePlaceUseCase, so what is left here is the rule
 * about *when* the active zone changes - which is what the map's top pill and the stomp percentage
 * both key off.
 */
class UpdateActiveNeighborhoodUseCaseTest {
    private val engine: HexGridEngine = mockk()
    private lateinit var useCase: UpdateActiveNeighborhoodUseCase

    @Before
    fun setUp() {
        every { engine.polygonToCells(any(), any()) } returns setOf("c1", "c2")
        useCase = UpdateActiveNeighborhoodUseCase(engine)
    }

    @Test
    fun `a new neighborhood name produces a new ActiveNeighborhood`() {
        val result = useCase(1.0, 2.0, current = null, place = PlaceInfo(neighborhood = "Downtown"))

        assertEquals("Downtown", result?.name)
        assertEquals(setOf("c1", "c2"), result?.totalCells)
    }

    @Test
    fun `same neighborhood name as current returns null (unchanged)`() {
        val current = ActiveNeighborhood("Downtown", 1.0, 2.0, setOf("c1"))

        val result = useCase(1.0, 2.0, current, PlaceInfo(neighborhood = "Downtown"))

        assertNull(result)
    }

    @Test
    fun `moving to a different neighborhood rebuilds it`() {
        val current = ActiveNeighborhood("Downtown", 1.0, 2.0, setOf("c1"))

        val result = useCase(1.0, 2.0, current, PlaceInfo(neighborhood = "Uptown"))

        assertEquals("Uptown", result?.name)
    }

    @Test
    fun `no resolved place falls back to Active Zone`() {
        val result = useCase(1.0, 2.0, current = null, place = null)

        assertEquals("Active Zone", result?.name)
    }

    @Test
    fun `a place with no neighborhood name falls back to Active Zone`() {
        val result = useCase(1.0, 2.0, current = null, place = PlaceInfo(city = "Baku"))

        assertEquals("Active Zone", result?.name)
    }

    @Test
    fun `an unresolved place while already in a zone keeps it unchanged`() {
        // Losing the geocoder mid-walk must not swap a real neighbourhood for the placeholder.
        val current = ActiveNeighborhood("Active Zone", 1.0, 2.0, setOf("c1"))

        val result = useCase(1.0, 2.0, current, place = null)

        assertNull(result)
    }
}
