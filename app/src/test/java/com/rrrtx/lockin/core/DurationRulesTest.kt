package com.rrrtx.lockin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DurationRulesTest {

    @Test
    fun presetsMatchSpecList() {
        assertEquals(
            listOf(15L, 25L, 30L, 50L, 60L, 120L, 240L, 480L, 1440L),
            DurationRules.PRESETS_MINUTES
        )
        DurationRules.PRESETS_MINUTES.forEach {
            assertTrue(it * 60_000L in DurationRules.MIN_CUSTOM_MS..DurationRules.MAX_MS)
        }
    }

    @Test
    fun boundsAreEnforced() {
        assertTrue(DurationRules.isValid(DurationRules.MIN_CUSTOM_MS))
        assertTrue(DurationRules.isValid(DurationRules.MAX_MS))
        assertFalse(DurationRules.isValid(DurationRules.MIN_CUSTOM_MS - 1))
        assertFalse(DurationRules.isValid(DurationRules.MAX_MS + 1))
        assertFalse(DurationRules.isValid(0L))
        assertFalse(DurationRules.isValid(-5L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidThrowsBelowMinimum() {
        DurationRules.requireValid(1_000L)
    }

    @Test
    fun expectedEndIsPlainEpochAddition() {
        // UTC epoch math: timezone/DST cannot alter the result.
        val start = 1_700_000_000_000L
        assertEquals(start + 90 * 60_000L, DurationRules.expectedEnd(start, 90 * 60_000L))
    }

    @Test
    fun partsSplitDaysHoursMinutes() {
        assertEquals(DurationRules.Parts(0, 0, 45), DurationRules.parts(45 * 60_000L))
        assertEquals(DurationRules.Parts(0, 2, 5), DurationRules.parts(125 * 60_000L))
        assertEquals(DurationRules.Parts(2, 3, 0), DurationRules.parts((2 * 24 + 3) * 60 * 60_000L))
        assertTrue(DurationRules.parts(0).isZero)
    }

    @Test
    fun formatShortRenders() {
        val loc = Locale.US
        assertEquals("45m", DurationRules.formatShort(45 * 60_000L, loc))
        assertEquals("1h 30m", DurationRules.formatShort(90 * 60_000L, loc))
        assertEquals("2d 3h", DurationRules.formatShort((2 * 24 + 3) * 60 * 60_000L, loc))
        assertEquals("0m", DurationRules.formatShort(0L, loc))
    }

    @Test
    fun formatCountdownRenders() {
        assertEquals("45:00", DurationRules.formatCountdown(45 * 60_000L))
        assertEquals("1:59:07", DurationRules.formatCountdown((3600 + 59 * 60 + 7) * 1000L))
        assertEquals("0:00", DurationRules.formatCountdown(-100L))
    }
}
