package com.gamecenter.app.wrongbook.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.data.QuestionEntity
import com.gamecenter.app.wrongbook.databinding.FragmentQuestionDetailBinding
import org.json.JSONArray
import java.io.File

class QuestionDetailDialogFragment : DialogFragment() {

    private var _binding: FragmentQuestionDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()
    private var questionId: Long = 0
    private var currentQuestion: QuestionEntity? = null
    private var isEditMode = false

    private var selectedSubject: String = ""
    private var selectedDifficulty: Int = 3

    private val moduleResources: android.content.res.Resources
        get() = com.gamecenter.app.modules.ModuleManager.getModuleResources(ModuleContextHelper.MODULE_ID)?.resources
            ?: super.getResources()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_GameMatrixApp)
        questionId = arguments?.getLong(ARG_QUESTION_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentQuestionDetailBinding.inflate(moduleInflater, container, false)
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

        setupListeners()
        loadQuestionData()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            dismiss()
        }

        binding.btnEditSave.setOnClickListener {
            if (isEditMode) {
                saveChanges()
            } else {
                enterEditMode()
            }
        }

        // 收藏星号按钮
        binding.btnFavorite.setOnClickListener {
            currentQuestion?.let { q ->
                val updated = q.copy(isFavorite = !q.isFavorite)
                viewModel.updateQuestionDetails(updated)
                currentQuestion = updated
                updateFavoriteIcon(updated.isFavorite)
            }
        }

        // 难度选择监听 (在编辑模式下生效)
        setupStarsClickListeners()

        // 掌握度滑动
        binding.sbMastery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvProgressPercent.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 图片全屏放大
        binding.ivQuestionImage.setOnClickListener {
            currentQuestion?.let { q ->
                if (q.imagePath.isNotBlank() && File(q.imagePath).exists()) {
                    val zoomDialog = FullscreenImageDialogFragment.newInstance(q.imagePath)
                    zoomDialog.show(parentFragmentManager, "FullscreenImage")
                }
            }
        }

        // 选择科目
        binding.btnChooseSubject.setOnClickListener {
            showSubjectChooser()
        }
    }

    private fun loadQuestionData() {
        // 使用协程或观察 LiveData，在这里我们能直接从 ViewModel 里的错题缓存查找
        val question = viewModel.questions.value?.find { it.id == questionId }
        if (question == null) {
            Toast.makeText(requireContext(), "未找到错题数据", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        currentQuestion = question
        selectedSubject = question.subject
        selectedDifficulty = question.difficulty

        displayQuestion(question)
    }

    private fun displayQuestion(q: QuestionEntity) {
        // 标题与收藏
        binding.tvTitle.text = moduleResources.getString(R.string.wrongbook_detail_title)
        updateFavoriteIcon(q.isFavorite)

        // 科目
        binding.tvSubject.text = q.subject
        binding.btnChooseSubject.text = q.subject

        // 图片
        if (q.imagePath.isNotBlank() && File(q.imagePath).exists()) {
            binding.layoutImageArea.visibility = View.VISIBLE
            binding.ivQuestionImage.setImageURI(Uri.fromFile(File(q.imagePath)))
        } else {
            binding.layoutImageArea.visibility = View.GONE
        }

        // 题目内容
        binding.tvQuestionContent.text = q.rawText
        binding.etQuestionContent.setText(q.rawText)

        // 难度
        updateStarsDisplay(q.difficulty)

        // 掌握度
        binding.tvProgressPercent.text = "${q.mastery}%"
        binding.sbMastery.progress = q.mastery

        // 解析
        binding.tvAnalysis.text = q.analysis
        binding.etAnalysis.setText(q.analysis)

        // 标签 Chip
        val topics = parseKnowledgePoints(q.knowledgePoints) +
                     q.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        binding.tagsContainer.removeAllViews()
        topics.distinct().forEach { topic ->
            val tv = TextView(requireContext()).apply {
                text = topic
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.wrongbook_chip_text))
                setBackgroundResource(R.drawable.wrongbook_chip_background)
                val pxPaddingHorizontal = (8 * resources.displayMetrics.density).toInt()
                val pxPaddingVertical = (4 * resources.displayMetrics.density).toInt()
                setPadding(pxPaddingHorizontal, pxPaddingVertical, pxPaddingHorizontal, pxPaddingVertical)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
                }
            }
            binding.tagsContainer.addView(tv)
        }

        // 标签编辑输入框默认填入
        binding.etTagsInput.setText(q.tags)

        // AI 解析增强字段
        displayAiExtra(q)
    }

    /**
     * 展示 AI 解析返回的题型/选项/答案/错因/复习建议/置信度。
     * 字段全为空时隐藏整块区域。
     */
    private fun displayAiExtra(q: QuestionEntity) {
        val hasAny = q.questionType.isNotBlank() && q.questionType != "unknown" ||
                     q.optionsJson != "[]" ||
                     q.answer.isNotBlank() ||
                     q.wrongReason.isNotBlank() ||
                     q.reviewSuggestion.isNotBlank() ||
                     q.confidence > 0

        if (!hasAny) {
            binding.layoutAiExtra.visibility = View.GONE
            return
        }
        binding.layoutAiExtra.visibility = View.VISIBLE

        // 题型
        if (q.questionType.isNotBlank() && q.questionType != "unknown") {
            binding.tvDetailQuestionType.visibility = View.VISIBLE
            binding.tvDetailQuestionType.text = moduleResources.getString(
                R.string.wrongbook_question_type_format, mapQuestionType(q.questionType)
            )
        } else {
            binding.tvDetailQuestionType.visibility = View.GONE
        }

        // 选项
        val options = parseStringList(q.optionsJson)
        if (options.isNotEmpty()) {
            // 显示"选项"label
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailOptions) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.VISIBLE
                    child.visibility = View.VISIBLE
                    (child as TextView).text = options.joinToString("\n")
                    break
                }
            }
        } else {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailOptions) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.GONE
                    child.visibility = View.GONE
                    break
                }
            }
        }

        // 答案
        if (q.answer.isNotBlank()) {
            // 显示"答案"label
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailAnswer) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.VISIBLE
                    child.visibility = View.VISIBLE
                    (child as TextView).text = q.answer
                    break
                }
            }
        } else {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailAnswer) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.GONE
                    child.visibility = View.GONE
                    break
                }
            }
        }

        // 易错原因
        if (q.wrongReason.isNotBlank()) {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailWrongReason) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.VISIBLE
                    child.visibility = View.VISIBLE
                    (child as TextView).text = q.wrongReason
                    break
                }
            }
        } else {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailWrongReason) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.GONE
                    child.visibility = View.GONE
                    break
                }
            }
        }

        // 复习建议
        if (q.reviewSuggestion.isNotBlank()) {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailReviewSuggestion) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.VISIBLE
                    child.visibility = View.VISIBLE
                    (child as TextView).text = q.reviewSuggestion
                    break
                }
            }
        } else {
            for (i in 0 until binding.layoutAiExtra.childCount) {
                val child = binding.layoutAiExtra.getChildAt(i)
                if (child.id == R.id.tvDetailReviewSuggestion) {
                    val label = binding.layoutAiExtra.getChildAt(i - 1)
                    label.visibility = View.GONE
                    child.visibility = View.GONE
                    break
                }
            }
        }

        // 置信度
        if (q.confidence > 0) {
            binding.tvDetailConfidence.visibility = View.VISIBLE
            binding.tvDetailConfidence.text = moduleResources.getString(
                R.string.wrongbook_confidence_format, q.confidence * 100
            )
        } else {
            binding.tvDetailConfidence.visibility = View.GONE
        }
    }

    /** 将后端题型枚举映射为本地化文案 */
    private fun mapQuestionType(type: String): String {
        return when (type) {
            "single_choice" -> "单选题"
            "multiple_choice" -> "多选题"
            "judge" -> "判断题"
            "fill_blank" -> "填空题"
            "short_answer" -> "简答题"
            else -> type
        }
    }

    /** 解析 JSON 数组字符串为 List<String> */
    private fun parseStringList(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun enterEditMode() {
        isEditMode = true
        binding.tvTitle.text = moduleResources.getString(R.string.wrongbook_edit_title)
        binding.btnEditSave.text = "保存"

        // 切换 View 状态
        binding.tvSubject.visibility = View.GONE
        binding.btnChooseSubject.visibility = View.VISIBLE

        binding.tvQuestionContent.visibility = View.GONE
        binding.etQuestionContent.visibility = View.VISIBLE

        binding.tagsContainer.visibility = View.GONE
        binding.etTagsInput.visibility = View.VISIBLE

        binding.tvAnalysis.visibility = View.GONE
        binding.etAnalysis.visibility = View.VISIBLE

        // 开启进度滑动条触控
        binding.sbMastery.isEnabled = true
    }

    private fun saveChanges() {
        val q = currentQuestion ?: return
        val rawText = binding.etQuestionContent.text.toString().trim()
        val analysis = binding.etAnalysis.text.toString().trim()
        val tags = binding.etTagsInput.text.toString().trim()
        val mastery = binding.sbMastery.progress

        if (rawText.isBlank()) {
            Toast.makeText(requireContext(), "题目内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = q.copy(
            rawText = rawText,
            subject = selectedSubject,
            difficulty = selectedDifficulty,
            analysis = analysis,
            tags = tags,
            mastery = mastery,
            updatedAt = System.currentTimeMillis()
        )

        viewModel.updateQuestionDetails(updated)
        currentQuestion = updated

        // 退出编辑模式
        isEditMode = false
        binding.btnEditSave.text = "编辑"
        binding.tvSubject.visibility = View.VISIBLE
        binding.btnChooseSubject.visibility = View.GONE

        binding.tvQuestionContent.visibility = View.VISIBLE
        binding.etQuestionContent.visibility = View.GONE

        binding.tagsContainer.visibility = View.VISIBLE
        binding.etTagsInput.visibility = View.GONE

        binding.tvAnalysis.visibility = View.VISIBLE
        binding.etAnalysis.visibility = View.GONE

        binding.sbMastery.isEnabled = false

        // 重新显示
        displayQuestion(updated)
        Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
    }

    private fun updateFavoriteIcon(fav: Boolean) {
        binding.btnFavorite.setImageResource(
            if (fav) R.drawable.wrongbook_star_filled else R.drawable.wrongbook_star_empty
        )
    }

    private fun updateStarsDisplay(difficulty: Int) {
        binding.layoutStars.removeAllViews()
        for (i in 1..5) {
            val star = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (24 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(0, 0, (6 * resources.displayMetrics.density).toInt(), 0)
                }
                setImageResource(if (i <= difficulty) R.drawable.wrongbook_star_filled else R.drawable.wrongbook_star_empty)
                
                // 只有在编辑模式下允许点击修改星级
                setOnClickListener {
                    if (isEditMode) {
                        selectedDifficulty = i
                        updateStarsDisplay(i)
                    }
                }
            }
            binding.layoutStars.addView(star)
        }
    }

    private fun setupStarsClickListeners() {
        // 重写在 updateStarsDisplay 中绑定了点击逻辑，此处直接复用
        binding.sbMastery.isEnabled = false
    }

    private fun showSubjectChooser() {
        val subjects = viewModel.subjects.value ?: emptyList()
        val names = subjects.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("修改科目")
            .setItems(names) { _, which ->
                selectedSubject = names[which]
                binding.btnChooseSubject.text = selectedSubject
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseKnowledgePoints(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_QUESTION_ID = "question_id"

        fun newInstance(questionId: Long): QuestionDetailDialogFragment {
            val fragment = QuestionDetailDialogFragment()
            val args = Bundle().apply {
                putLong(ARG_QUESTION_ID, questionId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
