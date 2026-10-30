package com.rrrtx.lockin.core

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persisted periodic backstop (15 min). Even if the countdown service died, this
 * re-checks due sessions, re-arms alarms, and warns — honestly, at most once every
 * six hours — when the accessibility service is off during an enforcing session.
 * A user force-stop still cancels jobs until the app is next opened (documented
 * platform limitation).
 */
class SessionWatchdogJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        scope.launch {
            try {
                val context = applicationContext
                SessionEngine.completeIfDue(context)

                val active = SessionRepository(context).activeSession()
                if (active != null) {
                    SessionAlarms.scheduleCompletion(context, active)
                    if (!AccessibilityStatus.isEnabled(context)) {
                        warnProtectionDown(context)
                    } else {
                        clearProtectionWarning(context)
                    }
                }
                SessionEngine.rearmSchedules(context)
            } catch (t: Throwable) {
                Log.e(TAG, "watchdog pass failed", t)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    private fun warnProtectionDown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_WARN, 0L)
        val now = System.currentTimeMillis()
        if (now - last < NOTIFY_COOLDOWN_MS) return
        runCatching {
            Notifications.notificationManager(context).notify(
                Notifications.ID_PROTECTION_WARNING,
                Notifications.protectionWarning(context)
            )
        }
        prefs.edit().putLong(KEY_LAST_WARN, now).apply()
    }

    private fun clearProtectionWarning(context: Context) {
        Notifications.cancel(context, Notifications.ID_PROTECTION_WARNING)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LAST_WARN).apply()
    }

    companion object {
        private const val TAG = "LockinWatchdog"
        private const val JOB_ID = 6101
        private const val PREFS = "lockin_watchdog"
        private const val KEY_LAST_WARN = "last_prot_warn"
        private const val NOTIFY_COOLDOWN_MS = 6L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                    as android.app.job.JobScheduler
                val job = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(context, SessionWatchdogJob::class.java)
                )
                    .setPersisted(true)
                    .setPeriodic(15L * 60L * 1000L)
                    .setRequiresDeviceIdle(false)
                    .setRequiresCharging(false)
                    .setOverrideDeadline(20L * 60L * 1000L)
                    .build()
                scheduler.schedule(job)
            }.onFailure { Log.w(TAG, "watchdog schedule failed", it) }
        }

        fun cancel(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                    as android.app.job.JobScheduler
                scheduler.cancel(JOB_ID)
            }
        }
    }
}
