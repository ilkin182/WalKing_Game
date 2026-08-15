package com.example.domain.model

import java.text.Collator
import java.util.Locale

/**
 * One country a player can belong to, identified by its ISO 3166-1 alpha-2 code.
 *
 * The code is what gets stored and compared (leaderboards are grouped by it); [name] is only ever
 * for display, so it is resolved per device language rather than persisted.
 */
data class Country(val code: String, val name: String) {
    /** The flag, built from the code itself - see [Countries.flagOf]. */
    val flag: String get() = Countries.flagOf(code)
}

/**
 * The country list, taken from the platform's ISO registry rather than hand-written.
 *
 * A hardcoded table would be one more thing to keep current, and would have to carry a translated
 * name per language; the JDK already knows both, so the list is derived once and cached.
 */
object Countries {
    /** Azerbaijani names where the platform has them, English where it does not. */
    private val displayLocale: Locale = Locale.forLanguageTag("az")

    val all: List<Country> by lazy {
        val collator = Collator.getInstance(displayLocale)
        Locale.getISOCountries()
            .map { code -> Country(code = code, name = displayNameOf(code)) }
            .sortedWith { left, right -> collator.compare(left.name, right.name) }
    }

    /**
     * Resolved without touching [all], so a screen that only shows the player's own country - the
     * profile, the sign-up form - does not pay for building and collating 250 localised names.
     */
    private val isoCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

    fun byCode(code: String?): Country? {
        val normalized = normalize(code)?.takeIf { it in isoCodes } ?: return null
        return Country(code = normalized, name = displayNameOf(normalized))
    }

    /** Uppercased and trimmed, or null if [code] is not a two-letter code. */
    fun normalize(code: String?): String? {
        val trimmed = code?.trim()?.uppercase(Locale.US) ?: return null
        return if (trimmed.length == 2 && trimmed.all { it in 'A'..'Z' }) trimmed else null
    }

    /**
     * Countries matching [query] by name or code, for the picker's search box.
     *
     * A query that is itself a country code puts that country first: "AZ" also appears inside
     * "Qazaxıstan" and "Braziliya", and the player who typed a code meant the code.
     */
    fun search(query: String): List<Country> {
        val needle = query.trim()
        if (needle.isEmpty()) return all
        val lower = needle.lowercase(displayLocale)
        val matches = all.filter {
            it.name.lowercase(displayLocale).contains(lower) || it.code.lowercase(Locale.US).startsWith(lower)
        }
        val exact = byCode(needle) ?: return matches
        return listOf(exact) + matches.filterNot { it.code == exact.code }
    }

    /**
     * The country the device is set to, as the picker's opening suggestion.
     *
     * Only a default: a player walking abroad, or one whose phone is set to another region, picks
     * their own from the list.
     */
    fun deviceDefault(): Country? = byCode(Locale.getDefault().country)

    /**
     * The flag emoji for a country code, as the pair of regional indicator symbols it maps to.
     *
     * Composed rather than stored: every two-letter code has exactly one flag, and building it here
     * means no bundled image set to keep in step with the list above.
     */
    fun flagOf(code: String): String {
        val normalized = normalize(code) ?: return "🏳" // white flag, for anything unknown
        val offset = REGIONAL_INDICATOR_A - 'A'.code
        return normalized.map { Character.toChars(it.code + offset).concatToString() }.joinToString("")
    }

    private fun displayNameOf(code: String): String {
        val locale = Locale.Builder().setRegion(code).build()
        val localized = locale.getDisplayCountry(displayLocale)
        // getDisplayCountry falls back to the raw code when the language has no name for it; English
        // is a friendlier last resort than "TF" in the middle of an alphabetised list.
        if (localized.isNotBlank() && !localized.equals(code, ignoreCase = true)) return localized
        return locale.getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
}
