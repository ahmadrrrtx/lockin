package com.rrrtx.lockin.ui.fragments

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rrrtx.lockin.BuildConfig
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.RestrictionGate
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.Prefs
import com.rrrtx.lockin.databinding.FragmentSettingsBinding
import com.rrrtx.lockin.protection.AdminReceiver
import com.rrrtx.lockin.protection.DevicePolicyHelper
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.ProtectionSnapshot
import com.rrrtx.lockin.ui.UiStatus
import com.rrrtx.lockin.ui.renderStatus
import com.rrrtx.lockin.ui.toast

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rowAccessibility.root.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.rowNotifications.root.setOnClickListener { requestNotifications() }
        binding.rowBattery.root.setOnClickListener { requestBattery() }
        binding.rowAdmin.root.setOnClickListener { toggleAdmin() }
        binding.rowProtectionCenterWrap.setOnClickListener {
            (activity as? MainActivity)?.push(ProtectionCenterFragment())
        }
        binding.rowTheme.root.setOnClickListener { pickTheme() }
        binding.rowPrivacy.root.setOnClickListener { showPrivacy() }
        binding.rowDelete.root.setOnClickListener { confirmDelete() }
        binding.rowAbout.root.setOnClickListener { showAbout() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindRow(
        row: androidx.viewbinding.ViewBinding,
        title: Int,
        subtitle: Int,
        status: UiStatus? = null
    ) {
        val root = row.root
        root.findViewById<TextView>(R.id.tvRowTitle).setText(title)
        root.findViewById<TextView>(R.id.tvRowSubtitle).setText(subtitle)
        val dot = root.findViewById<TextView>(R.id.dotRow)
        if (status == null) {
            dot.visibility = View.GONE
        } else {
            dot.visibility = View.VISIBLE
            dot.renderStatus(status)
        }
    }

    private fun refresh() {
        val ctx = context ?: return
        val snapshot = runCatching { ProtectionSnapshot.capture(ctx) }.getOrNull() ?: return

        bindRow(
            binding.rowAccessibility,
            R.string.settings_accessibility,
            R.string.settings_accessibility_sub,
            if (snapshot.blockerOn) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowNotifications,
            R.string.settings_notifications,
            R.string.settings_notifications_sub,
            if (snapshot.notificationsOn) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowBattery,
            R.string.settings_battery,
            R.string.settings_battery_sub,
            if (snapshot.batteryExempt) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowAdmin,
            R.string.settings_device_admin,
            R.string.settings_device_admin_sub,
            if (snapshot.adminActive) UiStatus.ON else UiStatus.OFF
        )
        val themeLabel = when (Prefs.themeMode(ctx)) {
            Prefs.THEME_LIGHT -> R.string.settings_theme_light
            Prefs.THEME_DARK -> R.string.settings_theme_dark
            else -> R.string.settings_theme_system
        }
        bindRow(binding.rowTheme, R.string.settings_theme, themeLabel)
        bindRow(binding.rowPrivacy, R.string.settings_privacy_policy, R.string.privacy_summary)
        bindRow(binding.rowDelete, R.string.settings_clear_data, R.string.settings_clear_data_confirm)
        bindRow(binding.rowAbout, R.string.settings_about_lockin, R.string.home_tagline)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requireContext().toast(getString(R.string.status_on))
        }
    }

    private fun requestBattery() {
        val ctx = requireContext()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            ctx.toast(getString(R.string.status_on))
            return
        }
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            )
            startActivity(intent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun toggleAdmin() {
        val ctx = requireContext()
        val active = AdminReceiver.isActive(ctx)

        if (active) {
            // Weakening action: gated while a session enforces.
            val sessionActive = runCatching {
                SessionRepository(ctx).activeSession() != null
            }.getOrDefault(false)
            val decision = RestrictionGate.decideAction(
                RestrictionGate.DeferredAction.ReleaseDeviceAdmin,
                sessionActive
            )
            when (decision) {
                RestrictionGate.Decision.DEFER_UNTIL_SESSION_END -> {
                    SessionRepository(ctx)
                        .enqueuePendingAction(
                            com.rrrtx.lockin.core.SessionEngine.PENDING_RELEASE_DEVICE_ADMIN
                        )
                    ctx.toast(getString(R.string.protection_admin_pending))
                }
                RestrictionGate.Decision.ALLOW_NOW -> {
                    runCatching {
                        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE)
                            as DevicePolicyManager
                        dpm.removeActiveAdmin(AdminReceiver.component(ctx))
                        ctx.toast(getString(R.string.status_off))
                    }.onFailure {
                        ctx.toast(getString(R.string.protection_open_settings))
                    }
                }
            }
            refresh()
            return
        }

        runCatching {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(ctx))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_description)
                )
            }
            adminLauncher.launch(intent)
        }.onFailure {
            ctx.toast(getString(R.string.protection_open_settings))
        }
    }

    private val adminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private fun pickTheme() {
        val labels = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark)
        )
        val current = Prefs.themeMode(requireContext())
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Lockin_Dialog)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Prefs.setThemeMode(requireContext(), which)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showPrivacy() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Lockin_Dialog)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_summary)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun showAbout() {
        val version = BuildConfig.VERSION_NAME
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Lockin_Dialog)
            .setTitle(R.string.settings_about_lockin)
            .setMessage(getString(R.string.settings_version, version) + "\n\n" +
                getString(R.string.home_tagline))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Lockin_Dialog)
            .setTitle(R.string.settings_clear_data)
            .setMessage(R.string.settings_clear_data_confirm)
            .setPositiveButton(R.string.settings_clear_data_yes) { _, _ ->
                val ctx = requireContext()
                runCatching {
                    val repo = SessionRepository(ctx)
                    val activePackages = repo.activeSession()?.let { repo.packagesOf(it.id) }
                        ?: emptyList()
                    DevicePolicyHelper.releaseAll(ctx, activePackages)
                    com.rrrtx.lockin.core.SessionAlarms.cancelCompletion(ctx)
                    com.rrrtx.lockin.core.SessionService.stop(ctx)
                    repo.clearAllData()
                    com.rrrtx.lockin.core.SessionEngine.rearmSchedules(ctx)
                    refresh()
                }
                ctx.toast(getString(R.string.settings_delete_done))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
