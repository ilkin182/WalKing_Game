package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.local.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePointDao {

    @Insert
    suspend fun insert(point: RoutePointEntity)

    /**
     * Every recorded point, oldest first within each walk.
     *
     * Ordered here rather than after grouping, so the caller can slice the rows into routes without
     * having to sort each one again - the shape analysis depends entirely on the order.
     */
    @Query("SELECT * FROM route_points ORDER BY sessionId, at")
    fun observeAll(): Flow<List<RoutePointEntity>>

    @Query("DELETE FROM route_points")
    suspend fun clearAll()
}
