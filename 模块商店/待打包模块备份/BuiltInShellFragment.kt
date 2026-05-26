package com.gamecenter.app.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.google.android.material.snackbar.Snackbar

class BuiltInShellFragment : Fragment() {
    
    private var moduleId: String? = null
    private var loadedFragment: Fragment? = null
    private var usedDownloaded = false
    private var currentVersion = 100
    private var isDestroyed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleId = arguments?.getString(ARG_MODULE_ID) ?: "browser"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val containerId = View.generateViewId()
        return FrameLayout(requireContext()).apply {
            id = containerId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isDestroyed = false
        
        if (savedInstanceState == null) {
            loadSmartModule(view as FrameLayout)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isDestroyed) {
            checkForUpdates()
        }
    }
    
    override fun onDestroyView() {
        isDestroyed = true
        super.onDestroyView()
    }
    
    private fun loadSmartModule(container: FrameLayout) {
        val id = moduleId ?: return
        
        try {
            val result = SmartModuleLoader.loadSmart(requireContext(), id)
            
            loadedFragment = result.fragment
            usedDownloaded = result.usedDownloaded
            currentVersion = result.version
            
            childFragmentManager.beginTransaction()
                .replace(container.id, result.fragment)
                .commitNowAllowingStateLoss()
            
            if (result.usedDownloaded) {
                showVersionInfo(container, "已更新: v${result.versionName}")
            }
            
        } catch (e: Exception) {
            showError(container, "加载失败: ${e.message}")
        }
    }
    
    private fun checkForUpdates() {
        val id = moduleId ?: return
        val context = context ?: return
        
        if (SmartModuleLoader.hasUpdate(context, id)) {
            val updateInfo = SmartModuleLoader.getUpdateInfo(context, id)
            updateInfo?.let { showUpdateSnackbar(it) }
        }
    }
    
    private fun showUpdateSnackbar(info: SmartModuleLoader.UpdateInfo) {
        view?.let { rootView ->
            Snackbar.make(rootView, "发现新版本: ${info.versionLabel}", Snackbar.LENGTH_LONG)
                .setAction("更新") {
                    downloadUpdate()
                }
                .show()
        }
    }
    
    private fun downloadUpdate() {
        moduleId?.let { id ->
            ModuleManager.downloadModule(requireContext(), id, null)
        }
    }
    
    private fun showVersionInfo(container: FrameLayout, message: String) {
        val textView = TextView(requireContext()).apply {
            text = message
            textSize = 12f
            setTextColor(0xFF4CAF50.toInt())
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(0, 16, 16, 0)
            }
        }
        container.addView(textView)
    }
    
    private fun showError(container: FrameLayout, message: String) {
        val errorView = layoutInflater.inflate(
            R.layout.module_shell_placeholder,
            container,
            false
        )
        
        errorView.findViewById<TextView>(R.id.btnGoToStore)?.apply {
            text = "重试"
            setOnClickListener {
                loadSmartModule(container)
            }
        }
        
        container.addView(errorView)
    }
    
    companion object {
        const val ARG_MODULE_ID = "module_id"
        
        fun newInstance(moduleId: String) = BuiltInShellFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MODULE_ID, moduleId)
            }
        }
    }
}
