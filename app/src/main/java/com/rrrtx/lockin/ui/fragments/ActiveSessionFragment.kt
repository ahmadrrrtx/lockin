package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.SessionEngine
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.PickTarget
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentActiveSessionBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.ProtectionSnapshot
import com.rrrtx.lockin.ui.UiStatus
import com.rrrtx.lockin.ui.formatTime
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.renderStatus
import com.rrrtx.lockin.ui.visible
import com.rrrtx.lockin.core.IntegrityFlag

/**
 * The strict session view. Contains NO cancel, pause, shorten or unlock control —
 * by design there is nothing here that could end the session.
 */
class ActiveSessionFragment : Fragment() {

    private var _binding: FragmentActiveSessionBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnAddApps.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val active = runCatching { SessionRepository(ctx).activeSession() }.getOrNull()
                ?: return@setOnClickListener
            val existing = runCatching { SessionRepository(ctx).packagesOf(active.id) }
                .getOrDefault(emptyList())
            SessionDraft.startAddToActive(existing)
            (activity as? MainActivity)?.push(SelectAppsFragment())
        }
        binding.btnProtection.setOnClickListener {
            (activity as? MainActivity)?.push(ProtectionCenterFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun render() {
        val ctx = context ?: return
        val evaluation = runCatching { SessionEngine.evaluate(ctx) }.getOrNull()
        if (evaluation == null) {
            if (evaluation == null && isResumed) {
                // Session ended while we were showing it (completed elsewhere).
                parentFragmentManager.popBackStack()
            }
            return
        }

        if (evaluation.result.expired) {
            runCatching { SessionEngine.completeIfDue(ctx) }
        }

        val session = evaluation.session
        binding.tvCountdown.text = DurationRules.formatCountdown(evaluation.result.remainingMs)
        binding.tvEndsAt.text = getString(
            R.string.active_end_time,
            formatTime(ctx, session.deadlineWallMs)
        )

        val appCount = runCatching { SessionRepository(ctx).packagesOf(session.id).size }
            .getOrDefault(0)
        binding.tvAppsChip.text = getString(R.string.active_locked_apps, appCount)

        if (session.scheduleId != null) {
            binding.tvScheduleChip.visible()
            binding.tvScheduleChip.setText(R.string.schedules_title)
        } else {
            binding.tvScheduleChip.gone()
        }

        session.commitmentText?.let {
            binding.tvCommitment.visible()
            binding.tvCommitment.text = it
        } ?: binding.tvCommitment.gone()

        binding.tvIntegrity.visibility =
            if (session.integrity == IntegrityFlag.DEGRADED) View.VISIBLE else View.GONE

        val snapshot = runCatching { ProtectionSnapshot.capture(ctx) }.getOrNull()
        if (snapshot == null || !snapshot.blockerOn) {
            binding.dotProtection.renderStatus(UiStatus.OFF)
            binding.tvProtectionState.setText(R.string.active_protection_off)
            binding.tvProtectionState.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.statusAlert)
            )
        } else {
            binding.dotProtection.renderStatus(UiStatus.ON)
            binding.tvProtectionState.setText(R.string.active_protection_on)
            binding.tvProtectionState.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.statusOk)
            )
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }
}
