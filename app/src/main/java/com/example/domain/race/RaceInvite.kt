package com.example.domain.race

import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * The share link that gets somebody into a race, and the reading of one that arrives.
 *
 * The link carries the *whole race definition*, not just its code. That is the one decision the rest
 * of the feature rests on: there is no server yet (see
 * [com.example.data.repository.LocalRaceRepository]), so a phone that has never heard of a race has
 * no way to look one up by code - and a link that only said "join ABC123" would open to an error on
 * every device except the host's. Carrying the name, the mode, the size and the deadline means the
 * invited player's app can build the same race locally and put them in it, which is exactly what
 * tapping a shared link is supposed to do.
 *
 * What that costs is trust: a link is text, and text can be edited before it is forwarded. So
 * everything read out of one is re-validated on the way in - [parse] clamps the size, insists the
 * deadline is after the start, and trims the name. A hand-edited link can make a race with a silly
 * name; it cannot make one with a thousand slots or no end.
 *
 * Two forms are produced and both are accepted:
 *  - `https://walkinggame.app/r/ABC123?...` - what gets shared, because every chat app makes it
 *    tappable and a phone without the app installed still sees something meaningful.
 *  - `walkinggame://race/ABC123?...` - the app's own scheme, for anything handing it a URI directly.
 *
 * A bare code typed into the join box parses here too, and comes back with no race attached: there
 * was nothing in it to build one from, so joining falls back to a race the device already knows.
 */
object RaceInvite {

    const val HTTPS_HOST = "walkinggame.app"
    const val HTTPS_PATH_PREFIX = "/r/"
    const val APP_SCHEME = "walkinggame"
    const val APP_HOST = "race"

    /** Bumped if the parameter names ever change, so an old link can still be read deliberately. */
    private const val VERSION = "1"

    private const val PARAM_VERSION = "v"
    private const val PARAM_CODE = "c"
    private const val PARAM_NAME = "n"
    private const val PARAM_MODE = "m"
    private const val PARAM_FORMAT = "f"
    private const val PARAM_TARGET = "t"
    private const val PARAM_MAX = "x"
    private const val PARAM_STARTS = "s"
    private const val PARAM_ENDS = "e"
    private const val PARAM_HOST_NAME = "h"
    private const val PARAM_HOST_ID = "i"

    /**
     * What was in a link: always a code, and the race itself when the link carried one.
     *
     * A null [race] is not a failure - it is a link a chat client truncated, or a code somebody
     * typed in by hand. Joining still works if the device already knows that race.
     */
    data class Parsed(val code: String, val race: Race?)

    /** The link to share, with the whole race packed into it. */
    fun linkFor(race: Race): String =
        "https://" + HTTPS_HOST + HTTPS_PATH_PREFIX + race.code + "?" + query(race)

    /** The same invitation under the app's own scheme. */
    fun appLinkFor(race: Race): String =
        APP_SCHEME + "://" + APP_HOST + "/" + race.code + "?" + query(race)

    /** The message that goes out with the link - the code is repeated so it survives a broken link. */
    fun shareText(race: Race): String = buildString {
        append("\"" + race.name + "\" yarışına qoşul!\n\n")
        append(race.format.title + " · " + race.mode.title + "\n")
        race.teamTarget?.let {
            append("Ortaq hədəf: " + race.mode.format(it) + " " + race.mode.unitLabel + "\n")
        }
        append("Qoşulma kodu: " + race.code + "\n\n")
        append(linkFor(race))
    }

    /** Reads a link, a URI or a bare code. Null when there is no usable code in it at all. */
    fun parse(raw: String?): Parsed? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val separator = text.indexOf('?')
        val beforeQuery = if (separator >= 0) text.substring(0, separator) else text
        val params = if (separator >= 0) parseQuery(text.substring(separator + 1)) else emptyMap()

        // The code is the last path segment of both link forms; `c=` is the fallback for a client
        // that rewrote the path, and a bare typed code is the whole path by itself.
        val code = RaceCode.normalize(beforeQuery.trimEnd('/').substringAfterLast('/'))
            ?: RaceCode.normalize(params[PARAM_CODE])
            ?: return null

        return Parsed(code = code, race = raceFrom(code, params))
    }

    private fun query(race: Race): String = listOfNotNull(
        PARAM_VERSION to VERSION,
        PARAM_NAME to race.name,
        PARAM_MODE to race.mode.name,
        PARAM_FORMAT to race.format.name,
        race.teamTarget?.let { PARAM_TARGET to it.toString() },
        PARAM_MAX to race.maxParticipants.toString(),
        PARAM_STARTS to race.startsAt.toString(),
        PARAM_ENDS to race.endsAt.toString(),
        PARAM_HOST_NAME to race.hostName,
        PARAM_HOST_ID to race.hostId
    ).joinToString("&") { (key, value) -> key + "=" + encode(value) }

    /**
     * The race a link describes, or null if it did not fully describe one.
     *
     * Everything is re-checked here rather than trusted, for the reason in the class comment: this
     * is the one place in the app where a stranger's text becomes a record.
     */
    private fun raceFrom(code: String, params: Map<String, String>): Race? {
        val name = params[PARAM_NAME]?.trim()?.take(Race.MAX_NAME_LENGTH)?.takeIf { it.isNotEmpty() }
            ?: return null
        val mode = params[PARAM_MODE]?.let { value ->
            RaceMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        } ?: return null
        val startsAt = params[PARAM_STARTS]?.toLongOrNull() ?: return null
        val endsAt = params[PARAM_ENDS]?.toLongOrNull() ?: return null
        if (endsAt <= startsAt) return null

        // A link written before co-op existed carries no format at all, and it means what it always
        // meant. A link carrying a format nobody here knows is refused rather than downgraded - the
        // same reasoning as an unknown mode below: guessing turns somebody's race into another game.
        val format = when (val raw = params[PARAM_FORMAT]) {
            null -> RaceFormat.VERSUS
            else -> RaceFormat.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: return null
        }

        // A co-op race with no goal is not a co-op race - there would be nothing on the screen to
        // walk towards - so an invitation missing or mangling it does not describe a race at all.
        val target = if (format == RaceFormat.COOP) {
            params[PARAM_TARGET]?.toDoubleOrNull()
                ?.takeIf { it > 0.0 && it.isFinite() }
                ?.coerceAtMost(Race.MAX_TARGET)
                ?: return null
        } else {
            null
        }

        val maxParticipants = (params[PARAM_MAX]?.toIntOrNull() ?: Race.DEFAULT_PARTICIPANTS)
            .coerceIn(Race.MIN_PARTICIPANTS, Race.MAX_PARTICIPANTS)

        return Race(
            id = RaceCode.idOf(code),
            code = code,
            name = name,
            mode = mode,
            format = format,
            targetScore = target,
            maxParticipants = maxParticipants,
            // Nothing in the link says when it was written, so the start doubles as the creation
            // time. Only the ordering of the player's own race list depends on it.
            createdAt = startsAt,
            startsAt = startsAt,
            endsAt = endsAt,
            hostId = params[PARAM_HOST_ID]?.takeIf { it.isNotBlank() } ?: ("host-" + code),
            hostName = params[PARAM_HOST_NAME]?.trim()?.take(Race.MAX_NAME_LENGTH)
                ?.takeIf { it.isNotEmpty() }
                ?: "Təşkilatçı"
        )
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                decode(pair.substring(0, index)).lowercase(Locale.US) to
                    decode(pair.substring(index + 1))
            }
            .toMap()

    private fun encode(value: String): String =
        try {
            URLEncoder.encode(value, "UTF-8")
        } catch (unsupported: UnsupportedEncodingException) {
            // UTF-8 is on every JVM; the checked exception is a 1998 API detail.
            value
        }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (invalid: IllegalArgumentException) {
            // A stray "%" in a forwarded link - keep the raw text rather than lose the whole link.
            value
        } catch (unsupported: UnsupportedEncodingException) {
            value
        }
}
