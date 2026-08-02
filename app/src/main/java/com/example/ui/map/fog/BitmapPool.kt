package com.example.ui.map.fog

import android.graphics.Bitmap

/**
 * A small pool of same-sized tile bitmaps.
 *
 * A 512x512 ARGB_8888 tile is 1 MB, so letting the GC churn through one per tile per pan is exactly
 * the allocation pressure that costs frames. Tiles evicted from the fog cache come back here and get
 * refilled instead of reallocated.
 *
 * Not thread-safe: it is confined to the thread that renders fog tiles (the UI thread, from
 * `Overlay.draw`), which is also the only thread that evicts from the cache.
 */
class BitmapPool(
    private val width: Int,
    private val height: Int,
    private val maxEntries: Int,
    private val config: Bitmap.Config = Bitmap.Config.ARGB_8888
) {
    private val free = ArrayDeque<Bitmap>(maxEntries)

    /** Number of bitmaps currently parked in the pool. Exposed for tests and profiling. */
    val available: Int get() = free.size

    /** How many bitmaps this pool has had to allocate. A steady state should stop growing this. */
    var allocations: Int = 0
        private set

    /** A blank tile bitmap, reused from the pool when one is free. */
    fun acquire(): Bitmap {
        val recycled = free.removeFirstOrNull()
        if (recycled != null && !recycled.isRecycled) {
            recycled.eraseColor(0)
            return recycled
        }
        allocations++
        return Bitmap.createBitmap(width, height, config)
    }

    /** Hands a bitmap back for reuse. Wrong-sized or recycled bitmaps are dropped, not stored. */
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.width != width || bitmap.height != height) return
        if (free.size >= maxEntries) {
            bitmap.recycle()
            return
        }
        free.addLast(bitmap)
    }

    fun clear() {
        free.forEach { if (!it.isRecycled) it.recycle() }
        free.clear()
    }
}
