package com.gamecenter.app.features

import android.content.Context
import androidx.fragment.app.Fragment

import com.gamecenter.app.modules.ModuleManager

data class SmartLoadResult(
    val usedDownloaded: Boolean,
    val fragment: Fragment,
    val version: Int,
    val versionName: String
)

object SmartModuleLoader {
    
    private const val TAG = "SmartModuleLoader"
    
    fun loadSmart(context: Context, moduleId: String): SmartLoadResult {
        
        val manifest = ModuleManager.getModuleManifest(moduleId)
        val builtInVersion = manifest?.builtInVersionCode ?: 100
        val builtInVersionName = manifest?.versionName ?: "1.0.0"
        
        if (ModuleManager.isModuleInstalled(context, moduleId)) {
            val remoteVersion = ModuleManager.getRemoteVersionCode(moduleId)
            
            if (remoteVersion > builtInVersion) {
                val downloaded = loadDownloadedModule(context, moduleId)
                if (downloaded != null) {
                    return SmartLoadResult(
                        usedDownloaded = true,
                        fragment = downloaded,
                        version = remoteVersion,
                        versionName = ModuleManager.getRemoteVersionName(moduleId)
                    )
                }
            }
        }
        
        val builtInFragment = createBuiltInFragment(moduleId)
            ?: throw IllegalStateException("无法创建内置模块: $moduleId")
        
        return SmartLoadResult(
            usedDownloaded = false,
            fragment = builtInFragment,
            version = builtInVersion,
            versionName = builtInVersionName
        )
    }
    
    private fun createBuiltInFragment(moduleId: String): Fragment? {
        // 主包极简化：移除了所有内置的非核心游戏模块UI，全部强制走远端下载（或沙盒运行）
        return null
    }
    
    private fun loadDownloadedModule(context: Context, moduleId: String): Fragment? {
        val feature = ModuleManager.getLoadedFeature(context, moduleId)
        return feature?.createFragment(context)
    }
    
    fun getBuiltInVersionCode(moduleId: String): Int {
        val manifest = ModuleManager.getModuleManifest(moduleId)
        return manifest?.builtInVersionCode ?: 100
    }
    
    fun getBuiltInVersionName(moduleId: String): String {
        val manifest = ModuleManager.getModuleManifest(moduleId)
        return manifest?.versionName ?: "1.0.0"
    }
    
    fun hasUpdate(context: Context, moduleId: String): Boolean {
        val manifest = ModuleManager.getModuleManifest(moduleId)
        if (manifest == null || manifest.builtInVersionCode == 0) {
            return false
        }
        
        val builtInVersion = getBuiltInVersionCode(moduleId)
        val remoteVersion = ModuleManager.getRemoteVersionCode(moduleId)
        
        return remoteVersion > builtInVersion
    }
    
    fun getUpdateInfo(context: Context, moduleId: String): UpdateInfo? {
        if (!hasUpdate(context, moduleId)) {
            return null
        }
        
        val builtInVersion = getBuiltInVersionCode(moduleId)
        val builtInVersionName = getBuiltInVersionName(moduleId)
        val remoteVersion = ModuleManager.getRemoteVersionCode(moduleId)
        val remoteVersionName = ModuleManager.getRemoteVersionName(moduleId)
        
        return UpdateInfo(
            moduleId = moduleId,
            currentVersion = builtInVersion,
            currentVersionName = builtInVersionName,
            newVersion = remoteVersion,
            newVersionName = remoteVersionName
        )
    }
    
    data class UpdateInfo(
        val moduleId: String,
        val currentVersion: Int,
        val currentVersionName: String,
        val newVersion: Int,
        val newVersionName: String
    ) {
        val versionLabel: String
            get() = "$currentVersionName → $newVersionName"
    }
}
