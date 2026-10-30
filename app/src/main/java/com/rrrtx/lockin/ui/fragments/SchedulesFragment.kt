package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.databinding.FragmentSchedulesBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.adapters.SchedulesAdapter
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.visible

class SchedulesFragment : Fragment() {

    private var _binding: FragmentSchedulesBinding? = null
    private val binding get() = _binding!!
    private var adapter: SchedulesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSchedulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SchedulesAdapter(
            onOpen = { schedule ->
                (activity as? MainActivity)?.push(ScheduleEditFragment.newInstance(schedule.id))
            },
            onToggle = { schedule, enabled ->
                runCatching {
                    val repo = SessionRepository(requireContext())
                    repo.updateSchedule(schedule.copy(enabled = enabled))
                    com.rrrtx.lockin.core.SessionEngine.rearmSchedules(requireContext())
                }
                refresh()
            }
        )
        binding.rvSchedules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedules.adapter = adapter

        binding.btnAdd.setOnClickListener { openEditor() }
        binding.btnAddBottom.setOnClickListener { openEditor() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openEditor() {
        (activity as? MainActivity)?.push(ScheduleEditFragment.newInstance(null))
    }

    private fun refresh() {
        val ctx = context ?: return
        runCatching {
            val list = SessionRepository(ctx).schedules()
            adapter?.submit(list)
            if (list.isEmpty()) {
                binding.tvEmptyTitle.visible()
                binding.tvEmptyBody.visible()
                binding.rvSchedules.gone()
            } else {
                binding.tvEmptyTitle.gone()
                binding.tvEmptyBody.gone()
                binding.rvSchedules.visible()
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
