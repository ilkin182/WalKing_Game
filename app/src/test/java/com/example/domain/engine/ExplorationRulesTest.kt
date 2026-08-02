package com.example.domain.engine

import com.example.domain.model.GeoLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationRulesTest {

    private fun fixWithAccuracy(accuracy: Float) =
        GeoLocation(latitude = 40.4093, longitude = 49.8671, accuracyMeters = accuracy, timestampMillis = 0L)

    @Test
    fun `a precise fix may clear fog`() {
        assertTrue(ExplorationRules.isAccurateEnough(fixWithAccuracy(5f)))
        assertTrue(ExplorationRules.isAccurateEnough(fixWithAccuracy(49.9f)))
    }

    @Test
    fun `50 metres is the last accuracy that still counts`() {
        assertTrue(ExplorationRules.isAccurateEnough(fixWithAccuracy(50f)))
        assertFalse(ExplorationRules.isAccurateEnough(fixWithAccuracy(50.1f)))
    }

    @Test
    fun `a noisy fix is ignored`() {
        // The jitter case from the epic: the phone says the player is 80 m away, and clearing
        // whatever cell that lands in would be permanent.
        assertFalse(ExplorationRules.isAccurateEnough(fixWithAccuracy(80f)))
        assertFalse(ExplorationRules.isAccurateEnough(fixWithAccuracy(500f)))
    }

    @Test
    fun `a nonsensical negative accuracy is rejected`() {
        assertFalse(ExplorationRules.isAccurateEnough(fixWithAccuracy(-1f)))
    }

    @Test
    fun `zero accuracy is accepted so mock providers still drive the map`() {
        assertTrue(ExplorationRules.isAccurateEnough(fixWithAccuracy(0f)))
    }
}
