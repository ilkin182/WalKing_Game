package com.example.data.mapper

import com.example.data.remote.OverpassElement
import com.example.data.remote.OverpassResponse
import com.example.domain.engine.GeoPath
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest
import java.util.Locale

/**
 * Turns one tile's Overpass answer into the places the achievements count.
 *
 * Everything unrecognised is dropped rather than guessed at. An element with no coordinates, an
 * element whose tags match none of the six kinds, a coastline way that came back with no geometry -
 * each is simply not a place as far as this app is concerned, and a query that returns nothing but
 * those is a legitimately empty tile.
 */
object OverpassMapper {

    /**
     * How finely a coastline way is cut into points.
     *
     * The coastline is the one thing here that is a line rather than a place, and "walk the whole
     * coastline" has to be measured as a fraction of something countable. Cutting it into 100 m
     * pieces is that something: each piece is one point the player either got near or did not, so
     * the achievement reads as "you have walked every hundred metres of the coast you have been to".
     */
    private const val COAST_SAMPLE_METERS = 100.0

    /**
     * Ceiling on the pieces one way contributes to one tile.
     *
     * A tile is about a kilometre across, so a coastline crossing it yields on the order of ten
     * pieces; anything approaching this many means the geometry came back unclipped, and storing
     * thousands of rows for one square would poison both the cache and the coverage percentage.
     */
    private const val MAX_COAST_POINTS_PER_WAY = 60

    fun toPois(response: OverpassResponse): List<PointOfInterest> {
        val byId = LinkedHashMap<String, PointOfInterest>()

        response.elements.orEmpty().forEach { element ->
            val id = element.osmId() ?: return@forEach

            if (element.tags?.get("natural") == "coastline") {
                coastPoints(id, element).forEach { byId[it.id] = it }
                return@forEach
            }

            val kind = element.kind() ?: return@forEach
            val poi = element.toPoi(id, kind) ?: return@forEach

            // The same element can come back from two of the query's result sets - a park that is
            // also tagged historic, say - once with its outline and once as a single point. Keep the
            // one that knows the shape.
            val existing = byId[id]
            if (existing == null || (existing.bounds == null && poi.bounds != null)) {
                byId[id] = poi
            }
        }

        return byId.values.toList()
    }

    private fun OverpassElement.osmId(): String? {
        val type = type ?: return null
        val id = id ?: return null
        return "$type/$id"
    }

    /**
     * Which kind of place an element's tags make it.
     *
     * Ordered by how specific the tag is, not by how the query grouped them: a metro entrance inside
     * a square is a metro entrance. Coastline is handled before this is reached, because it becomes
     * many places rather than one.
     */
    private fun OverpassElement.kind(): PoiKind? {
        val tags = tags ?: return null
        return when {
            tags["railway"] == "subway_entrance" || tags["station"] == "subway" -> PoiKind.METRO
            tags["man_made"] == "bridge" -> PoiKind.BRIDGE
            tags.containsKey("bridge") && tags["bridge"] != "no" -> PoiKind.BRIDGE
            tags["leisure"] == "park" -> PoiKind.PARK
            tags["place"] == "square" -> PoiKind.SQUARE
            tags.containsKey("historic") -> PoiKind.MONUMENT
            else -> null
        }
    }

    private fun OverpassElement.toPoi(id: String, kind: PoiKind): PointOfInterest? {
        val box = bounds?.let { b ->
            val north = b.maxLat ?: return@let null
            val south = b.minLat ?: return@let null
            val east = b.maxLon ?: return@let null
            val west = b.minLon ?: return@let null
            GeoBounds(north = north, south = south, east = east, west = west)
        }

        val middle = center?.let { c ->
            val cLat = c.lat ?: return@let null
            val cLng = c.lon ?: return@let null
            Coordinate(cLat, cLng)
        }
        val point = when {
            lat != null && lon != null -> Coordinate(lat, lon)
            middle != null -> middle
            box != null -> Coordinate(box.centerLat, box.centerLng)
            else -> return null
        }

        return PointOfInterest(
            id = id,
            kind = kind,
            name = tags?.get("name")?.takeIf { it.isNotBlank() },
            center = point,
            bounds = box
        )
    }

    /**
     * A coastline way as a run of points, one per [COAST_SAMPLE_METERS] along it.
     *
     * The pieces deliberately carry no name. A coastline way is usually named for the sea it borders,
     * and the achievements count *distinct* places by name where there is one - so naming every
     * piece "Xəzər dənizi" would collapse the whole coast into a single thing and make "walk all of
     * it" true the moment the player reached any part of it.
     */
    private fun coastPoints(id: String, element: OverpassElement): List<PointOfInterest> {
        val line = element.geometry.orEmpty().mapNotNull { point ->
            val lat = point?.lat ?: return@mapNotNull null
            val lng = point.lon ?: return@mapNotNull null
            Coordinate(lat, lng)
        }
        if (line.isEmpty()) return emptyList()

        val points = mutableListOf<Coordinate>()
        var sinceLast = COAST_SAMPLE_METERS
        line.forEachIndexed { i, point ->
            if (i > 0) sinceLast += GeoPath.distanceMeters(line[i - 1], point)
            if (sinceLast >= COAST_SAMPLE_METERS) {
                points.add(point)
                sinceLast = 0.0
            }
        }

        // Identified by where the piece is, not by its position in the list. The way is clipped to
        // the tile being fetched, so the same way sampled from the tile next door starts counting
        // from zero again - an index would make two different stretches of coast collide on one row
        // and quietly overwrite each other.
        return points.take(MAX_COAST_POINTS_PER_WAY).map { point ->
            PointOfInterest(
                id = "$id@${format(point.lat)},${format(point.lng)}",
                kind = PoiKind.COAST,
                name = null,
                center = point,
                bounds = null
            )
        }
    }

    /** ~1 m of precision: fine enough that two tiles agree on a shared point, coarse enough to match. */
    private fun format(degrees: Double) = String.format(Locale.US, "%.5f", degrees)
}
