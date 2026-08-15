package com.example.data.repository

import com.example.domain.model.LeaderboardEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLeaderboardRepositoryTest {

    private fun player(country: String, cells: Int = 12) = LeaderboardEntry(
        playerId = "local-player",
        nickname = "Tester",
        countryCode = country,
        exploredCells = cells,
        unlockedAchievements = 3
    )

    @Test
    fun `a country has a field to be ranked against before anything is published`() = runTest {
        val repository = LocalLeaderboardRepository()

        val entries = repository.observeCountry("AZ").first()

        assertTrue("Expected a benchmark field, got ${entries.size}", entries.isNotEmpty())
        assertTrue(entries.all { it.countryCode == "AZ" })
    }

    @Test
    fun `the same country gives the same field every time`() = runTest {
        val first = LocalLeaderboardRepository().observeCountry("AZ").first()
        val second = LocalLeaderboardRepository().observeCountry("AZ").first()

        assertEquals(first, second)
    }

    @Test
    fun `different countries get different fields`() = runTest {
        val repository = LocalLeaderboardRepository()

        val here = repository.observeCountry("AZ").first().map { it.playerId }
        val there = repository.observeCountry("TR").first().map { it.playerId }

        assertTrue(here.intersect(there.toSet()).isEmpty())
    }

    @Test
    fun `publishing puts the player on their own country's board`() = runTest {
        val repository = LocalLeaderboardRepository()
        val before = repository.observeCountry("AZ").first().size

        repository.publish(player("AZ"))
        val after = repository.observeCountry("AZ").first()

        assertEquals(before + 1, after.size)
        assertEquals(1, after.count { it.playerId == "local-player" })
    }

    @Test
    fun `republishing replaces the player's row rather than adding another`() = runTest {
        val repository = LocalLeaderboardRepository()

        repository.publish(player("AZ", cells = 5))
        repository.publish(player("AZ", cells = 40))
        val entries = repository.observeCountry("AZ").first()

        assertEquals(1, entries.count { it.playerId == "local-player" })
        assertEquals(40, entries.single { it.playerId == "local-player" }.exploredCells)
    }

    @Test
    fun `the player does not appear on another country's board`() = runTest {
        val repository = LocalLeaderboardRepository()

        repository.publish(player("AZ"))
        val elsewhere = repository.observeCountry("TR").first()

        assertTrue(elsewhere.none { it.playerId == "local-player" })
    }
}
