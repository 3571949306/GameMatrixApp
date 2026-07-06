package com.gamecenter.app.wrongbook.ui

import android.content.Intent
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.widget.TextView
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.databinding.FragmentWrongbookBinding

/**
 * 错题本主 Fragment。
 */
class WrongBookFragment : BaseWrongBookFragment() {

    private var _binding: FragmentWrongbookBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private val tabs: List<TextView>
        get() = listOf(binding.tabQuestions, binding.tabDashboard, binding.tabReview, binding.tabSettings)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentWrongbookBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> QuestionsFragment()
                1 -> DashboardFragment()
                2 -> ReviewFragment()
                else -> SettingsFragment()
            }
        }

        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                binding.viewPager.currentItem = index
            }
        }
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateSelectedTab(position)
            }
        })
        updateSelectedTab(0)

        binding.btnAdd.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), CaptureActivity::class.java))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    moduleResources.getString(R.string.wrongbook_add_question),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateSelectedTab(position: Int) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == position
            tab.setBackgroundResource(
                if (selected) R.drawable.wrongbook_tab_selected else R.drawable.wrongbook_tab_unselected
            )
            tab.setTextColor(if (selected) 0xFF6750A4.toInt() else 0xFF49454F.toInt())
        }
    }

    private fun showSubjectFilterDialog() {
        val subjects = viewModel.subjects.value ?: emptyList()
        val items = mutableListOf(moduleResources.getString(R.string.wrongbook_subject_all))
        items.addAll(subjects.map { it.name })
        var selected = 0
        AlertDialog.Builder(requireContext())
            .setTitle(moduleResources.getString(R.string.wrongbook_subject_filter))
            .setSingleChoiceItems(items.toTypedArray(), selected) { _, which ->
                selected = which
            }
            .setPositiveButton(moduleResources.getString(R.string.wrongbook_confirm)) { _, _ ->
                viewModel.setSubjectFilter(if (selected == 0) null else items[selected])
            }
            .setNegativeButton(moduleResources.getString(R.string.wrongbook_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
