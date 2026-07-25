package com.example.domain.engine

/**
 * Single source of truth for the grid's cell size. Every use case that stomps or renders cells
 * must resolve lat/lng through this same resolution, otherwise stomped addresses and rendered
 * cell addresses would be computed against different grids and silently stop matching each other.
 */
object HexGridConfig {
    const val RESOLUTION = 11
}
