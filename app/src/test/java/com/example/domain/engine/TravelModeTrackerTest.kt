package com.example.domain.engine

import com.example.domain.model.GeoLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelModeTrackerTest {
    private val tracker = TravelModeTracker()

    @Test
    fun `a walking pace is on foot`() {
        assertTrue(tracker.isOnFoot(fix(at = 0L, speed = 1.4f)))
        assertTrue(tracker.isOnFoot(fix(at = 3_000L, speed = 1.6f)))
    }

    @Test
    fun `a jogger is still on foot`() {
        // Well clear of the vehicle threshold, and exactly the runner the rule must not punish.
        assertTrue(tracker.isOnFoot(fix(at = 0L, speed = 3.5f)))
        assertTrue(tracker.isOnFoot(fix(at = 3_000L, speed = 4.2f)))
    }

    @Test
    fun `driving claims nothing`() {
        assertFalse(tracker.isOnFoot(fix(at = 0L, speed = 14f)))
        assertFalse(tracker.isOnFoot(fix(at = 3_000L, speed = 16f)))
        assertTrue(tracker.isInVehicle)
    }

    @Test
    fun `one wild reading is not a car ride`() {
        walk(from = 0L, to = 30_000L)
        // A single fix that jumped between buildings: refused, but the walk carries on afterwards.
        assertFalse(tracker.isOnFoot(fix(at = 33_000L, speed = 30f)))
        assertTrue(tracker.isOnFoot(fix(at = 36_000L, speed = 1.3f)))
        assertFalse(tracker.isInVehicle)
    }

    @Test
    fun `stopping at a red light does not start claiming the road`() {
        drive(from = 0L, to = 30_000L)

        // Standing still in traffic, twenty seconds of it - nowhere near enough to have got out.
        assertFalse(tracker.isOnFoot(fix(at = 33_000L, speed = 0f)))
        assertFalse(tracker.isOnFoot(fix(at = 43_000L, speed = 0f)))
        assertFalse(tracker.isOnFoot(fix(at = 53_000L, speed = 8f)))
        assertTrue(tracker.isInVehicle)
    }

    @Test
    fun `claiming resumes a minute after getting out`() {
        drive(from = 0L, to = 30_000L)

        assertFalse(tracker.isOnFoot(fix(at = 33_000L, speed = 1.2f)))
        assertFalse(tracker.isOnFoot(fix(at = 63_000L, speed = 1.4f)))
        assertTrue(tracker.isOnFoot(fix(at = 93_000L, speed = 1.3f)))
        assertFalse(tracker.isInVehicle)
    }

    @Test
    fun `a fix with no speed of its own is judged by how far it moved`() {
        // Most fixes carry a speed; the ones that do not still have to be judged, or a provider that
        // reports none would let a whole drive through.
        assertTrue(tracker.isOnFoot(fix(at = 0L, lat = 40.4000)))
        // ~330 m in three seconds: 110 m/s. Not a walk.
        assertFalse(tracker.isOnFoot(fix(at = 3_000L, lat = 40.4030)))
        assertFalse(tracker.isOnFoot(fix(at = 6_000L, lat = 40.4060)))
        assertTrue(tracker.isInVehicle)
    }

    @Test
    fun `fixes an age apart say nothing about how the ground was covered`() {
        assertTrue(tracker.isOnFoot(fix(at = 0L, lat = 40.4000)))
        // Half an hour later and two kilometres away: could be anything, so the mode is left as is.
        assertTrue(tracker.isOnFoot(fix(at = 1_800_000L, lat = 40.4180)))
        assertFalse(tracker.isInVehicle)
    }

    @Test
    fun `resetting forgets the ride`() {
        drive(from = 0L, to = 30_000L)
        tracker.reset()

        assertFalse(tracker.isInVehicle)
        assertTrue(tracker.isOnFoot(fix(at = 33_000L, speed = 1.2f)))
    }

    private fun walk(from: Long, to: Long) {
        var at = from
        while (at <= to) {
            tracker.isOnFoot(fix(at = at, speed = 1.4f))
            at += 3_000L
        }
    }

    private fun drive(from: Long, to: Long) {
        var at = from
        while (at <= to) {
            tracker.isOnFoot(fix(at = at, speed = 15f))
            at += 3_000L
        }
        assertTrue(tracker.isInVehicle)
    }

    private fun fix(
        at: Long,
        speed: Float? = null,
        lat: Double = 40.4093,
        lng: Double = 49.8671
    ) = GeoLocation(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
        timestampMillis = at,
        speedMetersPerSecond = speed
    )
}
