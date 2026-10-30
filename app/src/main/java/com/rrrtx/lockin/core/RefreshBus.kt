package com.rrrtx.lockin.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Lightweight same-UID, cross-process signal: "session state changed, reload".
 * The database remains the source of truth; this only tells caches when to refresh.
 */
object RefreshBus {

    const val ACTION_STATE_CHANGED = "com.rrrtx.lockin.ACTION_STATE_CHANGED"
    const val EXTRA_ORIGIN = "origin"

    fun ping(context: Context, origin: String) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ORIGIN, origin)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Register a not-exported receiver. Returns the receiver so callers can unregister.
     * Safe in any process of the app (same UID delivery).
     */
    fun register(context: Context, receiver: BroadcastReceiver): BroadcastReceiver {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    fun unregister(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered; teardown must never throw.
        }
    }
}
