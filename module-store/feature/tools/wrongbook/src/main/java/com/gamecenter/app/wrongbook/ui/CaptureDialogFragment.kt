package com.gamecenter.app.wrongbook.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.analysis.AnalysisResult
import com.gamecenter.app.wrongbook.analysis.ImageCompressHelper
import com.gamecenter.app.wrongbook.databinding.ActivityCaptureBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureDialogFragment : DialogFragment() {

    private var _binding: ActivityCaptureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()

    private var currentImageUri: Uri? = null
    private var currentImagePath: String = ""
    private var lastAnalysis: AnalysisResult? = null
    /** OCR 原始输出（未经过用户编辑），用于持久化溯源 */
    private var currentOcrText: String = ""
    /** 题目来源：photo / album / manual */
    private var currentSourceType: String = "manual"

    private var currentStep = 1

    /** 模块 Resources：用于读取模块自身的字符串资源，避免与宿主 R 冲突 */
    private val moduleResources: android.content.res.Resources
        get() = com.gamecenter.app.modules.ModuleManager.getModuleResources(ModuleContextHelper.MODULE_ID)?.resources
            ?: super.getResources()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentImageUri = uri
                currentImagePath = ""
                currentSourceType = "album"
                currentOcrText = ""
                binding.ivStep2Preview.setImageURI(uri)
                goToStep(2)
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentImageUri?.let { uri ->
                binding.ivStep2Preview.setImageURI(uri)
                goToStep(2)
            }
        }
    }

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
        _binding = ActivityCaptureBinding.inflate(moduleInflater, container, false)
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
        setupObservers()

        goToStep(1)
    }

    private fun setupListeners() {
        binding.btnToolbarBack.setOnClickListener {
            if (currentStep > 1) {
                goToStep(currentStep - 1)
            } else {
                dismiss()
            }
        }

        binding.btnStep1Camera.setOnClickListener { checkCameraAndTakePhoto() }
        binding.btnStep1Gallery.setOnClickListener { pickImage() }

        binding.btnStep2Recognize.setOnClickListener { recognizeCurrentImage(false) }
        binding.btnStep2ReRecognize.setOnClickListener { recognizeCurrentImage(false) }
        binding.btnStep2Accurate.setOnClickListener { recognizeCurrentImage(true) }

        binding.btnStep4Analyze.setOnClickListener { analyzeCurrentText() }

        binding.btnWizardPrev.setOnClickListener {
            if (currentStep > 1) {
                goToStep(currentStep - 1)
            }
        }

        binding.btnWizardNext.setOnClickListener {
            if (currentStep < 5) {
                if (currentStep == 3) {
                    val text = binding.etStep3Content.text?.toString()?.trim() ?: ""
                    if (text.isBlank()) {
                        showSnackbar("题目文本不能为空，请确认或修改")
                        return@setOnClickListener
                    }
                }
                goToStep(currentStep + 1)
            } else {
                saveQuestion()
            }
        }
    }

    private fun setupObservers() {
        viewModel.ocrResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                binding.pbStep2Ocr.visibility = View.GONE
                binding.btnStep2Recognize.visibility = View.VISIBLE
                binding.btnStep2ReRecognize.visibility = View.VISIBLE
                binding.btnStep2Accurate.visibility = View.VISIBLE
                if (it.success) {
                    // 保存 OCR 原始文本，用户编辑前的快照
                    currentOcrText = it.text
                    binding.etStep3Content.setText(it.text)
                    goToStep(3)
                } else {
                    showSnackbar(it.message)
                }
                viewModel.clearOcrResult()
            }
        }

        viewModel.analysisResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                stopShimmerAnimation(binding.shimmerAnalysis.shimmerContainer)
                if (it.success) {
                    lastAnalysis = it
                    showAnalysis(it)
                    goToStep(5)
                } else {
                    showSnackbar(it.message)
                }
                viewModel.clearAnalysisResult()
            }
        }
    }

    private fun goToStep(step: Int) {
        currentStep = step
        binding.pbStepProgress.progress = step

        val stepTitle = when (step) {
            1 -> "步骤 1 / 5: 选择图片来源"
            2 -> "步骤 2 / 5: 预览并识别"
            3 -> "步骤 3 / 5: 确认识别文本"
            4 -> "步骤 4 / 5: AI 智能解析"
            else -> "步骤 5 / 5: 效果预览并保存"
        }
        binding.tvStepIndicator.text = stepTitle

        binding.layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
        binding.layoutStep4.visibility = if (step == 4) View.VISIBLE else View.GONE
        binding.layoutStep5.visibility = if (step == 5) View.VISIBLE else View.GONE

        binding.layoutNavigation.visibility = if (step > 1) View.VISIBLE else View.GONE
        binding.btnWizardPrev.visibility = if (step > 1) View.VISIBLE else View.GONE

        binding.btnWizardNext.visibility = View.VISIBLE
        binding.btnWizardNext.text = if (step == 5) "保存错题" else "下一步"

        if (step == 2) {
            binding.btnWizardNext.visibility = View.GONE
        } else if (step == 4 && lastAnalysis == null) {
            binding.btnWizardNext.visibility = View.GONE
        }
    }

    private fun checkCameraAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            return
        }
        takePhoto()
    }

    private fun takePhoto() {
        val file = createImageFile()
        currentImagePath = file.absolutePath
        currentImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.wrongbook.fileprovider",
            file
        )
        currentSourceType = "photo"
        currentOcrText = ""
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri)
        takePhotoLauncher.launch(intent)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    /**
     * 识别当前图片。
     *
     * #24.4: 当使用云端 OCR 引擎时，先弹 consent 明示数据去向与本地替代。
     *
     * @param accurate true 表示高精度识别（后端切换 accurate 接口）
     */
    private fun recognizeCurrentImage(accurate: Boolean) {
        val uri = currentImageUri
        if (uri == null) {
            showSnackbar("请先选择图片")
            return
        }
        // 云端引擎需先获取 consent
        if (viewModel.isCloudOcrEngine) {
            val consent = buildOcrConsent()
            if (com.gamecenter.app.ui.ConsentDialog.needsConsent(requireContext(), consent)) {
                showOcrConsent(uri, accurate)
                return
            }
        }
        startRecognition(uri, accurate, forceLocal = false)
    }

    /** #24.4: 启动 OCR 识别（隐藏按钮、显示进度条） */
    private fun startRecognition(uri: Uri, accurate: Boolean, forceLocal: Boolean) {
        binding.pbStep2Ocr.visibility = View.VISIBLE
        binding.btnStep2Recognize.visibility = View.GONE
        binding.btnStep2ReRecognize.visibility = View.GONE
        binding.btnStep2Accurate.visibility = View.GONE
        viewModel.recognizeImage(uri, accurate, forceLocal)
    }

    /** #24.4: 弹出 OCR consent 弹窗 */
    private fun showOcrConsent(uri: Uri, accurate: Boolean) {
        com.gamecenter.app.ui.ConsentDialog.show(requireActivity(), buildOcrConsent()) { decision ->
            when (decision) {
                com.gamecenter.app.core.common.ConsentDecision.AGREE_CLOUD ->
                    startRecognition(uri, accurate, forceLocal = false)
                com.gamecenter.app.core.common.ConsentDecision.USE_LOCAL ->
                    startRecognition(uri, accurate, forceLocal = true)
                com.gamecenter.app.core.common.ConsentDecision.REFUSE ->
                    Toast.makeText(
                        requireContext(),
                        moduleResources.getString(R.string.wrongbook_ocr_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
    }

    /** #24.4: 构建 OCR consent 组件 */
    private fun buildOcrConsent(): com.gamecenter.app.core.common.ConsentComponent {
        val res = moduleResources
        return com.gamecenter.app.core.common.ConsentComponent(
            scope = "ocr_cloud",
            versionCode = 1,
            title = res.getString(R.string.wrongbook_ocr_consent_title),
            sendData = res.getString(R.string.wrongbook_ocr_consent_send),
            purpose = res.getString(R.string.wrongbook_ocr_consent_purpose),
            localAlternative = res.getString(R.string.wrongbook_ocr_consent_local),
            costAndNetwork = res.getString(R.string.wrongbook_ocr_consent_cost),
            cancelHint = res.getString(R.string.wrongbook_ocr_consent_cancel),
            providerInfo = res.getString(R.string.wrongbook_ocr_consent_provider),
            dataRetention = res.getString(R.string.wrongbook_ocr_consent_retention)
        )
    }

    private fun analyzeCurrentText() {
        val text = binding.etStep3Content.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            showSnackbar("题目文字不能为空")
            return
        }
        // #24.3: 云端 AI 分析需先获取 consent（本地模式尚未实现，无"改用本地"选项）
        if (viewModel.isCloudAiMode) {
            val consent = buildAiAnalysisConsent()
            if (com.gamecenter.app.ui.ConsentDialog.needsConsent(requireContext(), consent)) {
                showAiAnalysisConsent(text)
                return
            }
        }
        startAiAnalysis(text)
    }

    /** #24.3: 启动 AI 分析 */
    private fun startAiAnalysis(text: String) {
        binding.shimmerAnalysis.shimmerContainer.visibility = View.VISIBLE
        binding.layoutStep4Result.visibility = View.GONE
        startShimmerAnimation(binding.shimmerAnalysis.shimmerContainer)
        viewModel.analyzeText(text)
    }

    /** #24.3: 弹出 AI 分析 consent 弹窗 */
    private fun showAiAnalysisConsent(text: String) {
        com.gamecenter.app.ui.ConsentDialog.show(requireActivity(), buildAiAnalysisConsent()) { decision ->
            when (decision) {
                com.gamecenter.app.core.common.ConsentDecision.AGREE_CLOUD ->
                    startAiAnalysis(text)
                com.gamecenter.app.core.common.ConsentDecision.REFUSE ->
                    Toast.makeText(
                        requireContext(),
                        moduleResources.getString(R.string.wrongbook_ai_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                com.gamecenter.app.core.common.ConsentDecision.USE_LOCAL -> {
                    // 本地模式尚未实现，提示用户
                    Toast.makeText(
                        requireContext(),
                        moduleResources.getString(R.string.wrongbook_ai_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** #24.3: 构建 AI 分析 consent 组件（无本地替代，localAlternative 为空） */
    private fun buildAiAnalysisConsent(): com.gamecenter.app.core.common.ConsentComponent {
        val res = moduleResources
        return com.gamecenter.app.core.common.ConsentComponent(
            scope = "wrongbook_ai_cloud",
            versionCode = 1,
            title = res.getString(R.string.wrongbook_ai_consent_title),
            sendData = res.getString(R.string.wrongbook_ai_consent_send),
            purpose = res.getString(R.string.wrongbook_ai_consent_purpose),
            localAlternative = "",
            costAndNetwork = res.getString(R.string.wrongbook_ai_consent_cost),
            cancelHint = res.getString(R.string.wrongbook_ai_consent_cancel),
            providerInfo = res.getString(R.string.wrongbook_ai_consent_provider),
            dataRetention = res.getString(R.string.wrongbook_ai_consent_retention)
        )
    }

    /**
     * 显示 AI 分析结果（Step 4 和 Step 5）。
     *
     * 第五阶段增强：展示题型、选项、答案、错因、复习建议、置信度。
     */
    private fun showAnalysis(result: AnalysisResult) {
        // Step 4 简要展示
        binding.layoutStep4Result.visibility = View.VISIBLE
        binding.tvStep4Subject.text = result.subject.ifBlank { "通用" }
        binding.tvStep4Knowledge.text = if (result.knowledgePoints.isEmpty()) "-" else result.knowledgePoints.joinToString(", ")
        binding.tvStep4Analysis.text = result.analysis.ifBlank { "-" }

        // Step 5 完整展示
        binding.tvStep5Subject.text = result.subject.ifBlank { "通用" }

        // 题型
        if (result.questionType.isNotBlank() && result.questionType != "unknown") {
            binding.tvStep5QuestionType.visibility = View.VISIBLE
            binding.tvStep5QuestionType.text = moduleResources.getString(
                R.string.wrongbook_question_type_format, mapQuestionType(result.questionType)
            )
        } else {
            binding.tvStep5QuestionType.visibility = View.GONE
        }

        // 题目内容
        val contentText = binding.etStep3Content.text?.toString()?.trim() ?: ""
        binding.tvStep5Content.text = result.question.ifBlank { contentText }

        // 选项
        if (result.options.isNotEmpty()) {
            binding.tvStep5Options.visibility = View.VISIBLE
            binding.tvStep5Options.text = result.options.joinToString("\n")
        } else {
            binding.tvStep5Options.visibility = View.GONE
        }

        // 答案
        binding.tvStep5Answer.text = result.answer.ifBlank { "-" }

        // 解析
        binding.tvStep5Analysis.text = result.analysis.ifBlank { "-" }

        // 错因
        if (result.wrongReason.isNotBlank()) {
            binding.tvStep5WrongReasonLabel.visibility = View.VISIBLE
            binding.tvStep5WrongReason.visibility = View.VISIBLE
            binding.tvStep5WrongReason.text = result.wrongReason
        } else {
            binding.tvStep5WrongReasonLabel.visibility = View.GONE
            binding.tvStep5WrongReason.visibility = View.GONE
        }

        // 复习建议
        if (result.reviewSuggestion.isNotBlank()) {
            binding.tvStep5ReviewLabel.visibility = View.VISIBLE
            binding.tvStep5ReviewSuggestion.visibility = View.VISIBLE
            binding.tvStep5ReviewSuggestion.text = result.reviewSuggestion
        } else {
            binding.tvStep5ReviewLabel.visibility = View.GONE
            binding.tvStep5ReviewSuggestion.visibility = View.GONE
        }

        // 置信度
        if (result.confidence > 0) {
            binding.tvStep5Confidence.visibility = View.VISIBLE
            binding.tvStep5Confidence.text = moduleResources.getString(
                R.string.wrongbook_confidence_format, result.confidence * 100
            )
        } else {
            binding.tvStep5Confidence.visibility = View.GONE
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

    private fun saveQuestion() {
        val text = binding.etStep3Content.text?.toString()?.trim() ?: ""
        val analysis = lastAnalysis ?: AnalysisResult(
            success = true,
            subject = "通用",
            difficulty = 3,
            knowledgePoints = emptyList(),
            analysis = ""
        )
        if (text.isBlank()) {
            showSnackbar("题目文字不能为空")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // 第三阶段：使用图片压缩工具替代直接复制
            val savedImagePath = compressAndSaveImage()
            // 第三阶段：传递 OCR/AI 溯源元数据
            val success = viewModel.saveQuestion(
                rawText = text,
                analysisResult = analysis,
                imagePath = savedImagePath,
                ocrText = currentOcrText,
                sourceType = currentSourceType,
                ocrProvider = viewModel.currentOcrProvider,
                aiProvider = viewModel.currentAiProvider,
                aiModel = viewModel.currentAiModel
            )
            if (success) {
                Toast.makeText(requireContext(), moduleResources.getString(R.string.wrongbook_saved), Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                showSnackbar("错题保存失败，请重试")
            }
        }
    }

    /**
     * 使用 ImageCompressHelper 压缩并修正图片方向后保存到私有目录。
     * 压缩失败时回退到直接复制。
     * 重 IO 操作在 Dispatchers.IO 执行，避免阻塞主线程。
     */
    private suspend fun compressAndSaveImage(): String = withContext(Dispatchers.IO) {
        val uri = currentImageUri ?: return@withContext ""
        val compressed = ImageCompressHelper.compressAndFixOrientation(requireContext(), uri)
        if (compressed.isNotBlank()) return@withContext compressed
        copyImageToPrivateDir()
    }

    /** 回退方案：直接复制原始图片（不压缩） */
    private suspend fun copyImageToPrivateDir(): String {
        val uri = currentImageUri ?: return ""
        return try {
            val dir = File(requireContext().filesDir, "wrongbook_images")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

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
        view.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            takePhoto()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }
}
