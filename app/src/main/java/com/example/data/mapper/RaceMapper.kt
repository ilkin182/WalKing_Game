package com.example.data.mapper

import com.example.data.local.entity.RaceEntity
import com.example.data.local.entity.RaceParticipantEntity
import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceParticipant

fun RaceEntity.toDomain(): Race = Race(
    id = id,
    code = code,
    name = name,
    mode = modeOf(mode),
    format = formatOf(format),
    targetScore = targetScore,
    maxParticipants = maxParticipants,
    createdAt = createdAt,
    startsAt = startsAt,
    endsAt = endsAt,
    hostId = hostId,
    hostName = hostName
)

fun Race.toEntity(): RaceEntity = RaceEntity(
    id = id,
    code = code,
    name = name,
    mode = mode.name,
    format = format.name,
    targetScore = targetScore,
    maxParticipants = maxParticipants,
    createdAt = createdAt,
    startsAt = startsAt,
    endsAt = endsAt,
    hostId = hostId,
    hostName = hostName
)

fun RaceParticipantEntity.toDomain(): RaceParticipant = RaceParticipant(
    raceId = raceId,
    playerId = playerId,
    nickname = nickname,
    countryCode = countryCode,
    joinedAt = joinedAt,
    score = score,
    updatedAt = updatedAt
)

fun RaceParticipant.toEntity(): RaceParticipantEntity = RaceParticipantEntity(
    raceId = raceId,
    playerId = playerId,
    nickname = nickname,
    countryCode = countryCode,
    joinedAt = joinedAt,
    score = score,
    updatedAt = updatedAt
)

/**
 * A stored mode name back to the enum, falling back to [RaceMode.CELLS].
 *
 * The fallback is for a race stored by a *newer* build of the app than the one reading it - a
 * downgrade, or a link from a friend on a later version. Showing that race as a cell race is wrong
 * in a way the player can see and work around; crashing on the races tab is not.
 */
private fun modeOf(stored: String): RaceMode =
    RaceMode.entries.firstOrNull { it.name == stored } ?: RaceMode.CELLS

/**
 * A stored format name back to the enum, falling back to [RaceFormat.VERSUS] for the same reason as
 * [modeOf]: a race written by a newer build should read oddly rather than crash the tab. A co-op
 * race read this way loses its team panel but keeps its members and their numbers, which is the
 * least wrong thing an older build can show.
 */
private fun formatOf(stored: String): RaceFormat =
    RaceFormat.entries.firstOrNull { it.name == stored } ?: RaceFormat.VERSUS
