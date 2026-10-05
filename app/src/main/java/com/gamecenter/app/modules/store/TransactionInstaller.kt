package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.modules.ModuleManifest
import com.gamecenter.app.modules.ModuleVerifier
import java.io.File
import java.io.IOException
import java.util.UUID

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
 * 3. 按已知清单备份旧版本到last_good/，或隔离无法识别的 current
 * 4. 原子移动到current/
 * 5. 清理旧路径并设置只读权限
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
        val safeId = manifest.id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "module" }
        val safeFileName = manifest.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "module.bin" }
        return File(getQuarantineDir(context), "${safeId}_${UUID.randomUUID()}_$safeFileName")
    }

    /** Resolve a persisted quarantine basename without allowing directory traversal. */
    internal fun findQuarantineFile(context: Context, fileName: String): File? {
        if (fileName.isBlank() || File(fileName).name != fileName) return null
        return File(getQuarantineDir(context), fileName).takeIf { it.isFile }
    }
    
    /**
     * 安装事务结果。
     */
    sealed class InstallResult {
        data class Success(val quarantinedPrevious: File? = null) : InstallResult()
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
     *
     * 三参数入口仅兼容历史的“current 与新版本使用同一 fileName”场景。
     * 它不能安全推断 fileName 已变化时的旧文件归属；需要更换 fileName
     * 时必须由 ModuleManager 通过四参数入口显式传入 previousManifest。
     */
    fun install(context: Context, manifest: ModuleManifest, downloadedFile: File): InstallResult =
        // 保留三参数调用的历史行为；它只把 manifest 自身作为同名文件的
        // 兼容定位，绝不声称支持 fileName 变化。
        installInternal(
            context,
            manifest,
            downloadedFile,
            manifest,
            requirePreviousFile = false,
            allowUntrackedCurrent = false
        )

    /**
     * 执行安装，并可显式指定当前已安装版本的清单。
     *
     * [previousManifest] 为 null 表示没有可安全识别的旧文件：只安装新文件，
     * 不把可能属于旧版本的文件静默备份为 last_good。四参数入口是支持
     * fileName 变化的唯一安全路径。
     */
    fun install(
        context: Context,
        manifest: ModuleManifest,
        downloadedFile: File,
        previousManifest: ModuleManifest?
    ): InstallResult = installInternal(
        context,
        manifest,
        downloadedFile,
        previousManifest,
        requirePreviousFile = previousManifest != null,
        allowUntrackedCurrent = false
    )

    /**
     * 安全迁移没有 current manifest 快照的旧安装。
     *
     * 旧文件无法建立 last_good 时仍可更新，但必须先隔离未知 current，不能
     * 将其伪装成可验证的回滚快照。该路径只由 ModuleManager 的旧安装迁移调用。
     */
    internal fun install(
        context: Context,
        manifest: ModuleManifest,
        downloadedFile: File,
        previousManifest: ModuleManifest?,
        allowUntrackedCurrent: Boolean
    ): InstallResult = installInternal(
        context,
        manifest,
        downloadedFile,
        previousManifest,
        requirePreviousFile = previousManifest != null,
        allowUntrackedCurrent = allowUntrackedCurrent
    )

    private fun installInternal(
        context: Context,
        manifest: ModuleManifest,
        downloadedFile: File,
        previousManifest: ModuleManifest?,
        requirePreviousFile: Boolean,
        allowUntrackedCurrent: Boolean
    ): InstallResult {
        if (!BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
            Log.d(TAG, "事务安装已禁用，使用传统安装")
            return InstallResult.Success()
        }

        var untrackedCurrentBackup: File? = null
        var currentFileForRecovery: File? = null
        var previousCurrentFileForRecovery: File? = null
        var previousLastGoodFileForRecovery: File? = null
        var currentMoveCompleted = false
        return try {
            // 1. 验证文件完整性
            Log.d(TAG, "验证模块文件完整性: ${manifest.id}")
            val sizeMatches = manifest.fileSize <= 0L || downloadedFile.length() == manifest.fileSize
            if (!sizeMatches ||
                !ModuleVerifier.verifySha256(downloadedFile, manifest.sha256, allowEmpty = manifest.builtIn)
            ) {
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
                        }
                        Log.e(TAG, "模块签名校验失败: ${manifest.id}, $reason")
                        downloadedFile.delete()
                        return InstallResult.Failure("模块签名验证失败")
                    }
                }
            }

            if (previousManifest != null && previousManifest.id != manifest.id) {
                Log.w(TAG, "前一版本清单 ID 不匹配，拒绝安装: ${manifest.id}")
                downloadedFile.delete()
                return InstallResult.Failure("前一版本清单不匹配")
            }
            if (!isSafeFileName(manifest.fileName) ||
                (previousManifest != null && !isSafeFileName(previousManifest.fileName))
            ) {
                Log.w(TAG, "模块文件名不安全，拒绝安装: ${manifest.id}")
                downloadedFile.delete()
                return InstallResult.Failure("模块文件名不安全")
            }
            
            // 2. 备份当前版本到last_good
            val currentFile = getCurrentFile(context, manifest)
            val previousCurrentFile = previousManifest?.let { getCurrentFile(context, it) }
            val previousLastGoodFile = previousManifest?.let { getLastGoodFile(context, it) }
            currentFileForRecovery = currentFile
            previousCurrentFileForRecovery = previousCurrentFile
            previousLastGoodFileForRecovery = previousLastGoodFile

            // 四参数入口明确表示 previousManifest 归属了一个已安装文件；
            // 若该文件已经消失，不能把新文件伪装成完成了备份的事务。
            if (requirePreviousFile && previousCurrentFile?.isFile != true) {
                Log.w(TAG, "前一版本文件不存在，拒绝覆盖 current: ${manifest.id}")
                downloadedFile.delete()
                return InstallResult.Failure("前一版本文件不存在")
            }
            
            // previousManifest 为 null 时 current 中若已有文件，其归属无法确认，
            // 默认保守拒绝覆盖。旧安装迁移路径可先隔离未知文件，但不建立
            // last_good 元数据，因此后续回滚能力仍然是明确的 false。
            if (previousManifest == null && currentFile.exists()) {
                if (!allowUntrackedCurrent) {
                    Log.w(TAG, "current 中存在无法确认归属的文件，拒绝覆盖: ${manifest.id}")
                    downloadedFile.delete()
                    return InstallResult.Failure("current 文件归属无法确认")
                }
                val quarantineFile = getQuarantineFile(context, manifest)
                Log.w(TAG, "隔离没有 manifest 快照的旧 current: ${manifest.id}")
                if (!moveFile(currentFile, quarantineFile)) {
                    Log.e(TAG, "隔离未知 current 失败，拒绝覆盖: ${manifest.id}")
                    downloadedFile.delete()
                    return InstallResult.Failure("隔离旧 current 失败")
                }
                untrackedCurrentBackup = quarantineFile
            }

            // 更换 fileName 时，新路径若已经存在且不是 previousManifest 描述的
            // 旧文件，不能删除未知文件。
            if (currentFile.exists() && previousCurrentFile != null &&
                currentFile.canonicalFile != previousCurrentFile.canonicalFile
            ) {
                Log.w(TAG, "新 current 路径已有未知文件，拒绝覆盖: ${manifest.id}")
                downloadedFile.delete()
                return InstallResult.Failure("新 current 文件归属无法确认")
            }
            
            if (previousCurrentFile?.exists() == true && previousLastGoodFile != null) {
                Log.d(TAG, "备份当前版本到last_good: ${manifest.id}")
                if (!backupFile(previousCurrentFile, previousLastGoodFile)) {
                    Log.e(TAG, "备份失败，拒绝覆盖 current: ${manifest.id}")
                    downloadedFile.delete()
                    return InstallResult.Failure("备份旧版本失败")
                }
            }

            // 3. 原子移动到current
            Log.d(TAG, "移动模块到current: ${manifest.id}")
            if (!moveFile(downloadedFile, currentFile)) {
                // moveFile 在某些文件系统上可能先删除目标再 rename；备份已成功
                // 时立即恢复旧 current，确保安装失败不会留下半事务状态。
                if (previousCurrentFile != null && previousLastGoodFile?.isFile == true) {
                    copyFile(previousLastGoodFile, previousCurrentFile)
                }
                val backup = untrackedCurrentBackup
                if (backup?.isFile == true) {
                    moveFile(backup, previousCurrentFile ?: currentFile)
                }
                downloadedFile.delete()
                Log.e(TAG, "移动模块到current失败: ${manifest.id}")
                return InstallResult.Failure("移动模块文件失败")
            }
            currentMoveCompleted = true

            // fileName 变化时，旧文件已由 last_good 保留；current 只保留活动文件，
            // 避免冷启动扫描到两份同一模块文件。
            if (previousCurrentFile != null &&
                previousCurrentFile.canonicalFile != currentFile.canonicalFile &&
                previousCurrentFile.exists()
            ) {
                previousCurrentFile.setWritable(true, false)
                previousCurrentFile.delete()
            }
            
            // 4. 设置只读权限
            currentFile.setReadOnly()
            
            Log.d(TAG, "事务安装成功: ${manifest.id}")
            InstallResult.Success(untrackedCurrentBackup)
            
        } catch (e: Exception) {
            // move 成功后若清理/只读步骤异常，也必须先移走新 current，再恢复旧文件；
            // 否则下面的恢复会把旧文件错误地覆盖到新版本路径，或留下无状态新文件。
            if (currentMoveCompleted) {
                val currentFile = currentFileForRecovery
                if (currentFile?.isFile == true) {
                    moveFile(currentFile, getQuarantineFile(context, manifest))
                }
            }

            val previousCurrentFile = previousCurrentFileForRecovery
            val previousLastGoodFile = previousLastGoodFileForRecovery
            val untrackedBackup = untrackedCurrentBackup
            val recoveryTarget = previousCurrentFile ?: currentFileForRecovery
            val restored = when {
                previousCurrentFile != null && previousLastGoodFile?.isFile == true ->
                    copyFile(previousLastGoodFile, previousCurrentFile)
                untrackedBackup?.isFile == true && recoveryTarget != null ->
                    moveFile(untrackedBackup, recoveryTarget)
                else -> false
            }
            if (!restored && (previousLastGoodFile?.isFile == true || untrackedBackup?.isFile == true)) {
                Log.e(TAG, "安装异常后恢复旧 current 失败: ${manifest.id}")
            }
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
    fun rollback(context: Context, manifest: ModuleManifest): Boolean =
        // 保留三参数文件回滚兼容性；它只适用于 current/last_good 同名。
        // ModuleManager 使用带旧清单的入口，才支持两个版本 fileName 不同。
        rollbackInternal(context, manifest, manifest)

    /**
     * 将当前版本回滚到 [lastGoodManifest] 描述的版本。
     *
     * current 与 last_good 可能使用不同 fileName，不能用当前清单去定位旧文件。
     */
    fun rollback(
        context: Context,
        currentManifest: ModuleManifest,
        lastGoodManifest: ModuleManifest
    ): Boolean = rollbackInternal(context, currentManifest, lastGoodManifest)

    private fun rollbackInternal(
        context: Context,
        currentManifest: ModuleManifest,
        lastGoodManifest: ModuleManifest
    ): Boolean {
        if (!BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
            Log.d(TAG, "事务安装已禁用，无法回滚")
            return false
        }
        
        return try {
            if (currentManifest.id != lastGoodManifest.id ||
                !isSafeFileName(currentManifest.fileName) ||
                !isSafeFileName(lastGoodManifest.fileName)
            ) {
                Log.w(TAG, "回滚清单不匹配或缺少文件名: ${currentManifest.id}")
                return false
            }
            val currentFile = getCurrentFile(context, currentManifest)
            val lastGoodFile = getLastGoodFile(context, lastGoodManifest)
            
            if (!lastGoodFile.exists()) {
                Log.w(TAG, "没有last_good版本可回滚: ${currentManifest.id}")
                return false
            }
            
            // 1. 将当前版本移入quarantine；移动失败时必须保留 current，不能删除唯一副本。
            val hadCurrent = currentFile.isFile
            val quarantineFile = if (hadCurrent) getQuarantineFile(context, currentManifest) else null
            if (hadCurrent && quarantineFile != null) {
                Log.d(TAG, "将问题模块移入quarantine: ${currentManifest.id}")
                if (!moveFile(currentFile, quarantineFile)) {
                    Log.w(TAG, "移入quarantine失败，保留 current 并中止回滚: ${currentManifest.id}")
                    return false
                }
            }
            
            // 2. 从last_good恢复到current
            val restoredCurrentFile = getCurrentFile(context, lastGoodManifest)
            Log.d(TAG, "从last_good恢复到current: ${currentManifest.id} -> ${lastGoodManifest.fileName}")
            if (!copyFile(lastGoodFile, restoredCurrentFile)) {
                Log.e(TAG, "从last_good恢复失败: ${currentManifest.id}")
                if (quarantineFile != null && quarantineFile.isFile) {
                    if (currentFile.exists()) currentFile.delete()
                    if (!moveFile(quarantineFile, currentFile)) {
                        Log.e(TAG, "回滚失败后无法恢复原 current: ${currentManifest.id}")
                    }
                }
                return false
            }
            
            restoredCurrentFile.setReadOnly()
            
            Log.d(TAG, "回滚成功: ${currentManifest.id} -> v${lastGoodManifest.versionCode}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "回滚异常: ${currentManifest.id}, ${e.message}", e)
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
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.backup-${System.nanoTime()}")
            source.copyTo(temporary, overwrite = true)
            val previousTarget = File(target.parentFile, ".${target.name}.previous-${System.nanoTime()}")
            if (target.exists()) {
                target.setWritable(true, false)
                if (!target.renameTo(previousTarget)) {
                    temporary.delete()
                    return false
                }
            }
            if (!temporary.renameTo(target)) {
                if (previousTarget.exists()) previousTarget.renameTo(target)
                temporary.delete()
                false
            } else {
                previousTarget.delete()
                true
            }
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
            target.parentFile?.mkdirs()
            if (source.exists()) {
                // ModuleDownloader marks completed APK staging files read-only before
                // this transaction runs; normalize the source for older file systems.
                source.setWritable(true, false)
            }
            if (target.exists()) {
                target.setWritable(true, false)
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
                target.setWritable(true, false)
                target.delete()
            }
            source.copyTo(target, overwrite = true)
            true
        } catch (e: IOException) {
            Log.e(TAG, "复制文件失败: ${e.message}")
            false
        }
    }

    /**
     * 恢复一次性旧安装迁移中隔离的未知 current。
     *
     * [quarantinedPrevious] 为空时没有可定位的旧文件，只隔离当前失败的新文件；
     * 这会保留“已安装但无可信旧清单”的状态语义，不伪造回滚快照。
     */
    internal fun restoreUntrackedCurrent(
        context: Context,
        manifest: ModuleManifest,
        quarantinedPrevious: File?
    ): Boolean {
        if (!isSafeFileName(manifest.fileName)) return false
        return try {
            val currentFile = getCurrentFile(context, manifest)
            if (currentFile.exists()) {
                val rejectedFile = getQuarantineFile(context, manifest)
                if (!moveFile(currentFile, rejectedFile)) return false
            }
            if (quarantinedPrevious == null) {
                true
            } else if (quarantinedPrevious.isFile) {
                moveFile(quarantinedPrevious, currentFile)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复未知旧 current 失败: ${manifest.id}, ${e.message}", e)
            false
        }
    }

    private fun isSafeFileName(fileName: String): Boolean =
        fileName.isNotBlank() && fileName != "." && fileName != ".." &&
            File(fileName).name == fileName
}
