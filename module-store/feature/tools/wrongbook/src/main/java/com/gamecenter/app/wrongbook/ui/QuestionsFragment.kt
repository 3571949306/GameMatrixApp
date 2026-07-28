package com.gamecenter.app.wrongbook.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.databinding.FragmentQuestionsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

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

        setupRecyclerView()
        setupObservers()
        setupBatchActions()
        setupEmptyCta()
    }

    private fun setupRecyclerView() {
        adapter = QuestionAdapter(
            onItemClick = { question ->
                // 点击查看详情
                val detailDialog = QuestionDetailDialogFragment.newInstance(question.id)
                detailDialog.show(parentFragmentManager, "QuestionDetailDialog")
            },
            onItemLongClick = { question ->
                // 长按进入批量选择模式
                if (!adapter.isMultiSelectMode) {
                    enterBatchMode()
                    adapter.toggleSelection(question.id)
                    updateBatchUi()
                }
                true
            },
            onFavoriteClick = { question ->
                // 快速切换收藏
                val updated = question.copy(isFavorite = !question.isFavorite)
                viewModel.updateQuestionDetails(updated)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.questions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            
            // 空态处理
            if (list.isNullOrEmpty() && viewModel.isLoading.value != true) {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }

            if (adapter.isMultiSelectMode) {
                // 如果在批量模式中，且列表更新了（例如删除了），需要同步刷新 UI
                updateBatchUi()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading == true) {
                binding.shimmerView.shimmerContainer.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
                startShimmerAnimation(binding.shimmerView.shimmerContainer)
            } else {
                binding.shimmerView.shimmerContainer.visibility = View.GONE
                binding.recyclerView.visibility = if (viewModel.questions.value.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.emptyView.visibility = if (viewModel.questions.value.isNullOrEmpty()) View.VISIBLE else View.GONE
                stopShimmerAnimation(binding.shimmerView.shimmerContainer)
            }
        }
    }

    private fun setupEmptyCta() {
        binding.btnEmptyCta.setOnClickListener {
            CaptureDialogFragment().show(parentFragmentManager, "CaptureDialogFragment")
        }
    }

    // ===== 批量操作逻辑 =====
    private fun setupBatchActions() {
        binding.btnExitBatch.setOnClickListener {
            exitBatchMode()
        }

        binding.cbSelectAll.setOnClickListener {
            val allQuestions = viewModel.questions.value ?: emptyList()
            if (binding.cbSelectAll.isChecked) {
                adapter.selectAll(allQuestions.map { it.id })
            } else {
                adapter.clearSelection()
            }
            updateBatchUi()
        }

        binding.btnBatchDelete.setOnClickListener {
            val selectedCount = adapter.selectedIds.size
            if (selectedCount == 0) return@setOnClickListener

            AlertDialog.Builder(requireContext())
                .setTitle(moduleResources.getString(R.string.wrongbook_delete))
                .setMessage(moduleResources.getString(R.string.wrongbook_batch_delete_confirm, selectedCount))
                .setPositiveButton(moduleResources.getString(R.string.wrongbook_confirm)) { _, _ ->
                    val idsToDelete = adapter.selectedIds.toList()
                    
                    // 支持删除撤销 (Snackbar + Undo)
                    // 先保存要删除的数据副本
                    val questionsToBackup = viewModel.questions.value?.filter { idsToDelete.contains(it.id) } ?: emptyList()
                    
                    viewModel.deleteQuestions(idsToDelete)
                    exitBatchMode()

                    Snackbar.make(binding.root, moduleResources.getString(R.string.wrongbook_question_deleted), Snackbar.LENGTH_LONG)
                        .setAction(moduleResources.getString(R.string.wrongbook_undo)) {
                            // 撤销删除，将备份的错题重新存回
                            viewLifecycleOwner.lifecycleScope.launch {
                                questionsToBackup.forEach { backup ->
                                    viewModel.saveQuestion(
                                        rawText = backup.rawText,
                                        analysisResult = com.gamecenter.app.wrongbook.analysis.AnalysisResult(
                                            success = true,
                                            subject = backup.subject,
                                            difficulty = backup.difficulty,
                                            knowledgePoints = parseJsonList(backup.knowledgePoints),
                                            analysis = backup.analysis
                                        ),
                                        imagePath = backup.imagePath,
                                        isFavorite = backup.isFavorite,
                                        tags = backup.tags
                                    )
                                }
                                Snackbar.make(binding.root, moduleResources.getString(R.string.wrongbook_undo_success), Snackbar.LENGTH_SHORT).show()
                            }
                        }.show()
                }
                .setNegativeButton(moduleResources.getString(R.string.wrongbook_cancel), null)
                .show()
        }

        binding.btnBatchMove.setOnClickListener {
            val selectedCount = adapter.selectedIds.size
            if (selectedCount == 0) return@setOnClickListener

            val subjects = viewModel.subjects.value ?: emptyList()
            if (subjects.isEmpty()) {
                Toast.makeText(requireContext(), moduleResources.getString(R.string.wrongbook_no_subject), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val subjectNames = subjects.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(moduleResources.getString(R.string.wrongbook_batch_move_subject))
                .setItems(subjectNames) { _, which ->
                    val targetSubject = subjectNames[which]
                    viewModel.batchUpdateSubject(adapter.selectedIds.toList(), targetSubject)
                    exitBatchMode()
                }
                .setNegativeButton(moduleResources.getString(R.string.wrongbook_cancel), null)
                .show()
        }

        binding.btnBatchFavorite.setOnClickListener {
            val selectedCount = adapter.selectedIds.size
            if (selectedCount == 0) return@setOnClickListener

            val ids = adapter.selectedIds.toList()
            // 如果大部分选中的已经收藏，则执行批量取消收藏，否则批量收藏
            val currentList = viewModel.questions.value ?: emptyList()
            val selectedQuestions = currentList.filter { ids.contains(it.id) }
            val favoriteCount = selectedQuestions.count { it.isFavorite }
            val batchFav = favoriteCount < selectedQuestions.size / 2.0 + 0.5 // 收藏较少，则批量收藏

            viewModel.batchUpdateFavorite(ids, batchFav)
            exitBatchMode()
        }
    }

    private fun parseJsonList(json: String): List<String> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { array.optString(it, "") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun enterBatchMode() {
        adapter.isMultiSelectMode = true
        binding.layoutBatchHeader.visibility = View.VISIBLE
        binding.layoutBatchActions.visibility = View.VISIBLE
        binding.cbSelectAll.isChecked = false
    }

    private fun exitBatchMode() {
        adapter.isMultiSelectMode = false
        adapter.clearSelection()
        binding.layoutBatchHeader.visibility = View.GONE
        binding.layoutBatchActions.visibility = View.GONE
    }

    private fun updateBatchUi() {
        val totalCount = viewModel.questions.value?.size ?: 0
        val selectedCount = adapter.selectedIds.size
        binding.tvSelectedCount.text = moduleResources.getString(R.string.wrongbook_batch_selected_count, selectedCount)
        binding.cbSelectAll.isChecked = selectedCount == totalCount && totalCount > 0
    }

    // ===== 骨架屏脉冲动画 =====
    private fun startShimmerAnimation(view: View) {
        view.alpha = 0.4f
        view.animate()
            .alpha(1.0f)
            .setDuration(800)
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (view.visibility == View.VISIBLE) {
                        view.animate()
                            .alpha(0.4f)
                            .setDuration(800)
                            .setListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    if (view.visibility == View.VISIBLE) {
                                        startShimmerAnimation(view)
                                    }
                                }
                            }).start()
                    }
                }
            }).start()
    }

    private fun stopShimmerAnimation(view: View) {
        view.animate().cancel()
        view.alpha = 1.0f
    }

    override fun onResume() {
        super.onResume()
        if (adapter.isMultiSelectMode) {
            exitBatchMode()
        }
        viewModel.loadQuestions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopShimmerAnimation(binding.shimmerView.shimmerContainer)
        _binding = null
    }
}
