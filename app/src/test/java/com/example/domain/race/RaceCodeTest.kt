package com.example.domain.race

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RaceCodeTest {

    @Test
    fun `generated codes are six characters from the unambiguous alphabet`() {
        val random = Random(42)
        repeat(500) {
            val code = RaceCode.generate(random)
            assertEquals(RaceCode.LENGTH, code.length)
            assertTrue("'$code' should be valid", RaceCode.isValid(code))
        }
    }

    @Test
    fun `generated codes never contain a character with a look-alike`() {
        val random = Random(7)
        val banned = setOf('O', '0', 'I', '1')
        repeat(500) {
            val code = RaceCode.generate(random)
            assertTrue("'$code' contains a look-alike", code.none { it in banned })
        }
    }

    @Test
    fun `lower case and hand-written separators are accepted`() {
        assertEquals("ABC234", RaceCode.normalize("abc234"))
        assertEquals("ABC234", RaceCode.normalize(" abc-234 "))
        assertEquals("ABC234", RaceCode.normalize("ABC_234"))
        assertEquals("ABC234", RaceCode.normalize("ab c2 34"))
    }

    @Test
    fun `anything that is not a code is rejected rather than guessed at`() {
        assertNull(RaceCode.normalize(null))
        assertNull(RaceCode.normalize(""))
        assertNull(RaceCode.normalize("ABC23"))
        assertNull(RaceCode.normalize("ABC2345"))
        // The excluded characters are typos here, not near-misses to be folded onto something else.
        assertNull(RaceCode.normalize("ABC23O"))
        assertNull(RaceCode.normalize("ABC231"))
        assertNull(RaceCode.normalize("ABC-2!"))
    }

    @Test
    fun `the id is derived from the code so the same link is the same race everywhere`() {
        assertEquals(RaceCode.idOf("ABC234"), RaceCode.idOf("abc234"))
        assertNotNull(RaceCode.idOf("ABC234"))
    }
}
