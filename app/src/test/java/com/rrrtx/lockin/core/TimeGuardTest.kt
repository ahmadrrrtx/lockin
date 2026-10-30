package com.rrrtx.lockin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeGuardTest {

    private fun session(
        startWall: Long = 1_000_000L,
        duration: Long = 60 * 60_000L,
        startElapsed: Long = 10_000L,
        startBoot: Long = 7L,
        deadlineWall: Long = startWall + duration,
        wallAdjust: Long = 0L,
        crossedReboot: Boolean = false,
        integrity: IntegrityFlag = IntegrityFlag.OK
    ) = Session(
        id = 1L,
        state = SessionState.ACTIVE,
        startedAtWallMs = startWall,
        durationMs = duration,
        deadlineWallMs = deadlineWall,
        startElapsedMs = startElapsed,
        startBootCount = startBoot,
        wallAdjustMs = wallAdjust,
        crossedReboot = crossedReboot,
        integrity = integrity
    )

    private fun snap(wall: Long, elapsed: Long, boot: Long = 7L) =
        TimeGuard.ClockSnapshot(wall, elapsed, boot)

    /* ----------------------------------------------------- monotonic (same boot) */

    @Test
    fun sameBootUsesMonotonicBaseline() {
        val s = session()
        val now = snap(wall = 1_100_000L, elapsed = 10_000L + 20 * 60_000L)
        val r = TimeGuard.evaluate(s, now, lastSeen = null)
        assertEquals(40 * 60_000L, r.remainingMs)
        assertFalse(r.expired)
        assertEquals(TimeMode.MONOTONIC, r.mode)
        assertEquals(IntegrityFlag.OK, r.integrity)
    }

    @Test
    fun forwardWallJumpDoesNotUnlockEarlyWhileMonotonic() {
        val s = session()
        // Wall jumps +10 minutes; elapsed only advanced 10 minutes of a 60 min session.
        val last = TimeGuard.LastSeen(wallMs = 1_050_000L, elapsedMs = 10_000L + 10 * 60_000L)
        // Expected wall = 1_110_000; observed +10 min ahead → forward jump.
        val now = snap(wall = 1_710_000L, elapsed = 10_000L + 20 * 60_000L)
        val r = TimeGuard.evaluate(s, now, last)
        assertEquals(40 * 60_000L, r.remainingMs)
        assertFalse(r.expired)
    }

    @Test
    fun backwardWallJumpCorrectsDeadlineAndFlagsDegraded() {
        val s = session()
        // Expected wall = 1_110_000; observed wall 10 minutes earlier → jump of -600s.
        val last = TimeGuard.LastSeen(wallMs = 1_050_000L, elapsedMs = 10_000L + 10 * 60_000L)
        val now = snap(wall = 1_050_000L, elapsed = 10_000L + 20 * 60_000L)
        val r = TimeGuard.evaluate(s, now, last)
        assertEquals(-600_000L, now.wallMs - (last.wallMs + (now.elapsedMs - last.elapsedMs)))
        assertEquals(s.deadlineWallMs - 600_000L, r.deadlineWallMs)
        assertEquals(40 * 60_000L, r.remainingMs)
        assertEquals(IntegrityFlag.DEGRADED, r.integrity)
        assertEquals(s.wallAdjustMs - 600_000L, r.wallAdjustMs)
    }

    @Test
    fun smallWallStepCorrectsSilentlyWithoutDegradedFlag() {
        val s = session()
        val last = TimeGuard.LastSeen(wallMs = 1_000_000L, elapsedMs = 10_000L)
        // 8 second NTP step: beyond tolerance (5s), below flag threshold (120s).
        val now = snap(wall = 1_000_000L + 68_000L, elapsed = 10_000L + 60_000L)
        val r = TimeGuard.evaluate(s, now, last)
        assertEquals(8_000L, r.deadlineWallMs - s.deadlineWallMs)
        assertEquals(IntegrityFlag.OK, r.integrity)
        assertEquals(60 * 60_000L - 60_000L, r.remainingMs)
    }

    @Test
    fun monotonicExpiryOnlyAtDeadline() {
        val s = session()
        val almost = snap(wall = 1_000_000L, elapsed = 10_000L + 60 * 60_000L - 1)
        assertFalse(TimeGuard.evaluate(s, almost, null).expired)

        val exact = snap(wall = 1_000_000L + 60 * 60_000L, elapsed = 10_000L + 60 * 60_000L)
        val r = TimeGuard.evaluate(s, exact, null)
        assertTrue(r.expired)
        assertEquals(0L, r.remainingMs)
    }

    /* ------------------------------------------------------------ reboot → wall */

    @Test
    fun bootCounterChangeSwitchesToWallMode() {
        val s = session() // deadline 4_600_000
        val now = snap(wall = 3_400_000L, elapsed = 5_000L, boot = 8L) // rebooted
        val r = TimeGuard.evaluate(s, now, null)
        assertEquals(TimeMode.WALL, r.mode)
        assertTrue(r.rebootDetectedNow)
        assertEquals(20 * 60_000L, r.remainingMs)
    }

    @Test
    fun wallModeExpiresAtPersistedDeadline() {
        val s = session() // deadline = 1_000_000 + 3_600_000 = 4_600_000
        val now = snap(wall = 4_600_001L, elapsed = 3L, boot = 8L)
        val r = TimeGuard.evaluate(s, now, null)
        assertTrue(r.expired)
    }

    @Test
    fun elapsedRegressionDetectsRebootWithoutBootCounter() {
        val s = session(startBoot = Clock.UNKNOWN_BOOT)
        // Elapsed reset below the session's start baseline → reboot evidence.
        val now = snap(wall = 1_200_000L, elapsed = 500L, boot = Clock.UNKNOWN_BOOT)
        val r = TimeGuard.evaluate(s, now, null)
        assertTrue(r.rebootDetectedNow)
        assertEquals(TimeMode.WALL, r.mode)
        assertEquals(3_400_000L, r.remainingMs)
    }

    @Test
    fun lastSeenElapsedRegressionDetectsReboot() {
        val s = session(startBoot = Clock.UNKNOWN_BOOT)
        val last = TimeGuard.LastSeen(wallMs = 1_000_000L, elapsedMs = 90_000L)
        val now = snap(wall = 1_060_000L, elapsed = 45_000L, boot = Clock.UNKNOWN_BOOT)
        val r = TimeGuard.evaluate(s, now, last)
        assertTrue(r.rebootDetectedNow)
        assertEquals(TimeMode.WALL, r.mode)
    }

    @Test
    fun persistedCrossedRebootStaysInWallMode() {
        val s = session(crossedReboot = true, integrity = IntegrityFlag.DEGRADED)
        val now = snap(wall = 1_100_000L, elapsed = 999_999L, boot = 7L) // same boot ids
        val r = TimeGuard.evaluate(s, now, null)
        assertEquals(TimeMode.WALL, r.mode)
        assertEquals(IntegrityFlag.DEGRADED, r.integrity) // sticky
    }

    /* --------------------------------------------------------- fail-safe paths */

    @Test
    fun wallBeforeStartReanchorsOriginalDurationAndDegrades() {
        val s = session() // startWall 1_000_000
        val now = snap(wall = 900_000L, elapsed = 3L, boot = 9L)
        val r = TimeGuard.evaluate(s, now, null)
        assertFalse(r.expired) // never early
        assertEquals(s.durationMs, r.remainingMs) // never more than the original commitment
        assertEquals(IntegrityFlag.DEGRADED, r.integrity)
        assertEquals(now.wallMs + s.durationMs, r.deadlineWallMs)
    }

    @Test
    fun degradedFlagIsStickyEvenWhenTimeLooksFine() {
        val s = session(integrity = IntegrityFlag.DEGRADED, crossedReboot = true)
        val now = snap(wall = 1_100_000L, elapsed = 1L, boot = 8L)
        val r = TimeGuard.evaluate(s, now, null)
        assertEquals(IntegrityFlag.DEGRADED, r.integrity)
    }

    @Test
    fun deadlineAdjustmentAccumulatesAcrossMultipleJumps() {
        // First jump already persisted: deadlineWall reduced by 600s, adjust stored.
        val s = session(deadlineWall = 4_600_000L - 600_000L, wallAdjust = -600_000L)
        val last = TimeGuard.LastSeen(wallMs = 1_000_000L, elapsedMs = 10_000L)
        // Second backward jump of 120s.
        val now = snap(wall = 1_060_000L - 121_000L, elapsed = 10_000L + 60_000L)
        val r = TimeGuard.evaluate(s, now, last)
        assertEquals(-721_000L, r.wallAdjustMs)
        assertEquals(s.deadlineWallMs - 121_000L, r.deadlineWallMs)
        assertEquals(IntegrityFlag.DEGRADED, r.integrity)
    }
}
