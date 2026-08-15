package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {

    @Test
    fun `the list covers the usual suspects and is not tiny`() {
        assertTrue("Expected a full ISO list, got ${Countries.all.size}", Countries.all.size > 200)
        listOf("AZ", "TR", "GE", "RU", "US").forEach {
            assertNotNull("Expected $it in the country list", Countries.byCode(it))
        }
    }

    @Test
    fun `lookup is case and whitespace insensitive`() {
        assertEquals("AZ", Countries.byCode(" az ")?.code)
        assertEquals("AZ", Countries.byCode("Az")?.code)
    }

    @Test
    fun `anything that is not a two letter code is rejected`() {
        assertNull(Countries.byCode(null))
        assertNull(Countries.byCode(""))
        assertNull(Countries.byCode("AZE"))
        assertNull(Countries.byCode("A1"))
        // Well-formed but not a country: normalising must not invent one.
        assertNull(Countries.byCode("ZZ"))
    }

    @Test
    fun `every country has a two-glyph flag built from its code`() {
        assertEquals("🇦🇿", Countries.flagOf("AZ"))
        assertEquals(Countries.flagOf("AZ"), Countries.byCode("az")?.flag)
    }

    @Test
    fun `an unknown code falls back to a neutral flag instead of throwing`() {
        assertEquals("🏳", Countries.flagOf("bogus"))
    }

    @Test
    fun `searching by code puts that exact country first`() {
        val results = Countries.search("AZ")

        assertEquals("AZ", results.first().code)
        // The name matches are still there, just below the exact one.
        assertTrue(results.size > 1)
    }

    @Test
    fun `an empty query returns everything and a nonsense one returns nothing`() {
        assertEquals(Countries.all.size, Countries.search("   ").size)
        assertTrue(Countries.search("qwertyuiop").isEmpty())
    }

    @Test
    fun `names are unique enough to be picked from a list`() {
        val names = Countries.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
