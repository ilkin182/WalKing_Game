package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ExploredCell
import com.example.domain.repository.StompedHexRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkVisionRingUseCaseTest {
    private val repository: StompedHexRepository = mockk(relaxed = true)
    private val engine: HexGridEngine = mockk()
    private val useCase = MarkVisionRingUseCase(repository, engine)

    private val ring = listOf("center", "n1", "n2", "n3", "n4", "n5", "n6")

    @Test
    fun `reveals the ring around the dwelled-in cell but not the cell itself`() = runTest {
        every { engine.gridDisk("center", 1) } returns ring

        val revealed = useCase("center", "Downtown", knownLevels = emptyMap())

        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6"), revealed)
        coVerify {
            repository.markPartiallyExplored(
                listOf("n1", "n2", "n3", "n4", "n5", "n6"),
                ExploredCell.LEVEL_VISION,
                "Downtown"
            )
        }
    }

    @Test
    fun `cells already known at this level or better are not rewritten`() = runTest {
        every { engine.gridDisk("center", 1) } returns ring

        val revealed = useCase(
            "center",
            null,
            knownLevels = mapOf(
                "n1" to ExploredCell.LEVEL_WALKED,
                "n2" to ExploredCell.LEVEL_VISION
            )
        )

        assertEquals(listOf("n3", "n4", "n5", "n6"), revealed)
        val written = slot<List<String>>()
        coVerify { repository.markPartiallyExplored(capture(written), any(), any()) }
        assertTrue(written.captured.none { it == "n1" || it == "n2" })
    }

    @Test
    fun `a fully known ring writes nothing at all`() = runTest {
        every { engine.gridDisk("center", 1) } returns ring

        val revealed = useCase(
            "center",
            null,
            knownLevels = ring.associateWith { ExploredCell.LEVEL_WALKED }
        )

        assertTrue(revealed.isEmpty())
        // This is the debounce that keeps a phone sitting on a table off the database entirely.
        coVerify(exactly = 0) { repository.markPartiallyExplored(any(), any(), any()) }
    }

    @Test
    fun `a cell known only faintly is still raised`() = runTest {
        every { engine.gridDisk("center", 1) } returns listOf("center", "n1")

        val revealed = useCase("center", null, knownLevels = mapOf("n1" to 0.2f))

        assertEquals(listOf("n1"), revealed)
    }

    @Test
    fun `a grid engine failure degrades to revealing nothing`() = runTest {
        every { engine.gridDisk(any(), any()) } throws IllegalStateException("bad address")

        val revealed = useCase("center", null, knownLevels = emptyMap())

        assertTrue(revealed.isEmpty())
        coVerify(exactly = 0) { repository.markPartiallyExplored(any(), any(), any()) }
    }
}
