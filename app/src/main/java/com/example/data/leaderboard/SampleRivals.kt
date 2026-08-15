package com.example.data.leaderboard

import com.example.domain.model.LeaderboardEntry
import kotlin.random.Random

/**
 * The benchmark field a player is placed against until there is a server with real players on it.
 *
 * Generated from the country code alone, so a given country's field is the same on every launch and
 * on every device - a position that moved because the app was restarted would be worse than no
 * position at all. The numbers are drawn once from a seeded generator, not stored, so this costs
 * nothing to ship.
 *
 * Deliberately confined to the data layer: to the rest of the app these arrive through
 * [com.example.domain.repository.LeaderboardRepository] exactly like rows from a backend would, so
 * replacing this file with a network call changes nothing above it.
 */
internal object SampleRivals {

    /** Handles, walked-sounding rather than realistic names - nobody here is a real person. */
    private val HANDLES = listOf(
        "Xəzər", "Səyyah", "Yolçu", "İzsürən", "Şimşək", "Qartal", "Ceyran", "Şahin",
        "Tufan", "Zirvə", "Meşəçi", "Günəş", "Ulduz", "Kölgə", "Cığır", "Addım",
        "Alov", "Buzlaq", "Qranit", "Dəniz", "Külək", "Nar", "Qala", "Pusqu"
    )

    private val cache = HashMap<String, List<LeaderboardEntry>>()

    fun forCountry(countryCode: String): List<LeaderboardEntry> {
        val code = countryCode.uppercase()
        return cache.getOrPut(code) { generate(code) }
    }

    private fun generate(code: String): List<LeaderboardEntry> {
        val random = Random(code.hashCode().toLong())
        val size = MIN_FIELD + random.nextInt(MAX_FIELD - MIN_FIELD + 1)

        return (0 until size).map { index ->
            val handle = HANDLES[random.nextInt(HANDLES.size)]
            val cells = random.nextInt(MAX_CELLS)

            LeaderboardEntry(
                // Indexed so two draws of the same handle are still two different players.
                playerId = "sample-$code-$index",
                nickname = "$handle${random.nextInt(10, 100)}",
                countryCode = code,
                exploredCells = cells,
                // Badges track ground covered rather than being drawn independently - a field where
                // the top explorer had the fewest badges would read as broken.
                unlockedAchievements = (cells / CELLS_PER_BADGE + random.nextInt(BADGE_SPREAD))
                    .coerceAtMost(MAX_BADGES)
            )
        }
    }

    private const val MIN_FIELD = 12
    private const val MAX_FIELD = 23
    private const val MAX_CELLS = 1400
    private const val CELLS_PER_BADGE = 28
    private const val BADGE_SPREAD = 7
    private const val MAX_BADGES = 90
}
