package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One race this device knows about - either because the player set it up or because they opened an
 * invitation to it.
 *
 * [id] is derived from [code] rather than generated (see
 * [com.example.domain.race.RaceCode.idOf]), which is what makes re-opening the same share link
 * update the race instead of creating a second copy of it.
 *
 * The mode and the format are stored as the enums' names rather than their ordinals: a race outlives
 * an app update, and inserting a new value in the middle of an enum must not silently turn everyone's
 * distance race into something else.
 *
 * [targetScore] is null for every versus race and set for every co-op one. Nullable rather than
 * defaulted to zero because "no goal" and "a goal of nothing" are different things, and the second
 * one would show as a race already finished.
 */
@Entity(
    tableName = "races",
    indices = [Index(value = ["code"], unique = true)]
)
data class RaceEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val mode: String,
    @ColumnInfo(defaultValue = DEFAULT_FORMAT) val format: String,
    val targetScore: Double?,
    val maxParticipants: Int,
    val createdAt: Long,
    val startsAt: Long,
    val endsAt: Long,
    val hostId: String,
    val hostName: String
) {
    companion object {
        /**
         * Declared on the entity as well as in the migration that adds the column.
         *
         * Both halves have to say it: the migration needs a default because the rows already there
         * have no format, and the entity needs the same one or Room's schema validation is comparing
         * a table that has a default against a definition that does not. A fresh install and an
         * upgraded one then have exactly the same schema, which is the only way that stays true.
         */
        const val DEFAULT_FORMAT = "VERSUS"
    }
}

/**
 * One runner in one race.
 *
 * Keyed by the pair, so re-joining a race replaces the row instead of adding a second one, and so a
 * participant cannot exist in two places at once.
 *
 * [score] is stored rather than recomputed because only the runner's own device can measure their
 * own walking - see [com.example.domain.model.RaceParticipant]. [updatedAt] is what the screen shows
 * to keep a stale figure visibly stale.
 */
@Entity(
    tableName = "race_participants",
    primaryKeys = ["raceId", "playerId"],
    indices = [Index(value = ["raceId"])]
)
data class RaceParticipantEntity(
    val raceId: String,
    val playerId: String,
    val nickname: String,
    val countryCode: String?,
    val joinedAt: Long,
    val score: Double,
    val updatedAt: Long
)
