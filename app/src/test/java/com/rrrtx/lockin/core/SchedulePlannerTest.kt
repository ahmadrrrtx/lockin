package com.rrrtx.lockin.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SchedulePlannerTest {

    private lateinit var previousTz: TimeZone

    @Before
    fun pinTimezone() {
        previousTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimezone() {
        TimeZone.setDefault(previousTz)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayBit(monBased: Int) = SchedulePlanner.dayBit(monBased)

    @Test
    fun findsLaterTimeSameDay() {
        val after = at(2026, Calendar.OCTOBER, 30, 8, 0) // Friday 08:00
        val friday = dayBit(4)
        val next = SchedulePlanner.nextRunAfter(after, friday, 18, 30)
        assertEquals(at(2026, Calendar.OCTOBER, 30, 18, 30), next)
    }

    @Test
    fun skipsToNextMatchingDay() {
        val after = at(2026, Calendar.OCTOBER, 30, 20, 0) // Friday 20:00
        val monday = dayBit(0)
        val next = SchedulePlanner.nextRunAfter(after, monday, 9, 0)
        // Next Monday after Fri Oct 30 2026 is Nov 2.
        assertEquals(at(2026, Calendar.NOVEMBER, 2, 9, 0), next)
    }

    @Test
    fun strictlyAfterNowEvenIfTodayMatches() {
        val after = at(2026, Calendar.OCTOBER, 30, 18, 30) // exactly at run time
        val friday = dayBit(4)
        val next = SchedulePlanner.nextRunAfter(after, friday, 18, 30)
        // Same slot is not "after"; must roll to the following Friday.
        assertEquals(at(2026, Calendar.NOVEMBER, 6, 18, 30), next)
    }

    @Test
    fun everyDayMaskReturnsTomorrowWhenTodayPassed() {
        val after = at(2026, Calendar.OCTOBER, 30, 23, 59)
        val next = SchedulePlanner.nextRunAfter(after, SchedulePlanner.DAYS_ALL, 7, 0)
        assertEquals(at(2026, Calendar.OCTOBER, 31, 7, 0), next)
    }

    @Test
    fun wrapsAcrossWeekBoundary() {
        val after = at(2026, Calendar.OCTOBER, 26, 12, 0) // Monday noon
        val sunday = dayBit(6)
        val next = SchedulePlanner.nextRunAfter(after, sunday, 10, 0)
        assertEquals(at(2026, Calendar.NOVEMBER, 1, 10, 0), next)
    }

    @Test
    fun invalidInputsReturnZero() {
        assertEquals(0L, SchedulePlanner.nextRunAfter(0L, 0, 10, 0))       // empty mask
        assertEquals(0L, SchedulePlanner.nextRunAfter(0L, SchedulePlanner.DAYS_ALL, 24, 0))
        assertEquals(0L, SchedulePlanner.nextRunAfter(0L, SchedulePlanner.DAYS_ALL, 10, 60))
        assertTrue(!SchedulePlanner.isValidMask(0))
        assertTrue(!SchedulePlanner.isValidMask(SchedulePlanner.DAYS_ALL + 1))
    }

    @Test
    fun calendarSundayMapsToLastBit() {
        // Oct 25 2026 is a Sunday.
        val after = at(2026, Calendar.OCTOBER, 24, 0, 0) // Saturday
        val next = SchedulePlanner.nextRunAfter(after, dayBit(6), 6, 0)
        assertEquals(at(2026, Calendar.OCTOBER, 25, 6, 0), next)
    }
}
