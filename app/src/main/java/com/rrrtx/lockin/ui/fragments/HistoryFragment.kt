package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.databinding.FragmentHistoryBinding
import com.rrrtx.lockin.ui.adapters.HistoryAdapter
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.visible

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private var adapter: HistoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ctx = context ?: return
        runCatching {
            val repo = SessionRepository(ctx)
            val rows = repo.history(100)
            binding.tvTotalFocus.text = DurationRules.formatShort(repo.totalFocusedMs())
            binding.tvTotalAttempts.text = repo.totalAttempts().toString()
            adapter?.submit(rows)
            if (rows.isEmpty()) {
                binding.tvEmptyTitle.visible()
                binding.tvEmptyBody.visible()
                binding.rvHistory.gone()
            } else {
                binding.tvEmptyTitle.gone()
                binding.tvEmptyBody.gone()
                binding.rvHistory.visible()
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
