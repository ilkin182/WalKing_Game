package com.example.data.remote

import com.example.domain.model.GeoBounds
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.Locale

/**
 * OpenStreetMap's Overpass API - what is actually on the ground in a given box.
 *
 * Keyless like the weather service, and free for the same reason: it runs on donated hardware. That
 * is the constraint the whole points-of-interest layer is built around. The app asks about one fixed
 * ~1 km square at a time ([com.example.domain.engine.PoiTiles]), stores the answer in Room, and never
 * asks about that square again - see [com.example.data.repository.PoiRepositoryImpl] for the rate
 * limit that sits on top.
 *
 * The query goes as a form-encoded POST rather than in the URL: Overpass QL for six kinds of place
 * runs to several hundred characters, past what is safe to put in a query string.
 */
interface OverpassApi {

    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun query(@Field("data") query: String): OverpassResponse

    companion object {
        const val BASE_URL = "https://overpass-api.de/"

        /** Overpass' own limit for one query. The client's read timeout has to clear this. */
        const val QUERY_TIMEOUT_SECONDS = 25

        /**
         * Everything the geography achievements ask about, in one query per tile.
         *
         * Three result sets because they need three different amounts of geometry, and Overpass only
         * allows one geometry mode per `out`:
         *
         *  - **areas** (parks, squares) come back as `bb`, their bounding box. The player is inside
         *    the park or they are not, and a box answers that for a shape a few hundred metres
         *    across.
         *  - **points** (monuments, metro, bridges) come back as `center`, one coordinate. These are
         *    matched with a tolerance around them anyway, so the outline would add nothing.
         *  - **coast** comes back as `geom` *clipped to the tile*. Without the clip Overpass would
         *    return the whole way, and a single coastline way can run for a hundred kilometres past
         *    the square that was actually asked about.
         *
         * `historic` is restricted to the monument-like values rather than taken as a whole: the bare
         * key is on every old building in a historic centre, and "be near 10 monuments" is not meant
         * to be answered by walking down one street. Bridges exclude `bridge=no`, which is a tag that
         * exists to say a road is *not* one.
         */
        fun tileQuery(bounds: GeoBounds): String {
            val box = box(bounds)
            return buildString {
                append("[out:json][timeout:$QUERY_TIMEOUT_SECONDS];")
                append("(nw[\"leisure\"=\"park\"]($box);nw[\"place\"=\"square\"]($box);)->.areas;")
                append("(")
                append("nw[\"historic\"~\"^$HISTORIC_VALUES$\"]($box);")
                append("node[\"railway\"=\"subway_entrance\"]($box);")
                append("nw[\"station\"=\"subway\"]($box);")
                append("nw[\"man_made\"=\"bridge\"]($box);")
                append("way[\"bridge\"][\"bridge\"!=\"no\"][\"highway\"]($box);")
                append(")->.points;")
                append("way[\"natural\"=\"coastline\"]($box)->.coast;")
                append(".areas out tags bb;")
                append(".points out tags center;")
                append(".coast out geom($box);")
            }
        }

        private const val HISTORIC_VALUES =
            "(monument|memorial|statue|castle|fort|city_gate|ruins|archaeological_site|tomb|" +
                "monastery|citywalls)"

        /** Overpass takes its boxes as south,west,north,east. */
        private fun box(bounds: GeoBounds): String = listOf(
            bounds.south, bounds.west, bounds.north, bounds.east
        ).joinToString(",") { String.format(Locale.US, "%.6f", it) }
    }
}

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    @Json(name = "elements") val elements: List<OverpassElement>? = null
)

/**
 * One element, in whichever shape the `out` mode that produced it prints.
 *
 * Every geometry field is optional because the three result sets in
 * [OverpassApi.tileQuery] fill in different ones: a node carries [lat]/[lon], an area carries
 * [bounds], a point-mode way carries [center], and a coastline way carries [geometry].
 */
@JsonClass(generateAdapter = true)
data class OverpassElement(
    @Json(name = "type") val type: String? = null,
    @Json(name = "id") val id: Long? = null,
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null,
    @Json(name = "center") val center: OverpassPoint? = null,
    @Json(name = "bounds") val bounds: OverpassBounds? = null,
    /**
     * Nullable elements: clipped geometry leaves gaps where the way left the box, and Overpass
     * prints those as nulls rather than closing up the array.
     */
    @Json(name = "geometry") val geometry: List<OverpassPoint?>? = null,
    @Json(name = "tags") val tags: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class OverpassPoint(
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null
)

@JsonClass(generateAdapter = true)
data class OverpassBounds(
    @Json(name = "minlat") val minLat: Double? = null,
    @Json(name = "minlon") val minLon: Double? = null,
    @Json(name = "maxlat") val maxLat: Double? = null,
    @Json(name = "maxlon") val maxLon: Double? = null
)
