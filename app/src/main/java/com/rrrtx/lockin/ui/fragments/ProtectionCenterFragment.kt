package com.rrrtx.lockin.ui.fragments

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.IntegrityFlag
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.databinding.FragmentProtectionCenterBinding
import com.rrrtx.lockin.ui.ProtectionSnapshot
import com.rrrtx.lockin.ui.UiStatus
import com.rrrtx.lockin.ui.renderStatus
import androidx.viewbinding.ViewBinding

/**
 * Honest, live status of every protection Lockin claims — plus an explicit list
 * of what Android still allows. Nothing here can be toggled into a false state.
 */
class ProtectionCenterFragment : Fragment() {

    private var _binding: FragmentProtectionCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProtectionCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnOpenA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnExactAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:${requireContext().packageName}")
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindRow(
        row: ViewBinding,
        title: Int,
        subtitle: Int,
        status: UiStatus
    ) {
        val root = row.root
        root.findViewById<TextView>(R.id.tvRowTitle).setText(title)
        root.findViewById<TextView>(R.id.tvRowSubtitle).setText(subtitle)
        root.findViewById<TextView>(R.id.dotRow).apply {
            visibility = View.VISIBLE
            renderStatus(status)
        }
    }

    private fun refresh() {
        val ctx = context ?: return
        val snapshot = runCatching { ProtectionSnapshot.capture(ctx) }.getOrNull() ?: return

        bindRow(
            binding.rowBlocker,
            R.string.protection_blocker_row,
            R.string.protection_blocker_desc,
            if (snapshot.blockerOn) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowAdmin,
            R.string.protection_admin_row,
            R.string.protection_admin_desc,
            if (snapshot.adminActive) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowOwner,
            R.string.protection_owner_row,
            R.string.protection_owner_desc,
            if (snapshot.deviceOwner) UiStatus.ON else UiStatus.UNAVAILABLE
        )
        bindRow(
            binding.rowBattery,
            R.string.protection_battery_row,
            R.string.protection_battery_desc,
            if (snapshot.batteryExempt) UiStatus.ON else UiStatus.OFF
        )
        bindRow(
            binding.rowExact,
            R.string.protection_alarm_row,
            R.string.protection_alarm_desc,
            if (snapshot.exactAlarms) UiStatus.ON else UiStatus.OFF
        )

        val integrity = runCatching {
            SessionRepository(ctx).activeSession()?.integrity
        }.getOrNull()
        val integrityStatus = when (integrity) {
            IntegrityFlag.DEGRADED -> UiStatus.DEGRADED
            IntegrityFlag.OK -> UiStatus.ON
            null -> UiStatus.ON
        }
        bindRow(
            binding.rowTime,
            R.string.protection_time_row,
            R.string.protection_time_desc,
            integrityStatus
        )

        binding.btnExactAlarm.visibility =
            if (snapshot.exactAlarms) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
