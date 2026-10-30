package com.rrrtx.lockin.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * After reboot or self-update: persist-alarm and job state must be re-armed and any
 * session that expired while the device was off must be completed through the
 * verified TimeGuard path (wall-clock authority after reboot).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        try {
            SessionEngine.recover(context)
            Log.i(TAG, "Recovery pass completed for $action")
        } catch (t: Throwable) {
            Log.e(TAG, "Boot recovery failed", t)
        }
    }

    companion object {
        private const val TAG = "LockinBoot"
    }
}
