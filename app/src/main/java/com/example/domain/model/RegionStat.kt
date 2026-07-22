package com.example.domain.model

data class RegionStat(
    val name: String,
    val exploredHexes: Int,
    val totalEstimatedHexes: Int
) {
    val percentage: Double get() = (exploredHexes.toDouble() / totalEstimatedHexes) * 100.0
}
