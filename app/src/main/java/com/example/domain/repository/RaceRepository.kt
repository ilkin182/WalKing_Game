package com.example.domain.repository

import com.example.domain.model.Race
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRoster
import kotlinx.coroutines.flow.Flow

/**
 * Where races and the people in them live.
 *
 * Kept as a port for the same reason as [LeaderboardRepository]: the implementation behind it today
 * is on-device ([com.example.data.repository.LocalRaceRepository]), and swapping in a backend
 * - Firestore, an API - must not touch a single line above this interface. Everything a real
 * implementation would need is already in the signatures: [join] takes the whole [Race] rather than
 * an id, because a player arriving from a share link is asking about a race their device has never
 * seen, and capacity is checked inside [join] rather than by the caller, because on a server the gap
 * between "there is room" and "take the slot" is where two people get the last place at once.
 */
interface RaceRepository {

    /** Every race [playerId] is in, newest first. Includes finished ones - results are worth keeping. */
    fun observeRosters(playerId: String): Flow<List<RaceRoster>>

    /** The race carrying [code], if this device knows one. */
    suspend fun findByCode(code: String): RaceRoster?

    /**
     * Stores a new race with its host already in it.
     *
     * The host occupies one of the slots they set: a race for four is a race between four people,
     * not the host plus four.
     */
    suspend fun create(race: Race, host: RaceParticipant): RaceRoster

    /**
     * Puts [participant] in [race], creating the race locally if this device has not seen it.
     *
     * Returns the roster as it stands afterwards, or null if the race was full - the capacity check
     * and the insert are one operation on purpose, see the class comment.
     */
    suspend fun join(race: Race, participant: RaceParticipant): RaceRoster?

    /** Records a runner's latest figure. Silently does nothing if they are not in the race. */
    suspend fun updateScore(raceId: String, playerId: String, score: Double, at: Long)

    /** Takes a runner out. The host leaving does not end the race for everybody else. */
    suspend fun leave(raceId: String, playerId: String)

    /** Removes a race and its roster outright - what the host does when they call one off. */
    suspend fun delete(raceId: String)
}
