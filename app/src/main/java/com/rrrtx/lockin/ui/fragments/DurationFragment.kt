package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.DurationRules
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentDurationBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.toast
import com.rrrtx.lockin.ui.visible
import java.util.Locale

class DurationFragment : Fragment() {

    private var _binding: FragmentDurationBinding? = null
    private val binding get() = _binding!!
    private var customMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val presetChips = listOf(
            binding.chip15 to 15L,
            binding.chip25 to 25L,
            binding.chip30 to 30L,
            binding.chip50 to 50L,
            binding.chip60 to 60L,
            binding.chip120 to 120L,
            binding.chip240 to 240L,
            binding.chip480 to 480L,
            binding.chip1440 to 1440L
        )

        presetChips.forEach { (chip, minutes) ->
            chip.setOnClickListener {
                customMode = false
                binding.boxCustom.gone()
                binding.tvCustomError.gone()
                SessionDraft.durationMs = DurationRules.minutesToMs(minutes)
                refreshPresetChecks(presetChips)
                updateEndPreview()
            }
        }

        binding.chipCustom.setOnClickListener {
            customMode = true
            binding.boxCustom.visible()
            refreshPresetChecks(presetChips)
            applyCustomFromInputs()
            updateEndPreview()
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (customMode) applyCustomFromInputs()
            }
        }
        binding.etHours.addTextChangedListener(textWatcher)
        binding.etMinutes.addTextChangedListener(textWatcher)

        binding.btnContinue.setOnClickListener {
            if (!DurationRules.isValid(SessionDraft.durationMs)) {
                requireContext().toast(
                    getString(
                        R.string.duration_max_note,
                        DurationRules.formatShort(DurationRules.MAX_MS)
                    )
                )
                return@setOnClickListener
            }
            (activity as? MainActivity)?.push(ConfirmFragment())
        }

        // Restore previous choice if coming back.
        val restoredMinutes = SessionDraft.durationMs / 60_000L
        val preset = presetChips.firstOrNull { it.second == restoredMinutes }
        if (preset != null) {
            preset.first.isChecked = true
            SessionDraft.durationMs = DurationRules.minutesToMs(preset.second)
        } else if (DurationRules.isValid(SessionDraft.durationMs)) {
            customMode = true
            binding.boxCustom.visible()
            binding.chipCustom.isChecked = true
            binding.etHours.setText((restoredMinutes / 60).toString())
            binding.etMinutes.setText((restoredMinutes % 60).toString())
        } else {
            binding.chip25.isChecked = true
            SessionDraft.durationMs = DurationRules.minutesToMs(25)
        }

        binding.tvMaxNote.text =
            getString(R.string.duration_max_note, DurationRules.formatShort(DurationRules.MAX_MS))
        updateEndPreview()
    }

    private fun refreshPresetChecks(presetChips: List<Pair<Chip, Long>>) {
        if (!customMode) return
        presetChips.forEach { it.first.isChecked = false }
    }

    private fun applyCustomFromInputs() {
        val hours = binding.etHours.text?.toString()?.toLongOrNull() ?: 0L
        val minutes = binding.etMinutes.text?.toString()?.toLongOrNull() ?: 0L
        val totalMs = hours * 3_600_000L + minutes * 60_000L
        if (minutes >= 60) {
            binding.tvCustomError.visible()
            binding.tvCustomError.setText(R.string.duration_custom_minutes)
            return
        }
        if (!DurationRules.isValid(totalMs)) {
            SessionDraft.durationMs = 0L
            if (totalMs > 0) {
                binding.tvCustomError.visible()
                binding.tvCustomError.text = getString(
                    R.string.duration_max_note,
                    DurationRules.formatShort(DurationRules.MAX_MS)
                )
            } else {
                binding.tvCustomError.gone()
            }
            return
        }
        binding.tvCustomError.gone()
        SessionDraft.durationMs = totalMs
    }

    private fun updateEndPreview() {
        val valid = DurationRules.isValid(SessionDraft.durationMs)
        binding.tvEndsAt.isEnabled = valid
        if (!valid) {
            binding.tvEndsAt.text = getString(R.string.time_undefined)
            return
        }
        val end = DurationRules.expectedEnd(System.currentTimeMillis(), SessionDraft.durationMs)
        binding.tvEndsAt.text = getString(
            R.string.duration_ends_at,
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.FULL,
                java.text.DateFormat.SHORT
            ).format(java.util.Date(end))
        )
        val unitPreview = String.format(
            Locale.getDefault(),
            "%s",
            DurationRules.formatShort(SessionDraft.durationMs)
        )
        binding.tvEndsAt.contentDescription = unitPreview
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
