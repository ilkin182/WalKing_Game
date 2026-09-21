package com.example.domain.usecase

import com.example.domain.model.Race
import com.example.domain.model.RaceCreationResult
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceJoinResult
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceParticipant
import com.example.domain.model.RaceRejection
import com.example.domain.model.RaceRoster
import com.example.domain.model.RaceStatus
import com.example.domain.race.RaceCode
import com.example.domain.race.RaceInvite
import com.example.domain.repository.PlayerIdentityRepository
import com.example.domain.repository.RaceRepository
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/** Every race the player is in, for the races tab. */
class ObserveMyRacesUseCase(
    private val races: RaceRepository,
    private val identity: PlayerIdentityRepository
) {
    operator fun invoke(): Flow<List<RaceRoster>> = races.observeRosters(identity.playerId)
}

/**
 * Sets up a new race with the player as its host.
 *
 * The rules live here rather than in the form so the same answer comes back however a race is made -
 * the create screen today, a shortcut or a restored draft tomorrow. Every rejection names something
 * the host can change on the screen they are looking at; see [RaceRejection].
 */
class CreateRaceUseCase(
    private val races: RaceRepository,
    private val identity: PlayerIdentityRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default
) {
    /**
     * [requestedCode] lets the host pick the code their friends will be typing instead of taking the
     * draw - the point of a code is that it is the thing being passed around by hand, and a host who
     * wants "PARK24" for the neighbourhood race should get it. It still has to be a code the
     * alphabet allows and one nobody else is using; left null, one is drawn.
     */
    suspend operator fun invoke(
        name: String,
        mode: RaceMode,
        maxParticipants: Int,
        endsAt: Long,
        hostNickname: String,
        hostCountryCode: String?,
        format: RaceFormat = RaceFormat.VERSUS,
        targetScore: Double? = null,
        requestedCode: String? = null,
        startsAt: Long? = null
    ): RaceCreationResult {
        val createdAt = now()
        val start = startsAt ?: createdAt
        val trimmedName = name.trim().take(Race.MAX_NAME_LENGTH)

        reject(trimmedName, maxParticipants, start, endsAt, createdAt, format, targetScore)?.let {
            return RaceCreationResult.Rejected(it)
        }

        val code = when {
            requestedCode.isNullOrBlank() -> freshCode()
            else -> {
                val normalized = RaceCode.normalize(requestedCode)
                    ?: return RaceCreationResult.Rejected(RaceRejection.BAD_CODE)
                if (races.findByCode(normalized) != null) {
                    return RaceCreationResult.Rejected(RaceRejection.CODE_TAKEN)
                }
                normalized
            }
        }

        val race = Race(
            id = "",
            code = "",
            name = trimmedName,
            mode = mode,
            format = format,
            // Dropped for a versus race rather than carried unused: a target on a race nothing reads
            // it from would eventually be read by something.
            targetScore = targetScore.takeIf { format == RaceFormat.COOP },
            maxParticipants = maxParticipants,
            createdAt = createdAt,
            startsAt = start,
            endsAt = endsAt,
            hostId = identity.playerId,
            hostName = hostNickname.trim().take(Race.MAX_NAME_LENGTH).ifBlank { "Təşkilatçı" }
        ).withCode(code)

        val host = RaceParticipant(
            raceId = race.id,
            playerId = identity.playerId,
            nickname = race.hostName,
            countryCode = hostCountryCode,
            joinedAt = createdAt,
            score = 0.0,
            updatedAt = createdAt
        )

        return RaceCreationResult.Created(races.create(race, host))
    }

    private fun reject(
        name: String,
        maxParticipants: Int,
        startsAt: Long,
        endsAt: Long,
        now: Long,
        format: RaceFormat,
        targetScore: Double?
    ): RaceRejection? = when {
        name.isEmpty() -> RaceRejection.EMPTY_NAME
        maxParticipants < Race.MIN_PARTICIPANTS -> RaceRejection.TOO_FEW_PARTICIPANTS
        maxParticipants > Race.MAX_PARTICIPANTS -> RaceRejection.TOO_MANY_PARTICIPANTS
        endsAt <= now -> RaceRejection.DEADLINE_PASSED
        endsAt - startsAt < Race.MIN_DURATION_MILLIS -> RaceRejection.TOO_SHORT
        endsAt - startsAt > Race.MAX_DURATION_MILLIS -> RaceRejection.TOO_LONG
        // A co-op race is a team walking towards a number. Without the number there is nothing on
        // the screen to walk towards, and nothing to tell the team they have finished.
        format == RaceFormat.COOP && (targetScore == null || targetScore <= 0.0 || !targetScore.isFinite()) ->
            RaceRejection.NO_TARGET
        format == RaceFormat.COOP && targetScore != null && targetScore > Race.MAX_TARGET ->
            RaceRejection.TARGET_TOO_LARGE
        else -> null
    }

    /**
     * A code no race on this device is already using.
     *
     * A collision is a one-in-a-billion draw, but two races sharing a code would be one race with
     * two names - the code is the identity ([RaceCode.idOf]) - so it is worth the handful of
     * lookups. After [CODE_ATTEMPTS] tries the last draw is taken: something else is wrong, and
     * refusing to create the race would be a stranger failure than an astronomically unlikely clash.
     */
    private suspend fun freshCode(): String {
        var code = RaceCode.generate(random)
        repeat(CODE_ATTEMPTS) {
            if (races.findByCode(code) == null) return code
            code = RaceCode.generate(random)
        }
        return code
    }

    private fun Race.withCode(code: String): Race = copy(id = RaceCode.idOf(code), code = code)

    private companion object {
        const val CODE_ATTEMPTS = 5
    }
}

/**
 * Gets the player into a race from a typed code or a tapped link.
 *
 * One use case for both because they are the same act: [RaceInvite.parse] reduces a link to a code
 * plus, when the link carried one, the race itself. A race this device already knows always wins
 * over the copy in the link - the local roster has who actually joined in it, and the link is a
 * snapshot from whenever it was written.
 */
class JoinRaceUseCase(
    private val races: RaceRepository,
    private val identity: PlayerIdentityRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        codeOrLink: String,
        nickname: String,
        countryCode: String?
    ): RaceJoinResult {
        val invite = RaceInvite.parse(codeOrLink) ?: return RaceJoinResult.BadCode

        val known = races.findByCode(invite.code)
        val race = known?.race ?: invite.race ?: return RaceJoinResult.Unknown

        if (known != null && known.contains(identity.playerId)) {
            return RaceJoinResult.AlreadyJoined(known)
        }

        val moment = now()
        if (race.statusAt(moment) == RaceStatus.FINISHED) {
            return RaceJoinResult.Finished(known ?: RaceRoster(race, emptyList()))
        }

        val participant = RaceParticipant(
            raceId = race.id,
            playerId = identity.playerId,
            nickname = nickname.trim().take(Race.MAX_NAME_LENGTH).ifBlank { "Oyunçu" },
            countryCode = countryCode,
            joinedAt = moment,
            score = 0.0,
            updatedAt = moment
        )

        val joined = races.join(race, participant)
            ?: return RaceJoinResult.Full(known ?: RaceRoster(race, emptyList()))

        return RaceJoinResult.Joined(joined)
    }
}

/**
 * Records the player's current figure in a race.
 *
 * Called whenever the measured score changes while a race is being watched, in the same spirit as
 * [PublishLeaderboardEntryUseCase]: a player who finishes a walk and opens the race expects the
 * walk to be counted.
 */
class PublishRaceScoreUseCase(
    private val races: RaceRepository,
    private val identity: PlayerIdentityRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(raceId: String, score: Double) =
        races.updateScore(raceId, identity.playerId, score, now())
}

/** Takes the player out of a race without ending it for anyone else. */
class LeaveRaceUseCase(
    private val races: RaceRepository,
    private val identity: PlayerIdentityRepository
) {
    suspend operator fun invoke(raceId: String) = races.leave(raceId, identity.playerId)
}

/** Calls a race off. Only offered to the host, and only enforced in the repository's own terms. */
class DeleteRaceUseCase(private val races: RaceRepository) {
    suspend operator fun invoke(raceId: String) = races.delete(raceId)
}

/** The id this device races under, for marking the player's own row on a board. */
class GetPlayerIdUseCase(private val identity: PlayerIdentityRepository) {
    operator fun invoke(): String = identity.playerId
}
