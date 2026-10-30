package com.rrrtx.lockin.protection

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rrrtx.lockin.core.Notifications
import com.rrrtx.lockin.core.RefreshBus
import com.rrrtx.lockin.core.SessionRepository

/**
 * Reports protection-state changes honestly. Lockin never blocks the system's own
 * deactivation flow — it observes, notifies and updates the Protection Center state.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        notifyChange(context, enabled = true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        notifyChange(context, enabled = false)
        Notifications.cancel(context, Notifications.ID_PROTECTION_WARNING)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val active = runCatching {
            SessionRepository(context).activeSession() != null
        }.getOrDefault(false)
        if (active) {
            runCatching {
                Notifications.notificationManager(context).notify(
                    Notifications.ID_PROTECTION_WARNING,
                    androidx.core.app.NotificationCompat.Builder(context, Notifications.CHANNEL_PROTECTION)
                        .setSmallIcon(com.rrrtx.lockin.R.drawable.ic_warning)
                        .setContentTitle(
                            context.getString(com.rrrtx.lockin.R.string.protection_admin_lost)
                        )
                        .setContentText(
                            context.getString(com.rrrtx.lockin.R.string.protection_honest_body)
                        )
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }
        // Return null: the platform shows its standard confirmation flow.
        return null
    }

    private fun notifyChange(context: Context, enabled: Boolean) {
        Log.i(TAG, "Device admin enabled=$enabled")
        RefreshBus.ping(context, "admin")
    }

    companion object {
        private const val TAG = "LockinAdmin"

        fun component(context: Context): ComponentName =
            ComponentName(context, AdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(component(context))
        }
    }
}
