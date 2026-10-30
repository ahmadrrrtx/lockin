package com.rrrtx.lockin.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

data class Schedule(
    val id: Long,
    val label: String,
    val daysMask: Int,
    val hour: Int,
    val minute: Int,
    val durationMs: Long,
    val packages: List<String>,
    val enabled: Boolean,
    val lastRunAt: Long
)

data class HistoryRow(
    val session: Session,
    val packages: List<String>,
    val attempts: Int
)

/**
 * Data access for sessions, attempts, schedules and meta. Every mutation that could
 * weaken a session is issued as a conditional statement (e.g. complete only when
 * state = ACTIVE), so racing callers cannot double-finish or resurrect a session.
 */
class SessionRepository(context: Context) {

    private val db = LockinDatabase.getInstance(context).writableDatabase

    /* ------------------------------------------------------------------ meta */

    fun meta(key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun metaLong(key: String): Long? = meta(key)?.toLongOrNull()

    fun setMeta(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, cv, CONFLICT_REPLACE)
    }

    fun setMeta(key: String, value: Long) = setMeta(key, value.toString())

    fun lastSeen(): TimeGuard.LastSeen? {
        val wall = metaLong(META_LAST_SEEN_WALL) ?: return null
        val elapsed = metaLong(META_LAST_SEEN_ELAPSED) ?: return null
        return TimeGuard.LastSeen(wall, elapsed)
    }

    fun noteLastSeen(wall: Long, elapsed: Long) {
        setMeta(META_LAST_SEEN_WALL, wall)
        setMeta(META_LAST_SEEN_ELAPSED, elapsed)
    }

    /* -------------------------------------------------------------- sessions */

    fun activeSession(): Session? =
        db.rawQuery(
            "SELECT * FROM sessions WHERE state = ? ORDER BY id DESC LIMIT 1",
            arrayOf(STATE_ACTIVE)
        ).use { c -> if (c.moveToFirst()) sessionFromCursor(c) else null }

    fun insertActive(
        draft: PrepDraft,
        startedAtWall: Long,
        startElapsed: Long,
        startBoot: Long,
        scheduleId: Long? = null
    ): Long {
        val duration = DurationRules.requireValid(draft.durationMs)
        require(draft.distinctPackages.isNotEmpty()) { "Session requires at least one package" }

        db.beginTransaction()
        try {
            // Single-active invariant, enforced where writers serialize.
            check(activeSession() == null) { "Session already active" }
            val deadline = DurationRules.expectedEnd(startedAtWall, duration)
            val cv = ContentValues().apply {
                put("state", STATE_ACTIVE)
                put("started_at_wall", startedAtWall)
                put("duration_ms", duration)
                put("deadline_wall", deadline)
                put("start_elapsed", startElapsed)
                put("start_boot", startBoot)
                put("commitment", draft.commitmentText)
                if (scheduleId != null) put("schedule_id", scheduleId)
            }
            val id = db.insertOrThrow("sessions", null, cv)
            draft.distinctPackages.forEach { pkg ->
                val pcv = ContentValues().apply {
                    put("session_id", id)
                    put("pkg", pkg)
                }
                db.insertWithOnConflict("session_packages", null, pcv, CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Append-only while ACTIVE. Removing packages during a session is not an
     * operation Lockin exposes anywhere.
     */
    fun addPackages(sessionId: Long, packages: Collection<String>): Int {
        if (!isSessionActive(sessionId)) return 0
        var added = 0
        packages.forEach { pkg ->
            val trimmed = pkg.trim()
            if (trimmed.isEmpty()) return@forEach
            val cv = ContentValues().apply {
                put("session_id", sessionId)
                put("pkg", trimmed)
            }
            if (db.insertWithOnConflict("session_packages", null, cv, CONFLICT_IGNORE) != -1L) {
                added++
            }
        }
        return added
    }

    fun isSessionActive(sessionId: Long): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sessions WHERE id = ? AND state = ?",
            arrayOf(sessionId.toString(), STATE_ACTIVE)
        ).use { it.moveToFirst() }

    fun packagesOf(sessionId: Long): List<String> =
        db.rawQuery(
            "SELECT pkg FROM session_packages WHERE session_id = ? ORDER BY pkg",
            arrayOf(sessionId.toString())
        ).use { c -> c.mapToStrings() }

    fun blockingPackages(): Set<String> {
        val active = activeSession() ?: return emptySet()
        return packagesOf(active.id).toSet()
    }

    /** Guarded terminal transition: only ACTIVE rows can complete, exactly once. */
    fun markCompleted(
        sessionId: Long,
        completedAtWall: Long,
        reason: EndReason,
        integrity: IntegrityFlag? = null,
        deadlineWall: Long? = null,
        wallAdjust: Long? = null,
        crossedReboot: Boolean? = null
    ): Boolean {
        val cv = ContentValues().apply {
            put("state", STATE_COMPLETED)
            put("completed_at_wall", completedAtWall)
            put("end_reason", reason.name)
            integrity?.let { put("integrity", it.name) }
            deadlineWall?.let { put("deadline_wall", it) }
            wallAdjust?.let { put("wall_adjust_ms", it) }
            crossedReboot?.let { put("crossed_reboot", if (it) 1 else 0) }
        }
        val rows = db.update(
            "sessions", cv, "id = ? AND state = ?",
            arrayOf(sessionId.toString(), STATE_ACTIVE)
        )
        return rows == 1
    }

    /** Persist TimeGuard corrections (deadline drift, reboot crossing, integrity). */
    fun persistTimeState(
        sessionId: Long,
        deadlineWall: Long,
        wallAdjust: Long,
        crossedReboot: Boolean,
        integrity: IntegrityFlag
    ) {
        val cv = ContentValues().apply {
            put("deadline_wall", deadlineWall)
            put("wall_adjust_ms", wallAdjust)
            put("crossed_reboot", if (crossedReboot) 1 else 0)
            put("integrity", integrity.name)
        }
        db.update("sessions", cv, "id = ?", arrayOf(sessionId.toString()))
    }

    fun history(limit: Int): List<HistoryRow> =
        db.rawQuery(
            """SELECT * FROM sessions WHERE state = ?
               ORDER BY COALESCE(completed_at_wall, started_at_wall) DESC LIMIT ?""",
            arrayOf(STATE_COMPLETED, limit.toString())
        ).use { c ->
            c.mapToSessions().map { s ->
                HistoryRow(s, packagesOf(s.id), attemptsForSession(s.id))
            }
        }

    fun completedCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM sessions WHERE state = ?", arrayOf(STATE_COMPLETED))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun totalFocusedMs(): Long =
        db.rawQuery(
            "SELECT COALESCE(SUM(duration_ms), 0) FROM sessions WHERE state = ?",
            arrayOf(STATE_COMPLETED)
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun totalFocusedSince(sinceWallMs: Long): Long =
        db.rawQuery(
            "SELECT COALESCE(SUM(duration_ms), 0) FROM sessions WHERE state = ? AND COALESCE(completed_at_wall, started_at_wall) >= ?",
            arrayOf(STATE_COMPLETED, sinceWallMs.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /* -------------------------------------------------------------- attempts */

    fun recordAttempt(pkg: String, sessionId: Long?, now: Long) {
        val cv = ContentValues().apply {
            put("pkg", pkg)
            put("last_at", now)
            if (sessionId != null) put("session_id", sessionId)
        }
        db.insertWithOnConflict("blocked_attempts", null, cv, CONFLICT_IGNORE)
        db.execSQL(
            "UPDATE blocked_attempts SET attempts = attempts + 1, last_at = ? WHERE pkg = ?",
            arrayOf(now, pkg)
        )
    }

    fun totalAttempts(): Int =
        db.rawQuery("SELECT COALESCE(SUM(attempts), 0) FROM blocked_attempts", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun attemptsForSession(sessionId: Long): Int =
        db.rawQuery(
            "SELECT COALESCE(SUM(attempts), 0) FROM blocked_attempts WHERE session_id = ?",
            arrayOf(sessionId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /* ------------------------------------------------------------- schedules */

    fun schedules(): List<Schedule> =
        db.rawQuery("SELECT * FROM schedules ORDER BY hour, minute", null).use { c ->
            c.mapToSchedules()
        }

    fun scheduleById(id: Long): Schedule? =
        db.rawQuery("SELECT * FROM schedules WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) scheduleFromCursor(c) else null
        }

    fun insertSchedule(schedule: Schedule): Long {
        val cv = scheduleValues(schedule)
        return db.insertOrThrow("schedules", null, cv)
    }

    fun updateSchedule(schedule: Schedule) {
        val cv = scheduleValues(schedule)
        db.update("schedules", cv, "id = ?", arrayOf(schedule.id.toString()))
    }

    fun deleteSchedule(id: Long) {
        db.delete("schedules", "id = ?", arrayOf(id.toString()))
    }

    fun markScheduleRun(id: Long, at: Long) {
        val cv = ContentValues().apply { put("last_run_at", at) }
        db.update("schedules", cv, "id = ?", arrayOf(id.toString()))
    }

    private fun scheduleValues(schedule: Schedule): ContentValues =
        ContentValues().apply {
            put("label", schedule.label)
            put("days_mask", schedule.daysMask)
            put("hour", schedule.hour)
            put("minute", schedule.minute)
            put("duration_ms", schedule.durationMs)
            put("packages", encodePackages(schedule.packages))
            put("enabled", if (schedule.enabled) 1 else 0)
            put("last_run_at", schedule.lastRunAt)
        }

    /* -------------------------------------------------------- pending actions */

    fun enqueuePendingAction(key: String) {
        val cv = ContentValues().apply {
            put("action_key", key)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("pending_actions", null, cv, CONFLICT_IGNORE)
    }

    fun pendingActionKeys(): List<String> =
        db.rawQuery("SELECT action_key FROM pending_actions", null)
            .use { c -> c.mapToStrings() }

    fun clearPendingActions() {
        db.delete("pending_actions", null, null)
    }

    /* ------------------------------------------------------------------- misc */

    fun clearAllData() {
        db.beginTransaction()
        try {
            listOf(
                "sessions", "session_packages", "blocked_attempts",
                "schedules", "pending_actions", "meta"
            ).forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* --------------------------------------------------------------- mapping */

    private fun sessionFromCursor(c: Cursor): Session {
        val integrity = c.getString(c.getColumnIndexOrThrow("integrity"))
        val endReason = c.getStringOrNull(c.getColumnIndexOrThrow("end_reason"))
        return Session(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            state = if (c.getString(c.getColumnIndexOrThrow("state")) == STATE_ACTIVE) {
                SessionState.ACTIVE
            } else {
                SessionState.COMPLETED
            },
            startedAtWallMs = c.getLong(c.getColumnIndexOrThrow("started_at_wall")),
            durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
            deadlineWallMs = c.getLong(c.getColumnIndexOrThrow("deadline_wall")),
            startElapsedMs = c.getLong(c.getColumnIndexOrThrow("start_elapsed")),
            startBootCount = c.getLong(c.getColumnIndexOrThrow("start_boot")),
            wallAdjustMs = c.getLong(c.getColumnIndexOrThrow("wall_adjust_ms")),
            crossedReboot = c.getInt(c.getColumnIndexOrThrow("crossed_reboot")) == 1,
            integrity = if (integrity == IntegrityFlag.DEGRADED.name) {
                IntegrityFlag.DEGRADED
            } else {
                IntegrityFlag.OK
            },
            commitmentText = c.getStringOrNull(c.getColumnIndexOrThrow("commitment")),
            completedAtWallMs = c.getLongOrNull(c.getColumnIndexOrThrow("completed_at_wall")),
            endReason = endReason?.let { r ->
                runCatching { EndReason.valueOf(r) }.getOrNull()
            },
            scheduleId = c.getLongOrNull(c.getColumnIndexOrThrow("schedule_id"))
        )
    }

    private fun scheduleFromCursor(c: Cursor): Schedule = Schedule(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        label = c.getString(c.getColumnIndexOrThrow("label")),
        daysMask = c.getInt(c.getColumnIndexOrThrow("days_mask")),
        hour = c.getInt(c.getColumnIndexOrThrow("hour")),
        minute = c.getInt(c.getColumnIndexOrThrow("minute")),
        durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
        packages = decodePackages(c.getString(c.getColumnIndexOrThrow("packages"))),
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
        lastRunAt = c.getLong(c.getColumnIndexOrThrow("last_run_at"))
    )

    private fun Cursor.mapToStrings(): List<String> {
        val out = ArrayList<String>(count)
        while (moveToNext()) out.add(getString(0))
        return out
    }

    private fun Cursor.mapToSessions(): List<Session> {
        val out = ArrayList<Session>(count)
        while (moveToNext()) out.add(sessionFromCursor(this))
        return out
    }

    private fun Cursor.mapToSchedules(): List<Schedule> {
        val out = ArrayList<Schedule>(count)
        while (moveToNext()) out.add(scheduleFromCursor(this))
        return out
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    companion object {
        const val STATE_ACTIVE = "ACTIVE"
        const val STATE_COMPLETED = "COMPLETED"

        const val META_LAST_SEEN_WALL = "last_seen_wall"
        const val META_LAST_SEEN_ELAPSED = "last_seen_elapsed"
        const val META_SERVICE_ALIVE = "service_alive_wall"
        const val META_DEFERRED_APPLIED = "deferred_actions_applied_at"

        private const val CONFLICT_REPLACE = 2
        private const val CONFLICT_IGNORE = 5

        fun encodePackages(packages: List<String>): String = packages.joinToString(separator = "\n")

        fun decodePackages(raw: String): List<String> =
            if (raw.isEmpty()) emptyList() else raw.split("\n").filter { it.isNotEmpty() }
    }
}
