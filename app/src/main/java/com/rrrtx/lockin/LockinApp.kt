package com.rrrtx.lockin

import android.app.Application
import android.content.Context
import android.os.Build

/**
 * Application entry point. Initialization that touches the database or scheduling
 * is process-aware: only the UI process runs app-wide setup, while the isolated
 * `:lockin_core` process initializes exactly what the blocking engine needs.
 */
class LockinApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            // Main-process warm up happens lazily through the repositories.
        }
    }

    fun isMainProcess(): Boolean {
        val current = getProcessNameCompat()
        return current == packageName
    }

    private fun getProcessNameCompat(): String {
        // API 28+: one call. API 23–27: find our own pid in the process list
        // (no permission needed for own processes), then fall back to the
        // ApplicationInfo the runtime bound to this process.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val pid = android.os.Process.myPid()
        @Suppress("DEPRECATION")
        runCatching {
            (getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        return applicationInfo.processName ?: packageName
    }

    companion object {
        lateinit var instance: LockinApp
            private set
    }
}
