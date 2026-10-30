package com.rrrtx.lockin.core

import android.os.SystemClock
import android.provider.Settings
import android.content.Context

/** Real device clock: UTC wall time + elapsed realtime + best-effort boot counter. */
class AndroidClock(private val context: Context) : Clock {

    override fun wallMs(): Long = System.currentTimeMillis()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Long = try {
        // The "boot_count" global exists on many API-23 ROMs but the field was
        // only added at API 24 — reading the literal key with a default is safe
        // everywhere and returns UNKNOWN_BOOT where the setting is absent.
        Settings.Global.getInt(
            context.contentResolver,
            "boot_count",
            Clock.UNKNOWN_BOOT.toInt()
        ).toLong()
    } catch (_: Throwable) {
        Clock.UNKNOWN_BOOT
    }
}
