package com.gamecenter.app.wrongbook.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.MasteryLevel
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.databinding.FragmentDashboardBinding
import java.util.*

/**
 * 掌握度看板 Fragment。
 */
class DashboardFragment : BaseWrongBookFragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private lateinit var topicAdapter: TopicMasteryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentDashboardBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        topicAdapter = TopicMasteryAdapter()
        binding.recyclerWeakTopics.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWeakTopics.adapter = topicAdapter
    }

    private fun setupListeners() {
        binding.btnEmptyCta.setOnClickListener {
            CaptureDialogFragment().show(parentFragmentManager, "CaptureDialogFragment")
        }
    }

    private fun setupObservers() {
        viewModel.questions.observe(viewLifecycleOwner) { list ->
            // 空态隐藏
            if (list.isNullOrEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.dashboardContent.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.dashboardContent.visibility = View.VISIBLE
                
                binding.tvTotalQuestions.text = list.size.toString()
                updateAverageMastery(list.map { it.mastery })
                updateCharts(list)
            }
        }

        viewModel.reviews.observe(viewLifecycleOwner) { list ->
            binding.tvTodayReviews.text = list.size.toString()
        }

        viewModel.topicMastery.observe(viewLifecycleOwner) { list ->
            // 薄弱知识点：统一使用 MasteryLevel.REVIEWING_THRESHOLD（50）作为分界线，
            // 与列表页颜色分级一致（之前用 60 导致同一道题在列表页"中"色但看板页不算薄弱）。
            val weak = list.filter { MasteryLevel.isWeak(it.mastery) }.sortedBy { it.mastery }
            topicAdapter.submitList(weak)
            binding.tvWeakTopics.text = weak.size.toString()
            binding.tvNoWeakTopics.visibility = if (weak.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerWeakTopics.visibility = if (weak.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun updateAverageMastery(list: List<Int>) {
        val avg = if (list.isEmpty()) 0 else list.sum() / list.size
        binding.tvAverageMastery.text = "$avg%"
    }

    private fun updateCharts(questionsList: List<QuestionEntity>) {
        // 1. 7天复习趋势 (统计过去7天内每天创建的错题数)
        val trendData = IntArray(7)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)

        val dayInMillis = 24 * 60 * 60 * 1000L
        val todayMax = cal.timeInMillis

        for (i in 0 until 7) {
            val dayStart = todayMax - (i + 1) * dayInMillis
            val dayEnd = todayMax - i * dayInMillis
            val count = questionsList.count { it.createdAt in (dayStart + 1)..dayEnd }
            trendData[6 - i] = count
        }
        binding.trendChartView.setWeeklyData(trendData)

        // 2. 科目分布比例
        val subjectMap = questionsList.groupBy { it.subject }.mapValues { it.value.size }
        binding.pieChartView.setData(subjectMap)

        // 3. 掌握度级别分布（四档细分，仅图表使用；状态文案与颜色统一走 MasteryLevel）
        val masteryDist = IntArray(4)
        questionsList.forEach { q ->
            masteryDist[MasteryLevel.chartDistributionIndex(q.mastery)]++
        }
        binding.masteryChartView.setDistribution(masteryDist)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadQuestions()
        viewModel.loadReviews()
        viewModel.loadTopicMastery()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
