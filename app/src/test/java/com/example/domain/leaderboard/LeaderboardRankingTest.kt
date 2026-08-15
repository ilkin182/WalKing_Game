package com.example.domain.leaderboard

import com.example.domain.model.LeaderboardCategory
import com.example.domain.model.LeaderboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardRankingTest {

    private fun entry(
        id: String,
        cells: Int = 0,
        badges: Int = 0,
        country: String = "AZ",
        nickname: String = id
    ) = LeaderboardEntry(
        playerId = id,
        nickname = nickname,
        countryCode = country,
        exploredCells = cells,
        unlockedAchievements = badges
    )

    @Test
    fun `orders by explored cells, highest first`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("a", cells = 10), entry("b", cells = 90), entry("c", cells = 50)),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = null
        )

        assertEquals(listOf("b", "c", "a"), board.rows.map { it.entry.playerId })
        assertEquals(listOf(1, 2, 3), board.rows.map { it.position })
    }

    @Test
    fun `the achievements board reorders the same players`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("a", cells = 10, badges = 40), entry("b", cells = 90, badges = 3)),
            category = LeaderboardCategory.ACHIEVEMENTS,
            countryCode = "AZ",
            currentPlayerId = null
        )

        assertEquals(listOf("a", "b"), board.rows.map { it.entry.playerId })
        assertEquals(listOf(40, 3), board.rows.map { it.score })
    }

    @Test
    fun `players on the same score share a position and the next one skips`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(
                entry("a", cells = 100),
                entry("b", cells = 50),
                entry("c", cells = 50),
                entry("d", cells = 10)
            ),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = null
        )

        assertEquals(listOf(1, 2, 2, 4), board.rows.map { it.position })
    }

    @Test
    fun `only the country asked about is on the board`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("home", cells = 5), entry("away", cells = 900, country = "TR")),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = "home"
        )

        assertEquals(listOf("home"), board.rows.map { it.entry.playerId })
        assertEquals(1, board.playerCount)
    }

    @Test
    fun `the country is matched regardless of case`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("a", cells = 5, country = "az")),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = null
        )

        assertEquals(1, board.playerCount)
        assertEquals("AZ", board.countryCode)
    }

    @Test
    fun `the current player is marked and findable through playerRow`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("rival", cells = 100), entry("me", cells = 20)),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = "me"
        )

        val player = board.playerRow
        assertEquals(2, player?.position)
        assertEquals(20, player?.score)
        assertTrue(board.rows.count { it.isCurrentPlayer } == 1)
    }

    @Test
    fun `a player not on the board has no row`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("rival", cells = 100)),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = "me"
        )

        assertNull(board.playerRow)
    }

    @Test
    fun `a duplicate row for the same player is collapsed`() {
        val board = LeaderboardRanking.rank(
            entries = listOf(entry("me", cells = 10), entry("me", cells = 40)),
            category = LeaderboardCategory.CELLS,
            countryCode = "AZ",
            currentPlayerId = "me"
        )

        assertEquals(1, board.playerCount)
        assertEquals(40, board.rows.single().score)
    }

    @Test
    fun `tied players keep the same order every time they are ranked`() {
        val entries = listOf(
            entry("z", cells = 50, nickname = "Zaur"),
            entry("a", cells = 50, nickname = "Aysel")
        )

        val first = LeaderboardRanking.rank(entries, LeaderboardCategory.CELLS, "AZ", null)
        val second = LeaderboardRanking.rank(entries.reversed(), LeaderboardCategory.CELLS, "AZ", null)

        assertEquals(first.rows.map { it.entry.playerId }, second.rows.map { it.entry.playerId })
    }

    @Test
    fun `an empty country produces an empty board rather than failing`() {
        val board = LeaderboardRanking.rank(emptyList(), LeaderboardCategory.CELLS, "AZ", "me")

        assertEquals(0, board.playerCount)
        assertNull(board.playerRow)
    }
}
