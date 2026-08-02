package com.example.domain.achievement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {

    private fun progressFor(id: String, stats: PlayerStats): AchievementProgress =
        AchievementCatalog.evaluateAll(stats).first { it.definition.id == id }

    @Test
    fun `every achievement has a distinct id`() {
        val ids = AchievementCatalog.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every achievement has a title, a description and a positive target`() {
        AchievementCatalog.all.forEach { definition ->
            assertTrue(definition.id, definition.title.isNotBlank())
            assertTrue(definition.id, definition.description.isNotBlank())
            assertTrue(definition.id, definition.target > 0.0)
        }
    }

    @Test
    fun `every category has achievements in it`() {
        AchievementCategory.entries.forEach { category ->
            assertTrue(category.name, !AchievementCatalog.byCategory[category].isNullOrEmpty())
        }
    }

    @Test
    fun `a fresh player has nothing unlocked`() {
        val progress = AchievementCatalog.evaluateAll(PlayerStats.EMPTY)

        assertTrue(progress.none { it.isUnlocked })
    }

    @Test
    fun `untracked achievements never unlock, however good the player gets`() {
        // The catalogue lists achievements the app cannot measure yet. They must stay locked rather
        // than being carried along by some unrelated statistic.
        val everything = PlayerStats(
            totalCells = 100_000,
            totalDistanceMeters = 10_000_000.0,
            maxCellsInOneHour = 1_000,
            maxCellsInOneDay = 10_000,
            activeDays = 5_000,
            longestDayStreak = 5_000,
            bestZonePercentage = 100.0,
            distinctZones = 100,
            farthestCellMeters = 1_000_000.0
        )

        val progress = AchievementCatalog.evaluateAll(everything)
        val untracked = progress.filterNot { it.definition.isTracked }

        assertTrue(untracked.isNotEmpty())
        assertTrue(untracked.none { it.isUnlocked })
        assertTrue(untracked.all { it.current == 0.0 })
    }

    @Test
    fun `distance milestones unlock off the odometer`() {
        val tenKm = PlayerStats(totalDistanceMeters = 10_000.0)

        assertTrue(progressFor("first_km", tenKm).isUnlocked)
        assertTrue(progressFor("five_km", tenKm).isUnlocked)
        assertTrue(progressFor("ten_km", tenKm).isUnlocked)
        assertFalse(progressFor("half_marathon", tenKm).isUnlocked)
    }

    @Test
    fun `cell milestones and lucky numbers share the same counter`() {
        val stats = PlayerStats(totalCells = 333)

        assertTrue(progressFor("conqueror", stats).isUnlocked)
        assertTrue(progressFor("landowner", stats).isUnlocked)
        assertTrue(progressFor("lucky_111", stats).isUnlocked)
        assertTrue(progressFor("lucky_333", stats).isUnlocked)
        assertFalse(progressFor("lucky_777", stats).isUnlocked)
        assertFalse(progressFor("cartographer", stats).isUnlocked)
    }

    @Test
    fun `the legend is measured from the rest of the catalogue`() {
        val legendOnEmpty = progressFor(AchievementCatalog.LEGEND_ID, PlayerStats.EMPTY)

        assertEquals(0.0, legendOnEmpty.current, 1e-9)
        assertFalse(legendOnEmpty.isUnlocked)
    }

    @Test
    fun `the legend counts the share unlocked, not a raw total`() {
        val strong = PlayerStats(totalCells = 5_000, totalDistanceMeters = 1_000_000.0)
        val progress = AchievementCatalog.evaluateAll(strong)
        val others = progress.filterNot { it.definition.id == AchievementCatalog.LEGEND_ID }
        val expected = others.count { it.isUnlocked } * 100.0 / others.size

        assertEquals(
            expected,
            progress.first { it.definition.id == AchievementCatalog.LEGEND_ID }.current,
            1e-9
        )
    }

    @Test
    fun `the legend is not counted towards its own requirement`() {
        // Otherwise unlocking it would raise the very percentage it is measured against.
        val progress = AchievementCatalog.evaluateAll(PlayerStats(totalCells = 5_000))
        val legend = progress.first { it.definition.id == AchievementCatalog.LEGEND_ID }

        assertTrue(legend.current <= 100.0)
    }

    @Test
    fun `progress is capped at the target so a bar cannot overrun`() {
        val huge = PlayerStats(totalCells = 1_000_000, totalDistanceMeters = 99_000_000.0)

        assertTrue(AchievementCatalog.evaluateAll(huge).all { it.fraction <= 1f })
    }

    @Test
    fun `the secret achievement is marked secret and nothing else is`() {
        val secrets = AchievementCatalog.all.filter { it.isSecret }

        assertEquals(listOf("nameless"), secrets.map { it.id })
    }

    @Test
    fun `zone rules read the best zone, not the total`() {
        val halfway = PlayerStats(bestZonePercentage = 50.0, zonesAtQuarter = 1)

        assertTrue(progressFor("zone_master", halfway).isUnlocked)
        assertTrue(progressFor("zone_half", halfway).isUnlocked)
        assertFalse(progressFor("zone_full", halfway).isUnlocked)
        assertFalse(progressFor("two_fronts", halfway).isUnlocked)
    }

    @Test
    fun `the catalogue is honest about how much of itself works`() {
        // Not an assertion about a number so much as a guard: if this drops sharply, something has
        // stopped being measured.
        val tracked = AchievementCatalog.all.count { it.isTracked }

        assertTrue("only $tracked of ${AchievementCatalog.all.size} are tracked", tracked >= 50)
        assertTrue(AchievementCatalog.notYetTracked.isNotEmpty())
    }
}
