package com.example.domain.usecase

import com.example.domain.model.Race
import com.example.domain.model.RaceCreationResult
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceJoinResult
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRejection
import com.example.domain.model.RaceRoster
import com.example.domain.race.RaceCode
import com.example.domain.race.RaceInvite
import com.example.domain.repository.PlayerIdentityRepository
import com.example.domain.repository.RaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val HOUR = 60 * 60 * 1000L
private const val DAY = 24 * HOUR

/** The repository's own rules (capacity, "the stored race wins") kept, the storage thrown away. */
private class InMemoryRaceRepository : RaceRepository {
    val rosters = MutableStateFlow<Map<String, RaceRoster>>(emptyMap())

    override fun observeRosters(playerId: String): Flow<List<RaceRoster>> =
        rosters.map { all -> all.values.filter { it.contains(playerId) } }

    override suspend fun findByCode(code: String): RaceRoster? =
        rosters.value.values.firstOrNull { it.race.code == code }

    override suspend fun create(race: Race, host: RaceParticipant): RaceRoster {
        val roster = RaceRoster(race, listOf(host))
        rosters.value = rosters.value + (race.id to roster)
        return roster
    }

    override suspend fun join(race: Race, participant: RaceParticipant): RaceRoster? {
        val stored = rosters.value[race.id]
        val current = stored ?: RaceRoster(race, emptyList())
        if (current.contains(participant.playerId)) return current
        if (!current.race.hasRoomFor(current.participantCount)) return null

        val joined = current.copy(participants = current.participants + participant)
        rosters.value = rosters.value + (current.race.id to joined)
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
            raceId to roster.copy(
                participants = roster.participants.filterNot { it.playerId == playerId }
            )
            )
    }

    override suspend fun delete(raceId: String) {
        rosters.value = rosters.value - raceId
    }
}

private class FixedIdentity(override val playerId: String) : PlayerIdentityRepository

class CreateRaceUseCaseTest {

    private val races = InMemoryRaceRepository()
    private val createRace = CreateRaceUseCase(races, FixedIdentity("p-host"), now = { NOW })

    private suspend fun create(
        name: String = "Həftəlik yarış",
        maxParticipants: Int = 8,
        endsAt: Long = NOW + 7 * DAY,
        code: String? = null,
        format: RaceFormat = RaceFormat.VERSUS,
        targetScore: Double? = null
    ) = createRace(
        name = name,
        mode = RaceMode.CELLS,
        maxParticipants = maxParticipants,
        endsAt = endsAt,
        hostNickname = "Elçin",
        hostCountryCode = "AZ",
        format = format,
        targetScore = targetScore,
        requestedCode = code
    )

    @Test
    fun `a created race has the host in it and a usable code`() = runTest {
        val result = create()

        assertTrue(result is RaceCreationResult.Created)
        val roster = (result as RaceCreationResult.Created).roster
        assertEquals(1, roster.participantCount)
        assertEquals("p-host", roster.race.hostId)
        assertTrue(roster.contains("p-host"))
        assertTrue(RaceCode.isValid(roster.race.code))
    }

    @Test
    fun `the host takes one of the slots they set`() = runTest {
        val roster = (create(maxParticipants = 2) as RaceCreationResult.Created).roster

        assertEquals(1, roster.freeSlots)
    }

    @Test
    fun `a race with no name is refused`() = runTest {
        val result = create(name = "   ")

        assertEquals(RaceRejection.EMPTY_NAME, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `a deadline in the past is refused`() = runTest {
        val result = create(endsAt = NOW - HOUR)

        assertEquals(RaceRejection.DEADLINE_PASSED, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `a race too short to fit a walk is refused`() = runTest {
        val result = create(endsAt = NOW + 60_000L)

        assertEquals(RaceRejection.TOO_SHORT, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `a race longer than three months is refused`() = runTest {
        val result = create(endsAt = NOW + 200 * DAY)

        assertEquals(RaceRejection.TOO_LONG, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `sizes outside the allowed range are refused`() = runTest {
        assertEquals(
            RaceRejection.TOO_FEW_PARTICIPANTS,
            (create(maxParticipants = 1) as RaceCreationResult.Rejected).reason
        )
        assertEquals(
            RaceRejection.TOO_MANY_PARTICIPANTS,
            (create(maxParticipants = 500) as RaceCreationResult.Rejected).reason
        )
    }

    @Test
    fun `the host can choose the code their friends will type`() = runTest {
        val roster = (create(code = "park24") as RaceCreationResult.Created).roster

        assertEquals("PARK24", roster.race.code)
    }

    @Test
    fun `a code already in use is refused instead of shadowing the other race`() = runTest {
        create(code = "PARK24")

        val result = create(code = "PARK24")

        assertEquals(RaceRejection.CODE_TAKEN, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `a co-op race keeps the goal the team is walking towards`() = runTest {
        val result = create(format = RaceFormat.COOP, targetScore = 5_000.0)

        val race = (result as RaceCreationResult.Created).roster.race
        assertEquals(RaceFormat.COOP, race.format)
        assertEquals(5_000.0, race.teamTarget!!, 0.0001)
    }

    @Test
    fun `a co-op race with no goal is refused - there would be nothing to walk towards`() = runTest {
        assertEquals(
            RaceRejection.NO_TARGET,
            (create(format = RaceFormat.COOP) as RaceCreationResult.Rejected).reason
        )
        assertEquals(
            RaceRejection.NO_TARGET,
            (create(format = RaceFormat.COOP, targetScore = 0.0) as RaceCreationResult.Rejected).reason
        )
    }

    @Test
    fun `a goal nobody could reach is refused`() = runTest {
        val result = create(format = RaceFormat.COOP, targetScore = Race.MAX_TARGET * 10)

        assertEquals(RaceRejection.TARGET_TOO_LARGE, (result as RaceCreationResult.Rejected).reason)
    }

    @Test
    fun `a versus race carries no goal even if one is handed to it`() = runTest {
        val race = (create(targetScore = 5_000.0) as RaceCreationResult.Created).roster.race

        assertNull(race.targetScore)
        assertNull(race.teamTarget)
    }

    @Test
    fun `a code the alphabet does not allow is refused`() = runTest {
        val result = create(code = "PARK2O")

        assertEquals(RaceRejection.BAD_CODE, (result as RaceCreationResult.Rejected).reason)
    }
}

class JoinRaceUseCaseTest {

    private val races = InMemoryRaceRepository()
    private val hostIdentity = FixedIdentity("p-host")

    private suspend fun hostARace(maxParticipants: Int = 4, endsAt: Long = NOW + 7 * DAY): Race {
        val result = CreateRaceUseCase(races, hostIdentity, now = { NOW })(
            name = "Həftəlik yarış",
            mode = RaceMode.CELLS,
            maxParticipants = maxParticipants,
            endsAt = endsAt,
            hostNickname = "Elçin",
            hostCountryCode = "AZ"
        )
        return (result as RaceCreationResult.Created).roster.race
    }

    private fun joinAs(playerId: String) =
        JoinRaceUseCase(races, FixedIdentity(playerId)) { NOW }

    @Test
    fun `a typed code gets a second player into a race this device knows`() = runTest {
        val race = hostARace()

        val result = joinAs("p-friend")(race.code, "Nigar", "AZ")

        assertTrue(result is RaceJoinResult.Joined)
        assertEquals(2, (result as RaceJoinResult.Joined).roster.participantCount)
    }

    @Test
    fun `a share link gets a player into a race this device has never seen`() = runTest {
        val race = hostARace()
        val link = RaceInvite.linkFor(race)
        // A different device: nothing stored, only the link.
        val freshDevice = InMemoryRaceRepository()

        val result = JoinRaceUseCase(freshDevice, FixedIdentity("p-friend")) { NOW }(
            link,
            "Nigar",
            "AZ"
        )

        assertTrue(result is RaceJoinResult.Joined)
        val roster = (result as RaceJoinResult.Joined).roster
        assertEquals(race.code, roster.race.code)
        assertEquals(race.name, roster.race.name)
        assertEquals(race.endsAt, roster.race.endsAt)
        assertTrue(roster.contains("p-friend"))
    }

    @Test
    fun `a bare code for a race this device has never seen cannot conjure one`() = runTest {
        val result = joinAs("p-friend")("ZZZZ22", "Nigar", "AZ")

        assertEquals(RaceJoinResult.Unknown, result)
    }

    @Test
    fun `nonsense in the box is a bad code, not an unknown race`() = runTest {
        assertEquals(RaceJoinResult.BadCode, joinAs("p-friend")("??", "Nigar", "AZ"))
    }

    @Test
    fun `joining twice opens the race rather than taking a second slot`() = runTest {
        val race = hostARace()
        val join = joinAs("p-friend")
        join(race.code, "Nigar", "AZ")

        val again = join(race.code, "Nigar", "AZ")

        assertTrue(again is RaceJoinResult.AlreadyJoined)
        assertEquals(2, (again as RaceJoinResult.AlreadyJoined).roster.participantCount)
    }

    @Test
    fun `a full race turns the next player away`() = runTest {
        val race = hostARace(maxParticipants = 2)
        joinAs("p-second")(race.code, "Nigar", "AZ")

        val result = joinAs("p-third")(race.code, "Aysel", "AZ")

        assertTrue(result is RaceJoinResult.Full)
        assertEquals(2, (result as RaceJoinResult.Full).roster.participantCount)
    }

    @Test
    fun `a finished race cannot be joined`() = runTest {
        val race = hostARace(endsAt = NOW + 2 * HOUR)

        val result = JoinRaceUseCase(races, FixedIdentity("p-late")) { NOW + 3 * HOUR }(
            race.code,
            "Nigar",
            "AZ"
        )

        assertTrue(result is RaceJoinResult.Finished)
    }

    @Test
    fun `an edited link cannot widen a race this device already knows`() = runTest {
        val race = hostARace(maxParticipants = 2)
        joinAs("p-second")(race.code, "Nigar", "AZ")
        val tampered = RaceInvite.linkFor(race.copy(maxParticipants = 50))

        val result = joinAs("p-third")(tampered, "Aysel", "AZ")

        assertTrue(result is RaceJoinResult.Full)
    }
}

class RaceMembershipUseCasesTest {

    private val races = InMemoryRaceRepository()

    private suspend fun hostARace(): Race {
        val result = CreateRaceUseCase(races, FixedIdentity("p-host"), now = { NOW })(
            name = "Həftəlik yarış",
            mode = RaceMode.DISTANCE,
            maxParticipants = 4,
            endsAt = NOW + 7 * DAY,
            hostNickname = "Elçin",
            hostCountryCode = "AZ"
        )
        return (result as RaceCreationResult.Created).roster.race
    }

    @Test
    fun `publishing a score updates only the publisher's own row`() = runTest {
        val race = hostARace()
        JoinRaceUseCase(races, FixedIdentity("p-friend")) { NOW }(race.code, "Nigar", "AZ")

        PublishRaceScoreUseCase(races, FixedIdentity("p-friend")) { NOW + HOUR }(race.id, 4.2)

        val roster = races.findByCode(race.code)!!
        assertEquals(4.2, roster.participants.first { it.playerId == "p-friend" }.score, 0.0001)
        assertEquals(0.0, roster.participants.first { it.playerId == "p-host" }.score, 0.0001)
    }

    @Test
    fun `leaving removes the player but not the race`() = runTest {
        val race = hostARace()
        JoinRaceUseCase(races, FixedIdentity("p-friend")) { NOW }(race.code, "Nigar", "AZ")

        LeaveRaceUseCase(races, FixedIdentity("p-friend"))(race.id)

        val roster = races.findByCode(race.code)
        assertEquals(1, roster?.participantCount)
    }

    @Test
    fun `the host cancelling takes the race away entirely`() = runTest {
        val race = hostARace()

        DeleteRaceUseCase(races)(race.id)

        assertEquals(null, races.findByCode(race.code))
    }
}
