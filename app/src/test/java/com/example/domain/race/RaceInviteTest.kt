package com.example.domain.race

import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val START = 1_700_000_000_000L
private const val END = START + 7 * 24 * 60 * 60 * 1000L

private fun race(
    code: String = "ABC234",
    name: String = "Həftəlik yarış",
    mode: RaceMode = RaceMode.CELLS,
    format: RaceFormat = RaceFormat.VERSUS,
    targetScore: Double? = null,
    maxParticipants: Int = 8
) = Race(
    id = RaceCode.idOf(code),
    code = code,
    name = name,
    mode = mode,
    format = format,
    targetScore = targetScore,
    maxParticipants = maxParticipants,
    createdAt = START,
    startsAt = START,
    endsAt = END,
    hostId = "p-host",
    hostName = "Elçin"
)

class RaceInviteTest {

    @Test
    fun `a link round-trips the whole race`() {
        val original = race(mode = RaceMode.DISTANCE, maxParticipants = 12)

        val parsed = RaceInvite.parse(RaceInvite.linkFor(original))

        assertNotNull(parsed)
        assertEquals(original.code, parsed!!.code)
        assertEquals(original, parsed.race)
    }

    @Test
    fun `the app scheme carries the same race as the https link`() {
        val original = race()

        val fromApp = RaceInvite.parse(RaceInvite.appLinkFor(original))
        val fromHttps = RaceInvite.parse(RaceInvite.linkFor(original))

        assertEquals(fromHttps?.race, fromApp?.race)
    }

    @Test
    fun `names with spaces and Azerbaijani letters survive the round trip`() {
        val original = race(name = "Şəhər üzrə gəzinti - 2 həftə")

        val parsed = RaceInvite.parse(RaceInvite.linkFor(original))

        assertEquals(original.name, parsed?.race?.name)
    }

    @Test
    fun `a bare typed code parses to a code with no race behind it`() {
        val parsed = RaceInvite.parse("abc234")

        assertEquals("ABC234", parsed?.code)
        // Nothing in a bare code says what the race is; joining falls back to a race already known.
        assertNull(parsed?.race)
    }

    @Test
    fun `a truncated link still yields its code`() {
        val parsed = RaceInvite.parse("https://walkinggame.app/r/ABC234")

        assertEquals("ABC234", parsed?.code)
        assertNull(parsed?.race)
    }

    @Test
    fun `nonsense is not an invitation`() {
        assertNull(RaceInvite.parse(null))
        assertNull(RaceInvite.parse(""))
        assertNull(RaceInvite.parse("https://example.com/hello"))
    }

    @Test
    fun `an edited link cannot raise the participant limit past the ceiling`() {
        val tampered = RaceInvite.linkFor(race()).replace("&x=8", "&x=100000")

        val parsed = RaceInvite.parse(tampered)

        assertEquals(Race.MAX_PARTICIPANTS, parsed?.race?.maxParticipants)
    }

    @Test
    fun `an edited link cannot describe a race that ends before it starts`() {
        val original = race()
        val tampered = RaceInvite.linkFor(original).replace("e=$END", "e=${START - 1}")

        val parsed = RaceInvite.parse(tampered)

        // The code still works - it is the race definition that is refused, not the invitation.
        assertEquals(original.code, parsed?.code)
        assertNull(parsed?.race)
    }

    @Test
    fun `an unknown mode is refused rather than silently turned into another game`() {
        val tampered = RaceInvite.linkFor(race()).replace("m=CELLS", "m=TELEPORT")

        assertNull(RaceInvite.parse(tampered)?.race)
    }

    @Test
    fun `a co-op race round-trips with its shared goal`() {
        val original = race(format = RaceFormat.COOP, targetScore = 5_000.0)

        val parsed = RaceInvite.parse(RaceInvite.linkFor(original))

        assertEquals(original, parsed?.race)
        assertEquals(5_000.0, parsed?.race?.teamTarget!!, 0.0001)
    }

    @Test
    fun `a co-op link with no goal does not describe a race`() {
        val stripped = RaceInvite.linkFor(race(format = RaceFormat.COOP, targetScore = 5_000.0))
            .replace("&t=5000.0", "")

        val parsed = RaceInvite.parse(stripped)

        // The code still works; there is simply no race in the link to build from.
        assertEquals("ABC234", parsed?.code)
        assertNull(parsed?.race)
    }

    @Test
    fun `a link written before co-op existed is still a versus race`() {
        val old = "https://walkinggame.app/r/ABC234?v=1&n=Yaris&m=CELLS&x=8" +
            "&s=$START&e=$END&h=Elcin&i=p-host"

        val parsed = RaceInvite.parse(old)

        assertEquals(RaceFormat.VERSUS, parsed?.race?.format)
        assertNull(parsed?.race?.teamTarget)
    }

    @Test
    fun `an unknown format is refused rather than downgraded`() {
        val tampered = RaceInvite.linkFor(race()).replace("f=VERSUS", "f=BATTLE_ROYALE")

        assertNull(RaceInvite.parse(tampered)?.race)
    }

    @Test
    fun `an edited link cannot set an unreachable shared goal`() {
        val tampered = RaceInvite.linkFor(race(format = RaceFormat.COOP, targetScore = 5_000.0))
            .replace("t=5000.0", "t=99999999999")

        val parsed = RaceInvite.parse(tampered)

        assertEquals(Race.MAX_TARGET, parsed?.race?.teamTarget!!, 0.0001)
    }

    @Test
    fun `a co-op share message names the shared goal`() {
        val text = RaceInvite.shareText(race(format = RaceFormat.COOP, targetScore = 5_000.0))

        assertTrue(text.contains("Ortaq hədəf"))
        assertTrue(text.contains("5000"))
    }

    @Test
    fun `the share message carries the code as well as the link`() {
        val subject = race()

        val text = RaceInvite.shareText(subject)

        assertTrue(text.contains(subject.code))
        assertTrue(text.contains(subject.name))
        assertTrue(text.contains(RaceInvite.linkFor(subject)))
    }
}
