package com.example.domain.model

data class ActiveNeighborhood(
    val name: String,
    val centerLat: Double,
    val centerLng: Double,
    val totalCells: Set<String>
)
