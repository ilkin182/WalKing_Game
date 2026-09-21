package com.example.domain.race

import java.util.Locale
import kotlin.random.Random

/**
 * The short code that both names a race and gets a player into it.
 *
 * It is meant to survive being read out over the phone, typed from a screenshot and written on a
 * napkin, so the alphabet leaves out every character that has a twin in a sans-serif font: no O and
 * no 0, no I and no 1, on the principle that dropping *both* halves of an ambiguous pair is the only
 * fix that needs no guessing afterwards - a code that cannot contain either one has no wrong answer
 * to fold. Six characters out of the remaining 32 is a billion codes: far more than a hand-typed
 * guess will find, and short enough to dictate.
 *
 * The code is also the race's identity ([idOf]). A race opened from a link on two different phones
 * has to be the same race on both, and the code is the only thing both of them ever saw.
 */
object RaceCode {

    /** Digits and capitals with the visually ambiguous ones (I, O, 0, 1) removed. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val LENGTH = 6

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /**
     * A typed code in canonical form, or null if it is not a code at all.
     *
     * Lower case is accepted because the keyboard offers it first, and spaces, dashes and
     * underscores are dropped because that is how people break a six-character code up when they
     * write it down. Anything left that is not in the alphabet is a typo, not a near-miss to be
     * guessed at, so it fails here rather than quietly joining the wrong race.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw
            .filterNot { it.isWhitespace() || it == '-' || it == '_' }
            .uppercase(Locale.US)
        return cleaned.takeIf { isValid(it) }
    }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** The race id a code stands for, so the same link opens the same race on every device. */
    fun idOf(code: String): String = "race-${code.uppercase(Locale.US)}"
}
