package com.rrrtx.lockin.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.Prefs
import com.rrrtx.lockin.ui.fragments.ActiveSessionFragment
import com.rrrtx.lockin.ui.fragments.HistoryFragment
import com.rrrtx.lockin.ui.fragments.HomeFragment
import com.rrrtx.lockin.ui.fragments.OnboardingFragment
import com.rrrtx.lockin.ui.fragments.ProtectionCenterFragment
import com.rrrtx.lockin.ui.fragments.SchedulesFragment
import com.rrrtx.lockin.ui.fragments.SettingsFragment

/**
 * Single-activity shell: bottom navigation across four root screens with an inner
 * stack for the session-creation flow, active session, onboarding and protection
 * screens. The bottom bar hides for every pushed screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var suppressNavCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyTheme(Prefs.themeMode(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applyInsets()

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavCallback) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_home -> showRoot(HomeFragment::class.java)
                R.id.nav_history -> showRoot(HistoryFragment::class.java)
                R.id.nav_schedules -> showRoot(SchedulesFragment::class.java)
                R.id.nav_settings -> showRoot(SettingsFragment::class.java)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateBottomBarVisibility()
            if (supportFragmentManager.backStackEntryCount == 0) syncBottomSelection()
        }

        if (savedInstanceState == null) {
            if (Prefs.isOnboardingDone(this)) {
                showRoot(HomeFragment::class.java)
            } else {
                push(OnboardingFragment(), false)
            }
        }
        updateBottomBarVisibility()

        handleRoute(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRoute(intent)
    }

    private fun handleRoute(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        when (route) {
            "active" -> if (SessionRepository(this).activeSession() != null) {
                clearStack()
                push(ActiveSessionFragment(), true)
            }
            "protection" -> {
                clearStack()
                push(ProtectionCenterFragment(), true)
            }
            "history" -> {
                clearStack()
                selectTab(R.id.nav_history)
            }
        }
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.mainRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun showRoot(clazz: Class<out Fragment>) {
        clearStack()
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, clazz.getDeclaredConstructor().newInstance())
            .commit()
        bottomNav.visibility = View.VISIBLE
    }

    fun push(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, fragment)
            .apply { if (addToBackStack) addToBackStack(null) }
            .commit()
    }

    private fun clearStack() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /** Called by onboarding when the user finishes (or skips to) the app. */
    fun completeOnboarding() {
        Prefs.setOnboardingDone(this)
        showRoot(HomeFragment::class.java)
    }

    /**
     * After START, history must be exactly Home → Active: no creation screen may
     * remain behind the session (back from Active lands on Home).
     */
    fun navigateToActiveAfterStart() {
        clearStack()
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, HomeFragment::class.java.getDeclaredConstructor().newInstance())
            .commit()
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentContainer, ActiveSessionFragment())
            .addToBackStack(null)
            .commit()
        bottomNav.visibility = View.GONE
    }

    private fun selectTab(itemId: Int) {
        if (bottomNav.selectedItemId == itemId) {
            showRootFor(itemId)
        } else {
            suppressNavCallback = true
            bottomNav.selectedItemId = itemId
            suppressNavCallback = false
            showRootFor(itemId)
        }
    }

    private fun showRootFor(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> showRoot(HomeFragment::class.java)
            R.id.nav_history -> showRoot(HistoryFragment::class.java)
            R.id.nav_schedules -> showRoot(SchedulesFragment::class.java)
            R.id.nav_settings -> showRoot(SettingsFragment::class.java)
        }
    }

    private fun updateBottomBarVisibility() {
        bottomNav.visibility =
            if (supportFragmentManager.backStackEntryCount == 0) View.VISIBLE else View.GONE
    }

    private fun syncBottomSelection() {
        val current = supportFragmentManager.findFragmentById(R.id.contentContainer)
        val target = when (current) {
            is HistoryFragment -> R.id.nav_history
            is SchedulesFragment -> R.id.nav_schedules
            is SettingsFragment -> R.id.nav_settings
            else -> R.id.nav_home
        }
        if (bottomNav.selectedItemId != target) {
            suppressNavCallback = true
            bottomNav.selectedItemId = target
            suppressNavCallback = false
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
