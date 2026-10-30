package com.rrrtx.lockin.core

import java.util.Calendar
import java.util.TimeZone

/**
 * Weekly recurring schedules. Days are a bitmask: bit 0 = Monday … bit 6 = Sunday,
 * matching the creation dialog order. Pure epoch/Calendar math (DST-correct locally).
 */
object SchedulePlanner {

    const val DAYS_ALL = 0b1111111

    fun dayBit(mondayBasedIndex: Int): Int = 1 shl mondayBasedIndex

    fun isValidMask(mask: Int): Boolean = mask in 0..DAYS_ALL && mask != 0

    fun isValidTime(hour: Int, minute: Int): Boolean =
        hour in 0..23 && minute in 0..59

    /** Next matching local wall time strictly after [afterWallMs]; 0 when mask empty. */
    fun nextRunAfter(afterWallMs: Long, daysMask: Int, hour: Int, minute: Int): Long {
        if (!isValidMask(daysMask) || !isValidTime(hour, minute)) return 0L

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = afterWallMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        for (i in 0..7) {
            val matchesDay = matches(cal.get(Calendar.DAY_OF_WEEK), daysMask)
            val afterNow = cal.timeInMillis > afterWallMs
            if (matchesDay && afterNow) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return 0L
    }

    /** Calendar.DAY_OF_WEEK (SUNDAY=1) → our Monday-first bit. */
    private fun matches(calendarDayOfWeek: Int, mask: Int): Boolean {
        val monBased = (calendarDayOfWeek + 5) % 7 // SUN(1)->6, MON(2)->0, SAT(7)->5
        return (mask shr monBased) and 1 == 1
    }

    fun labelFor(mask: Int, labels: Array<String>): String {
        if (mask == DAYS_ALL) return "Every day"
        val bits = (0..6).filter { (mask shr it) and 1 == 1 }
        return bits.joinToString(", ") { labels[it] }
    }
}
