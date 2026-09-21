package com.example.domain.race

import com.example.domain.model.RaceBoard
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRoster
import com.example.domain.model.RaceStanding
import com.example.domain.model.RaceTeamProgress

/**
 * Turns a race's roster into a board - a ranking in a versus race, a shared total in a co-op one.
 *
 * The ordering is the same either way, and deliberately so: biggest first is how a list of people
 * and numbers is read, whatever the numbers mean. What changes is what the board *claims*. A versus
 * board claims positions, ties included (see [position]); a co-op board claims a team total and each
 * person's share of it, and has no winner at all.
 */
object RaceStandings {

    fun board(roster: RaceRoster, currentPlayerId: String?, now: Long): RaceBoard {
        val ordered = roster.participants
            // One row per player: a re-join after leaving must not show up as a second runner.
            .associateBy { it.playerId }
            .values
            .sortedWith(
                compareByDescending<RaceParticipant> { it.score }
                    .thenBy { it.joinedAt }
                    .thenBy { it.nickname.lowercase() }
                    .thenBy { it.playerId }
            )

        val total = ordered.sumOf { it.score }

        var previousScore: Double? = null
        var previousPosition = 0

        val rows = ordered.mapIndexed { index, participant ->
            val score = participant.score
            val position = if (previousScore != null && score == previousScore) {
                previousPosition
            } else {
                index + 1
            }
            previousScore = score
            previousPosition = position

            RaceStanding(
                position = position,
                participant = participant,
                score = score,
                isCurrentPlayer = currentPlayerId != null && participant.playerId == currentPlayerId,
                share = if (total > 0.0) (score / total).toFloat() else 0f
            )
        }

        return RaceBoard(
            race = roster.race,
            status = roster.race.statusAt(now),
            rows = rows,
            freeSlots = roster.freeSlots,
            isHost = currentPlayerId != null && roster.race.hostId == currentPlayerId,
            team = teamProgress(roster, total)
        )
    }

    /**
     * The team's number against the goal, for a co-op race. Null for a versus one.
     *
     * ## What the total adds up
     *
     * Everybody's published figure, summed. For distance that is exactly right - two people walking
     * five kilometres each did walk ten. For cells it is *contributions*, not distinct ground: if two
     * teammates both walk down the same street on the same day, that street is new to each of them
     * and counts once for each.
     *
     * Counting it as one would mean every device knowing which cells every other device had claimed
     * - the exact positions of everybody in the race, on everybody's phone - which is a great deal
     * of somebody else's map to be shipping around for a tidier number, and impossible at all without
     * a server. Summing contributions rewards the same thing the format is for: each person's own
     * walking counting for the group. The screen says "birgə kəşf" rather than claiming a distinct
     * count, so the number means what it says.
     */
    private fun teamProgress(roster: RaceRoster, total: Double): RaceTeamProgress? {
        if (roster.race.format != RaceFormat.COOP) return null
        val target = roster.race.teamTarget ?: return null

        return RaceTeamProgress(
            total = total,
            target = target,
            remaining = (target - total).coerceAtLeast(0.0),
            fraction = (total / target).toFloat().coerceIn(0f, 1f),
            isReached = total >= target
        )
    }
}
