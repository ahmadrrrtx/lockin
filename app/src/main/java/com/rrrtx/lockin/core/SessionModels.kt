package com.rrrtx.lockin.core

/** Immutable commitment drafted by the user before START. */
data class PrepDraft(
    val packages: List<String>,
    val durationMs: Long,
    val commitmentText: String? = null,
    /** For scheduled sessions that should start at a fixed wall time; null = now. */
    val plannedStartWallMs: Long? = null
) {
    val distinctPackages: List<String> get() = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

enum class IntegrityFlag { OK, DEGRADED }

enum class EndReason { NATURAL }

/** Which clock source TimeGuard trusted for the current evaluation. */
enum class TimeMode { MONOTONIC, WALL }

/**
 * Persisted session record. Wall/elapsed/boot triples make clock behavior auditable:
 * [deadlineWallMs] may be adjusted (with [wallAdjustMs] as the cumulative audit trail)
 * when wall-clock jumps are detected while a monotonic baseline is active.
 */
data class Session(
    val id: Long = 0L,
    val state: SessionState = SessionState.ACTIVE,
    val startedAtWallMs: Long,
    val durationMs: Long,
    val deadlineWallMs: Long,
    val startElapsedMs: Long,
    val startBootCount: Long,
    val wallAdjustMs: Long = 0L,
    val crossedReboot: Boolean = false,
    val integrity: IntegrityFlag = IntegrityFlag.OK,
    val commitmentText: String? = null,
    val completedAtWallMs: Long? = null,
    val endReason: EndReason? = null,
    val scheduleId: Long? = null
)

/** Injectable time source so every engine path is testable off-device. */
interface Clock {
    fun wallMs(): Long
    fun elapsedMs(): Long

    /** Best-available boot counter; [UNKNOWN_BOOT] when the platform does not expose one. */
    fun bootCount(): Long

    companion object {
        const val UNKNOWN_BOOT = -1L
    }
}
