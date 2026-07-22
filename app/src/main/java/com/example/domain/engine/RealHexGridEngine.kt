package com.example.domain.engine

import com.example.domain.model.Coordinate
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng

class RealHexGridEngine(private val h3: H3Core) : HexGridEngine {
    override fun latLngToCellAddress(lat: Double, lng: Double, resolution: Int): String {
        return h3.latLngToCellAddress(lat, lng, resolution)
    }

    override fun cellToBoundary(cellAddress: String): List<Coordinate> {
        return h3.cellToBoundary(cellAddress).map { Coordinate(it.lat, it.lng) }
    }

    override fun gridDisk(centerCellAddress: String, radius: Int): List<String> {
        return h3.gridDisk(centerCellAddress, radius)
    }

    override fun polygonToCells(corners: List<Coordinate>, resolution: Int): Set<String> {
        return try {
            val h3Corners = corners.map { LatLng(it.lat, it.lng) }
            h3.polygonToCells(h3Corners, emptyList(), resolution).map { h3.h3ToString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
