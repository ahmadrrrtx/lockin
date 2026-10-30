package com.rrrtx.lockin.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rrrtx.lockin.core.ProtectedRules
import com.rrrtx.lockin.protection.RolesResolver

/**
 * Real installed-application inventory: launcher-visible apps with genuine icons
 * and labels. Protected system rows are INCLUDED with a visible explanation —
 * never silently omitted.
 */
class AppInventory(context: Context) {

    private val appContext = context.applicationContext

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val roles: ProtectedRules.Roles,
        val isProtected: Boolean,
        val protectionExplanation: String?
    )

    fun load(): List<AppEntry> {
        val pm = appContext.packageManager
        val roles = RolesResolver.resolve(appContext)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(
            launcherIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )

        return activities
            .asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val protectedNow = ProtectedRules.isProtected(pkg, roles)
                AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                    roles = roles,
                    isProtected = protectedNow,
                    protectionExplanation = if (protectedNow) {
                        ProtectedRules.explain(pkg, roles)
                    } else {
                        null
                    }
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppEntry> { it.isProtected }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }
}
