package com.example.domain.engine

import com.example.domain.model.Coordinate

interface HexGridEngine {
    fun latLngToCellAddress(lat: Double, lng: Double, resolution: Int): String
    fun cellToBoundary(cellAddress: String): List<Coordinate>
    fun gridDisk(centerCellAddress: String, radius: Int): List<String>
    fun polygonToCells(corners: List<Coordinate>, resolution: Int): Set<String>
}
