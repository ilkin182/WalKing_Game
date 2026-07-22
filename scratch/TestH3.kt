import com.uber.h3core.H3Core

fun main() {
    val h3 = H3Core.newInstance()
    val cell = h3.latLngToCellAddress(37.775938728915946, -122.41795063018799, 9)
    val boundary = h3.cellToBoundary(cell)
    println(boundary.size)
}
