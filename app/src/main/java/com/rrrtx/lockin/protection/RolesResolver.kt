package com.rrrtx.lockin.protection

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.rrrtx.lockin.core.ProtectedRules

/** Resolves platform roles that must never be blockable on THIS device. */
object RolesResolver {

    fun resolve(context: Context): ProtectedRules.Roles {
        val pm = context.packageManager

        val launcher = runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()

        val ime = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )?.substringBefore('/')
        }.getOrNull()

        val dialer = runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_DIAL),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()

        return ProtectedRules.Roles(
            ownPackage = context.packageName,
            launcherPkg = launcher,
            imePkg = ime,
            defaultDialerPkg = dialer
        )
    }
}
