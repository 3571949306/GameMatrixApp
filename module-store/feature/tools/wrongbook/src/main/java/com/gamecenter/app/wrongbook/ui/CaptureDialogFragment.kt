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
import com.gamecenter.app.wrongbook.databinding.ActivityCaptureBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
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

    private var currentStep = 1

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentImageUri = uri
                currentImagePath = ""
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

        binding.btnStep2Recognize.setOnClickListener { recognizeCurrentImage() }

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
                if (it.success) {
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
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri)
        takePhotoLauncher.launch(intent)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun recognizeCurrentImage() {
        val uri = currentImageUri
        if (uri == null) {
            showSnackbar("请先选择图片")
            return
        }
        binding.pbStep2Ocr.visibility = View.VISIBLE
        viewModel.recognizeImage(uri)
    }

    private fun analyzeCurrentText() {
        val text = binding.etStep3Content.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            showSnackbar("题目文字不能为空")
            return
        }
        binding.shimmerAnalysis.shimmerContainer.visibility = View.VISIBLE
        binding.layoutStep4Result.visibility = View.GONE
        startShimmerAnimation(binding.shimmerAnalysis.shimmerContainer)
        viewModel.analyzeText(text)
    }

    private fun showAnalysis(result: AnalysisResult) {
        binding.layoutStep4Result.visibility = View.VISIBLE
        binding.tvStep4Subject.text = result.subject.ifBlank { "通用" }
        binding.tvStep4Knowledge.text = if (result.knowledgePoints.isEmpty()) "-" else result.knowledgePoints.joinToString(", ")
        binding.tvStep4Analysis.text = result.analysis.ifBlank { "-" }

        binding.tvStep5Subject.text = result.subject.ifBlank { "通用" }
        binding.tvStep5Content.text = binding.etStep3Content.text?.toString()?.trim() ?: ""
        binding.tvStep5Analysis.text = result.analysis.ifBlank { "-" }
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
            val savedImagePath = copyImageToPrivateDir()
            viewModel.saveQuestion(text, analysis, savedImagePath)
            Toast.makeText(requireContext(), "错题保存成功！", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

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
