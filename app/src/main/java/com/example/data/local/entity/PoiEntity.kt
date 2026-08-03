package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One place from OpenStreetMap, cached so it is fetched once and never again.
 *
 * The primary key is the OSM type and id (`way/12345`), not a generated one: the same park comes
 * back from every tile it overlaps, and letting those collapse onto one row is what keeps "five
 * different parks" honest. [tileKey] is the tile whose fetch happened to produce the row, kept only
 * so a tile's results can be replaced wholesale when it is refreshed.
 *
 * The bounding-box columns are null for places that are a single point; see
 * [com.example.domain.model.PointOfInterest] for why only the box is stored and not the outline.
 */
@Entity(
    tableName = "pois",
    indices = [Index("tileKey")]
)
data class PoiEntity(
    @PrimaryKey val id: String,
    val tileKey: String,
    /** [com.example.domain.model.PoiKind] by name, so a new kind cannot renumber the existing rows. */
    val kind: String,
    val name: String? = null,
    val lat: Double,
    val lng: Double,
    val north: Double? = null,
    val south: Double? = null,
    val east: Double? = null,
    val west: Double? = null
)

/**
 * A record that a tile has been asked about, kept whether or not it contained anything.
 *
 * This is the row that makes the cache work. Without it a square of plain residential streets with
 * no park, no bridge and no monument in it would come back empty, leave nothing behind, and be
 * queried again on the next pass, forever - which is precisely the hammering of a free shared
 * service the tile scheme exists to avoid. An empty answer is an answer.
 */
@Entity(tableName = "poi_tiles")
data class PoiTileEntity(
    @PrimaryKey val tileKey: String,
    val fetchedAt: Long
)

/**
 * A town's extent, from Nominatim, cached per name.
 *
 * Keyed by the name because that is what the cells carry. [found] false records a name Nominatim
 * could not place, so the lookup is not repeated on every pass - the same reasoning as
 * [PoiTileEntity].
 */
@Entity(tableName = "city_bounds")
data class CityBoundsEntity(
    @PrimaryKey val city: String,
    val countryCode: String? = null,
    val found: Boolean,
    val north: Double? = null,
    val south: Double? = null,
    val east: Double? = null,
    val west: Double? = null,
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val fetchedAt: Long
)
