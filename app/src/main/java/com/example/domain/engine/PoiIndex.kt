package com.example.domain.engine

import com.example.domain.model.GeoBounds
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest

/**
 * The cached points of interest, bucketed by tile so a position can be matched against them cheaply.
 *
 * The achievements are recomputed from scratch whenever the exploration history changes, and they
 * ask "which places was this cell at" of every cell the player has ever claimed. Against a flat list
 * that is cells x places - thousands times hundreds - on every walk. Bucketing by
 * [PoiTiles] turns it into cells x (the handful of places in the nine tiles around each one), which
 * is what makes the geography rules affordable to answer continuously rather than on a schedule.
 *
 * A place is filed under every tile its outline touches, so a park spanning four tiles is found from
 * any of them.
 */
class PoiIndex private constructor(
    private val byTile: Map<String, List<PointOfInterest>>,
    /** Every distinct place, for the rules that ask what share of them has been visited. */
    val all: List<PointOfInterest>
) {

    /**
     * How many places of a kind are known at all - the denominator of a coverage percentage.
     *
     * Counted by [PointOfInterest.identity] rather than by row, so the numerator and the denominator
     * of "half the parks" are counting the same things.
     */
    private val countsByKind: Map<PoiKind, Int> = all
        .groupBy { it.kind }
        .mapValues { (_, ofKind) -> ofKind.distinctBy { it.identity }.size }

    fun countOf(kind: PoiKind): Int = countsByKind[kind] ?: 0

    /**
     * The places a position counts as having been at.
     *
     * Deduplicated by id: a place filed under several tiles appears in more than one of the nine
     * buckets searched, and counting it twice would inflate "five different parks".
     */
    fun matching(lat: Double, lng: Double): List<PointOfInterest> {
        val hits = LinkedHashMap<String, PointOfInterest>()
        PoiTiles.keysAround(lat, lng).forEach { key ->
            byTile[key]?.forEach { poi ->
                if (poi.id !in hits && poi.reaches(lat, lng)) hits[poi.id] = poi
            }
        }
        return hits.values.toList()
    }

    companion object {
        val EMPTY = PoiIndex(emptyMap(), emptyList())

        fun build(pois: List<PointOfInterest>): PoiIndex {
            if (pois.isEmpty()) return EMPTY

            // The same place can arrive from two tiles' worth of results; the id is what makes them
            // one place, so it is deduplicated here rather than at the point of counting.
            val distinct = pois.associateBy { it.id }.values.toList()

            val byTile = HashMap<String, MutableList<PointOfInterest>>()
            distinct.forEach { poi ->
                val box = poi.bounds ?: GeoBounds(
                    north = poi.center.lat,
                    south = poi.center.lat,
                    east = poi.center.lng,
                    west = poi.center.lng
                )
                PoiTiles.keysCovering(box).forEach { key ->
                    byTile.getOrPut(key) { mutableListOf() }.add(poi)
                }
            }
            return PoiIndex(byTile, distinct)
        }
    }
}
