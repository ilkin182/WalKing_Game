package com.example.domain.race

import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRoster
import com.example.domain.model.RaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val START = 1_700_000_000_000L
private const val HOUR = 60 * 60 * 1000L
private const val END = START + 48 * HOUR

private val RACE = Race(
    id = "race-ABC234",
    code = "ABC234",
    name = "Həftəlik yarış",
    mode = RaceMode.CELLS,
    maxParticipants = 6,
    createdAt = START,
    startsAt = START,
    endsAt = END,
    hostId = "p-host",
    hostName = "Elçin"
)

private fun runner(id: String, score: Double, joinedAt: Long = START) = RaceParticipant(
    raceId = RACE.id,
    playerId = id,
    nickname = id,
    countryCode = "AZ",
    joinedAt = joinedAt,
    score = score,
    updatedAt = joinedAt
)

private val COOP_RACE = RACE.copy(
    mode = RaceMode.DISTANCE,
    format = RaceFormat.COOP,
    targetScore = 100.0
)

class RaceStandingsTest {

    @Test
    fun `runners are numbered from the highest score down`() {
        val roster = RaceRoster(
            RACE,
            listOf(runner("a", 12.0), runner("b", 40.0), runner("c", 25.0))
        )

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        assertEquals(listOf("b", "c", "a"), board.rows.map { it.participant.playerId })
        assertEquals(listOf(1, 2, 3), board.rows.map { it.position })
    }

    @Test
    fun `equal scores share a position and the next one skips`() {
        val roster = RaceRoster(
            RACE,
            listOf(runner("a", 40.0), runner("b", 40.0), runner("c", 10.0))
        )

        val board = RaceStandings.board(roster, currentPlayerId = null, now = START + HOUR)

        assertEquals(listOf(1, 1, 3), board.rows.map { it.position })
    }

    @Test
    fun `among equal scores the earlier joiner is drawn first`() {
        val roster = RaceRoster(
            RACE,
            listOf(
                runner("late", 40.0, joinedAt = START + 5 * HOUR),
                runner("early", 40.0, joinedAt = START)
            )
        )

        val board = RaceStandings.board(roster, currentPlayerId = null, now = START + HOUR)

        assertEquals(listOf("early", "late"), board.rows.map { it.participant.playerId })
        // Equal all the same - the order is only there so it does not shuffle between frames.
        assertEquals(listOf(1, 1), board.rows.map { it.position })
    }

    @Test
    fun `the current player and the host are both recognised`() {
        val roster = RaceRoster(RACE, listOf(runner("p-host", 5.0), runner("me", 9.0)))

        val board = RaceStandings.board(roster, currentPlayerId = "me", now = START + HOUR)

        assertEquals("me", board.playerRow?.participant?.playerId)
        assertFalse(board.isHost)

        val hostView = RaceStandings.board(roster, currentPlayerId = "p-host", now = START + HOUR)
        assertTrue(hostView.isHost)
    }

    @Test
    fun `free slots count what the host allowed, host included`() {
        val roster = RaceRoster(RACE, listOf(runner("a", 1.0), runner("b", 2.0)))

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        assertEquals(4, board.freeSlots)
    }

    @Test
    fun `there is no winner until the race is over`() {
        val roster = RaceRoster(RACE, listOf(runner("a", 1.0), runner("b", 2.0)))

        val running = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)
        assertEquals(RaceStatus.RUNNING, running.status)
        assertNull(running.winner)

        val finished = RaceStandings.board(roster, currentPlayerId = "a", now = END + HOUR)
        assertEquals(RaceStatus.FINISHED, finished.status)
        assertEquals("b", finished.winner?.participant?.playerId)
    }

    @Test
    fun `a co-op board adds everybody into one team total`() {
        val roster = RaceRoster(
            COOP_RACE,
            listOf(runner("a", 12.0), runner("b", 8.0), runner("c", 5.0))
        )

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        val team = board.team!!
        assertEquals(25.0, team.total, 0.0001)
        assertEquals(100.0, team.target, 0.0001)
        assertEquals(75.0, team.remaining, 0.0001)
        assertEquals(0.25f, team.fraction, 0.0001f)
        assertFalse(team.isReached)
    }

    @Test
    fun `each row carries its share of the team total`() {
        val roster = RaceRoster(COOP_RACE, listOf(runner("a", 30.0), runner("b", 10.0)))

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        assertEquals(0.75f, board.rows[0].share, 0.0001f)
        assertEquals(0.25f, board.rows[1].share, 0.0001f)
        assertEquals(1f, board.rows.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun `nobody having walked yet is a zero share rather than a division by zero`() {
        val roster = RaceRoster(COOP_RACE, listOf(runner("a", 0.0), runner("b", 0.0)))

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        assertEquals(0f, board.rows[0].share, 0.0f)
        assertEquals(0f, board.team!!.fraction, 0.0f)
    }

    @Test
    fun `passing the target reads as reached and does not overflow the bar`() {
        val roster = RaceRoster(COOP_RACE, listOf(runner("a", 90.0), runner("b", 60.0)))

        val team = RaceStandings.board(roster, "a", START + HOUR).team!!

        assertTrue(team.isReached)
        assertEquals(150.0, team.total, 0.0001)
        assertEquals(0.0, team.remaining, 0.0001)
        assertEquals(1f, team.fraction, 0.0f)
    }

    @Test
    fun `a finished co-op race has no winner - the team either got there or did not`() {
        val roster = RaceRoster(COOP_RACE, listOf(runner("a", 90.0), runner("b", 60.0)))

        val board = RaceStandings.board(roster, "a", now = END + HOUR)

        assertEquals(RaceStatus.FINISHED, board.status)
        assertNull(board.winner)
        assertTrue(board.isCoop)
        assertTrue(board.team!!.isReached)
    }

    @Test
    fun `a versus race has no team panel at all`() {
        val roster = RaceRoster(RACE, listOf(runner("a", 1.0)))

        assertNull(RaceStandings.board(roster, "a", START + HOUR).team)
    }

    @Test
    fun `a duplicated row is one runner, not two`() {
        val roster = RaceRoster(RACE, listOf(runner("a", 1.0), runner("a", 9.0)))

        val board = RaceStandings.board(roster, currentPlayerId = "a", now = START + HOUR)

        assertEquals(1, board.rows.size)
    }
}
