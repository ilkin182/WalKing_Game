package com.example.domain.engine

import com.example.domain.model.Coordinate

class FallbackHexGridEngine : HexGridEngine {
    // 0.00035 degrees in Mercator corresponds to about 30-40 meters at mid latitudes
    private val hexSize = 0.00035

    override fun latLngToCellAddress(lat: Double, lng: Double, resolution: Int): String {
        val (q, r) = toAxial(lat, lng)
        return "fb_${q}_${r}"
    }

    override fun cellToBoundary(cellAddress: String): List<Coordinate> {
        val (q, r) = parseAddress(cellAddress) ?: return emptyList()

        val xCenter = hexSize * 1.5 * q
        val yCenter = hexSize * (Math.sqrt(3.0) / 2.0 * q + Math.sqrt(3.0) * r)

        val list = mutableListOf<Coordinate>()
        for (i in 0 until 6) {
            val angleRad = Math.toRadians(60.0 * i)
            val cornerYmerc = yCenter + hexSize * Math.sin(angleRad)
            val cornerXmerc = xCenter + hexSize * Math.cos(angleRad)

            // Inverse Web Mercator
            val cornerLng = cornerXmerc
            val cornerYmercRad = Math.toRadians(cornerYmerc)
            val cornerLat = Math.toDegrees(2.0 * Math.atan(Math.exp(cornerYmercRad)) - Math.PI / 2.0)

            list.add(Coordinate(cornerLat, cornerLng))
        }
        return list
    }

    override fun gridDisk(centerCellAddress: String, radius: Int): List<String> {
        val (q, r) = parseAddress(centerCellAddress) ?: return listOf(centerCellAddress)

        val cells = mutableListOf<String>()
        for (dq in -radius..radius) {
            for (dr in Math.max(-radius, -dq - radius)..Math.min(radius, -dq + radius)) {
                cells.add("fb_${q + dq}_${r + dr}")
            }
        }
        return cells
    }

    override fun polygonToCells(corners: List<Coordinate>, resolution: Int): Set<String> {
        if (corners.size < 3) return emptySet()

        // Cast a wide net over every axial cell whose bounding rectangle could overlap the
        // polygon, then keep only the ones whose center actually falls inside it. This lets the
        // grid cover an arbitrary map area (a whole viewport, a neighborhood boundary, ...)
        // instead of being limited to a fixed-radius disk around a single point.
        val axialCorners = corners.map { toAxial(it.lat, it.lng) }
        var minQ = axialCorners.minOf { it.first } - 1
        var maxQ = axialCorners.maxOf { it.first } + 1
        var minR = axialCorners.minOf { it.second } - 1
        var maxR = axialCorners.maxOf { it.second } + 1

        // If the requested area would expand into an unreasonable number of cells (e.g. the map
        // is zoomed far out), clamp the scan window to a capped square centered on the requested
        // area instead of bailing out to an empty set - the grid should always show as much of
        // the map as it reasonably can, never just vanish.
        val cellCount = (maxQ - minQ + 1).toLong() * (maxR - minR + 1).toLong()
        if (cellCount > MAX_CELLS) {
            val centerQ = (minQ + maxQ) / 2
            val centerR = (minR + maxR) / 2
            val halfSpan = (Math.sqrt(MAX_CELLS.toDouble()).toInt() / 2).coerceAtLeast(1)
            minQ = centerQ - halfSpan
            maxQ = centerQ + halfSpan
            minR = centerR - halfSpan
            maxR = centerR + halfSpan
        }

        val cells = mutableSetOf<String>()
        for (q in minQ..maxQ) {
            for (r in minR..maxR) {
                val center = axialToCenter(q, r)
                if (isPointInPolygon(center.lat, center.lng, corners)) {
                    cells.add("fb_${q}_${r}")
                }
            }
        }
        return cells
    }

    private fun toAxial(lat: Double, lng: Double): Pair<Int, Int> {
        // Project lat/lng to Web Mercator (degrees) to ensure perfectly regular hexagons on map
        val x = lng
        val latRad = Math.toRadians(lat)
        val y = Math.toDegrees(Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0)))

        val qFraction = (2.0 / 3.0 * x) / hexSize
        val rFraction = (-1.0 / 3.0 * x + Math.sqrt(3.0) / 3.0 * y) / hexSize

        val xFraction = qFraction
        val zFraction = rFraction
        val yFraction = -xFraction - zFraction

        var q = Math.round(xFraction).toInt()
        var r = Math.round(zFraction).toInt()
        var s = Math.round(yFraction).toInt()

        val qDiff = Math.abs(q - xFraction)
        val rDiff = Math.abs(r - zFraction)
        val sDiff = Math.abs(s - yFraction)

        if (qDiff > rDiff && qDiff > sDiff) {
            q = -r - s
        } else if (rDiff > sDiff) {
            r = -q - s
        }

        return q to r
    }

    private fun axialToCenter(q: Int, r: Int): Coordinate {
        val xCenter = hexSize * 1.5 * q
        val yCenter = hexSize * (Math.sqrt(3.0) / 2.0 * q + Math.sqrt(3.0) * r)
        val yCenterRad = Math.toRadians(yCenter)
        val lat = Math.toDegrees(2.0 * Math.atan(Math.exp(yCenterRad)) - Math.PI / 2.0)
        return Coordinate(lat, xCenter)
    }

    private fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Coordinate>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.lng > lng) != (pj.lng > lng) &&
                lat < (pj.lat - pi.lat) * (lng - pi.lng) / (pj.lng - pi.lng) + pi.lat
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun parseAddress(cellAddress: String): Pair<Int, Int>? {
        val parts = cellAddress.split("_")
        if (parts.size < 3) return null
        val q = parts[1].toIntOrNull() ?: return null
        val r = parts[2].toIntOrNull() ?: return null
        return q to r
    }

    private companion object {
        const val MAX_CELLS = 4000L
    }
}
