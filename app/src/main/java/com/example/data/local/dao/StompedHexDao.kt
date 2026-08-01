package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.StompedHexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StompedHexDao {
    @Query("SELECT * FROM stomped_hexes ORDER BY timestamp DESC")
    fun getAllStompedHexesFlow(): Flow<List<StompedHexEntity>>

    @Query("SELECT * FROM stomped_hexes")
    suspend fun getAllStompedHexes(): List<StompedHexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHex(hex: StompedHexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHexes(hexes: List<StompedHexEntity>)

    /**
     * Inserts cells that are not in the table yet and leaves existing rows untouched.
     *
     * Used for the partially explored ring around a dwell: a cell the player has actually walked
     * into must never be knocked back down to a partial level by later seeing it from next door.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHexesIfAbsent(hexes: List<StompedHexEntity>)

    /** Raises the exploration level of existing cells, never lowering one. */
    @Query(
        "UPDATE stomped_hexes SET explorationLevel = :level, timestamp = :timestamp " +
            "WHERE hexAddress IN (:hexAddresses) AND explorationLevel < :level"
    )
    suspend fun raiseExplorationLevel(hexAddresses: List<String>, level: Float, timestamp: Long): Int

    /**
     * Cells whose elevation has not been looked up yet, oldest first.
     *
     * Drives the batch enrichment pass, which is also what backfills cells claimed before the
     * column existed - so the queue naturally drains from the start of the player's history.
     */
    @Query("SELECT hexAddress FROM stomped_hexes WHERE elevationM IS NULL ORDER BY timestamp LIMIT :limit")
    suspend fun cellsMissingElevation(limit: Int): List<String>

    @Query("UPDATE stomped_hexes SET elevationM = :elevationMeters WHERE hexAddress = :hexAddress")
    suspend fun setElevation(hexAddress: String, elevationMeters: Double)

    /**
     * Cells with no place recorded yet, oldest first, skipping [skip].
     *
     * The skip list is what stops one unresolvable cell - a point in the sea, somewhere the geocoder
     * simply has no name for - from sitting at the head of the queue forever and blocking every
     * cell behind it. Callers must pass a non-empty list; `NOT IN ()` is a syntax error in SQLite.
     */
    @Query(
        "SELECT hexAddress FROM stomped_hexes " +
            "WHERE city IS NULL AND countryCode IS NULL AND hexAddress NOT IN (:skip) " +
            "ORDER BY timestamp LIMIT :limit"
    )
    suspend fun cellsMissingPlace(limit: Int, skip: List<String>): List<String>

    @Query("UPDATE stomped_hexes SET city = :city, countryCode = :countryCode WHERE hexAddress = :hexAddress")
    suspend fun setPlace(hexAddress: String, city: String?, countryCode: String?)

    @Query("DELETE FROM stomped_hexes WHERE hexAddress = :hexAddress")
    suspend fun deleteHex(hexAddress: String)

    @Query("DELETE FROM stomped_hexes")
    suspend fun clearAll()
}
