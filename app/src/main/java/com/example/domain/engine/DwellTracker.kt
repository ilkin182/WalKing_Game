package com.example.domain.engine

/**
 * Notices when the player has stayed in one cell long enough to have looked around.
 *
 * Feed it the cell each accepted fix lands in; it reports the cell exactly once, the first time the
 * player has been in it continuously for [thresholdMs]. Leaving and coming back starts the clock
 * again and earns a fresh report.
 *
 * Reporting once per dwell is what keeps a phone sitting on a windowsill from writing to the
 * database every three seconds forever.
 *
 * Not thread-safe; call it from one place (the ViewModel's location handler).
 */
class DwellTracker(private val thresholdMs: Long = ExplorationRules.DWELL_THRESHOLD_MS) {

    private var currentCellId: String? = null
    private var enteredAtMs: Long = 0L
    private var alreadyReported = false

    /** The cell the player is currently sitting in, or null if they have not been placed yet. */
    val currentCell: String? get() = currentCellId

    /**
     * @return [cellId] the first time the dwell threshold is crossed for this stay, otherwise null.
     */
    fun onFix(cellId: String, nowMs: Long): String? {
        if (cellId != currentCellId) {
            currentCellId = cellId
            enteredAtMs = nowMs
            alreadyReported = false
            return null
        }

        if (alreadyReported) return null
        // A fix with a clock earlier than the entry (a device time change, a replayed fix) would
        // otherwise make the dwell look negative and never fire; restart the stay instead.
        if (nowMs < enteredAtMs) {
            enteredAtMs = nowMs
            return null
        }
        if (nowMs - enteredAtMs < thresholdMs) return null

        alreadyReported = true
        return cellId
    }

    /** Forgets the current stay - used when tracking stops, so resuming elsewhere starts clean. */
    fun reset() {
        currentCellId = null
        enteredAtMs = 0L
        alreadyReported = false
    }
}
