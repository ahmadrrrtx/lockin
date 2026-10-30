package com.rrrtx.lockin.core

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.rrrtx.lockin.ui.BlockActivity
import com.rrrtx.lockin.protection.RolesResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The enforcement core, isolated in `:lockin_core`.
 *
 * Contract:
 *  - Cheap checks only inside the event callback; no node traversal ever
 *    (the service config does not even allow content retrieval).
 *  - Individual feature failures never crash the service: everything is wrapped.
 *  - Blocking decision = active session (memory cache backed by DB) + package in the
 *    immutable-for-this-session set + TimeGuard says not expired. Expiry is only
 *    reached through SessionEngine, never by this class directly.
 */
class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LockinBlocker"
        private const val CACHE_TTL_MS = 5_000L
        private const val HEARTBEAT_MS = 30_000L
        private const val BLOCK_GAP_MS = 600L
        private const val ATTEMPT_THROTTLE_MS = 5_000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var cachedSession: Session? = null
    @Volatile private var cachedPackages: Set<String> = emptySet()
    @Volatile private var cachedLastSeen: TimeGuard.LastSeen? = null
    @Volatile private var cacheLoadedAtMs: Long = 0L
    @Volatile private var roles: ProtectedRules.Roles? = null
    @Volatile private var lastHandledPkg: String = ""
    @Volatile private var lastBlockShownAt: Long = 0L

    private val attemptThrottle = ConcurrentHashMap<String, Long>()
    private var refreshReceiver: BroadcastReceiver? = null
    private var heartbeatPosted = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            scope.launchSafely {
                runCatching {
                    SessionEngine.completeIfDue(applicationContext)
                }
                runCatching {
                    SessionRepository(applicationContext).setMeta(
                        SessionRepository.META_SERVICE_ALIVE,
                        System.currentTimeMillis()
                    )
                }
            }
            mainHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        runCatching { startAsForeground() }
        runCatching { roles = RolesResolver.resolve(applicationContext) }
        runCatching { reloadCache() }
        runCatching { SessionWatchdogJob.schedule(applicationContext) }

        refreshReceiver?.let { RefreshBus.unregister(this, it) }
        refreshReceiver = RefreshBus.register(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                requestCacheReload()
            }
        })

        if (!heartbeatPosted) {
            heartbeatPosted = true
            mainHandler.post(heartbeatRunnable)
        }
        scope.launchSafely { runCatching { SessionEngine.completeIfDue(applicationContext) } }
        Log.i(TAG, "Blocker service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            maybeHeartbeatFromEvent()
            handle(event)
        } catch (t: Throwable) {
            // A feature failure must never take the service down.
            Log.e(TAG, "Event handling failed safely", t)
        }
    }

    override fun onInterrupt() = Unit

    private var lastHeartbeatFromEvent = 0L

    private fun maybeHeartbeatFromEvent() {
        val now = SystemClock.uptimeMillis()
        if (now - lastHeartbeatFromEvent < HEARTBEAT_MS) return
        lastHeartbeatFromEvent = now
        if (!heartbeatPosted) {
            heartbeatPosted = true
            mainHandler.post(heartbeatRunnable)
        }
    }

    private fun handle(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isEmpty()) return
        if (pkg == lastHandledPkg) return
        lastHandledPkg = pkg
        if (pkg == packageName) return

        val nowWall = System.currentTimeMillis()
        if (nowWall - cacheLoadedAtMs > CACHE_TTL_MS) requestCacheReload()

        val session = cachedSession ?: return
        if (session.state != SessionState.ACTIVE) return

        val roleSet = roles ?: run {
            roles = RolesResolver.resolve(applicationContext)
            roles
        } ?: return
        if (ProtectedRules.isProtected(pkg, roleSet)) return
        if (pkg !in cachedPackages) return

        /* --- The package is in the block set of an enforcing session. --- */

        val snapshot = TimeGuard.ClockSnapshot(
            wallMs = nowWall,
            elapsedMs = SystemClock.elapsedRealtime(),
            bootCount = AndroidClock(applicationContext).bootCount()
        )
        val result = TimeGuard.evaluate(session, snapshot, cachedLastSeen)

        // Keep the observation pair and (async) any corrections.
        cachedLastSeen = TimeGuard.LastSeen(snapshot.wallMs, snapshot.elapsedMs)
        val correctedSession = session.copy(
            deadlineWallMs = result.deadlineWallMs,
            wallAdjustMs = result.wallAdjustMs,
            integrity = result.integrity,
            crossedReboot = result.rebootDetectedNow || session.crossedReboot
        )
        if (result.deadlineWallMs != session.deadlineWallMs ||
            result.integrity != session.integrity ||
            (result.rebootDetectedNow && !session.crossedReboot)
        ) {
            cachedSession = correctedSession
            val sid = session.id
            scope.launchSafely {
                runCatching {
                    SessionRepository(applicationContext).persistTimeState(
                        sid, result.deadlineWallMs, result.wallAdjustMs,
                        result.rebootDetectedNow || session.crossedReboot, result.integrity
                    )
                }
            }
        }
        cachedLastSeen?.let { seen ->
            scope.launchSafely {
                runCatching {
                    SessionRepository(applicationContext)
                        .noteLastSeen(seen.wallMs, seen.elapsedMs)
                }
            }
        }

        if (result.expired) {
            // Time says done — only SessionEngine may perform the transition.
            scope.launchSafely { runCatching { SessionEngine.completeIfDue(applicationContext) } }
            return
        }

        recordAttempt(pkg, session.id, nowWall)
        performGlobalAction(GLOBAL_ACTION_HOME)

        val sinceLastBlock = nowWall - lastBlockShownAt
        if (sinceLastBlock >= BLOCK_GAP_MS) {
            lastBlockShownAt = nowWall
            mainHandler.postDelayed(
                {
                    runCatching {
                        val intent = Intent(applicationContext, BlockActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                        }
                        startActivity(intent)
                    }
                },
                90L
            )
        }
    }

    private fun recordAttempt(pkg: String, sessionId: Long, nowWall: Long) {
        val last = attemptThrottle[pkg]
        if (last != null && nowWall - last < ATTEMPT_THROTTLE_MS) return
        attemptThrottle[pkg] = nowWall
        scope.launchSafely {
            runCatching {
                SessionRepository(applicationContext).recordAttempt(pkg, sessionId, nowWall)
            }
        }
    }

    private fun requestCacheReload() {
        scope.launchSafely { runCatching { reloadCache() } }
    }

    /** Runs on a background dispatcher; volatiles are swapped after the read. */
    private fun reloadCache() {
        val repo = SessionRepository(applicationContext)
        val session = repo.activeSession()
        val packages = if (session != null) repo.packagesOf(session.id).toSet() else emptySet()
        val lastSeen = repo.lastSeen()
        cachedSession = session
        cachedPackages = packages
        cachedLastSeen = lastSeen
        cacheLoadedAtMs = System.currentTimeMillis()
        if (session == null) attemptThrottle.clear()
    }

    private fun startAsForeground() {
        val notification = Notifications.blockerService(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.ID_BLOCKER_FGS,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifications.ID_BLOCKER_FGS, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        refreshReceiver?.let { RefreshBus.unregister(this, it) }
        refreshReceiver = null
        runCatching { scope.cancel() }
        super.onDestroy()
    }

    private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
        launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Background work failed safely", t)
            }
        }
    }
}

/** Local settings helpers used by the blocker/watchdog. */
object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${context.packageName}/${BlockerAccessibilityService::class.java.name}"
        val targetNoProcess = "${context.packageName}/com.rrrtx.lockin.core.BlockerAccessibilityService"
        return flat.split(':').any { it == target || it == targetNoProcess }
    }
}
