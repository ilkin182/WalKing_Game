package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.RaceEntity
import com.example.data.local.entity.RaceParticipantEntity
import kotlinx.coroutines.flow.Flow

/**
 * An abstract class rather than the interface the other DAOs are, because [joinIfRoom] needs a body:
 * counting the runners and taking a slot has to be one transaction, or two invitations opened at the
 * same moment both see the last place free.
 */
@Dao
abstract class RaceDao {

    /**
     * Every race on the device, not only the player's.
     *
     * The repository filters by membership. The volume here is a handful of rows - a person is in a
     * few races, not a few thousand - and one flow over everything is far less machinery than a
     * parameterised query plus a second one for the rosters that would have to be kept in step.
     */
    @Query("SELECT * FROM races ORDER BY createdAt DESC")
    abstract fun observeRaces(): Flow<List<RaceEntity>>

    @Query("SELECT * FROM race_participants ORDER BY joinedAt")
    abstract fun observeParticipants(): Flow<List<RaceParticipantEntity>>

    @Query("SELECT * FROM races WHERE code = :code LIMIT 1")
    abstract suspend fun raceByCode(code: String): RaceEntity?

    @Query("SELECT * FROM races WHERE id = :raceId")
    abstract suspend fun raceById(raceId: String): RaceEntity?

    @Query("SELECT * FROM race_participants WHERE raceId = :raceId ORDER BY joinedAt")
    abstract suspend fun participantsOf(raceId: String): List<RaceParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRace(race: RaceEntity)

    /**
     * A race arriving from a link must not overwrite the local copy: the local one has the real
     * roster behind it, while the link is a snapshot from whenever it was written.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRaceIfAbsent(race: RaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertParticipant(participant: RaceParticipantEntity)

    @Query(
        "UPDATE race_participants SET score = :score, updatedAt = :updatedAt " +
            "WHERE raceId = :raceId AND playerId = :playerId"
    )
    abstract suspend fun updateScore(raceId: String, playerId: String, score: Double, updatedAt: Long)

    @Query("DELETE FROM race_participants WHERE raceId = :raceId AND playerId = :playerId")
    abstract suspend fun deleteParticipant(raceId: String, playerId: String)

    @Query("DELETE FROM race_participants WHERE raceId = :raceId")
    abstract suspend fun deleteParticipantsOf(raceId: String)

    @Query("DELETE FROM races WHERE id = :raceId")
    abstract suspend fun deleteRace(raceId: String)

    /** Stores a race and seats its host in one go, so a race never exists with nobody in it. */
    @Transaction
    open suspend fun createWithHost(race: RaceEntity, host: RaceParticipantEntity) {
        upsertRace(race)
        upsertParticipant(host)
    }

    /**
     * Takes a slot if there is one, and reports whether it did.
     *
     * A player already in the race keeps their row untouched - re-opening an invitation must not
     * reset the score they have built up - and does not consume a second slot.
     *
     * The capacity that counts is the *stored* race's, never the one that arrived with the joiner:
     * an invitation is editable text, and the host's limit is not up for renegotiation by whoever
     * forwards the link.
     */
    @Transaction
    open suspend fun joinIfRoom(race: RaceEntity, participant: RaceParticipantEntity): Boolean {
        insertRaceIfAbsent(race)
        val stored = raceById(race.id) ?: race

        val existing = participantsOf(stored.id)
        if (existing.any { it.playerId == participant.playerId }) return true
        if (existing.size >= stored.maxParticipants) return false

        upsertParticipant(participant)
        return true
    }

    @Transaction
    open suspend fun removeRace(raceId: String) {
        deleteParticipantsOf(raceId)
        deleteRace(raceId)
    }
}
