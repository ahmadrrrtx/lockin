package com.rrrtx.lockin.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Completion and schedule alarms. Exact when permitted, best-effort otherwise. */
object SessionAlarms {

    private const val RC_COMPLETION = 5101
    private const val RC_SCHEDULE_PREFIX = 5200

    const val ACTION_COMPLETE = "com.rrrtx.lockin.action.SESSION_COMPLETE"

    fun canUseExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleCompletion(context: Context, session: Session) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = completionPendingIntent(context)

        val wallTarget = session.deadlineWallMs
        try {
            if (canUseExactAlarms(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTarget, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTarget, pi)
            }
        } catch (_: SecurityException) {
            // Exact-alarm permission raced away; fall back to inexact delivery.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTarget, pi)
        }
    }

    fun cancelCompletion(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(completionPendingIntent(context))
    }

    private fun completionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            action = ACTION_COMPLETE
        }
        return PendingIntent.getBroadcast(
            context,
            RC_COMPLETION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleScheduleTrigger(context: Context, schedule: Schedule, atWallMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRIGGER
            putExtra(ScheduleAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            RC_SCHEDULE_PREFIX + (schedule.id % 1000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canUseExactAlarms(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallMs, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallMs, pi)
        }
    }

    fun cancelScheduleTrigger(context: Context, scheduleId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRIGGER
            putExtra(ScheduleAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            RC_SCHEDULE_PREFIX + (scheduleId % 1000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
