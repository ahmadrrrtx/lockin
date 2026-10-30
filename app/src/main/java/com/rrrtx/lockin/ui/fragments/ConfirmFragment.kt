package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.SessionEngine
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.PickTarget
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentConfirmBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.ProtectionSnapshot
import com.rrrtx.lockin.ui.UiStatus
import com.rrrtx.lockin.ui.formatDateTime
import com.rrrtx.lockin.ui.renderStatus
import com.rrrtx.lockin.ui.toast

class ConfirmFragment : Fragment() {

    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnBackText.setOnClickListener { parentFragmentManager.popBackStack() }

        val pm = requireContext().packageManager
        binding.tvAppCount.text =
            getString(R.string.select_count_many, SessionDraft.packages.size).let {
                if (SessionDraft.packages.size == 1) {
                    getString(R.string.select_count_one, 1)
                } else {
                    it
                }
            }

        binding.chipGroupApps.removeAllViews()
        SessionDraft.packages.forEach { pkg ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val chip = Chip(requireContext()).apply {
                text = label
                isClickable = false
                isCheckable = false
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            binding.chipGroupApps.addView(chip)
        }

        binding.tvDuration.text = DurationRules.formatShort(SessionDraft.durationMs)
        val end = DurationRules.expectedEnd(System.currentTimeMillis(), SessionDraft.durationMs)
        binding.tvEnds.text = formatDateTime(requireContext(), end)

        binding.btnStart.setOnClickListener { onStartClicked() }
    }

    override fun onResume() {
        super.onResume()
        renderProtection()
    }

    private fun renderProtection() {
        val snapshot = runCatching { ProtectionSnapshot.capture(requireContext()) }.getOrNull()
            ?: return
        binding.dotBlocker.renderStatus(if (snapshot.blockerOn) UiStatus.ON else UiStatus.OFF)
        binding.tvBlockerState.text = getString(
            if (snapshot.blockerOn) R.string.protection_state_on else R.string.protection_state_off
        )
        binding.dotAdmin.renderStatus(if (snapshot.adminActive) UiStatus.ON else UiStatus.OFF)
        binding.tvAdminState.text = getString(
            if (snapshot.adminActive) {
                R.string.protection_admin_active_notice
            } else {
                R.string.settings_device_admin_sub
            }
        )
    }

    private fun onStartClicked() {
        if (!binding.checkCommit.isChecked) {
            requireContext().toast(getString(R.string.confirm_commitment_label))
            return
        }
        val snapshot = runCatching { ProtectionSnapshot.capture(requireContext()) }.getOrNull()
        if (snapshot != null && !snapshot.blockerOn) {
            requireContext().toast(getString(R.string.active_protection_off))
            return
        }

        SessionDraft.commitmentText = binding.etCommitment.text?.toString()
            ?.trim()?.takeIf { it.isNotEmpty() }

        val draft = com.rrrtx.lockin.core.PrepDraft(
            packages = SessionDraft.packages.toList(),
            durationMs = SessionDraft.durationMs,
            commitmentText = SessionDraft.commitmentText
        )

        val started = runCatching {
            SessionEngine.startSession(requireContext(), draft)
        }
        started.onFailure {
            requireContext().toast(it.message ?: "Could not start session")
            return
        }

        val act = activity as? MainActivity
        // Stack becomes exactly Home → Active (no creation screens behind START).
        act?.navigateToActiveAfterStart()
        SessionDraft.startNew()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
