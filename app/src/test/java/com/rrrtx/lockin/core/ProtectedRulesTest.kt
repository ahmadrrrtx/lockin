package com.rrrtx.lockin.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedRulesTest {

    private val roles = ProtectedRules.Roles(
        ownPackage = "com.rrrtx.lockin",
        launcherPkg = "com.example.launcher",
        imePkg = "com.example.keyboard",
        defaultDialerPkg = "com.example.dialer"
    )

    @Test
    fun ownPackageIsNeverBlockable() {
        assertTrue(ProtectedRules.isProtected("com.rrrtx.lockin", roles))
        assertNotNull(ProtectedRules.explain("com.rrrtx.lockin", roles))
    }

    @Test
    fun staticEssentialsAreProtected() {
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.contacts",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller"
        ).forEach {
            assertTrue("$it must be protected", ProtectedRules.isProtected(it, roles))
        }
    }

    @Test
    fun dynamicRolesAreProtected() {
        assertTrue(ProtectedRules.isProtected("com.example.launcher", roles))
        assertTrue(ProtectedRules.isProtected("com.example.keyboard", roles))
        assertTrue(ProtectedRules.isProtected("com.example.dialer", roles))
    }

    @Test
    fun ordinaryAppsAreSelectable() {
        listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.whatsapp"
        ).forEach {
            assertFalse("$it must be blockable", ProtectedRules.isProtected(it, roles))
            assertNull(ProtectedRules.explain(it, roles))
        }
    }

    @Test
    fun explanationsExistForEveryProtectedReason() {
        assertNotNull(ProtectedRules.explain("com.rrrtx.lockin", roles))
        assertNotNull(ProtectedRules.explain("com.android.systemui", roles))
        assertNotNull(ProtectedRules.explain("com.example.launcher", roles))
        assertNotNull(ProtectedRules.explain("com.example.keyboard", roles))
        assertNotNull(ProtectedRules.explain("com.example.dialer", roles))
    }

    @Test
    fun emptyPackageCountsAsProtected() {
        assertTrue(ProtectedRules.isProtected("", roles))
    }

    @Test
    fun rolesWithoutResolutionStillProtectStaticsAndSelf() {
        val bare = ProtectedRules.Roles(ownPackage = "com.rrrtx.lockin")
        assertTrue(ProtectedRules.isProtected("com.rrrtx.lockin", bare))
        assertTrue(ProtectedRules.isProtected("com.android.systemui", bare))
        assertFalse(ProtectedRules.isProtected("com.some.app", bare))
    }
}
