package com.rrrtx.lockin.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rrrtx.lockin.R
import com.rrrtx.lockin.ui.MainActivity

/**
 * Notification channels and builders. Session notifications carry NO actions that
 * can affect the session — the only interaction is opening a view of the session.
 */
object Notifications {

    const val CHANNEL_SESSION = "lockin_session"
    const val CHANNEL_PROTECTION = "lockin_protection"
    const val CHANNEL_DONE = "lockin_done"

    const val ID_BLOCKER_FGS = 3101
    const val ID_SESSION_COUNTDOWN = 3102
    const val ID_PROTECTION_WARNING = 3103
    const val ID_COMPLETED = 3104
    const val ID_SCHEDULE_STARTED = 3105

    fun ensureChannels(context: Context) {
        // Notification channels exist from Android 8.0 (API 26) only. Below that
        // the system has no channels at all: NotificationCompat silently drops the
        // channel id, so nothing else needs a fallback path.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                context.getString(R.string.session_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.session_channel_description)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROTECTION,
                context.getString(R.string.protection_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.protection_channel_description)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.done_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.done_channel_description)
            }
        )
    }

    fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun openIntent(context: Context, route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Persistent low-priority status notification for the blocking service itself. */
    fun blockerService(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.accessibility_service_label))
            .setContentText(context.getString(R.string.protection_blocker_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent(context))
            .build()
    }

    fun countdown(context: Context, remainingMs: Long, appCount: Int): Notification {
        ensureChannels(context)
        val text = context.getString(
            R.string.notif_session_text,
            DurationRules.formatShort(remainingMs),
            appCount
        )
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_session_title))
            .setContentText(text)
            .setSubText(context.getString(R.string.active_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openIntent(context, "active"))
            .build()
    }

    fun protectionWarning(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(context.getString(R.string.notif_protection_low_title))
            .setContentText(context.getString(R.string.notif_protection_low_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, "protection"))
            .build()
    }

    fun completed(
        context: Context,
        durationMs: Long,
        attempts: Int
    ): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_completed_title))
            .setContentText(
                context.getString(
                    R.string.notif_completed_text,
                    DurationRules.formatShort(durationMs),
                    attempts
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, "history"))
            .build()
    }

    fun scheduleStarted(context: Context, label: String, durationMs: Long): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_schedule_started_title))
            .setContentText(
                context.getString(
                    R.string.notif_schedule_started_text,
                    label,
                    DurationRules.formatShort(durationMs)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, "active"))
            .build()
    }

    fun cancel(context: Context, id: Int) {
        notificationManager(context).cancel(id)
    }
}
