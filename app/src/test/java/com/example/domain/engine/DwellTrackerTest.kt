package com.example.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DwellTrackerTest {
    private val tracker = DwellTracker(thresholdMs = 30_000L)

    @Test
    fun `arriving in a cell does not immediately count as a dwell`() {
        assertNull(tracker.onFix("cell_a", 0L))
        assertNull(tracker.onFix("cell_a", 29_999L))
    }

    @Test
    fun `staying past the threshold reports the cell`() {
        tracker.onFix("cell_a", 0L)

        assertEquals("cell_a", tracker.onFix("cell_a", 30_000L))
    }

    @Test
    fun `a dwell is reported once no matter how long the player stands there`() {
        tracker.onFix("cell_a", 0L)
        assertEquals("cell_a", tracker.onFix("cell_a", 30_000L))

        // This is the "standing still does not repeatedly write to the database" guarantee: every
        // later fix in the same cell has to come back empty.
        assertNull(tracker.onFix("cell_a", 60_000L))
        assertNull(tracker.onFix("cell_a", 600_000L))
    }

    @Test
    fun `moving to another cell restarts the clock`() {
        tracker.onFix("cell_a", 0L)
        assertNull(tracker.onFix("cell_b", 29_000L))

        // The 29 s already spent in cell_a must not count towards cell_b.
        assertNull(tracker.onFix("cell_b", 50_000L))
        assertEquals("cell_b", tracker.onFix("cell_b", 59_000L))
    }

    @Test
    fun `returning to a cell earns a fresh dwell`() {
        tracker.onFix("cell_a", 0L)
        assertEquals("cell_a", tracker.onFix("cell_a", 30_000L))

        tracker.onFix("cell_b", 40_000L)
        tracker.onFix("cell_a", 50_000L)

        assertEquals("cell_a", tracker.onFix("cell_a", 80_000L))
    }

    @Test
    fun `reset forgets the current stay`() {
        tracker.onFix("cell_a", 0L)
        tracker.reset()

        assertNull(tracker.currentCell)
        // The clock restarts from the first fix after the reset, not from before it.
        assertNull(tracker.onFix("cell_a", 30_000L))
        assertEquals("cell_a", tracker.onFix("cell_a", 60_000L))
    }

    @Test
    fun `a clock that jumps backwards restarts the stay instead of stalling forever`() {
        tracker.onFix("cell_a", 100_000L)

        assertNull(tracker.onFix("cell_a", 0L))
        assertEquals("cell_a", tracker.onFix("cell_a", 30_000L))
    }

    @Test
    fun `currentCell tracks where the player is`() {
        assertNull(tracker.currentCell)

        tracker.onFix("cell_a", 0L)

        assertEquals("cell_a", tracker.currentCell)
    }
}
