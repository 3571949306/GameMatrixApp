package com.gamecenter.app.wrongbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.databinding.FragmentReviewBinding

/**
 * 复习计划 Fragment。
 */
class ReviewFragment : BaseWrongBookFragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private lateinit var adapter: ReviewAdapter

    private var totalReviewsCount = 0
    private var completedReviewsCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentReviewBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = ReviewAdapter(
            onComplete = { item ->
                completedReviewsCount++
                viewModel.completeReview(item.plan)
                updateProgressUi()
            },
            onSkip = { item ->
                // 跳过：从当前列表中临时隐藏该项
                val currentList = adapter.currentList.toMutableList()
                currentList.remove(item)
                adapter.submitList(currentList)
                
                // 如果跳过导致全部列表为空，则也判定完成复习或显示空态
                if (currentList.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.layoutReviewProgress.visibility = View.GONE
                    checkCelebration()
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        // 空态下的 CTA 点击：切换到“错题”Tab
        binding.btnEmptyCta.setOnClickListener {
            viewModel.selectTab(0)
        }
    }

    private fun setupObservers() {
        viewModel.reviewItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            
            val remaining = items.size
            if (remaining > 0) {
                binding.emptyView.visibility = View.GONE
                binding.layoutReviewProgress.visibility = View.VISIBLE
                
                // 重设今日总任务数
                if (totalReviewsCount == 0 || remaining > totalReviewsCount) {
                    totalReviewsCount = remaining + completedReviewsCount
                }
                updateProgressUi()
            } else {
                binding.emptyView.visibility = View.VISIBLE
                binding.layoutReviewProgress.visibility = View.GONE
                checkCelebration()
            }
        }
    }

    private fun updateProgressUi() {
        val remaining = adapter.currentList.size
        val total = totalReviewsCount.coerceAtLeast(remaining + completedReviewsCount)
        
        binding.pbReviewProgress.max = total
        binding.pbReviewProgress.progress = completedReviewsCount
        binding.tvReviewProgressPercent.text = "$completedReviewsCount/$total"
    }

    private fun checkCelebration() {
        // 如果今天完成了所有复习，则展示庆祝横幅与纸屑粒子特效（Stage 3 需求）
        if (completedReviewsCount > 0 && adapter.currentList.isEmpty()) {
            binding.celebrationView.visibility = View.VISIBLE
            binding.celebrationView.startConfetti()

            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                moduleResources.getString(R.string.wrongbook_review_celebration),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show()
            completedReviewsCount = 0
            totalReviewsCount = 0
        }
    }

    override fun onResume() {
        super.onResume()
        completedReviewsCount = 0
        totalReviewsCount = 0
        viewModel.loadReviews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
