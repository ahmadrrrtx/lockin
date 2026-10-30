package com.rrrtx.lockin.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.SessionEngine
import com.rrrtx.lockin.core.SessionRepository
import com.rrrtx.lockin.data.AppInventory
import com.rrrtx.lockin.data.PickTarget
import com.rrrtx.lockin.data.SessionDraft
import com.rrrtx.lockin.databinding.FragmentSelectAppsBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.adapters.AppsAdapter
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.toast
import com.rrrtx.lockin.ui.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectAppsFragment : Fragment() {

    private var _binding: FragmentSelectAppsBinding? = null
    private val binding get() = _binding!!
    private var adapter: AppsAdapter? = null
    private var inventory: List<AppInventory.AppEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = AppsAdapter { entry -> adapter?.toggle(entry) }
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter

        // Preselect based on the target of this picking session.
        val initial = when (SessionDraft.target) {
            PickTarget.NEW_SESSION -> SessionDraft.packages
            PickTarget.ADD_TO_ACTIVE -> SessionDraft.packages
            PickTarget.FOR_SCHEDULE -> SessionDraft.packages
        }
        adapter?.setSelected(initial)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        binding.btnContinue.setOnClickListener { onContinue() }

        loadInventory()
    }

    private fun loadInventory() {
        binding.progress.visible()
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching { AppInventory(requireContext()).load() }.getOrDefault(emptyList())
            }
            inventory = entries
            binding.progress.gone()
            applyFilter()
            updateFooter()
        }
    }

    private fun applyFilter() {
        val query = binding.etSearch.text?.toString()?.trim()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            inventory
        } else {
            inventory.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        adapter?.submit(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvApps.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        updateFooter()
    }

    private fun updateFooter() {
        val count = adapter?.selectedPackages()?.size ?: 0
        binding.tvCount.text = when {
            count == 1 -> getString(R.string.select_count_one, count)
            else -> getString(R.string.select_count_many, count)
        }
        binding.btnContinue.isEnabled = count > 0
    }

    private fun onContinue() {
        val selected = adapter?.selectedPackages() ?: return
        if (selected.isEmpty()) {
            requireContext().toast(getString(R.string.select_unlocked_hint))
            return
        }
        SessionDraft.packages = selected.toMutableList()

        when (SessionDraft.target) {
            PickTarget.NEW_SESSION -> {
                (activity as? MainActivity)?.push(DurationFragment())
            }

            PickTarget.ADD_TO_ACTIVE -> {
                val added = runCatching {
                    SessionEngine.addPackagesDuringActive(requireContext(), selected)
                }.getOrDefault(0)
                requireContext().toast(getString(R.string.active_added_count, added))
                parentFragmentManager.popBackStack()
            }

            PickTarget.FOR_SCHEDULE -> {
                val bundle = Bundle().apply { putStringArrayList("packages", ArrayList(selected)) }
                parentFragmentManager.setFragmentResult("schedule_pick", bundle)
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
