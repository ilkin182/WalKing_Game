package com.example.domain.race

import com.example.domain.model.ExploredCell
import com.example.domain.model.RaceMode
import com.example.domain.model.WalkSession
import org.junit.Assert.assertEquals
import org.junit.Test

private const val START = 1_700_000_000_000L
private const val HOUR = 60 * 60 * 1000L
private const val END = START + 48 * HOUR

private fun cell(at: Long) = ExploredCell(
    cellId = "cell-$at",
    exploredAt = at,
    explorationLevel = ExploredCell.LEVEL_WALKED
)

private fun walk(startedAt: Long, meters: Double) = WalkSession(
    id = startedAt,
    startedAt = startedAt,
    endedAt = startedAt + HOUR,
    distanceMeters = meters
)

class RaceScoringTest {

    @Test
    fun `only cells claimed inside the window count`() {
        val cells = listOf(
            cell(START - HOUR),   // before the race
            cell(START + HOUR),
            cell(START + 2 * HOUR),
            cell(END + HOUR)      // after the deadline
        )

        val score = RaceScoring.score(
            mode = RaceMode.CELLS,
            startsAt = START,
            endsAt = END,
            now = END + 10 * HOUR,
            cells = cells,
            sessions = emptyList()
        )

        assertEquals(2.0, score, 0.0)
    }

    @Test
    fun `a race still running is scored only up to now`() {
        val cells = listOf(cell(START + HOUR), cell(START + 30 * HOUR))

        val score = RaceScoring.score(
            mode = RaceMode.CELLS,
            startsAt = START,
            endsAt = END,
            now = START + 5 * HOUR,
            cells = cells,
            sessions = emptyList()
        )

        assertEquals(1.0, score, 0.0)
    }

    @Test
    fun `distance is the sum of the walks started inside the window, in kilometres`() {
        val sessions = listOf(
            walk(START - HOUR, 5_000.0),
            walk(START + HOUR, 3_000.0),
            walk(START + 3 * HOUR, 1_500.0)
        )

        val score = RaceScoring.score(
            mode = RaceMode.DISTANCE,
            startsAt = START,
            endsAt = END,
            now = END,
            cells = emptyList(),
            sessions = sessions
        )

        assertEquals(4.5, score, 0.0001)
    }

    @Test
    fun `the longest walk mode takes the single best walk, not the total`() {
        val sessions = listOf(
            walk(START + HOUR, 3_000.0),
            walk(START + 2 * HOUR, 7_200.0),
            walk(START + 3 * HOUR, 1_000.0)
        )

        val score = RaceScoring.score(
            mode = RaceMode.LONGEST_WALK,
            startsAt = START,
            endsAt = END,
            now = END,
            cells = emptyList(),
            sessions = sessions
        )

        assertEquals(7.2, score, 0.0001)
    }

    @Test
    fun `a race that has not started yet scores zero`() {
        val score = RaceScoring.score(
            mode = RaceMode.CELLS,
            startsAt = START,
            endsAt = END,
            now = START - HOUR,
            cells = listOf(cell(START - 2 * HOUR)),
            sessions = emptyList()
        )

        assertEquals(0.0, score, 0.0)
    }

    @Test
    fun `nothing walked is zero rather than an error`() {
        RaceMode.entries.forEach { mode ->
            val score = RaceScoring.score(
                mode = mode,
                startsAt = START,
                endsAt = END,
                now = END,
                cells = emptyList(),
                sessions = emptyList()
            )
            assertEquals("$mode should score zero", 0.0, score, 0.0)
        }
    }
}
