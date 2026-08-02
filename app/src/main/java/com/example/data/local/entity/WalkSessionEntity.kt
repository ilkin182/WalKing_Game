package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One continuous walk, from the moment tracking started to the moment it stopped.
 *
 * The odometer in user stats is a single running total and can only ever answer "how far in all";
 * "how far in one go" and "how far today" need the walks kept apart, which is what this table is
 * for. Rows are written as the walk happens rather than at the end, so a crash or a kill by the
 * system costs the last few metres instead of the whole session.
 */
@Entity(tableName = "walk_sessions")
data class WalkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double
)
