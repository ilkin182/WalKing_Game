package com.example.data.repository

import com.example.data.local.dao.PoiDao
import com.example.data.mapper.OverpassMapper
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.data.remote.NominatimApi
import com.example.data.remote.NominatimPlaceDto
import com.example.data.remote.OverpassApi
import com.example.domain.engine.PoiTiles
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import com.example.domain.model.PointOfInterest
import com.example.domain.repository.PoiRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The points-of-interest cache, and the only thing in the app allowed to talk to Overpass or
 * Nominatim.
 *
 * Both are free services running on donated hardware, with no API key to throttle per account and
 * no commercial relationship to fall back on - the only thing keeping the app welcome is that it
 * behaves. Three rules do that, and all three live here:
 *
 *  1. **A tile is asked about once.** The world is cut into fixed ~1 km squares
 *     ([PoiTiles]) and each square's answer is stored, empty answers included. A player walking a
 *     familiar route generates no requests at all after the first time.
 *  2. **One request at a time, with a gap between them.** [requestGapMillis] is enforced across
 *     every caller, so a backlog of two hundred tiles drains over hours rather than arriving as a
 *     burst that looks exactly like an attack.
 *  3. **A failure is not cached.** A tile that could not be fetched is left unmarked and retried
 *     later, but only later - the gap applies to failed attempts too, so an outage does not turn
 *     into a retry loop.
 *
 * The cache does not expire in any practical sense: [tileTtlMillis] is long because parks and
 * bridges do not move, and re-asking about a square that has already been answered is exactly the
 * traffic there is no justification for.
 */
class PoiRepositoryImpl(
    private val dao: PoiDao,
    private val overpass: OverpassApi,
    private val nominatim: NominatimApi,
    private val tileTtlMillis: Long = DEFAULT_TILE_TTL_MILLIS,
    private val requestGapMillis: Long = DEFAULT_REQUEST_GAP_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) : PoiRepository {

    override val pois: Flow<List<PointOfInterest>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override val cityBounds: Flow<List<CityBounds>> =
        dao.observeCityBounds().map { rows -> rows.map { it.toDomain() } }

    /** Serialises every outbound request, which is what makes the gap between them meaningful. */
    private val requestLock = Mutex()

    private var lastRequestAt = 0L

    override suspend fun cachedTiles(tileKeys: List<String>): Set<String> {
        if (tileKeys.isEmpty()) return emptySet()
        val freshAfter = now() - tileTtlMillis
        // Chunked because the ids go into a SQL `IN`, and SQLite has a ceiling on how many host
        // parameters one statement may carry.
        return tileKeys.chunked(SQL_PARAMETER_LIMIT)
            .flatMap { dao.cachedTiles(it, freshAfter) }
            .toSet()
    }

    override suspend fun fetchTile(tileKey: String): Boolean {
        val bounds = PoiTiles.boundsOf(tileKey) ?: return false

        return withRequestGap {
            val response = try {
                overpass.query(OverpassApi.tileQuery(bounds))
            } catch (e: Exception) {
                // Offline, a timeout, Overpass shedding load with a 429 or a 504 - all the same
                // here. The tile stays unmarked, so it comes round again on a later pass.
                return@withRequestGap false
            }

            val pois = OverpassMapper.toPois(response)
                // A place is filed under the tile that found it, whichever tile its centre is
                // actually in, so re-fetching this tile can clean up after itself.
                .map { it.toEntity(tileKey) }

            dao.replaceTile(tileKey, pois, now())
            true
        }
    }

    override suspend fun knownCities(): Set<String> = dao.knownCities().toSet()

    override suspend fun fetchCityBounds(city: String, countryCode: String?): Boolean {
        if (city.isBlank()) return false

        return withRequestGap {
            val result = try {
                nominatim.search(place = city, countryCodes = countryCode?.lowercase())
                    .firstOrNull()
            } catch (e: Exception) {
                return@withRequestGap false
            }

            // A town Nominatim has no answer for is stored as "looked up, not found". Without that
            // row the same hopeless name would be sent again on every pass for the rest of the
            // player's life, which is the one thing the cache exists to prevent.
            dao.insertCityBounds(result.toCityBounds(city, countryCode).toEntity(now()))
            true
        }
    }

    override suspend fun clearAll() {
        dao.clearPois()
        dao.clearTiles()
        dao.clearCityBounds()
    }

    /**
     * Runs one request, never overlapping another, and never sooner than [requestGapMillis] after
     * the previous one - counting from the previous request's *start*, so a slow answer does not
     * earn the next one an extra wait on top.
     */
    private suspend fun <T> withRequestGap(request: suspend () -> T): T = requestLock.withLock {
        val waitFor = requestGapMillis - (now() - lastRequestAt)
        if (waitFor > 0) pause(waitFor)
        lastRequestAt = now()
        request()
    }

    private fun NominatimPlaceDto?.toCityBounds(city: String, countryCode: String?): CityBounds {
        val box = this?.boundingBox
            ?.takeIf { it.size == 4 }
            ?.mapNotNull { it.toDoubleOrNull() }
            ?.takeIf { it.size == 4 }
            // Nominatim orders it south, north, west, east.
            ?.let { GeoBounds(south = it[0], north = it[1], west = it[2], east = it[3]) }

        val center = this?.let { place ->
            val lat = place.lat?.toDoubleOrNull() ?: return@let null
            val lng = place.lon?.toDoubleOrNull() ?: return@let null
            Coordinate(lat, lng)
        }

        // Both or neither. The two city achievements measure a position against the centre *and*
        // the extent together, so half an answer is not a usable one and is stored as a miss.
        val complete = box != null && center != null
        return CityBounds(
            city = city,
            countryCode = countryCode,
            bounds = box.takeIf { complete },
            center = center.takeIf { complete }
        )
    }

    companion object {
        /**
         * A month. Long because the answer barely changes - and because the cost of being wrong is
         * a park that opened last week going uncounted, against the cost of being eager, which is
         * traffic to a donated service for an answer already on disk.
         */
        const val DEFAULT_TILE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

        /**
         * Fifteen seconds between requests.
         *
         * Slower than either service's published limit, deliberately. Nothing on screen is waiting:
         * this fills in statistics behind a game, so there is no reason to be anywhere near the line.
         */
        const val DEFAULT_REQUEST_GAP_MILLIS = 15_000L

        /** SQLite's default ceiling on host parameters in one statement, less a little headroom. */
        private const val SQL_PARAMETER_LIMIT = 900
    }
}
