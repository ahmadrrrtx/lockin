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
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentHomeBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.ProtectionSnapshot
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.startOfDayMs
import com.rrrtx.lockin.ui.visible

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            bindActive()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnStartFocus.setOnClickListener {
            SessionDraft.startNew()
            (activity as? MainActivity)?.push(SelectAppsFragment())
        }
        binding.btnViewActive.setOnClickListener {
            (activity as? MainActivity)?.push(ActiveSessionFragment())
        }
        binding.tvProtectionBanner.setOnClickListener {
            (activity as? MainActivity)?.push(ProtectionCenterFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun refreshAll() {
        val ctx = context ?: return
        runCatching {
            val repo = SessionRepository(ctx)
            binding.tvStatFocus.text = DurationRules.formatShort(
                repo.totalFocusedSince(startOfDayMs())
            )
            binding.tvStatSessions.text = repo.completedCount().toString()
            binding.tvStatAttempts.text = repo.totalAttempts().toString()
        }

        val snapshot = runCatching { ProtectionSnapshot.capture(ctx) }.getOrNull()
        if (snapshot != null && !snapshot.blockerOn) {
            binding.tvProtectionBanner.visible()
        } else {
            binding.tvProtectionBanner.gone()
        }
        bindActive()
    }

    private fun bindActive() {
        val ctx = context ?: return
        val evaluation = runCatching {
            com.rrrtx.lockin.core.SessionEngine.evaluate(ctx)
        }.getOrNull()

        if (evaluation == null) {
            binding.cardActive.gone()
            binding.boxIdle.visible()
            binding.btnStartFocus.visible()
            return
        }

        binding.cardActive.visible()
        binding.boxIdle.gone()
        binding.btnStartFocus.gone()

        binding.tvActiveCountdown.text =
            DurationRules.formatCountdown(evaluation.result.remainingMs)
        val appCount = runCatching {
            SessionRepository(ctx).packagesOf(evaluation.session.id).size
        }.getOrDefault(0)
        binding.tvActiveMeta.text = getString(
            R.string.active_locked_apps, appCount
        )
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }
}
