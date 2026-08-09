package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.repository.StompedHexRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An unbounded axial hex grid, addressed "q:r".
 *
 * A real grid rather than a hand-written adjacency map: the fill decides whether to look at all from
 * the shape of the ring around a cell - which cells of it touch each other - so a graph that does not
 * behave like a hex grid would not exercise the thing under test. Coordinates are laid out flat
 * enough for the bounding-box sweep to work on: cells sit ~40 m apart, as they do in the app.
 */
private class AxialHexGridEngine : HexGridEngine {
    override fun latLngToCellAddress(lat: Double, lng: Double, resolution: Int): String {
        val q = Math.round(lng / CELL_DEGREES).toInt()
        val r = Math.round(lat / CELL_DEGREES).toInt()
        return "$q:$r"
    }

    override fun cellToBoundary(cellAddress: String): List<Coordinate> {
        val (q, r) = parse(cellAddress) ?: return emptyList()
        val lat = r * CELL_DEGREES
        val lng = q * CELL_DEGREES
        val half = CELL_DEGREES / 2
        return listOf(
            Coordinate(lat - half, lng - half),
            Coordinate(lat - half, lng + half),
            Coordinate(lat + half, lng + half),
            Coordinate(lat + half, lng - half)
        )
    }

    override fun gridDisk(centerCellAddress: String, radius: Int): List<String> {
        val (q, r) = parse(centerCellAddress) ?: return listOf(centerCellAddress)
        val cells = mutableListOf<String>()
        for (dq in -radius..radius) {
            for (dr in maxOf(-radius, -dq - radius)..minOf(radius, -dq + radius)) {
                cells.add("${q + dq}:${r + dr}")
            }
        }
        return cells
    }

    override fun polygonToCells(corners: List<Coordinate>, resolution: Int): Set<String> =
        error("not used")

    private fun parse(address: String): Pair<Int, Int>? {
        val parts = address.split(":")
        if (parts.size != 2) return null
        val q = parts[0].toIntOrNull() ?: return null
        val r = parts[1].toIntOrNull() ?: return null
        return q to r
    }

    private companion object {
        /** ~40 m, close to the width of a cell at the resolution the app plays at. */
        const val CELL_DEGREES = 0.00036
    }
}

class FillEnclosedAreasUseCaseTest {
    private val repository: StompedHexRepository = mockk(relaxed = true)
    private val engine = AxialHexGridEngine()

    private fun useCase(onAreasEnclosed: suspend (Int) -> Unit = {}) =
        FillEnclosedAreasUseCase(repository, engine, onAreasEnclosed)

    /** The ring of six around a cell, with one cell held back as the one that closes it. */
    private fun ringAround(center: String): List<String> =
        engine.gridDisk(center, 1).filter { it != center }

    @Test
    fun `a pocket fully surrounded by stomped cells is auto-filled`() = runTest {
        val ring = ringAround(CENTER)
        val loops = useCase()(ring.last(), "Downtown", ring.dropLast(1).toSet())

        assertEquals(1, loops)
        coVerify { repository.stompAll(match { it.toSet() == setOf(CENTER) }, "Downtown") }
    }

    @Test
    fun `closing a loop is reported so it can be counted`() = runTest {
        // The event leaves no trace once the interior is claimed - it looks exactly like ground the
        // player walked over - so it has to be reported as it happens or not at all.
        val reported = mutableListOf<Int>()
        val ring = ringAround(CENTER)

        useCase { reported.add(it) }(ring.last(), "Downtown", ring.dropLast(1).toSet())

        assertEquals(listOf(1), reported)
    }

    @Test
    fun `walking on open ground fills nothing`() = runTest {
        val reported = mutableListOf<Int>()
        // A straight trail, with the player at the end of it: nothing is closed off anywhere.
        val trail = (0..5).map { "$it:0" }

        val loops = useCase { reported.add(it) }(trail.last(), "Downtown", trail.dropLast(1).toSet())

        assertEquals(0, loops)
        assertEquals(emptyList<Int>(), reported)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }

    @Test
    fun `a gap that opens onto the world is not claimed`() = runTest {
        // Two parallel trails one cell apart, joined at one end only - a dead-end alley, not a loop.
        // The corridor between them touches open ground at the far end, so none of it is enclosed.
        val lower = (0..8).map { "$it:0" }
        val upper = (0..8).map { "$it:2" }
        val joined = lower + upper + "0:1"

        val loops = useCase()("0:1", "Downtown", (joined - "0:1").toSet())

        assertEquals(0, loops)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }

    @Test
    fun `a loop far bigger than a courtyard is filled in`() = runTest {
        // The whole point of the feature, and what the old two-hundred-cell limit could never do:
        // a ring ~40 cells across, the size of a walk around a district, claims its whole interior.
        val (border, interior) = ringOfRadius(20)
        val closing = border.first()

        val loops = useCase()(closing, "Downtown", (border - closing).toSet())

        assertEquals(1, loops)
        val filled = mutableListOf<List<String>>()
        coVerify { repository.stompAll(capture(filled), "Downtown") }
        assertEquals(interior, filled.flatten().toSet())
    }

    @Test
    fun `the sweep claims a loop that was closed long ago`() = runTest {
        // Nothing is being stomped here: this is the pass that looks at a finished history, which is
        // the only thing that can help a player who closed their loop before any of this existed.
        val (border, interior) = ringOfRadius(12)

        val loops = useCase().fillAll(border, "Downtown")

        assertEquals(1, loops)
        val filled = mutableListOf<List<String>>()
        coVerify { repository.stompAll(capture(filled), "Downtown") }
        assertEquals(interior, filled.flatten().toSet())
    }

    @Test
    fun `the sweep leaves open ground alone`() = runTest {
        // A trail that never closes: everything around it is the outside world.
        val trail = (0..40).map { "$it:0" }.toSet()

        val loops = useCase().fillAll(trail, "Downtown")

        assertEquals(0, loops)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }

    @Test
    fun `the sweep finds every pocket in one pass`() = runTest {
        val (firstBorder, firstInterior) = ringOfRadius(6)
        val (secondBorder, secondInterior) = ringOfRadius(6, offsetQ = 40)

        val loops = useCase().fillAll(firstBorder + secondBorder, "Downtown")

        assertEquals(2, loops)
        val filled = mutableListOf<List<String>>()
        coVerify { repository.stompAll(capture(filled), "Downtown") }
        assertEquals(firstInterior + secondInterior, filled.flatten().toSet())
    }

    @Test
    fun `an unbounded region is never filled`() = runTest {
        // One lone cell in the middle of nowhere: every direction out of it is open ground.
        val loops = useCase()(CENTER, "Downtown", emptySet())

        assertEquals(0, loops)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }

    /**
     * A closed hexagonal border of the given radius in cells, with the cells it encloses.
     *
     * Built from the axial disk rather than drawn by hand: the border is everything at exactly
     * [radius] steps out, the interior everything inside it.
     */
    private fun ringOfRadius(radius: Int, offsetQ: Int = 0): Pair<Set<String>, Set<String>> {
        val disk = engine.gridDisk("$offsetQ:0", radius).toSet()
        val inner = engine.gridDisk("$offsetQ:0", radius - 1).toSet()
        assertTrue(inner.isNotEmpty())
        return (disk - inner) to inner
    }

    private companion object {
        const val CENTER = "0:0"
    }
}
