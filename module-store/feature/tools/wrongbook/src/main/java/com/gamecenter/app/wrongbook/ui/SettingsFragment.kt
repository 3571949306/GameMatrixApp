package com.gamecenter.app.wrongbook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gamecenter.app.wrongbook.R
import com.gamecenter.app.wrongbook.analysis.AiAnalysisService
import com.gamecenter.app.wrongbook.analysis.BackendProxyConfig
import com.gamecenter.app.wrongbook.analysis.OcrService
import com.gamecenter.app.wrongbook.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 错题本设置 Fragment。
 *
 * 支持三种 OCR 引擎（local / scnet / baidu）与三种 AI 模式（cloud / local / backend_proxy）。
 * 后端代理相关 UI 受 BuildConfig.WRONGBOOK_BACKEND_PROXY feature flag 控制，
 * 关闭时隐藏百度 OCR、后端代理 AI 选项及后端配置区块。
 */
class SettingsFragment : BaseWrongBookFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WrongBookViewModel by activityViewModels()

    private lateinit var ocrService: OcrService
    private lateinit var aiService: AiAnalysisService
    private lateinit var backendConfig: BackendProxyConfig

    /** 后端配置检查接口路径 */
    private val backendStatusPath = "/api/wrongbook/config/status"

    private var isExporting = true
    private var isCloudAction = false

    /** 测试连接专用 OkHttp 客户端（短超时，避免 UI 卡顿） */
    private val testHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

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
        backendConfig = aiService.backendConfig

        applyFeatureFlagVisibility()
        loadSettings()
        setupListeners()
        setupObservers()
    }

    /**
     * 根据 BuildConfig.WRONGBOOK_BACKEND_PROXY 控制 baidu OCR 选项、
     * 后端代理 AI 选项、后端配置区块的可见性。
     * 根据 BuildConfig.WRONGBOOK_SECURE_API_CONFIG 控制正式版密钥配置区块可见性。
     */
    private fun applyFeatureFlagVisibility() {
        val enabled = BackendProxyConfig.isFeatureFlagEnabled()
        val visibility = if (enabled) View.VISIBLE else View.GONE
        binding.rbOcrBaidu.visibility = visibility
        binding.rbAiBackendProxy.visibility = visibility
        binding.tvBackendSectionTitle.visibility = visibility
        binding.tvBackendSectionHint.visibility = visibility
        binding.llBackendConfigContainer.visibility = visibility

    }

    private fun loadSettings() {
        when (ocrService.currentEngine) {
            "scnet" -> binding.rbOcrCloud.isChecked = true
            "baidu" -> {
                if (BackendProxyConfig.isFeatureFlagEnabled()) {
                    binding.rbOcrBaidu.isChecked = true
                } else {
                    binding.rbOcrLocal.isChecked = true
                }
            }
            else -> binding.rbOcrLocal.isChecked = true
        }

        when (aiService.mode) {
            "local" -> binding.rbAiLocal.isChecked = true
            "backend_proxy" -> {
                if (BackendProxyConfig.isFeatureFlagEnabled()) {
                    binding.rbAiBackendProxy.isChecked = true
                } else {
                    binding.rbAiCloud.isChecked = true
                }
            }
            else -> binding.rbAiCloud.isChecked = true
        }

        binding.etAiBaseUrl.setText(aiService.baseUrl)
        binding.etAiApiKey.setText(aiService.apiKey)
        binding.etAiModel.setText(aiService.model)

        binding.etBackendBaseUrl.setText(backendConfig.baseUrl)
        binding.etBackendApiToken.setText(backendConfig.apiToken)
        binding.cbBackendEnabled.isChecked = backendConfig.enabled

        // EditText 获得焦点时全选已有文本，避免输入追加而非替换
        setupEditTextSelectAllOnFocus(
            binding.etAiBaseUrl,
            binding.etAiApiKey,
            binding.etAiModel,
            binding.etBackendBaseUrl,
            binding.etBackendApiToken
        )
    }

    /**
     * 为多个 EditText 设置获得焦点时全选文本。
     *
     * 使用 setSelectAllOnFocus(true) 处理点击获得焦点的场景（在 Editor 内部
     * 的 ACTION_UP 时生效，避免被触摸事件重置光标）；同时保留
     * OnFocusChangeListener 处理 Tab 键等非触摸焦点切换场景。
     */
    private fun setupEditTextSelectAllOnFocus(vararg editTexts: EditText) {
        for (et in editTexts) {
            et.setSelectAllOnFocus(true)
            et.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) et.selectAll()
            }
        }
    }

    private fun setupListeners() {
        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        binding.btnTestConnection.setOnClickListener { testBackendConnection() }

        val exportDir = File(requireContext().filesDir, "wrongbook_export")
        val exportFile = File(exportDir, "wrongbook_backup.json")

        binding.btnExport.setOnClickListener {
            isExporting = true
            isCloudAction = false
            viewModel.exportDatabase(exportFile)
        }

        binding.btnImport.setOnClickListener {
            if (!exportFile.exists()) {
                Toast.makeText(requireContext(), moduleResources.getString(R.string.wrongbook_no_backup), Toast.LENGTH_SHORT).show()
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
        // OCR 引擎
        ocrService.currentEngine = when {
            binding.rbOcrCloud.isChecked -> "scnet"
            binding.rbOcrBaidu.isChecked && BackendProxyConfig.isFeatureFlagEnabled() -> "baidu"
            else -> "local"
        }

        // AI 模式
        aiService.mode = when {
            binding.rbAiLocal.isChecked -> "local"
            binding.rbAiBackendProxy.isChecked && BackendProxyConfig.isFeatureFlagEnabled() -> "backend_proxy"
            else -> "cloud"
        }

        aiService.baseUrl = binding.etAiBaseUrl.text?.toString()?.trim() ?: ""
        aiService.apiKey = binding.etAiApiKey.text?.toString()?.trim() ?: ""
        aiService.model = binding.etAiModel.text?.toString()?.trim() ?: ""

        // 后端代理配置
        if (BackendProxyConfig.isFeatureFlagEnabled()) {
            val url = binding.etBackendBaseUrl.text?.toString()?.trim() ?: ""
            backendConfig.baseUrl = url
            backendConfig.apiToken = binding.etBackendApiToken.text?.toString()?.trim() ?: ""
            backendConfig.enabled = binding.cbBackendEnabled.isChecked
        }

        Toast.makeText(
            requireContext(),
            moduleResources.getString(R.string.wrongbook_settings_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 调用后端 /api/wrongbook/config/status 测试连接。
     *
     * 后端响应字段：success / baiduOcrConfigured / zhipuConfigured / ocrDefaultMode / aiDefaultModel。
     */
    private fun testBackendConnection() {
        if (!BackendProxyConfig.isFeatureFlagEnabled()) {
            Toast.makeText(
                requireContext(),
                moduleResources.getString(R.string.wrongbook_backend_flag_disabled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val url = binding.etBackendBaseUrl.text?.toString()?.trim() ?: ""
        if (url.isBlank()) {
            Toast.makeText(
                requireContext(),
                moduleResources.getString(R.string.wrongbook_backend_url_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 先把当前输入框地址写入配置（避免用户尚未保存就测试时使用旧地址）
        backendConfig.baseUrl = url
        backendConfig.apiToken = binding.etBackendApiToken.text?.toString()?.trim() ?: ""

        val testingMsg = moduleResources.getString(R.string.wrongbook_backend_testing)
        binding.tvBackendStatus.visibility = View.VISIBLE
        binding.tvBackendStatus.text = testingMsg
        binding.btnTestConnection.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { fetchBackendStatus(url) }
            binding.btnTestConnection.isEnabled = true

            result
                .onSuccess { triple ->
                    val (success, ocrOk, aiOk) = triple
                    if (success) {
                        val ocrText = moduleResources.getString(
                            if (ocrOk) R.string.wrongbook_backend_test_ocr_ok
                            else R.string.wrongbook_backend_test_ocr_off
                        )
                        val aiText = moduleResources.getString(
                            if (aiOk) R.string.wrongbook_backend_test_ai_ok
                            else R.string.wrongbook_backend_test_ai_off
                        )
                        binding.tvBackendStatus.text = moduleResources.getString(
                            R.string.wrongbook_backend_test_success, ocrText, aiText
                        )
                        Toast.makeText(
                            requireContext(),
                            moduleResources.getString(R.string.wrongbook_backend_test_success_simple),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val msg = moduleResources.getString(R.string.wrongbook_backend_test_fail, "success=false")
                        binding.tvBackendStatus.text = msg
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { e ->
                    val msg = moduleResources.getString(
                        R.string.wrongbook_backend_test_fail, e.message ?: "unknown"
                    )
                    binding.tvBackendStatus.text = msg
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * 在 IO 线程执行 GET /api/wrongbook/config/status 请求。
     *
     * @return Triple(success, baiduOcrConfigured, zhipuConfigured)
     */
    private suspend fun fetchBackendStatus(baseUrl: String): Triple<Boolean, Boolean, Boolean> =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/')
            val fullUrl = "$base$backendStatusPath"
            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("X-API-Key", backendConfig.apiToken)
                .get()
                .build()

            testHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}")
                }
                val json = JSONObject(body)
                Triple(
                    json.optBoolean("success", false),
                    json.optBoolean("baiduOcrConfigured", false),
                    json.optBoolean("zhipuConfigured", false)
                )
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
