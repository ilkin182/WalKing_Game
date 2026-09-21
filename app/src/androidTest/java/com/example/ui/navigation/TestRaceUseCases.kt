package com.example.ui.navigation

import com.example.domain.model.Race
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRoster
import com.example.domain.model.StompedHex
import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.model.CellContext
import com.example.domain.model.PlaceInfo
import com.example.domain.repository.PlayerIdentityRepository
import com.example.domain.repository.RaceRepository
import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.repository.WalkSessionRepository
import com.example.domain.usecase.CreateRaceUseCase
import com.example.domain.usecase.DeleteRaceUseCase
import com.example.domain.usecase.GetPlayerIdUseCase
import com.example.domain.usecase.JoinRaceUseCase
import com.example.domain.usecase.LeaveRaceUseCase
import com.example.domain.usecase.ObserveCountryUseCase
import com.example.domain.usecase.ObserveExploredCellsUseCase
import com.example.domain.usecase.ObserveMyRacesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObserveWalkSessionsUseCase
import com.example.domain.usecase.PublishRaceScoreUseCase
import com.example.ui.race.RaceUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Races held in a map instead of Room, so a UI test gets the real create/join/score behaviour with
 * no database to stand up or tear down between cases.
 */
private class InMemoryRaceRepository : RaceRepository {
    private val rosters = MutableStateFlow<Map<String, RaceRoster>>(emptyMap())

    override fun observeRosters(playerId: String): Flow<List<RaceRoster>> =
        rosters.map { all ->
            all.values.filter { it.contains(playerId) }.sortedByDescending { it.race.createdAt }
        }

    override suspend fun findByCode(code: String): RaceRoster? =
        rosters.value.values.firstOrNull { it.race.code == code }

    override suspend fun create(race: Race, host: RaceParticipant): RaceRoster {
        val roster = RaceRoster(race, listOf(host))
        rosters.value = rosters.value + (race.id to roster)
        return roster
    }

    override suspend fun join(race: Race, participant: RaceParticipant): RaceRoster? {
        val existing = rosters.value[race.id] ?: RaceRoster(race, emptyList())
        if (existing.contains(participant.playerId)) return existing
        if (!existing.race.hasRoomFor(existing.participantCount)) return null

        val joined = existing.copy(participants = existing.participants + participant)
        rosters.value = rosters.value + (race.id to joined)
        return joined
    }

    override suspend fun updateScore(raceId: String, playerId: String, score: Double, at: Long) {
        val roster = rosters.value[raceId] ?: return
        rosters.value = rosters.value + (
            raceId to roster.copy(
                participants = roster.participants.map {
                    if (it.playerId == playerId) it.copy(score = score, updatedAt = at) else it
                }
            )
            )
    }

    override suspend fun leave(raceId: String, playerId: String) {
        val roster = rosters.value[raceId] ?: return
        rosters.value = rosters.value + (
            raceId to roster.copy(participants = roster.participants.filterNot { it.playerId == playerId })
            )
    }

    override suspend fun delete(raceId: String) {
        rosters.value = rosters.value - raceId
    }
}

private class FixedPlayerIdentity : PlayerIdentityRepository {
    override val playerId: String = "test-player"
}

private class RaceTestStatsRepository : UserStatsRepository {
    override val nickname: Flow<String> = MutableStateFlow("Tester")
    override val totalDistanceWalked: Flow<Double> = MutableStateFlow(0.0)
    override val statsStartTimestamp: Flow<Long> = MutableStateFlow(0L)
    override val closedLoops: Flow<Int> = MutableStateFlow(0)
    override val countryCode: Flow<String?> = MutableStateFlow("AZ")
    override fun updateNickname(name: String) {}
    override fun updateCountry(code: String) {}
    override fun addDistance(deltaMeters: Double) {}
    override fun recordClosedLoops(count: Int) {}
    override fun resetStats() {}
}

private class RaceTestCellsRepository : StompedHexRepository {
    override val stompedHexes: Flow<List<StompedHex>> = MutableStateFlow(emptyList())
    override suspend fun getAll(): List<StompedHex> = emptyList()
    override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {}
    override suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String?,
        context: CellContext?
    ) {}
    override suspend fun cellsMissingElevation(limit: Int): List<String> = emptyList()
    override suspend fun setElevations(elevations: Map<String, Double>) {}
    override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> = emptyList()
    override suspend fun setPlaces(places: Map<String, PlaceInfo>) {}
    override suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String?
    ) {}
    override suspend fun unstomp(hexAddress: String) {}
    override suspend fun clearAll() {}
}

private class RaceTestWalkRepository : WalkSessionRepository {
    override val sessions: Flow<List<WalkSession>> = MutableStateFlow(emptyList())
    override val routes: Flow<List<WalkRoute>> = MutableStateFlow(emptyList())
    override suspend fun startSession(startedAt: Long) {}
    override suspend fun addDistance(meters: Double, at: Long) {}
    override suspend fun recordPoint(lat: Double, lng: Double, at: Long) {}
    override suspend fun endSession() {}
    override suspend fun clearAll() {}
}

/** A fully wired RaceUseCases bundle backed by in-memory fakes, for UI/navigation tests. */
fun createTestRaceUseCases(): RaceUseCases {
    val races = InMemoryRaceRepository()
    val identity = FixedPlayerIdentity()
    val stats = RaceTestStatsRepository()

    return RaceUseCases(
        observeMyRaces = ObserveMyRacesUseCase(races, identity),
        createRace = CreateRaceUseCase(races, identity),
        joinRace = JoinRaceUseCase(races, identity),
        leaveRace = LeaveRaceUseCase(races, identity),
        deleteRace = DeleteRaceUseCase(races),
        publishRaceScore = PublishRaceScoreUseCase(races, identity),
        playerId = GetPlayerIdUseCase(identity),
        observeNickname = ObserveNicknameUseCase(stats),
        observeCountry = ObserveCountryUseCase(stats),
        observeExploredCells = ObserveExploredCellsUseCase(RaceTestCellsRepository()),
        observeWalkSessions = ObserveWalkSessionsUseCase(RaceTestWalkRepository())
    )
}
