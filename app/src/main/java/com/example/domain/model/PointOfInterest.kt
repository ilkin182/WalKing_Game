package com.example.domain.model

/**
 * The kinds of place the geography achievements ask about.
 *
 * [reachMeters] is how close a claimed cell has to be to count as having been there, and it differs
 * per kind because the achievements mean different things by "at". A monument is somewhere you walk
 * past, a metro station is a building you notice from across the road, a bridge is something you are
 * either on or not. The value is applied around whatever geometry the place has - the outline of a
 * park, the single point of a statue - so it is a tolerance, not a search radius.
 */
enum class PoiKind(val reachMeters: Double) {
    /** `leisure=park`. An area: the player is in it or they are not. */
    PARK(25.0),

    /** Anything tagged `historic` - statues, memorials, ruins, castle walls. */
    MONUMENT(75.0),

    /** Metro stations and their street entrances. Generous: you see a station before you reach it. */
    METRO(120.0),

    /** Road and foot bridges. Tight, or the street either side would count as having crossed. */
    BRIDGE(30.0),

    /** `place=square`. An area, like a park. */
    SQUARE(25.0),

    /**
     * A sampled point on the coastline. Wide enough to take in the promenade rather than only the
     * waterline itself, which is usually fenced off or in the sea.
     */
    COAST(150.0)
}

/**
 * One place from OpenStreetMap, as far as the achievements need to know about it.
 *
 * [bounds] is the outline's bounding box for places that have an extent (a park, a square, a bridge)
 * and null for places that are a single point (a statue, a metro entrance, a sampled point of
 * coastline). Only the box is kept, never the polygon: the achievements ask "was the player in this
 * park", and for a park - a compact, roughly convex shape a few hundred metres across - the box
 * answers that as well as the outline would, at a fraction of the storage and none of the
 * point-in-polygon arithmetic on every recomputation.
 *
 * [id] is the OSM type and id (`way/12345`), which is what makes "five *different* parks" countable:
 * a park that straddles two of the cached tiles comes back from both and collapses to one row.
 */
data class PointOfInterest(
    val id: String,
    val kind: PoiKind,
    val name: String?,
    val center: Coordinate,
    val bounds: GeoBounds? = null
) {
    /**
     * What counts as "one place" when the achievements count distinct ones.
     *
     * The name, where there is one, rather than the id. A long bridge is mapped as a dozen separate
     * ways in OpenStreetMap, and a park as one outline per fenced section - counting ids would make
     * "cross five bridges" answerable by walking over one. Where there is no name there is nothing
     * better to go on than the id, which is the right answer for an unnamed footbridge: it is its
     * own bridge.
     *
     * Two genuinely different places sharing a name in two different towns would merge here. That is
     * accepted: it can only ever undercount, which is the safe direction for an achievement, and no
     * rule here asks for enough distinct places for it to be reachable in practice.
     */
    val identity: String
        get() = name?.takeIf { it.isNotBlank() }?.let { "${kind.name}:$it" } ?: id

    /**
     * Whether a position is close enough to this place to count as having been at it.
     *
     * The tolerance is applied as a box rather than a circle - the corners are up to 40% more
     * generous than the sides - because everything this is compared against is already a grid cell
     * about 40 m across, and no achievement here turns on a few metres.
     */
    fun reaches(lat: Double, lng: Double): Boolean {
        val padLat = kind.reachMeters / METERS_PER_DEGREE_LAT
        val padLng = padLat / Math.cos(Math.toRadians(lat)).coerceAtLeast(MIN_COS_LAT)
        val box = bounds ?: GeoBounds(center.lat, center.lat, center.lng, center.lng)
        return box.expand(padLat, padLng).contains(lat, lng)
    }

    private companion object {
        const val METERS_PER_DEGREE_LAT = 111_111.0

        /** Guards the 1/cos(lat) blow-up near the poles, as [com.example.domain.engine.HexGridConfig] does. */
        const val MIN_COS_LAT = 0.01
    }
}

/**
 * The extent of a town or city, from Nominatim.
 *
 * Stored per city name because that is what the cells carry - reverse geocoding records the town a
 * cell was claimed in, not an identifier for it. [found] is false for a city Nominatim could not
 * place: the row exists so the lookup is not retried on every pass, which is the whole reason this
 * is cached at all.
 *
 * The centre is the point Nominatim returns for the place, which for a city is its recognised
 * centre, not the middle of the bounding box - those are rarely the same for a coastal city.
 */
data class CityBounds(
    val city: String,
    val countryCode: String?,
    val bounds: GeoBounds?,
    val center: Coordinate?
) {
    val found: Boolean get() = bounds != null && center != null
}
