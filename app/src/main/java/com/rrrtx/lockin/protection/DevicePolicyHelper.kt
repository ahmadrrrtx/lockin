package com.rrrtx.lockin.protection

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.rrrtx.lockin.core.SessionRepository

/**
 * Uninstall-protection primitives, gated on verified platform authority.
 *
 *  - Ordinary personal phone: active device admin adds deactivation friction only.
 *  - Verified device owner: `setUninstallBlocked` genuinely prevents removal of the
 *    listed packages until we release it. Lockin applies this ONLY when
 *    [DevicePolicyManager.isDeviceOwnerApp] says so — never pretends otherwise.
 */
object DevicePolicyHelper {

    private const val TAG = "LockinDevicePolicy"

    enum class ProtectionLevel { NONE, ADMIN_ACTIVE, DEVICE_OWNER }

    fun level(context: Context): ProtectionLevel {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return when {
            dpm.isDeviceOwnerApp(context.packageName) -> ProtectionLevel.DEVICE_OWNER
            AdminReceiver.isActive(context) -> ProtectionLevel.ADMIN_ACTIVE
            else -> ProtectionLevel.NONE
        }
    }

    fun isDeviceOwner(context: Context): Boolean =
        level(context) == ProtectionLevel.DEVICE_OWNER

    /**
     * Called when a session activates. No-op unless the device genuinely recognizes
     * Lockin as device owner.
     */
    fun onSessionStarted(context: Context, packages: Collection<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val all = (packages + context.packageName).distinct()
        var failures = 0
        all.forEach { pkg ->
            try {
                dpm.setUninstallBlocked(component(context), pkg, true)
            } catch (t: Throwable) {
                failures++
                Log.w(TAG, "setUninstallBlocked(true) failed for $pkg", t)
            }
        }
        Log.i(TAG, "Device-owner uninstall blocks applied (failures=$failures)")
    }

    /** Called when a session completes (or data is cleared) — releases only what we set. */
    fun onSessionCompleted(context: Context, packages: Collection<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        (packages + context.packageName).distinct().forEach { pkg ->
            try {
                dpm.setUninstallBlocked(component(context), pkg, false)
            } catch (t: Throwable) {
                Log.w(TAG, "setUninstallBlocked(false) failed for $pkg", t)
            }
        }
    }

    /** Context-only release used when clearing all data. */
    fun releaseAll(context: Context, packages: Collection<String>) {
        onSessionCompleted(context, packages)
    }

    private fun component(context: Context) =
        android.content.ComponentName(context, AdminReceiver::class.java)
}
