package com.rrrtx.lockin.core

/**
 * Which packages can never be part of a Lockin block set.
 *
 * Static essentials plus dynamic platform roles (launcher, keyboard, default dialer).
 * The selection UI must surface these rows with an explanation, never hide them silently.
 */
object ProtectedRules {

    const val SYSTEMUI = "com.android.systemui"

    /** Role-resolved packages (null when unresolvable on this device). */
    data class Roles(
        val ownPackage: String,
        val launcherPkg: String? = null,
        val imePkg: String? = null,
        val defaultDialerPkg: String? = null
    )

    private val STATIC_PROTECTED: Set<String> = setOf(
        // Telecom & emergency
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.google.android.contacts",
        // System chrome
        SYSTEMUI,
        "com.android.settings",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "org.adw.launcher2",
        "org.adw.launcher",
        "com.teslacoilsw.launcher",
        // Installer / permission flows (spec: never blockable)
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        // Device bootstrap
        "com.android.provision",
        "com.google.android.setupwizard",
        "com.android.setupwizard",
        // Common keyboards (dynamic IME check also runs)
        "com.google.android.inputmethod.latin",
        "org.androidkeyboard",
        "com.touchtype.swiftkey",
        "com.samsung.android.honeyboard"
    )

    fun isProtected(pkg: String, roles: Roles): Boolean {
        if (pkg.isEmpty()) return true
        if (pkg == roles.ownPackage) return true
        if (pkg in STATIC_PROTECTED) return true
        if (roles.launcherPkg != null && pkg == roles.launcherPkg) return true
        if (roles.imePkg != null && pkg == roles.imePkg) return true
        if (roles.defaultDialerPkg != null && pkg == roles.defaultDialerPkg) return true
        return false
    }

    /** Human-readable class of protection, for the selection UI's explanation row. */
    fun explain(pkg: String, roles: Roles): String? = when {
        pkg == roles.ownPackage -> "Lockin can never block itself"
        pkg == SYSTEMUI || pkg == "com.android.settings" -> "Essential Android system function"
        pkg == roles.launcherPkg -> "Your home launcher must stay available"
        pkg == roles.imePkg -> "Your keyboard must stay available"
        pkg == roles.defaultDialerPkg || pkg in STATIC_PROTECTED && isTelecomOrInstaller(pkg) ->
            "Emergency calling and core system flows must stay available"
        pkg in STATIC_PROTECTED -> "Essential Android system function"
        else -> null
    }

    private fun isTelecomOrInstaller(pkg: String): Boolean = pkg in setOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )
}
