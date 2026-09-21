package com.example.data.repository

import com.example.data.local.dao.RaceDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Race
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRoster
import com.example.domain.repository.RaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Races while the app has no server, in the same shape one with a server would have.
 *
 * ## What actually works, and what does not
 *
 * More of this is real than the leaderboard's on-device stand-in. A race the player creates is a
 * real race: it is stored, it has a real code, it counts real walking against a real deadline, and
 * the invitation link carries the whole race definition
 * ([com.example.domain.race.RaceInvite]) - so a friend who taps that link genuinely ends up in the
 * same race, on their own phone, with the same code, the same mode and the same finish time. Every
 * part of the flow the player performs is the real thing.
 *
 * What a server would add is the part no device can do alone: **carrying the runners' scores between
 * phones**. Each device can only measure its own walking, so each device shows its own figure live
 * and everyone else's as it stood when they last met. The races screen says so plainly rather than
 * letting a roster of stale zeroes pass for a live scoreboard.
 *
 * Swapping in a backend is one line in `AppContainer`: [RaceRepository] is already the interface a
 * networked implementation needs, and the atomic capacity check the port asks for is implemented
 * here as a database transaction ([RaceDao.joinIfRoom]) exactly where a server would implement it as
 * a conditional write.
 */
class LocalRaceRepository(private val dao: RaceDao) : RaceRepository {

    override fun observeRosters(playerId: String): Flow<List<RaceRoster>> =
        combine(dao.observeRaces(), dao.observeParticipants()) { races, participants ->
            val rostersByRace = participants.groupBy { it.raceId }
            races.mapNotNull { race ->
                val roster = rostersByRace[race.id].orEmpty()
                // Races the player is not in are still on the device - a link opened and then not
                // joined leaves one behind - and are none of their business until they join.
                if (roster.none { it.playerId == playerId }) return@mapNotNull null
                RaceRoster(
                    race = race.toDomain(),
                    participants = roster.map { it.toDomain() }.sortedBy { it.joinedAt }
                )
            }
        }

    override suspend fun findByCode(code: String): RaceRoster? {
        val race = dao.raceByCode(code) ?: return null
        return RaceRoster(
            race = race.toDomain(),
            participants = dao.participantsOf(race.id).map { it.toDomain() }
        )
    }

    override suspend fun create(race: Race, host: RaceParticipant): RaceRoster {
        dao.createWithHost(race.toEntity(), host.toEntity())
        return RaceRoster(race = race, participants = listOf(host))
    }

    override suspend fun join(race: Race, participant: RaceParticipant): RaceRoster? {
        val seated = dao.joinIfRoom(race.toEntity(), participant.toEntity())
        if (!seated) return null
        // Read back rather than assembling from the arguments: the stored race wins over the copy
        // that arrived in the link, and the roster now includes everyone already there.
        return findByCode(race.code)
    }

    override suspend fun updateScore(raceId: String, playerId: String, score: Double, at: Long) {
        dao.updateScore(raceId, playerId, score, at)
    }

    override suspend fun leave(raceId: String, playerId: String) {
        dao.deleteParticipant(raceId, playerId)
    }

    override suspend fun delete(raceId: String) {
        dao.removeRace(raceId)
    }
}
