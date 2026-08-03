package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.CityBoundsEntity
import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.PoiTileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {

    @Query("SELECT * FROM pois")
    fun observeAll(): Flow<List<PoiEntity>>

    /** Tiles already asked about, out of the ones the caller cares about. */
    @Query("SELECT tileKey FROM poi_tiles WHERE tileKey IN (:tileKeys) AND fetchedAt > :freshAfter")
    suspend fun cachedTiles(tileKeys: List<String>, freshAfter: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPois(pois: List<PoiEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markTileFetched(tile: PoiTileEntity)

    @Query("DELETE FROM pois WHERE tileKey = :tileKey")
    suspend fun deletePoisOfTile(tileKey: String)

    /**
     * Stores a tile's results and marks it done, as one unit.
     *
     * The two have to land together: rows without the marker would be re-fetched and duplicated, and
     * a marker without the rows would record a square as answered that the app knows nothing about.
     *
     * The tile's previous rows go first so a refresh does not leave behind places that have since
     * been deleted from OpenStreetMap. Rows are keyed by OSM id, so a place shared with a
     * neighbouring tile survives - it is re-inserted by whichever tile owns it now.
     */
    @Transaction
    suspend fun replaceTile(tileKey: String, pois: List<PoiEntity>, fetchedAt: Long) {
        deletePoisOfTile(tileKey)
        if (pois.isNotEmpty()) insertPois(pois)
        markTileFetched(PoiTileEntity(tileKey, fetchedAt))
    }

    @Query("SELECT * FROM city_bounds")
    fun observeCityBounds(): Flow<List<CityBoundsEntity>>

    @Query("SELECT city FROM city_bounds")
    suspend fun knownCities(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCityBounds(bounds: CityBoundsEntity)

    @Query("DELETE FROM pois")
    suspend fun clearPois()

    @Query("DELETE FROM poi_tiles")
    suspend fun clearTiles()

    @Query("DELETE FROM city_bounds")
    suspend fun clearCityBounds()
}
