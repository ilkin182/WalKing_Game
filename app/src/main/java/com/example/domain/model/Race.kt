package com.example.domain.model

/**
 * A private contest between players over a fixed window of time.
 *
 * The solo game has no end and no opponent: ground stays claimed forever and the country board
 * ranks lifetime totals, so a player who started last year can never be caught. A race is the
 * opposite of that on purpose - it counts only what happens between [startsAt] and [endsAt], which
 * is what makes a newcomer and a veteran able to line up against each other.
 *
 * A race has two independent axes, and it is worth keeping them apart: [format] is *who you are
 * playing against* - each other, or the target together - and [mode] is *what is being counted*.
 * Every mode works under either format, because "how much ground" and "who it counts for" are not
 * the same question.
 *
 * [code] is what the whole invitation system hangs on. It is the race's public name - short enough
 * to read out loud, and the only thing a player needs to get in - so it doubles as the join secret
 * and as the id carried in a share link. That is why it is generated from an unambiguous alphabet
 * (see [com.example.domain.race.RaceCode]) rather than from the primary key.
 */
data class Race(
    /** Stable identity. Derived from [code], so the same race opened from a link on two devices is one race. */
    val id: String,
    val code: String,
    val name: String,
    val mode: RaceMode,
    val format: RaceFormat = RaceFormat.VERSUS,
    /**
     * What the team is trying to reach together, in [mode]'s own unit. Only [RaceFormat.COOP] has
     * one - a versus race is won by beating the others, and there is nothing to aim at.
     */
    val targetScore: Double? = null,
    /** Including the host. Capped so a race stays a group of people who know each other. */
    val maxParticipants: Int,
    val createdAt: Long,
    val startsAt: Long,
    val endsAt: Long,
    val hostId: String,
    val hostName: String
) {
    fun statusAt(now: Long): RaceStatus = when {
        now < startsAt -> RaceStatus.PENDING
        now >= endsAt -> RaceStatus.FINISHED
        else -> RaceStatus.RUNNING
    }

    /** How much of the window is left, floored at zero so a finished race reads as 0 rather than negative. */
    fun remainingMillisAt(now: Long): Long = (endsAt - now).coerceAtLeast(0L)

    fun hasRoomFor(currentParticipants: Int): Boolean = currentParticipants < maxParticipants

    /** The goal a co-op race is chasing, or null if this race is not one. */
    val teamTarget: Double? get() = targetScore?.takeIf { format == RaceFormat.COOP && it > 0.0 }

    companion object {
        /** Two is a race; below that there is nobody to beat. */
        const val MIN_PARTICIPANTS = 2

        /**
         * The invite link carries the whole race definition, so the ceiling is also what keeps a
         * hand-edited link from creating a thousand-slot room on somebody else's phone.
         */
        const val MAX_PARTICIPANTS = 50

        const val DEFAULT_PARTICIPANTS = 8

        /** Long enough that a walk fits inside it; the host moves it from there. */
        const val DEFAULT_DURATION_DAYS = 7

        /** A race has to outlast at least one walk to mean anything. */
        const val MIN_DURATION_MILLIS = 60 * 60 * 1000L

        /** Beyond three months a "race" is just the country board again. */
        const val MAX_DURATION_MILLIS = 92L * 24 * 60 * 60 * 1000L

        const val MAX_NAME_LENGTH = 40

        /**
         * The largest shared goal a co-op race can be set.
         *
         * Same reasoning as [MAX_PARTICIPANTS]: the target travels inside an editable share link, so
         * there has to be a number past which the app stops believing it. Far above anything a group
         * of friends will walk in three months, and far below "unreachable by construction".
         */
        const val MAX_TARGET = 1_000_000.0
    }
}

/**
 * Who a race is played against: each other, or the goal together.
 *
 * [COOP] exists because the competitive shape leaves most of a group out. In a versus race one
 * person wins and eight people were beaten, and after two days the eight can see they are not going
 * to catch the first, so they stop. Pooling the same walking turns every one of those nine people
 * into somebody whose next street still matters - the person who walks two kilometres contributes
 * two kilometres whether or not somebody else walked twenty.
 *
 * It is deliberately not a *teams* format - there are no sides. Everybody in the race is on the one
 * team, walking their own part of the map towards one number.
 */
enum class RaceFormat(
    val title: String,
    val shortLabel: String,
    val explanation: String
) {
    VERSUS(
        title = "Rəqabət",
        shortLabel = "Rəqabət",
        explanation = "Hər kəs təkbaşına yarışır - kim daha çox edərsə, o qalib gəlir."
    ),
    COOP(
        title = "Komanda",
        shortLabel = "Komanda",
        explanation = "Hamının nəticəsi bir yerə toplanır - ortaq hədəfə birlikdə çatırsınız."
    )
}

/** Where a race is in its own lifetime. */
enum class RaceStatus(val label: String) {
    /** Created with a start in the future - the host set it up for tomorrow morning. */
    PENDING("Başlamayıb"),
    RUNNING("Davam edir"),
    FINISHED("Bitib")
}

/**
 * What a race ranks its runners on.
 *
 * Every mode has to be measurable from what the device already writes down with a timestamp on it,
 * because a race score is always "since the race started" - a lifetime counter cannot answer that.
 * That is what rules out steps and badges for now: the step counter is a live sensor reading with no
 * history behind it, and an unlocked badge is not dated. See
 * [com.example.domain.race.RaceScoring].
 */
enum class RaceMode(
    val title: String,
    val shortLabel: String,
    val unitLabel: String,
    private val versusExplanation: String,
    private val coopExplanation: String
) {
    CELLS(
        title = "Ən çox xana",
        shortLabel = "Xana",
        unitLabel = "xana",
        versusExplanation = "Yarış müddətində ən çox yeni xana kəşf edən qalib gəlir.",
        coopExplanation = "Hamının kəşf etdiyi yeni xanalar bir yerə toplanır."
    ),
    DISTANCE(
        title = "Ən çox məsafə",
        shortLabel = "Məsafə",
        unitLabel = "km",
        versusExplanation = "Yarış müddətində piyada ən çox məsafə yürüyən qalib gəlir.",
        coopExplanation = "Hamının piyada yürüdüyü məsafə bir yerə toplanır."
    ),
    LONGEST_WALK(
        title = "Ən uzun tək gəzinti",
        shortLabel = "Tək gəzinti",
        unitLabel = "km",
        versusExplanation = "Bir dəfəyə, fasiləsiz ən uzun gəzintini edən qalib gəlir.",
        coopExplanation = "Hər kəsin ən uzun tək gəzintisi bir yerə toplanır."
    );

    /**
     * What this mode counts, said the way the chosen format makes it true.
     *
     * Every mode works under both formats, but the same sentence cannot describe both: "ən çox xana
     * kəşf edən qalib gəlir" is the opposite of what a co-op race does with the same measurement.
     */
    fun explanationFor(format: RaceFormat): String = when (format) {
        RaceFormat.VERSUS -> versusExplanation
        RaceFormat.COOP -> coopExplanation
    }

    /** Cells are counted, the rest are measured - so only the distances get a decimal. */
    fun format(score: Double): String = when (this) {
        CELLS -> score.toInt().toString()
        DISTANCE, LONGEST_WALK -> String.format(java.util.Locale.US, "%.2f", score)
    }
}

/**
 * One runner in a race, with the score they last published.
 *
 * The score is stored rather than computed on read: only the player's own device can measure their
 * own walking, so every other row is the last figure that reached this device. With no server that
 * is the moment they joined; with one it is their last sync. Either way the screen shows
 * [updatedAt] so a stale row is visibly stale instead of silently wrong.
 */
data class RaceParticipant(
    val raceId: String,
    val playerId: String,
    val nickname: String,
    val countryCode: String?,
    val joinedAt: Long,
    val score: Double,
    val updatedAt: Long
)

/** A race together with everyone currently in it - what both the list and the detail screen read. */
data class RaceRoster(
    val race: Race,
    val participants: List<RaceParticipant>
) {
    val participantCount: Int get() = participants.size
    val freeSlots: Int get() = (race.maxParticipants - participants.size).coerceAtLeast(0)

    fun contains(playerId: String): Boolean = participants.any { it.playerId == playerId }
}

/**
 * One row of a race board: a runner, what they have done, and where that puts them.
 *
 * [position] and [share] are the same row read two ways. A versus board wants the position - the
 * whole point is the order. A co-op board wants the share: rows are still drawn biggest-first
 * because that is a readable order, but what the row is *saying* is "this is how much of the team's
 * number came from this person", and calling that "4th place" would reintroduce the competition the
 * format exists to remove.
 */
data class RaceStanding(
    val position: Int,
    val participant: RaceParticipant,
    val score: Double,
    val isCurrentPlayer: Boolean,
    /** This runner's fraction of the team total, 0..1. Zero when nobody has walked anything yet. */
    val share: Float
)

/**
 * How far a co-op team has got towards what they set out to do.
 *
 * [total] is the sum of what everybody has published, which is the only thing a device without a
 * server can add up - see [com.example.domain.race.RaceStandings.teamProgress] for what that sum
 * does and does not mean when two people walk the same street.
 */
data class RaceTeamProgress(
    val total: Double,
    val target: Double,
    /** How much is still to go, floored at zero so a finished goal reads as done, not as negative. */
    val remaining: Double,
    /** 0..1, capped - a team that walked double the target has still finished it once. */
    val fraction: Float,
    val isReached: Boolean
)

/** A whole race board, already ordered and numbered. */
data class RaceBoard(
    val race: Race,
    val status: RaceStatus,
    val rows: List<RaceStanding>,
    val freeSlots: Int,
    val isHost: Boolean,
    /** Non-null exactly when this is a co-op race - it is what that board is actually about. */
    val team: RaceTeamProgress? = null
) {
    val playerRow: RaceStanding? get() = rows.firstOrNull { it.isCurrentPlayer }
    val participantCount: Int get() = rows.size

    val isCoop: Boolean get() = race.format == RaceFormat.COOP

    /**
     * Only meaningful once a *versus* race is over, which is the only time a result is final.
     *
     * A co-op race has no winner by construction: everybody either got there or did not, and
     * crowning the biggest contributor would be the app quietly scoring a game nobody was playing.
     */
    val winner: RaceStanding?
        get() = if (status == RaceStatus.FINISHED && !isCoop) rows.firstOrNull() else null
}
