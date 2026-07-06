package com.gamecenter.app.wrongbook.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.databinding.FragmentQuestionsBinding

/**
 * 错题列表 Fragment。
 */
class QuestionsFragment : BaseWrongBookFragment() {

    private var _binding: FragmentQuestionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private lateinit var adapter: QuestionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentQuestionsBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QuestionAdapter { question ->
            AlertDialog.Builder(requireContext())
                .setTitle(moduleResources.getString(R.string.wrongbook_delete_confirm))
                .setPositiveButton(moduleResources.getString(R.string.wrongbook_confirm)) { _, _ ->
                    viewModel.deleteQuestion(question)
                }
                .setNegativeButton(moduleResources.getString(R.string.wrongbook_cancel), null)
                .show()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.questions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadQuestions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
