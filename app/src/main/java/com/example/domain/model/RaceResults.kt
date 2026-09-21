package com.example.domain.model

/**
 * Why creating a race did or did not work.
 *
 * A sealed result rather than an exception or a bare null: every one of these is something the host
 * can fix in the form they are looking at, so the screen needs to know *which* thing went wrong, and
 * none of them is exceptional.
 */
sealed interface RaceCreationResult {
    data class Created(val roster: RaceRoster) : RaceCreationResult

    /** The name is empty, the size is out of range, or the deadline is not far enough away. */
    data class Rejected(val reason: RaceRejection) : RaceCreationResult
}

/** Why joining did or did not work. Same reasoning as [RaceCreationResult]. */
sealed interface RaceJoinResult {
    data class Joined(val roster: RaceRoster) : RaceJoinResult

    /** Already in it - tapping an invitation twice opens the race rather than complaining. */
    data class AlreadyJoined(val roster: RaceRoster) : RaceJoinResult

    /** The code is well formed but no race on this device carries it, and no link came with it. */
    data object Unknown : RaceJoinResult

    /** Not a code at all. */
    data object BadCode : RaceJoinResult

    /** Every slot the host allowed is taken. */
    data class Full(val roster: RaceRoster) : RaceJoinResult

    /** The deadline has already passed - there is nothing left to race for. */
    data class Finished(val roster: RaceRoster) : RaceJoinResult
}

/** The fixable things wrong with a race somebody is trying to set up. */
enum class RaceRejection(val message: String) {
    EMPTY_NAME("Yarışın adını yaz."),
    TOO_FEW_PARTICIPANTS("Yarışda ən azı ${Race.MIN_PARTICIPANTS} iştirakçı olmalıdır."),
    TOO_MANY_PARTICIPANTS("İştirakçı sayı ən çox ${Race.MAX_PARTICIPANTS} ola bilər."),
    TOO_SHORT("Yarış ən azı 1 saat davam etməlidir."),
    TOO_LONG("Yarış ən çox 3 ay davam edə bilər."),
    DEADLINE_PASSED("Bitmə tarixi gələcəkdə olmalıdır."),
    BAD_CODE("Kod 6 simvol olmalıdır (O, 0, I, 1 istifadə olunmur)."),
    CODE_TAKEN("Bu kod artıq başqa yarışdadır. Başqasını seç."),
    NO_TARGET("Komanda yarışı üçün ortaq hədəf təyin et."),
    TARGET_TOO_LARGE("Ortaq hədəf çox böyükdür.")
}
