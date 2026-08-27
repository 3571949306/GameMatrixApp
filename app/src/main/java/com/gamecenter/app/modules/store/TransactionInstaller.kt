package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.modules.ModuleManifest
import com.gamecenter.app.modules.ModuleVerifier
import java.io.File
import java.io.IOException

/**
 * 事务性模块安装管理器。
 * 
 * 实现staging/current/last_good/quarantine目录结构，
 * 确保模块安装过程的原子性和可回滚性。
 * 
 * 目录结构：
 * - staging/: 下载中的模块
 * - current/: 当前使用的模块
 * - last_good/: 上一个稳定版本
 * - quarantine/: 有问题的模块
 * 
 * 安装流程：
 * 1. 下载到staging/
 * 2. 验证SHA-256和签名
 * 3. 原子移动到current/
 * 4. 备份旧版本到last_good/
 * 
 * 回滚流程：
 * - 加载失败时自动回滚到last_good/
 * - 严重问题移入quarantine/
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
object TransactionInstaller {
    
    private const val TAG = "TransactionInstaller"
    
    private const val STAGING_DIR = "staging"
    private const val CURRENT_DIR = "current"
    private const val LAST_GOOD_DIR = "last_good"
    private const val QUARANTINE_DIR = "quarantine"
    
    /**
     * 获取模块根目录。
     */
    private fun getModuleRootDir(context: Context): File {
        return File(context.filesDir, "modules").apply { mkdirs() }
    }
    
    /**
     * 获取staging目录。
     */
    fun getStagingDir(context: Context): File {
        return File(getModuleRootDir(context), STAGING_DIR).apply { mkdirs() }
    }
    
    /**
     * 获取current目录。
     */
    fun getCurrentDir(context: Context): File {
        return File(getModuleRootDir(context), CURRENT_DIR).apply { mkdirs() }
    }
    
    /**
     * 获取last_good目录。
     */
    fun getLastGoodDir(context: Context): File {
        return File(getModuleRootDir(context), LAST_GOOD_DIR).apply { mkdirs() }
    }
    
    /**
     * 获取quarantine目录。
     */
    fun getQuarantineDir(context: Context): File {
        return File(getModuleRootDir(context), QUARANTINE_DIR).apply { mkdirs() }
    }
    
    /**
     * 获取模块在staging目录的文件。
     */
    fun getStagingFile(context: Context, manifest: ModuleManifest): File {
        return File(getStagingDir(context), manifest.fileName)
    }
    
    /**
     * 获取模块在current目录的文件。
     */
    fun getCurrentFile(context: Context, manifest: ModuleManifest): File {
        return File(getCurrentDir(context), manifest.fileName)
    }
    
    /**
     * 获取模块在last_good目录的文件。
     */
    fun getLastGoodFile(context: Context, manifest: ModuleManifest): File {
        return File(getLastGoodDir(context), manifest.fileName)
    }
    
    /**
     * 获取模块在quarantine目录的文件。
     */
    fun getQuarantineFile(context: Context, manifest: ModuleManifest): File {
        return File(getQuarantineDir(context), "${manifest.id}_${System.currentTimeMillis()}_${manifest.fileName}")
    }
    
    /**
     * 安装事务结果。
     */
    sealed class InstallResult {
        object Success : InstallResult()
        data class Failure(val reason: String) : InstallResult()
        
        val isSuccess: Boolean get() = this is Success
    }
    
    /**
     * 执行事务性安装。
     * 
     * @param context 上下文
     * @param manifest 模块清单
     * @param downloadedFile 已下载的文件（在staging目录）
     * @return 安装结果
     */
    fun install(context: Context, manifest: ModuleManifest, downloadedFile: File): InstallResult {
        if (!BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
            Log.d(TAG, "事务安装已禁用，使用传统安装")
            return InstallResult.Success
        }
        
        return try {
            // 1. 验证文件完整性
            Log.d(TAG, "验证模块文件完整性: ${manifest.id}")
            if (!ModuleVerifier.verifySha256(downloadedFile, manifest.sha256, allowEmpty = manifest.builtIn)) {
                Log.e(TAG, "模块SHA-256校验失败: ${manifest.id}")
                downloadedFile.delete()
                return InstallResult.Failure("SHA-256校验失败")
            }

            // 1.5 APK 签名强校验（发布证书钉扎）：与 ModuleDownloader 下载路径保持一致。
            // 此前事务安装只做 SHA-256，是下载链路上唯一未校验发布证书的旁路。
            // 归档包（.zip）不走此处：其信任由 Catalog V2 绑定在下载路径断言。
            if (downloadedFile.name.endsWith(".apk", ignoreCase = true)) {
                when (val signature = ModuleSignatureVerifier.verify(downloadedFile, context)) {
                    ModuleSignatureVerifier.Result.Success -> Unit
                    is ModuleSignatureVerifier.Result.Warning,
                    is ModuleSignatureVerifier.Result.Failure -> {
                        val reason = when (signature) {
                            is ModuleSignatureVerifier.Result.Warning -> signature.reason
                            is ModuleSignatureVerifier.Result.Failure -> signature.reason
                            else -> "未知原因"
                        }
                        Log.e(TAG, "模块签名校验失败: ${manifest.id}, $reason")
                        downloadedFile.delete()
                        return InstallResult.Failure("模块签名验证失败")
                    }
                }
            }
            
            // 2. 备份当前版本到last_good
            val currentFile = getCurrentFile(context, manifest)
            val lastGoodFile = getLastGoodFile(context, manifest)
            
            if (currentFile.exists()) {
                Log.d(TAG, "备份当前版本到last_good: ${manifest.id}")
                if (!backupFile(currentFile, lastGoodFile)) {
                    Log.w(TAG, "备份失败，但继续安装: ${manifest.id}")
                }
            }
            
            // 3. 原子移动到current
            Log.d(TAG, "移动模块到current: ${manifest.id}")
            if (!moveFile(downloadedFile, currentFile)) {
                Log.e(TAG, "移动模块到current失败: ${manifest.id}")
                return InstallResult.Failure("移动模块文件失败")
            }
            
            // 4. 设置只读权限
            currentFile.setReadOnly()
            
            Log.d(TAG, "事务安装成功: ${manifest.id}")
            InstallResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "事务安装异常: ${manifest.id}, ${e.message}", e)
            InstallResult.Failure("安装异常: ${e.message}")
        }
    }
    
    /**
     * 回滚模块到last_good版本。
     * 
     * @param context 上下文
     * @param manifest 模块清单
     * @return 是否回滚成功
     */
    fun rollback(context: Context, manifest: ModuleManifest): Boolean {
        if (!BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
            Log.d(TAG, "事务安装已禁用，无法回滚")
            return false
        }
        
        return try {
            val currentFile = getCurrentFile(context, manifest)
            val lastGoodFile = getLastGoodFile(context, manifest)
            
            if (!lastGoodFile.exists()) {
                Log.w(TAG, "没有last_good版本可回滚: ${manifest.id}")
                return false
            }
            
            // 1. 将当前版本移入quarantine
            if (currentFile.exists()) {
                val quarantineFile = getQuarantineFile(context, manifest)
                Log.d(TAG, "将问题模块移入quarantine: ${manifest.id}")
                if (!moveFile(currentFile, quarantineFile)) {
                    Log.w(TAG, "移入quarantine失败，尝试删除: ${manifest.id}")
                    currentFile.delete()
                }
            }
            
            // 2. 从last_good恢复到current
            Log.d(TAG, "从last_good恢复到current: ${manifest.id}")
            if (!copyFile(lastGoodFile, currentFile)) {
                Log.e(TAG, "从last_good恢复失败: ${manifest.id}")
                return false
            }
            
            currentFile.setReadOnly()
            
            Log.d(TAG, "回滚成功: ${manifest.id}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "回滚异常: ${manifest.id}, ${e.message}", e)
            false
        }
    }
    
    /**
     * 清理staging目录。
     */
    fun cleanStaging(context: Context) {
        try {
            val stagingDir = getStagingDir(context)
            stagingDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "清理staging目录完成")
        } catch (e: Exception) {
            Log.w(TAG, "清理staging目录失败: ${e.message}")
        }
    }
    
    /**
     * 清理quarantine目录（保留最近7天）。
     */
    fun cleanQuarantine(context: Context, maxAgeDays: Int = 7) {
        try {
            val quarantineDir = getQuarantineDir(context)
            val cutoffTime = System.currentTimeMillis() - maxAgeDays * 24 * 60 * 60 * 1000L
            
            quarantineDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.d(TAG, "删除过期的quarantine文件: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理quarantine目录失败: ${e.message}")
        }
    }
    
    /**
     * 备份文件。
     */
    private fun backupFile(source: File, target: File): Boolean {
        return try {
            if (target.exists()) {
                target.delete()
            }
            source.copyTo(target, overwrite = true)
            true
        } catch (e: IOException) {
            Log.e(TAG, "备份文件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 移动文件（原子操作）。
     */
    private fun moveFile(source: File, target: File): Boolean {
        return try {
            if (target.exists()) {
                target.delete()
            }
            source.renameTo(target)
        } catch (e: Exception) {
            Log.e(TAG, "移动文件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 复制文件。
     */
    private fun copyFile(source: File, target: File): Boolean {
        return try {
            if (target.exists()) {
                target.delete()
            }
            source.copyTo(target, overwrite = true)
            true
        } catch (e: IOException) {
            Log.e(TAG, "复制文件失败: ${e.message}")
            false
        }
    }
}
