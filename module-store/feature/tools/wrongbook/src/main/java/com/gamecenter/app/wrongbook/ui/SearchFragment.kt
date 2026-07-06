package com.gamecenter.app.wrongbook.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.databinding.FragmentSearchBinding

class SearchFragment : DialogFragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private lateinit var adapter: QuestionAdapter

    private var currentSubject: String? = null
    private var currentSort = SortType.TIME_DESC
    private var onlyFavorites = false

    private val moduleResources: android.content.res.Resources
        get() = com.gamecenter.app.modules.ModuleManager.getModuleResources(ModuleContextHelper.MODULE_ID)?.resources
            ?: super.getResources()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_GameMatrixApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentSearchBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        setupObservers()

        performSearch()
    }

    private fun setupRecyclerView() {
        adapter = QuestionAdapter(
            onItemClick = { question ->
                val detailDialog = QuestionDetailDialogFragment.newInstance(question.id)
                detailDialog.show(parentFragmentManager, "QuestionDetailDialog")
            },
            onFavoriteClick = { question ->
                val updated = question.copy(isFavorite = !question.isFavorite)
                viewModel.updateQuestionDetails(updated)
            }
        )
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            dismiss()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text = null
            performSearch()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                performSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnFilterSubject.setOnClickListener {
            showSubjectChooser()
        }

        binding.btnFilterSort.setOnClickListener {
            showSortChooser()
        }

        binding.btnFilterFavorite.setOnClickListener {
            onlyFavorites = !onlyFavorites
            updateFavoriteChipUi()
            performSearch()
        }
    }

    private fun setupObservers() {
        viewModel.questions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyResultView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text?.toString()?.trim() ?: ""
        viewModel.setSearchQuery(query)
        viewModel.setSubjectFilter(currentSubject)
        viewModel.setSortType(currentSort)
        viewModel.setFavoriteFilter(onlyFavorites)
    }

    private fun showSubjectChooser() {
        val subjects = viewModel.subjects.value ?: emptyList()
        val names = mutableListOf("全部科目")
        names.addAll(subjects.map { it.name })
        
        var selectedIdx = 0
        currentSubject?.let { sub ->
            val idx = names.indexOf(sub)
            if (idx >= 0) selectedIdx = idx
        }

        AlertDialog.Builder(requireContext())
            .setTitle("筛选科目")
            .setSingleChoiceItems(names.toTypedArray(), selectedIdx) { dialog, which ->
                currentSubject = if (which == 0) null else names[which]
                binding.btnFilterSubject.text = currentSubject ?: "全部科目"
                updateChipSelectedState(binding.btnFilterSubject, currentSubject != null)
                performSearch()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSortChooser() {
        val sorts = arrayOf("按时间 (新→旧)", "按时间 (旧→新)", "按难度 (高→低)", "按难度 (低→高)", "按掌握度 (高→低)", "按掌握度 (低→高)")
        val sortTypes = arrayOf(
            SortType.TIME_DESC, SortType.TIME_ASC,
            SortType.DIFFICULTY_DESC, SortType.DIFFICULTY_ASC,
            SortType.MASTERY_DESC, SortType.MASTERY_ASC
        )
        val selectedIdx = sortTypes.indexOf(currentSort).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("排序方式")
            .setSingleChoiceItems(sorts, selectedIdx) { dialog, which ->
                currentSort = sortTypes[which]
                binding.btnFilterSort.text = sorts[which].substringBefore(" (")
                performSearch()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateFavoriteChipUi() {
        updateChipSelectedState(binding.btnFilterFavorite, onlyFavorites)
    }

    private fun updateChipSelectedState(view: TextView, selected: Boolean) {
        if (selected) {
            view.setBackgroundResource(R.drawable.wrongbook_tab_selected)
            view.setTextColor(ContextCompat.getColor(requireContext(), R.color.wrongbook_tab_selected_text))
        } else {
            view.setBackgroundResource(R.drawable.wrongbook_chip_background)
            view.setTextColor(ContextCompat.getColor(requireContext(), R.color.wrongbook_chip_text))
        }
    }

    override fun onDestroyView() {
        viewModel.setSearchQuery("")
        viewModel.setSubjectFilter(null)
        viewModel.setSortType(SortType.TIME_DESC)
        viewModel.setFavoriteFilter(false)
        super.onDestroyView()
        _binding = null
    }
}
