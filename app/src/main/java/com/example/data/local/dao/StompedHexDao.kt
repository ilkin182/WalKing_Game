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

    @Query("DELETE FROM stomped_hexes WHERE hexAddress = :hexAddress")
    suspend fun deleteHex(hexAddress: String)

    @Query("DELETE FROM stomped_hexes")
    suspend fun clearAll()
}
