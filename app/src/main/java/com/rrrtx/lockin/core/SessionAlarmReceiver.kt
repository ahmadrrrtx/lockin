package com.rrrtx.lockin.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarm entry point living in `:lockin_core`. The completion decision itself is
 * always re-verified by TimeGuard inside SessionEngine — an early or duplicated
 * alarm can never complete a session before its verified deadline.
 */
class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SessionAlarms.ACTION_COMPLETE) return
        try {
            val completed = SessionEngine.completeIfDue(context)
            Log.i(TAG, "Completion alarm processed (completedNow=$completed)")
        } catch (t: Throwable) {
            Log.e(TAG, "Completion alarm failed", t)
        }
    }

    companion object {
        private const val TAG = "LockinAlarm"
    }
}
