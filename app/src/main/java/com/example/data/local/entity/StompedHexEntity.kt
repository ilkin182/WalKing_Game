package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stomped_hexes")
data class StompedHexEntity(
    @PrimaryKey val hexAddress: String,
    val neighborhood: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
