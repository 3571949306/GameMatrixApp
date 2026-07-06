package com.gamecenter.app.wrongbook.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.analysis.AnalysisResult
import com.gamecenter.app.wrongbook.databinding.ActivityCaptureBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拍照/选图识别与 AI 分析页面。
 */
class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var viewModel: WrongBookViewModel

    private var currentImageUri: Uri? = null
    private var currentImagePath: String = ""
    private var lastAnalysis: AnalysisResult? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentImageUri = uri
                currentImagePath = ""
                binding.ivPreview.setImageURI(uri)
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentImageUri?.let { uri ->
                binding.ivPreview.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[WrongBookViewModel::class.java]

        binding.toolbarTitle.setOnClickListener { finish() }

        binding.btnTakePhoto.setOnClickListener { checkCameraAndTakePhoto() }
        binding.btnPickImage.setOnClickListener { pickImage() }
        binding.btnRecognize.setOnClickListener { recognizeCurrentImage() }
        binding.btnAnalyze.setOnClickListener { analyzeCurrentText() }
        binding.btnSave.setOnClickListener { saveQuestion() }

        viewModel.ocrResult.observe(this) { result ->
            result?.let {
                binding.progressBar.visibility = View.GONE
                if (it.success) {
                    binding.etQuestion.setText(it.text)
                } else {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
                viewModel.clearOcrResult()
            }
        }

        viewModel.analysisResult.observe(this) { result ->
            result?.let {
                binding.progressBar.visibility = View.GONE
                if (it.success) {
                    lastAnalysis = it
                    showAnalysis(it)
                } else {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
                viewModel.clearAnalysisResult()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }
    }

    private fun checkCameraAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
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
            this,
            "${packageName}.wrongbook.fileprovider",
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
            Toast.makeText(this, R.string.wrongbook_take_photo, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.recognizeImage(uri)
    }

    private fun analyzeCurrentText() {
        val text = binding.etQuestion.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, R.string.wrongbook_no_api_config, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.analyzeText(text)
    }

    private fun showAnalysis(result: AnalysisResult) {
        binding.cardAnalysis.visibility = View.VISIBLE
        binding.tvAnalysisSubject.text = result.subject.ifBlank { getString(R.string.wrongbook_subject_all) }
        binding.tvAnalysisKnowledge.text = if (result.knowledgePoints.isEmpty()) {
            "-"
        } else {
            result.knowledgePoints.joinToString(", ")
        }
        binding.tvAnalysisText.text = result.analysis.ifBlank { "-" }
    }

    private fun saveQuestion() {
        val text = binding.etQuestion.text?.toString()?.trim() ?: ""
        val analysis = lastAnalysis ?: AnalysisResult(
            success = true,
            subject = "",
            difficulty = 3,
            knowledgePoints = emptyList(),
            analysis = ""
        )
        if (text.isBlank()) {
            Toast.makeText(this, R.string.wrongbook_question_content, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            // 复制图片到应用私有目录
            val savedImagePath = copyImageToPrivateDir()
            viewModel.saveQuestion(text, analysis, savedImagePath)
            Toast.makeText(this@CaptureActivity, R.string.wrongbook_save_question, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun copyImageToPrivateDir(): String {
        val uri = currentImageUri ?: return ""
        return try {
            val dir = File(filesDir, "wrongbook_images")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
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
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
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

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }
}
