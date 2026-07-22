cat app/src/main/java/com/example/ui/GameViewModel.kt | sed -n '1,343p' > temp.kt

cat << 'INNER_EOF' >> temp.kt
class FallbackH3Engine : IH3Engine {
    // 0.00035 degrees in Mercator corresponds to about 30-40 meters at mid latitudes
    private val HEX_SIZE = 0.00035

    override fun latLngToCellAddress(lat: Double, lng: Double, res: Int): String {
        // Project lat/lng to Web Mercator (degrees) to ensure perfectly regular hexagons on map
        val x = lng
        val latRad = Math.toRadians(lat)
        val y = Math.toDegrees(Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0)))

        val qFraction = (2.0 / 3.0 * x) / HEX_SIZE
        val rFraction = (-1.0 / 3.0 * x + Math.sqrt(3.0) / 3.0 * y) / HEX_SIZE

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

        return "fb_${q}_${r}"
    }

    override fun cellToBoundary(cellAddress: String): List<LatLng> {
        val parts = cellAddress.split("_")
        if (parts.size < 3) return emptyList()
        val q = parts[1].toDoubleOrNull() ?: 0.0
        val r = parts[2].toDoubleOrNull() ?: 0.0

        val xCenter = HEX_SIZE * 1.5 * q
        val yCenter = HEX_SIZE * (Math.sqrt(3.0) / 2.0 * q + Math.sqrt(3.0) * r)

        val list = mutableListOf<LatLng>()
        for (i in 0 until 6) {
            val angleRad = Math.toRadians(60.0 * i)
            val cornerYmerc = yCenter + HEX_SIZE * Math.sin(angleRad)
            val cornerXmerc = xCenter + HEX_SIZE * Math.cos(angleRad)
            
            // Inverse Web Mercator
            val cornerLng = cornerXmerc
            val cornerYmercRad = Math.toRadians(cornerYmerc)
            val cornerLat = Math.toDegrees(2.0 * Math.atan(Math.exp(cornerYmercRad)) - Math.PI / 2.0)
            
            list.add(LatLng(cornerLat, cornerLng))
        }
        return list
    }

    override fun gridDisk(centerCellAddress: String, radius: Int): List<String> {
        val parts = centerCellAddress.split("_")
        if (parts.size < 3) return listOf(centerCellAddress)
        val q = parts[1].toIntOrNull() ?: 0
        val r = parts[2].toIntOrNull() ?: 0

        val cells = mutableListOf<String>()
        for (dq in -radius..radius) {
            for (dr in Math.max(-radius, -dq - radius)..Math.min(radius, -dq + radius)) {
                cells.add("fb_${q + dq}_${r + dr}")
            }
        }
        return cells
    }

    override fun polygonToCells(corners: List<LatLng>, res: Int): Set<String> {
        return emptySet() // Not implemented in fallback
    }
}
INNER_EOF

mv temp.kt app/src/main/java/com/example/ui/GameViewModel.kt
