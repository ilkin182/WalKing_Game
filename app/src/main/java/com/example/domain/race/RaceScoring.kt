package com.example.domain.race

import com.example.domain.model.ExploredCell
import com.example.domain.model.RaceMode
import com.example.domain.model.WalkSession

/**
 * How far the player has got in a race, measured from what their own device wrote down.
 *
 * Only the race window counts. That is the whole point of a race next to the country board: the
 * board ranks everything a player has ever done, so a newcomer can never catch a veteran, while a
 * race starts everyone at zero on the same morning.
 *
 * Two consequences worth knowing, both deliberate:
 *
 *  - **[RaceMode.CELLS] rewards new ground, not repeated ground.** A cell is recorded the first time
 *    it is walked into and never again, so a lap of the same park scores once. Walking somewhere you
 *    have never been is how you win a cell race, which is the behaviour the game exists to encourage.
 *  - **A walk belongs to the window it started in.** A session only knows when it began, how long it
 *    ran and how far it went, so a walk that crosses the finish line counts in full rather than
 *    being split at a boundary nobody was watching. Starting a walk one minute before the deadline
 *    to bank an hour of it is possible in principle; between friends who invited each other it is
 *    not a threat worth mangling the maths for.
 */
object RaceScoring {

    private const val METERS_PER_KM = 1000.0

    /**
     * The player's score in [mode] for the window [startsAt] until [endsAt], as of [now].
     *
     * [now] clamps the window's end so a race still running is scored up to this moment and a
     * finished one stays frozen at its deadline - reopening an old race must not let later walking
     * change a result that is already settled.
     */
    fun score(
        mode: RaceMode,
        startsAt: Long,
        endsAt: Long,
        now: Long,
        cells: List<ExploredCell>,
        sessions: List<WalkSession>
    ): Double {
        val windowEnd = minOf(endsAt, now)
        if (windowEnd <= startsAt) return 0.0

        return when (mode) {
            RaceMode.CELLS -> cells.count { it.exploredAt in startsAt..windowEnd }.toDouble()

            RaceMode.DISTANCE -> sessionsIn(sessions, startsAt, windowEnd)
                .sumOf { it.distanceMeters } / METERS_PER_KM

            RaceMode.LONGEST_WALK -> (
                sessionsIn(sessions, startsAt, windowEnd)
                    .maxOfOrNull { it.distanceMeters } ?: 0.0
                ) / METERS_PER_KM
        }
    }

    private fun sessionsIn(sessions: List<WalkSession>, from: Long, until: Long): List<WalkSession> =
        sessions.filter { it.startedAt in from..until }
}
