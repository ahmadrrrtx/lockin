package com.rrrtx.lockin.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.AccessibilityStatus
import com.rrrtx.lockin.protection.AdminReceiver
import com.rrrtx.lockin.protection.DevicePolicyHelper
import java.text.DateFormat
import java.util.Date

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** Shared status-dot rendering: green = enforced, gray = off, amber/red = degraded. */
fun TextView.renderStatus(state: UiStatus) {
    text = "\u25CF"
    setTextSize(
        android.util.TypedValue.COMPLEX_UNIT_SP,
        10f
    )
    val colorRes = when (state) {
        UiStatus.ON -> R.color.statusOk
        UiStatus.OFF -> R.color.statusIdle
        UiStatus.DEGRADED -> R.color.statusAlert
        UiStatus.UNAVAILABLE -> R.color.statusIdle
    }
    setTextColor(ContextCompat.getColor(context, colorRes))
}

enum class UiStatus { ON, OFF, DEGRADED, UNAVAILABLE }

/** Snapshot of every protection, honestly computed from live platform state. */
data class ProtectionSnapshot(
    val blockerOn: Boolean,
    val adminActive: Boolean,
    val deviceOwner: Boolean,
    val batteryExempt: Boolean,
    val exactAlarms: Boolean,
    val notificationsOn: Boolean
) {
    companion object {
        fun capture(context: Context): ProtectionSnapshot {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val exact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
                    .canScheduleExactAlarms()
            } else {
                true
            }
            val notifOn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            return ProtectionSnapshot(
                blockerOn = AccessibilityStatus.isEnabled(context),
                adminActive = AdminReceiver.isActive(context),
                deviceOwner = DevicePolicyHelper.isDeviceOwner(context),
                batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName),
                exactAlarms = exact,
                notificationsOn = notifOn
            )
        }
    }
}

fun formatDateTime(context: Context, ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun formatTime(context: Context, ms: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date(ms))

fun formatDate(context: Context, ms: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun startOfDayMs(): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

val Activity.isActivityAlive: Boolean get() = !isFinishing && !isDestroyed
