package com.rrrtx.lockin.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.rrrtx.lockin.protection.DevicePolicyHelper

/**
 * The single place sessions start and finish. Every caller (UI, alarms, boot,
 * accessibility events, watchdog) funnels through here, and every step is ordered
 * so a race can only produce "still enforcing", never "unlocked too early".
 */
object SessionEngine {

    private const val TAG = "LockinEngine"

    const val PENDING_RELEASE_DEVICE_ADMIN = "release_device_admin"

    /** Thrown when a second start is attempted while a session enforces. */
    class SessionAlreadyActiveException : IllegalStateException("A session is already active")

    @Synchronized
    fun startSession(
        context: Context,
        draft: PrepDraft,
        scheduleId: Long? = null
    ): Session {
        val repo = SessionRepository(context)
        if (repo.activeSession() != null) throw SessionAlreadyActiveException()

        val clock = AndroidClock(context)
        val startWall = clock.wallMs()
        val startElapsed = clock.elapsedMs()
        val startBoot = clock.bootCount()

        val id = repo.insertActive(draft, startWall, startElapsed, startBoot, scheduleId)
        val session = repo.activeSession()
            ?: throw IllegalStateException("Session insert did not persist")

        // Baseline note so first post-reboot evaluations have a coherent pair.
        repo.noteLastSeen(startWall, startElapsed)

        SessionAlarms.scheduleCompletion(context, session)
        SessionWatchdogJob.schedule(context)
        SessionService.start(context)

        val packages = repo.packagesOf(id)
        DevicePolicyHelper.onSessionStarted(context, packages)

        RefreshBus.ping(context, "session-started")

        if (scheduleId != null) {
            val label = repo.scheduleById(scheduleId)?.label ?: "Scheduled"
            Notifications.notificationManager(context).notify(
                Notifications.ID_SCHEDULE_STARTED,
                Notifications.scheduleStarted(context, label, draft.durationMs)
            )
        }

        Log.i(TAG, "Session #$id active: ${packages.size} apps, ${draft.durationMs}ms")
        return session
    }

    data class Evaluation(
        val session: Session,
        val result: TimeGuard.Result
    )

    /**
     * Evaluate the active session against trustworthy time, persist any corrections
     * (wall drift, reboot crossing, integrity flags) and refresh the observation pair.
     */
    fun evaluate(context: Context): Evaluation? {
        val repo = SessionRepository(context)
        val session = repo.activeSession() ?: return null
        val clock = AndroidClock(context)
        val now = TimeGuard.ClockSnapshot.from(clock)
        val lastSeen = repo.lastSeen()
        val result = TimeGuard.evaluate(session, now, lastSeen)

        val changed =
            result.deadlineWallMs != session.deadlineWallMs ||
                result.wallAdjustMs != session.wallAdjustMs ||
                result.integrity != session.integrity ||
                (result.rebootDetectedNow && !session.crossedReboot)

        if (changed) {
            repo.persistTimeState(
                sessionId = session.id,
                deadlineWall = result.deadlineWallMs,
                wallAdjust = result.wallAdjustMs,
                crossedReboot = result.rebootDetectedNow || session.crossedReboot,
                integrity = result.integrity
            )
            Log.i(TAG, "Time state corrected: note=${result.note}")
        }
        repo.noteLastSeen(now.wallMs, now.elapsedMs)
        return Evaluation(session.copy(
            deadlineWallMs = result.deadlineWallMs,
            wallAdjustMs = result.wallAdjustMs,
            integrity = result.integrity,
            crossedReboot = result.rebootDetectedNow || session.crossedReboot
        ), result)
    }

    /**
     * The only completion path. Returns true when a session transitioned
     * ACTIVE → COMPLETED during this call.
     */
    @Synchronized
    fun completeIfDue(context: Context): Boolean {
        val repo = SessionRepository(context)
        val evaluation = evaluate(context) ?: return false
        if (!evaluation.result.expired) return false

        val session = evaluation.session
        // The state machine is law: verify the transition is legal before writing.
        val next = SessionMachine.transition(
            session.state,
            SessionEvent.DeadlineReached
        )
        if (next != SessionState.COMPLETED) {
            Log.w(TAG, "DeadlineReached rejected by state machine for #${session.id}")
            return false
        }

        val packages = repo.packagesOf(session.id)
        val now = AndroidClock(context).wallMs()

        val completed = repo.markCompleted(
            sessionId = session.id,
            completedAtWall = now,
            reason = EndReason.NATURAL,
            integrity = evaluation.result.integrity,
            deadlineWall = evaluation.result.deadlineWallMs,
            wallAdjust = evaluation.result.wallAdjustMs,
            crossedReboot = true
        )
        if (!completed) return false // another caller won the race; both must stop here

        // --- Post-completion effects (idempotent, failure-tolerant) ---
        runCatching { SessionAlarms.cancelCompletion(context) }
        Notifications.cancel(context, Notifications.ID_SESSION_COUNTDOWN)

        val attempts = repo.attemptsForSession(session.id)
        runCatching {
            Notifications.notificationManager(context).notify(
                Notifications.ID_COMPLETED,
                Notifications.completed(context, session.durationMs, attempts)
            )
        }
        runCatching { DevicePolicyHelper.onSessionCompleted(context, packages) }
        runCatching { SessionService.stop(context) }
        runCatching { applyDeferredActions(context) }

        RefreshBus.ping(context, "session-completed")
        Log.i(TAG, "Session #${session.id} completed naturally")
        return true
    }

    /** Append-only strengthening while ACTIVE (SQL enforces the state). */
    fun addPackagesDuringActive(context: Context, packages: Collection<String>): Int {
        val repo = SessionRepository(context)
        val active = repo.activeSession() ?: return 0
        val added = repo.addPackages(active.id, packages)
        if (added > 0) {
            DevicePolicyHelper.onSessionStarted(context, repo.packagesOf(active.id))
            RefreshBus.ping(context, "packages-added")
        }
        return added
    }

    /** Applies queued weakening-deferred actions once no session enforces. */
    fun applyDeferredActions(context: Context) {
        val repo = SessionRepository(context)
        if (repo.activeSession() != null) return
        val keys = repo.pendingActionKeys()
        if (keys.isEmpty()) return
        keys.forEach { key ->
            when (key) {
                PENDING_RELEASE_DEVICE_ADMIN -> runCatching {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                        as android.app.admin.DevicePolicyManager
                    dpm.removeActiveAdmin(com.rrrtx.lockin.protection.AdminReceiver.component(context))
                }
                else -> Log.w(TAG, "Unknown deferred action: $key")
            }
        }
        repo.clearPendingActions()
        repo.setMeta(SessionRepository.META_DEFERRED_APPLIED, System.currentTimeMillis())
        RefreshBus.ping(context, "deferred-applied")
    }

    /** Boot / self-update / cold-start recovery. */
    fun recover(context: Context) {
        runCatching { SessionWatchdogJob.schedule(context) }

        val repo = SessionRepository(context)
        val hadActive = repo.activeSession() != null
        if (hadActive) {
            val completedNow = runCatching { completeIfDue(context) }.getOrDefault(false)
            if (!completedNow) {
                val still = repo.activeSession()
                if (still != null) {
                    SessionAlarms.scheduleCompletion(context, still)
                    SessionService.start(context)
                }
            }
        } else {
            Notifications.cancel(context, Notifications.ID_SESSION_COUNTDOWN)
            runCatching { SessionAlarms.cancelCompletion(context) }
        }
        rearmSchedules(context)
        RefreshBus.ping(context, "recover")
    }

    fun rearmSchedules(context: Context) {
        val repo = SessionRepository(context)
        val now = System.currentTimeMillis()
        repo.schedules().filter { it.enabled }.forEach { schedule ->
            val next = SchedulePlanner.nextRunAfter(now, schedule.daysMask, schedule.hour, schedule.minute)
            if (next > 0L) {
                SessionAlarms.scheduleScheduleTrigger(context, schedule, next)
            }
        }
    }

    /** Route an intent to the UI shell (used by notifications/watchdog). */
    fun openAppIntent(context: Context, route: String): Intent =
        Intent(context, com.rrrtx.lockin.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.rrrtx.lockin.ui.MainActivity.EXTRA_ROUTE, route)
        }
}
