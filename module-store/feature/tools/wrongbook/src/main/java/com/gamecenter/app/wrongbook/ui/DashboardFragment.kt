package com.gamecenter.app.wrongbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.databinding.FragmentDashboardBinding

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

        topicAdapter = TopicMasteryAdapter()
        binding.recyclerWeakTopics.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWeakTopics.adapter = topicAdapter

        viewModel.questions.observe(viewLifecycleOwner) { list ->
            binding.tvTotalQuestions.text = list.size.toString()
            updateAverageMastery(list.map { it.mastery })
        }

        viewModel.reviews.observe(viewLifecycleOwner) { list ->
            binding.tvTodayReviews.text = list.size.toString()
        }

        viewModel.topicMastery.observe(viewLifecycleOwner) { list ->
            val weak = list.filter { it.mastery < 60 }.sortedBy { it.mastery }
            topicAdapter.submitList(weak)
            binding.tvWeakTopics.text = weak.size.toString()
            binding.tvNoWeakTopics.visibility = if (weak.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerWeakTopics.visibility = if (weak.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadQuestions()
        viewModel.loadReviews()
        viewModel.loadTopicMastery()
    }

    private fun updateAverageMastery(list: List<Int>) {
        val avg = if (list.isEmpty()) 0 else list.sum() / list.size
        binding.tvAverageMastery.text = "$avg%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
