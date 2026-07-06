package com.gamecenter.app.wrongbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.gamecenter.app.wrongbook.analysis.AiAnalysisService
import com.gamecenter.app.wrongbook.analysis.OcrService
import com.gamecenter.app.wrongbook.databinding.FragmentSettingsBinding

/**
 * 错题本设置 Fragment。
 */
class SettingsFragment : BaseWrongBookFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var ocrService: OcrService
    private lateinit var aiService: AiAnalysisService

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

        binding.btnSaveSettings.setOnClickListener { saveSettings() }
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

    private fun saveSettings() {
        ocrService.currentEngine = if (binding.rbOcrCloud.isChecked) "scnet" else "local"
        aiService.mode = if (binding.rbAiLocal.isChecked) "local" else "cloud"
        aiService.baseUrl = binding.etAiBaseUrl.text?.toString()?.trim() ?: ""
        aiService.apiKey = binding.etAiApiKey.text?.toString()?.trim() ?: ""
        aiService.model = binding.etAiModel.text?.toString()?.trim() ?: ""
        Toast.makeText(
            requireContext(),
            moduleResources.getString(com.gamecenter.app.wrongbook.R.string.wrongbook_settings_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
