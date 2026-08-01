package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A grid cell the player has revealed.
 *
 * The epic (TASK 10) describes this table as `explored_cells(cellId, exploredAt, explorationLevel)`.
 * It is the same table: `stomped_hexes` already stored the cell id and the timestamp and already
 * carried the whole persisted exploration history, so the column was added here rather than standing
 * up a second, parallel notion of "explored" that would have to be kept in sync with this one.
 * `hexAddress` is the `cellId`, `timestamp` is the `exploredAt`.
 */
@Entity(tableName = "stomped_hexes")
data class StompedHexEntity(
    @PrimaryKey val hexAddress: String,
    val neighborhood: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** 0.0 = still under full fog, 1.0 = fully cleared. Cells walked into are stored at 1.0. */
    val explorationLevel: Float = 1.0f,

    // The conditions the cell was claimed in, flattened from CellContext. Nullable throughout: a
    // cell claimed offline, or before version 6 of the schema, simply has none of it. See
    // CellContext for why this is stored rather than looked up later.
    val temperatureC: Double? = null,
    val weatherCode: Int? = null,
    val windKmh: Double? = null,
    val sunriseMinute: Int? = null,
    val sunsetMinute: Int? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val elevationM: Double? = null
)
