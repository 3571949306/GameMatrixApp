package com.gamecenter.app.wrongbook.ui

import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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
        
        // 延迟初始化以确保 Tab 测量完毕
        binding.tabContainer.post {
            updateSelectedTab(0)
        }

        binding.btnAdd.setOnClickListener {
            CaptureDialogFragment().show(parentFragmentManager, "CaptureDialogFragment")
        }

        // 返回按钮：触发宿主 onBackPressedDispatcher，与系统返回键/边缘滑动走同一回调
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 搜索入口
        binding.btnSearch.setOnClickListener {
            val searchDialog = SearchFragment()
            searchDialog.show(parentFragmentManager, "SearchFragment")
        }

        // 科目管理入口
        binding.btnSubjectManage.setOnClickListener {
            val subjectDialog = SubjectManagementFragment()
            subjectDialog.show(parentFragmentManager, "SubjectManagementFragment")
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.selectTabEvent.observe(viewLifecycleOwner) { position ->
            position?.let {
                binding.viewPager.currentItem = it
                viewModel.clearSelectTabEvent()
            }
        }
    }

    private fun updateSelectedTab(position: Int) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == position
            tab.setBackgroundResource(
                if (selected) R.drawable.wrongbook_tab_selected else R.drawable.wrongbook_tab_unselected
            )
            tab.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.wrongbook_tab_selected_text else R.color.wrongbook_tab_unselected_text
                )
            )
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        animateIndicator(position)
    }

    private fun animateIndicator(position: Int) {
        val targetTab = tabs[position]
        binding.tabIndicator.post {
            if (_binding == null) return@post
            val width = targetTab.width
            val x = targetTab.left + binding.tabContainer.left

            val indicatorWidth = binding.tabIndicator.width
            val targetX = x + (width - indicatorWidth) / 2

            binding.tabIndicator.animate()
                .translationX(targetX.toFloat())
                .setDuration(200)
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
