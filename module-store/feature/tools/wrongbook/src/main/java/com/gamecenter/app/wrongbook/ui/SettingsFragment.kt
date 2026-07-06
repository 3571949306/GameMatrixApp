package com.gamecenter.app.wrongbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.analysis.AiAnalysisService
import com.gamecenter.app.wrongbook.analysis.OcrService
import com.gamecenter.app.wrongbook.databinding.FragmentSettingsBinding
import java.io.File

/**
 * 错题本设置 Fragment。
 */
class SettingsFragment : BaseWrongBookFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()

    private lateinit var ocrService: OcrService
    private lateinit var aiService: AiAnalysisService

    private var isExporting = true
    private var isCloudAction = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val moduleInflater = ModuleContextHelper.getLayoutInflater(requireContext())
        _binding = FragmentSettingsBinding.inflate(moduleInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        ocrService = OcrService(ctx)
        aiService = AiAnalysisService(ctx)

        loadSettings()
        setupListeners()
        setupObservers()
    }

    private fun loadSettings() {
        when (ocrService.currentEngine) {
            "scnet" -> binding.rbOcrCloud.isChecked = true
            else -> binding.rbOcrLocal.isChecked = true
        }

        when (aiService.mode) {
            "local" -> binding.rbAiLocal.isChecked = true
            else -> binding.rbAiCloud.isChecked = true
        }

        binding.etAiBaseUrl.setText(aiService.baseUrl)
        binding.etAiApiKey.setText(aiService.apiKey)
        binding.etAiModel.setText(aiService.model)
    }

    private fun setupListeners() {
        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        val exportDir = File(requireContext().filesDir, "wrongbook_export")
        val exportFile = File(exportDir, "wrongbook_backup.json")

        binding.btnExport.setOnClickListener {
            isExporting = true
            isCloudAction = false
            viewModel.exportDatabase(exportFile)
        }

        binding.btnImport.setOnClickListener {
            if (!exportFile.exists()) {
                Toast.makeText(requireContext(), "未找到备份文件，请先点击导出备份", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isExporting = false
            isCloudAction = false
            viewModel.importDatabase(exportFile)
        }

        binding.btnCloudExport.setOnClickListener {
            isExporting = true
            isCloudAction = true
            viewModel.exportToCloud(requireContext(), exportFile)
        }

        binding.btnCloudImport.setOnClickListener {
            isExporting = false
            isCloudAction = true
            viewModel.importFromCloud(requireContext(), exportFile)
        }
    }

    private fun setupObservers() {
        val exportDir = File(requireContext().filesDir, "wrongbook_export")
        val exportFile = File(exportDir, "wrongbook_backup.json")

        viewModel.importExportStatus.observe(viewLifecycleOwner) { success ->
            success?.let {
                val msg = if (it) {
                    if (isCloudAction) {
                        if (isExporting) {
                            moduleResources.getString(R.string.wrongbook_cloud_export_success)
                        } else {
                            moduleResources.getString(R.string.wrongbook_cloud_import_success)
                        }
                    } else {
                        if (isExporting) {
                            moduleResources.getString(R.string.wrongbook_export_success, exportFile.absolutePath)
                        } else {
                            moduleResources.getString(R.string.wrongbook_import_success)
                        }
                    }
                } else {
                    if (isExporting) {
                        moduleResources.getString(R.string.wrongbook_export_fail)
                    } else {
                        moduleResources.getString(R.string.wrongbook_import_fail)
                    }
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearImportExportStatus()
            }
        }
    }

    private fun saveSettings() {
        ocrService.currentEngine = if (binding.rbOcrCloud.isChecked) "scnet" else "local"
        aiService.mode = if (binding.rbAiLocal.isChecked) "local" else "cloud"
        aiService.baseUrl = binding.etAiBaseUrl.text?.toString()?.trim() ?: ""
        aiService.apiKey = binding.etAiApiKey.text?.toString()?.trim() ?: ""
        aiService.model = binding.etAiModel.text?.toString()?.trim() ?: ""
        Toast.makeText(
            requireContext(),
            moduleResources.getString(R.string.wrongbook_settings_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
