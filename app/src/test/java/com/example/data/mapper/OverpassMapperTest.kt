package com.example.data.mapper

import com.example.data.remote.OverpassBounds
import com.example.data.remote.OverpassElement
import com.example.data.remote.OverpassPoint
import com.example.data.remote.OverpassResponse
import com.example.domain.model.PoiKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassMapperTest {

    private fun response(vararg elements: OverpassElement) =
        OverpassResponse(elements.toList())

    private fun node(id: Long, lat: Double, lon: Double, vararg tags: Pair<String, String>) =
        OverpassElement(type = "node", id = id, lat = lat, lon = lon, tags = tags.toMap())

    private fun way(
        id: Long,
        bounds: OverpassBounds? = null,
        center: OverpassPoint? = null,
        geometry: List<OverpassPoint?>? = null,
        vararg tags: Pair<String, String>
    ) = OverpassElement(
        type = "way",
        id = id,
        bounds = bounds,
        center = center,
        geometry = geometry,
        tags = tags.toMap()
    )

    @Test
    fun `an empty answer maps to no places`() {
        assertTrue(OverpassMapper.toPois(OverpassResponse()).isEmpty())
        assertTrue(OverpassMapper.toPois(OverpassResponse(emptyList())).isEmpty())
    }

    @Test
    fun `a park keeps its outline as a box`() {
        val pois = OverpassMapper.toPois(
            response(
                way(
                    1,
                    bounds = OverpassBounds(
                        minLat = 40.376, minLon = 49.832, maxLat = 40.378, maxLon = 49.834
                    ),
                    tags = arrayOf("leisure" to "park", "name" to "Dənizkənarı")
                )
            )
        )

        assertEquals(1, pois.size)
        val park = pois.single()
        assertEquals("way/1", park.id)
        assertEquals(PoiKind.PARK, park.kind)
        assertEquals("Dənizkənarı", park.name)
        assertEquals(40.378, park.bounds!!.north, 1e-9)
        // The centre falls out of the box when the element carries no point of its own.
        assertEquals(40.377, park.center.lat, 1e-9)
    }

    @Test
    fun `the six kinds are recognised from their tags`() {
        val pois = OverpassMapper.toPois(
            response(
                node(1, 40.377, 49.832, "railway" to "subway_entrance"),
                node(2, 40.377, 49.832, "station" to "subway"),
                node(3, 40.377, 49.832, "historic" to "monument"),
                way(4, center = OverpassPoint(40.377, 49.832), tags = arrayOf("man_made" to "bridge")),
                way(
                    5,
                    center = OverpassPoint(40.377, 49.832),
                    tags = arrayOf("bridge" to "yes", "highway" to "primary")
                ),
                node(6, 40.377, 49.832, "place" to "square")
            )
        )

        assertEquals(
            listOf(
                PoiKind.METRO,
                PoiKind.METRO,
                PoiKind.MONUMENT,
                PoiKind.BRIDGE,
                PoiKind.BRIDGE,
                PoiKind.SQUARE
            ),
            pois.map { it.kind }
        )
    }

    /** `bridge=no` is a tag that exists to say a road is not one. */
    @Test
    fun `a road explicitly tagged as not a bridge is not one`() {
        val pois = OverpassMapper.toPois(
            response(
                way(
                    1,
                    center = OverpassPoint(40.377, 49.832),
                    tags = arrayOf("bridge" to "no", "highway" to "primary")
                )
            )
        )

        assertTrue(pois.isEmpty())
    }

    @Test
    fun `elements with no recognisable tags or no position are dropped`() {
        val pois = OverpassMapper.toPois(
            response(
                node(1, 40.377, 49.832, "amenity" to "cafe"),
                OverpassElement(type = "node", id = 2, tags = mapOf("historic" to "monument")),
                OverpassElement(type = null, id = 3, lat = 40.377, lon = 49.832)
            )
        )

        assertTrue(pois.isEmpty())
    }

    /**
     * An element can be returned by two of the query's result sets - a park that is also tagged
     * historic - once with its outline and once as a bare point. The outline is the better answer.
     */
    @Test
    fun `an element returned twice keeps the version that knows its shape`() {
        val pois = OverpassMapper.toPois(
            response(
                way(1, center = OverpassPoint(40.377, 49.833), tags = arrayOf("leisure" to "park")),
                way(
                    1,
                    bounds = OverpassBounds(
                        minLat = 40.376, minLon = 49.832, maxLat = 40.378, maxLon = 49.834
                    ),
                    tags = arrayOf("leisure" to "park")
                )
            )
        )

        assertEquals(1, pois.size)
        assertNotNull(pois.single().bounds)
    }

    // ---------------------------------------------------------------- the coast

    @Test
    fun `a coastline way becomes one place per hundred metres of it`() {
        // Six points about 55 m apart, so roughly every other one is kept.
        val line = (0..5).map { OverpassPoint(40.3770 + it * 0.0005, 49.8320) }

        val pois = OverpassMapper.toPois(
            response(way(9, geometry = line, tags = arrayOf("natural" to "coastline")))
        )

        assertTrue(pois.all { it.kind == PoiKind.COAST })
        assertEquals(3, pois.size)
        assertEquals(pois.size, pois.map { it.id }.distinct().size)
    }

    /**
     * The pieces must not be named. A coastline way carries the name of the sea, and the
     * achievements count distinct places *by name* - so naming every piece "Xəzər dənizi" would
     * collapse the whole coast into one thing and make "walk all of it" true on arrival.
     */
    @Test
    fun `coastline pieces carry no name however the way is tagged`() {
        val line = (0..3).map { OverpassPoint(40.3770 + it * 0.002, 49.8320) }

        val pois = OverpassMapper.toPois(
            response(
                way(
                    9,
                    geometry = line,
                    tags = arrayOf("natural" to "coastline", "name" to "Xəzər dənizi")
                )
            )
        )

        assertTrue(pois.isNotEmpty())
        assertTrue(pois.all { it.name == null })
        assertEquals(pois.size, pois.map { it.identity }.distinct().size)
    }

    /**
     * Two tiles clip the same way differently, so a piece has to be identified by where it is. An
     * index into the clipped list would make the first piece of one stretch collide with the first
     * piece of another and silently overwrite it in the cache.
     */
    @Test
    fun `coastline pieces are identified by position, not by their place in the list`() {
        val first = OverpassMapper.toPois(
            response(
                way(
                    9,
                    geometry = listOf(OverpassPoint(40.3770, 49.8320), OverpassPoint(40.3790, 49.8320)),
                    tags = arrayOf("natural" to "coastline")
                )
            )
        )
        val second = OverpassMapper.toPois(
            response(
                way(
                    9,
                    geometry = listOf(OverpassPoint(40.3810, 49.8320), OverpassPoint(40.3830, 49.8320)),
                    tags = arrayOf("natural" to "coastline")
                )
            )
        )

        assertTrue((first.map { it.id } intersect second.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `gaps in clipped geometry are skipped rather than crashing`() {
        val line = listOf(
            OverpassPoint(40.3770, 49.8320),
            null,
            OverpassPoint(40.3790, 49.8320),
            OverpassPoint(null, 49.8320)
        )

        val pois = OverpassMapper.toPois(
            response(way(9, geometry = line, tags = arrayOf("natural" to "coastline")))
        )

        assertEquals(2, pois.size)
    }

    @Test
    fun `a coastline way with no geometry contributes nothing`() {
        val pois = OverpassMapper.toPois(
            response(way(9, tags = arrayOf("natural" to "coastline")))
        )

        assertTrue(pois.isEmpty())
    }

    @Test
    fun `coastline pieces have no bounding box`() {
        val pois = OverpassMapper.toPois(
            response(
                way(
                    9,
                    geometry = listOf(OverpassPoint(40.3770, 49.8320)),
                    tags = arrayOf("natural" to "coastline")
                )
            )
        )

        assertNull(pois.single().bounds)
    }
}
