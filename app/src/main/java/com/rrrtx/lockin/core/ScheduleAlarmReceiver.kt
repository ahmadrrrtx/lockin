package com.rrrtx.lockin.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires a recurring schedule. A schedule can only START a session while idle —
 * it can never alter, shorten or cancel an enforcing session.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LockinSchedule"
        const val ACTION_SCHEDULE_TRIGGER = "com.rrrtx.lockin.action.SCHEDULE_TRIGGER"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        private const val MIN_GAP_MS = 60_000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SCHEDULE_TRIGGER) return
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId < 0) return

        try {
            val repo = SessionRepository(context)
            val schedule = repo.scheduleById(scheduleId) ?: return

            if (schedule.enabled) {
                if (repo.activeSession() != null) {
                    Log.i(TAG, "Schedule #${scheduleId} skipped: session already active")
                } else if (System.currentTimeMillis() - schedule.lastRunAt < MIN_GAP_MS) {
                    Log.i(TAG, "Schedule #${scheduleId} de-duplicated")
                } else {
                    runCatching {
                        SessionEngine.startSession(
                            context,
                            PrepDraft(schedule.packages, schedule.durationMs),
                            scheduleId = schedule.id
                        )
                        repo.markScheduleRun(schedule.id, System.currentTimeMillis())
                    }.onFailure {
                        Log.w(TAG, "Schedule #${scheduleId} could not start: ${it.message}")
                    }
                }
            }

            // Re-arm the next occurrence regardless of this run's outcome.
            val next = SchedulePlanner.nextRunAfter(
                System.currentTimeMillis(),
                schedule.daysMask,
                schedule.hour,
                schedule.minute
            )
            if (next > 0L) {
                SessionAlarms.scheduleScheduleTrigger(context, schedule, next)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Schedule trigger failed", t)
        }
    }
}
