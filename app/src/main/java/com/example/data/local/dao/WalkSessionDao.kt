package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.local.entity.WalkSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkSessionDao {

    @Insert
    suspend fun insert(session: WalkSessionEntity): Long

    /** Extends the open session in place, so an interrupted walk keeps what it had covered. */
    @Query("UPDATE walk_sessions SET distanceMeters = :distanceMeters, endedAt = :endedAt WHERE id = :id")
    suspend fun update(id: Long, distanceMeters: Double, endedAt: Long)

    @Query("SELECT * FROM walk_sessions ORDER BY startedAt")
    fun observeAll(): Flow<List<WalkSessionEntity>>

    @Query("DELETE FROM walk_sessions")
    suspend fun clearAll()
}
