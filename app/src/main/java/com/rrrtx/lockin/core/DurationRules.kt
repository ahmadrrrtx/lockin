package com.rrrtx.lockin.core

import java.util.Locale

/** Duration presets, bounds and formatting. Pure Kotlin, fully unit-testable. */
object DurationRules {

    val PRESETS_MINUTES: List<Long> = listOf(15, 25, 30, 50, 60, 120, 240, 480, 1440)

    const val MIN_CUSTOM_MS: Long = 5L * 60L * 1000L
    const val MAX_MS: Long = 7L * 24L * 60L * 60L * 1000L // documented 7-day cap

    fun isValid(durationMs: Long): Boolean = durationMs in MIN_CUSTOM_MS..MAX_MS

    fun requireValid(durationMs: Long): Long {
        require(isValid(durationMs)) {
            "Duration must be between ${MIN_CUSTOM_MS / 60000} and ${MAX_MS / 3600000} minutes"
        }
        return durationMs
    }

    fun minutesToMs(minutes: Long): Long = minutes * 60_000L

    /** Epoch end time; UTC epoch math is timezone/DST independent by construction. */
    fun expectedEnd(startWallMs: Long, durationMs: Long): Long = startWallMs + durationMs

    data class Parts(val days: Int, val hours: Int, val minutes: Int) {
        val isZero: Boolean get() = days == 0 && hours == 0 && minutes == 0
    }

    fun parts(durationMs: Long): Parts {
        val totalMinutes = durationMs / 60_000L
        return Parts(
            days = (totalMinutes / (24 * 60)).toInt(),
            hours = ((totalMinutes / 60) % 24).toInt(),
            minutes = (totalMinutes % 60).toInt()
        )
    }

    /** Compact human form: "45m", "1h 30m", "2d 3h". */
    fun formatShort(durationMs: Long, locale: Locale = Locale.getDefault()): String {
        val p = parts(durationMs)
        val out = StringBuilder()
        if (p.days > 0) out.append(p.days).append('d')
        if (p.hours > 0) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(p.hours).append('h')
        }
        if (p.minutes > 0) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(p.minutes).append('m')
        }
        if (out.isEmpty()) out.append("0m")
        return out.toString()
    }

    /** Countdown form for the active screen: "1:59:07" / "12:05". */
    fun formatCountdown(remainingMs: Long): String {
        val total = (remainingMs.coerceAtLeast(0L)) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
