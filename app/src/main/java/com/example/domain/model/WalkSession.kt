package com.example.domain.model

/** One continuous walk. See the `walk_sessions` table for why walks are kept apart. */
data class WalkSession(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double
)
