package com.rrrtx.lockin.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.rrrtx.lockin.R
import com.rrrtx.lockin.core.AccessibilityStatus
import com.rrrtx.lockin.databinding.FragmentOnboardingBinding
import com.rrrtx.lockin.ui.MainActivity
import com.rrrtx.lockin.ui.UiStatus
import com.rrrtx.lockin.ui.gone
import com.rrrtx.lockin.ui.renderStatus
import com.rrrtx.lockin.ui.visible

/**
 * Three-step introduction + permission wizard. Steps are one layout with dynamic
 * visibility — no viewpager dependency, back button just steps backwards.
 */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private var step = 0

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            renderPermissionDots()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnNext.setOnClickListener {
            if (step < LAST_STEP) {
                step++
                render()
            } else {
                (activity as? MainActivity)?.completeOnboarding()
            }
        }
        binding.btnSkip.setOnClickListener {
            (activity as? MainActivity)?.completeOnboarding()
        }
        binding.btnPerm1.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnPerm2.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        renderPermissionDots()
    }

    private fun render() {
        binding.tvStep.text = getString(R.string.onboarding_step, step + 1, LAST_STEP + 1)
        when (step) {
            0 -> {
                binding.tvTitle.setText(R.string.onboarding_page1_title)
                binding.tvBody.setText(R.string.onboarding_page1_body)
                binding.boxPermissions.gone()
                binding.btnNext.setText(R.string.action_continue)
            }
            1 -> {
                binding.tvTitle.setText(R.string.onboarding_page2_title)
                binding.tvBody.setText(R.string.onboarding_page2_body)
                binding.boxPermissions.gone()
                binding.btnNext.setText(R.string.action_continue)
            }
            2 -> {
                binding.tvTitle.setText(R.string.onboarding_page3_title)
                binding.tvBody.setText(R.string.onboarding_page3_body)
                binding.boxPermissions.visible()
                binding.btnNext.setText(R.string.onboarding_done)
                renderPermissionDots()
            }
        }
    }

    private fun renderPermissionDots() {
        val ctx = context ?: return
        val a11yOn = AccessibilityStatus.isEnabled(ctx)
        binding.dotPerm1.renderStatus(if (a11yOn) UiStatus.ON else UiStatus.OFF)
        binding.btnPerm1.text = getString(
            if (a11yOn) R.string.status_on else R.string.onboarding_enable_accessibility
        )

        val notifOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        binding.dotPerm2.renderStatus(if (notifOn) UiStatus.ON else UiStatus.OFF)
        binding.btnPerm2.text = getString(
            if (notifOn) R.string.status_on else R.string.onboarding_enable_notifications
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val LAST_STEP = 2
    }
}
