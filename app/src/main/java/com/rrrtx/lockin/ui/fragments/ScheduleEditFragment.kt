package com.rrrtx.lockin.ui.fragments

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.Schedule
import com.rrrtx.lockin.core.SessionEngine
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentScheduleEditBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.toast
import com.rrrtx.lockin.ui.visible
import java.text.DateFormat
import java.util.Calendar

class ScheduleEditFragment : Fragment() {

    private var _binding: FragmentScheduleEditBinding? = null
    private val binding get() = _binding!!

    private var scheduleId: Long = -1L
    private var hourOfDay: Int = 8
    private var minuteOfHour: Int = 0
    private var durationMs: Long = 25L * 60_000L
    private var daysMask: Int = 0b0011111 // Mon–Fri
    private var packages: MutableList<String> = mutableListOf()

    private val dayChips: List<Pair<Chip, Int>>
        get() = listOf(
            binding.dayMon to 0, binding.dayTue to 1, binding.dayWed to 2,
            binding.dayThu to 3, binding.dayFri to 4, binding.daySat to 5, binding.daySun to 6
        )

    private val durationChips: List<Pair<Chip, Long>>
        get() = listOf(
            binding.dur15 to 15L, binding.dur25 to 25L, binding.dur30 to 30L,
            binding.dur50 to 50L, binding.dur60 to 60L, binding.dur120 to 120L,
            binding.dur240 to 240L
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scheduleId = arguments?.getLong(ARG_ID, -1L) ?: -1L
        val existing = if (scheduleId >= 0) {
            runCatching { SessionRepository(requireContext()).scheduleById(scheduleId) }.getOrNull()
        } else null

        if (existing != null) {
            binding.tvTitle.setText(R.string.schedule_dialog_title)
            binding.etLabel.setText(existing.label)
            hourOfDay = existing.hour
            minuteOfHour = existing.minute
            durationMs = existing.durationMs
            daysMask = existing.daysMask
            packages = existing.packages.toMutableList()
            binding.switchEnabled.isChecked = existing.enabled
            binding.btnDelete.visible()
        } else {
            binding.switchEnabled.visibility = View.VISIBLE
            binding.btnDelete.gone()
        }

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        durationChips.forEach { (chip, minutes) ->
            chip.isChecked = minutes * 60_000L == durationMs
            chip.setOnClickListener {
                durationMs = minutes * 60_000L
                durationChips.forEach { it.first.isChecked = it.second == durationMs }
            }
        }

        dayChips.forEach { (chip, bit) ->
            chip.isChecked = (daysMask shr bit) and 1 == 1
            chip.setOnClickListener {
                daysMask = if (chip.isChecked) {
                    daysMask or (1 shl bit)
                } else {
                    daysMask and (1 shl bit).inv()
                }
            }
        }

        binding.tvTime.setOnClickListener { pickTime() }
        binding.tvApps.setOnClickListener { pickApps() }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete(existing) }

        parentFragmentManager.setFragmentResultListener("schedule_pick", viewLifecycleOwner) { _, bundle ->
            val picked = bundle.getStringArrayList("packages")
            if (picked != null) {
                packages = picked
                renderAppsCount()
            }
        }

        renderTime()
        renderAppsCount()
    }

    private fun renderTime() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minuteOfHour)
        }
        binding.tvTime.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.time)
    }

    private fun renderAppsCount() {
        binding.tvApps.text = if (packages.isEmpty()) {
            getString(R.string.schedule_apps_label)
        } else {
            getString(R.string.schedules_edit_apps_count, packages.size)
        }
    }

    private fun pickTime() {
        TimePickerDialog(requireContext(), { _, h, m ->
            hourOfDay = h
            minuteOfHour = m
            renderTime()
        }, hourOfDay, minuteOfHour, android.text.format.DateFormat.is24HourFormat(requireContext()))
            .show()
    }

    private fun pickApps() {
        SessionDraft.startForSchedule(scheduleId.takeIf { it >= 0 }, null)
        SessionDraft.packages = packages.toMutableList()
        (activity as? MainActivity)?.push(SelectAppsFragment())
    }

    private fun showError(resId: Int) {
        binding.tvError.visible()
        binding.tvError.setText(resId)
    }

    private fun save() {
        val label = binding.etLabel.text?.toString()?.trim().orEmpty()
            .ifEmpty { getString(R.string.schedules_title) }
        if (packages.isEmpty()) {
            showError(R.string.schedule_needs_apps)
            return
        }
        if (daysMask == 0) {
            showError(R.string.schedule_repeats_label)
            return
        }

        runCatching {
            val repo = SessionRepository(requireContext())
            if (scheduleId >= 0) {
                val current = repo.scheduleById(scheduleId)
                    ?: error("Schedule disappeared")
                repo.updateSchedule(
                    current.copy(
                        label = label,
                        daysMask = daysMask,
                        hour = hourOfDay,
                        minute = minuteOfHour,
                        durationMs = durationMs,
                        packages = packages.toList(),
                        enabled = binding.switchEnabled.isChecked
                    )
                )
            } else {
                repo.insertSchedule(
                    Schedule(
                        id = 0L,
                        label = label,
                        daysMask = daysMask,
                        hour = hourOfDay,
                        minute = minuteOfHour,
                        durationMs = durationMs,
                        packages = packages.toList(),
                        enabled = binding.switchEnabled.isChecked,
                        lastRunAt = 0L
                    )
                )
            }
            SessionEngine.rearmSchedules(requireContext())
        }.onFailure {
            requireContext().toast(it.message ?: "Save failed")
            return
        }
        parentFragmentManager.popBackStack()
    }

    private fun confirmDelete(existing: Schedule?) {
        existing ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            requireContext(), R.style.ThemeOverlay_Lockin_Dialog
        )
            .setTitle(R.string.schedule_delete)
            .setMessage(existing.label)
            .setPositiveButton(R.string.schedule_delete) { _, _ ->
                runCatching {
                    SessionRepository(requireContext()).deleteSchedule(existing.id)
                    SessionEngine.rearmSchedules(requireContext())
                }
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "schedule_id"

        fun newInstance(id: Long?): ScheduleEditFragment = ScheduleEditFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, id ?: -1L) }
        }
    }
}
