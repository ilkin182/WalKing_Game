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

    @Query("DELETE FROM stomped_hexes WHERE hexAddress = :hexAddress")
    suspend fun deleteHex(hexAddress: String)

    @Query("DELETE FROM stomped_hexes")
    suspend fun clearAll()
}
