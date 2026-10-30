package com.rrrtx.lockin.core

/**
 * Time authority for sessions.
 *
 * Rules (in evaluation order):
 *  1. Same-boot evaluations trust the monotonic elapsed-realtime baseline — immune to
 *     wall-clock changes, timezone changes and DST (all epoch math).
 *  2. Wall-clock jumps observed during a monotonic session are measured against the
 *     last seen (wall, elapsed) pair; the stored deadline is corrected by exactly the
 *     measured drift so a later reboot still agrees with the monotonic truth. Jumps
 *     beyond [FLAG_JUMP_MS] also mark the session DEGRADED.
 *  3. After a reboot (detected via boot counter, elapsed regression, or persisted flag),
 *     the persisted wall deadline is authoritative.
 *  4. Fail-safe: if wall time is incoherent (now before the session started), the full
 *     original duration is re-anchored to now — never an early unlock, never more than
 *     the user's original commitment — and integrity is DEGRADED.
 *  5. An expired verdict only ever comes from these rules; an unexpired verdict likewise.
 */
object TimeGuard {

    const val JUMP_TOLERANCE_MS = 5_000L
    const val FLAG_JUMP_MS = 120_000L

    data class ClockSnapshot(
        val wallMs: Long,
        val elapsedMs: Long,
        val bootCount: Long
    ) {
        companion object {
            fun from(clock: Clock): ClockSnapshot =
                ClockSnapshot(clock.wallMs(), clock.elapsedMs(), clock.bootCount())
        }
    }

    data class LastSeen(val wallMs: Long, val elapsedMs: Long)

    data class Result(
        val remainingMs: Long,
        val expired: Boolean,
        val integrity: IntegrityFlag,
        val deadlineWallMs: Long,
        val wallAdjustMs: Long,
        val mode: TimeMode,
        val rebootDetectedNow: Boolean,
        val note: String?
    )

    fun evaluate(
        session: Session,
        now: ClockSnapshot,
        lastSeen: LastSeen?
    ): Result {
        val stickyDegraded = session.integrity == IntegrityFlag.DEGRADED

        val rebootDetected = detectReboot(session, now, lastSeen)
        val inWallMode = session.crossedReboot || rebootDetected

        return if (!inWallMode) {
            evaluateMonotonic(session, now, lastSeen, stickyDegraded, rebootDetected)
        } else {
            evaluateWall(session, now, stickyDegraded, rebootDetected)
        }
    }

    /** True when evidence says the device rebooted since the session started. */
    private fun detectReboot(
        session: Session,
        now: ClockSnapshot,
        lastSeen: LastSeen?
    ): Boolean {
        if (session.crossedReboot) return true
        if (session.startBootCount != Clock.UNKNOWN_BOOT &&
            now.bootCount != Clock.UNKNOWN_BOOT
        ) {
            return now.bootCount != session.startBootCount
        }
        // Heuristic without a boot counter: elapsed time reset below where it must be.
        if (now.elapsedMs < session.startElapsedMs) return true
        if (lastSeen != null && now.elapsedMs < lastSeen.elapsedMs) return true
        return false
    }

    private fun evaluateMonotonic(
        session: Session,
        now: ClockSnapshot,
        lastSeen: LastSeen?,
        stickyDegraded: Boolean,
        rebootDetected: Boolean
    ): Result {
        val monotonicEnd = session.startElapsedMs + session.durationMs
        val remaining = monotonicEnd - now.elapsedMs

        var deadlineWall = session.deadlineWallMs
        var adjust = session.wallAdjustMs
        var integrity = if (stickyDegraded) IntegrityFlag.DEGRADED else IntegrityFlag.OK
        var note: String? = null

        if (lastSeen != null) {
            val expectedWall = lastSeen.wallMs + (now.elapsedMs - lastSeen.elapsedMs)
            val drift = now.wallMs - expectedWall
            if (kotlin.math.abs(drift) > JUMP_TOLERANCE_MS) {
                deadlineWall += drift
                adjust += drift
                if (kotlin.math.abs(drift) > FLAG_JUMP_MS) {
                    integrity = IntegrityFlag.DEGRADED
                    note = "wall clock jumped by ${drift}ms while monotonic time was trusted"
                } else {
                    note = "wall clock corrected by ${drift}ms (small step)"
                }
            }
        }

        return Result(
            remainingMs = remaining,
            expired = remaining <= 0L,
            integrity = integrity,
            deadlineWallMs = deadlineWall,
            wallAdjustMs = adjust,
            mode = TimeMode.MONOTONIC,
            rebootDetectedNow = rebootDetected,
            note = note
        )
    }

    private fun evaluateWall(
        session: Session,
        now: ClockSnapshot,
        stickyDegraded: Boolean,
        rebootDetected: Boolean
    ): Result {
        var integrity = if (stickyDegraded) IntegrityFlag.DEGRADED else IntegrityFlag.OK
        var note: String? = null

        if (now.wallMs < session.startedAtWallMs) {
            // Clock is incoherent (set back before the recorded start): fail-safe.
            // Re-anchor the ORIGINAL duration to now — bounded, visible, never early.
            integrity = IntegrityFlag.DEGRADED
            note = "wall clock is before session start; original duration re-anchored"
            val deadline = now.wallMs + session.durationMs
            return Result(
                remainingMs = session.durationMs,
                expired = false,
                integrity = integrity,
                deadlineWallMs = deadline,
                wallAdjustMs = session.wallAdjustMs + (deadline - session.deadlineWallMs),
                mode = TimeMode.WALL,
                rebootDetectedNow = rebootDetected,
                note = note
            )
        }

        val remaining = session.deadlineWallMs - now.wallMs
        return Result(
            remainingMs = remaining,
            expired = remaining <= 0L,
            integrity = integrity,
            deadlineWallMs = session.deadlineWallMs,
            wallAdjustMs = session.wallAdjustMs,
            mode = TimeMode.WALL,
            rebootDetectedNow = rebootDetected,
            note = note
        )
    }
}
