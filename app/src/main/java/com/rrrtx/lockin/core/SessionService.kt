package com.rrrtx.lockin.core

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Foreground countdown keeper. Ticks once per second while a session is active so
 * the notification stays truthful and completion is detected even when no exact
 * alarm fires. No notification action can affect the session.
 */
class SessionService : android.app.Service() {

    companion object {
        private const val TAG = "LockinSessionService"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, SessionService::class.java)
                )
            }.onFailure {
                // Background-start restrictions: enforcement continues via the
                // accessibility service + alarms; the UI re-arms this on open.
                Log.w(TAG, "startForegroundService refused: ${it.message}")
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionService::class.java)) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastAppCount = -1

    private val tick = object : Runnable {
        override fun run() {
            val evaluation = runCatching { SessionEngine.evaluate(applicationContext) }.getOrNull()
            if (evaluation == null) {
                Notifications.cancel(applicationContext, Notifications.ID_SESSION_COUNTDOWN)
                stopSelf()
                return
            }
            if (evaluation.result.expired) {
                val completed = runCatching {
                    SessionEngine.completeIfDue(applicationContext)
                }.getOrDefault(false)
                if (completed) {
                    stopSelf()
                    return
                }
            } else {
                val appCount = runCatching {
                    SessionRepository(applicationContext)
                        .packagesOf(evaluation.session.id).size
                }.getOrDefault(lastAppCount.coerceAtLeast(0))
                lastAppCount = appCount
                runCatching {
                    Notifications.notificationManager(applicationContext).notify(
                        Notifications.ID_SESSION_COUNTDOWN,
                        Notifications.countdown(
                            applicationContext,
                            evaluation.result.remainingMs,
                            appCount
                        )
                    )
                }
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        try {
            val notification = Notifications.countdown(applicationContext, 0L, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Notifications.ID_SESSION_COUNTDOWN,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Notifications.ID_SESSION_COUNTDOWN, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
