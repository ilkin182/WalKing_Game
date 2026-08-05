package com.example.domain.stats

import java.util.TimeZone

/**
 * Turning instants into the days the player's own calendar says they happened on.
 *
 * Everything is in the player's *local* zone rather than UTC: "yesterday" and "today" mean what the
 * clock on their wall said, and a UTC boundary would put an evening walk on the wrong day for anyone
 * east of Greenwich. Deliberately arithmetic rather than `java.time` - `minSdk` is 24, and the
 * achievement code has always done its calendar work this way.
 */
object CalendarDays {

    const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    /** Day-of-week for epoch day 0 (1 January 1970 was a Thursday), counting Monday as 0. */
    private const val EPOCH_DAY_OF_WEEK_OFFSET = 3

    /** Which local day an instant falls on, counted from 1 January 1970. */
    fun epochDay(millis: Long, zone: TimeZone): Long =
        Math.floorDiv(millis + zone.getOffset(millis), MILLIS_PER_DAY)

    /** Monday is 0, so Saturday and Sunday are 5 and 6. */
    fun dayOfWeek(epochDay: Long): Int =
        (((epochDay + EPOCH_DAY_OF_WEEK_OFFSET) % 7) + 7).toInt() % 7

    /**
     * The instant that formats as [epochDay]'s calendar date when read in UTC.
     *
     * For display only. A day here is a local date with no time of day attached, so there is no one
     * instant it "is"; this gives a formatter something to print the date parts from without
     * dragging the player's zone offset back through it.
     */
    fun utcInstantOf(epochDay: Long): Long = epochDay * MILLIS_PER_DAY
}
