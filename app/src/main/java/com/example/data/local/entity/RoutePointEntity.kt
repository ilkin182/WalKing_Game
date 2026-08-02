package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One position on the line a walk traced, tied to the [WalkSessionEntity] it belongs to.
 *
 * Written down-sampled rather than one row per GPS fix: a fix arrives every three seconds, so an
 * hour's walk would be twelve hundred rows, nearly all of them a metre or two apart and telling the
 * shape analysis nothing it does not already know. See the repository for the spacing.
 *
 * No foreign key on `sessionId`: the sessions table is cleared wholesale when progress is reset, and
 * a constraint would only turn that into an ordering problem. Routes are cleared alongside it.
 */
@Entity(
    tableName = "route_points",
    indices = [Index("sessionId")]
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val lat: Double,
    val lng: Double,
    val at: Long
)
