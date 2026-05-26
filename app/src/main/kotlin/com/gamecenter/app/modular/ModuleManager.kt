package com.gamecenter.app.modular

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ModuleManager(
    private val moduleDao: ModuleDao,
    private val downloader: ModuleDownloader,
    private val cacheManager: ModuleCacheManager,
    private val moduleLoader: ModuleLoader
) {
    companion object {
        private const val TAG = "ModuleManager"
    }

    fun observeAllModules(): Flow<List<ModuleEntity>> {
        return moduleDao.getAllModules()
    }

    suspend fun downloadModule(
        moduleInfo: ModuleInfo,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val existing = moduleDao.getModuleById(moduleInfo.moduleId)

        if (existing != null && existing.state == ModuleState.DOWNLOADING.name) {
            Log.w(TAG, "Module ${moduleInfo.moduleId} is already downloading")
            return@withContext DownloadResult(
                moduleInfo.moduleId,
                false,
                error = "模块正在下载中"
            )
        }

        if (existing != null && existing.versionCode == moduleInfo.versionCode
            && existing.state == ModuleState.VERIFIED.name
        ) {
            Log.d(TAG, "Module ${moduleInfo.moduleId} v${moduleInfo.versionCode} already verified")
            return@withContext DownloadResult(
                moduleInfo.moduleId,
                true,
                filePath = existing.localPath
            )
        }

        val entity = ModuleEntity(
            moduleId = moduleInfo.moduleId,
            moduleName = moduleInfo.moduleName,
            versionCode = moduleInfo.versionCode,
            versionName = moduleInfo.versionName,
            downloadUrl = moduleInfo.downloadUrl,
            fileSize = moduleInfo.fileSize,
            sha256 = moduleInfo.sha256,
            entryClass = moduleInfo.entryClass,
            minAppVersion = moduleInfo.minAppVersion,
            description = moduleInfo.description,
            state = ModuleState.DOWNLOADING.name
        )
        moduleDao.insertOrUpdate(entity)

        val existingSize = downloader.getExistingDownloadedSize(
            moduleInfo.moduleId,
            moduleInfo.versionCode
        )

        val result = downloader.download(moduleInfo, existingSize) { progress ->
            moduleDao.updateDownloadedSize(progress.moduleId, progress.downloadedBytes)
            onProgress(progress)
        }

        if (result.success && result.filePath != null) {
            moduleDao.updateStateAndPath(
                moduleId = moduleInfo.moduleId,
                state = ModuleState.DOWNLOADED.name,
                path = result.filePath
            )

            val verified = cacheManager.verifyModule(moduleInfo.moduleId)
            if (!verified) {
                DownloadResult(
                    moduleInfo.moduleId,
                    false,
                    error = "完整性校验失败"
                )
            } else {
                cacheManager.cleanOldVersions(moduleInfo.moduleId, moduleInfo.versionCode)
                DownloadResult(
                    moduleInfo.moduleId,
                    true,
                    filePath = result.filePath
                )
            }
        } else {
            moduleDao.updateState(moduleInfo.moduleId, ModuleState.ERROR.name)
            result
        }
    }

    suspend fun loadModule(moduleId: String): LoadResult = withContext(Dispatchers.IO) {
        if (moduleLoader.isModuleLoaded(moduleId)) {
            val existing = moduleLoader.getLoadedModule(moduleId)
            return@withContext LoadResult(moduleId, true, existing)
        }

        val entity = moduleDao.getModuleById(moduleId)
        if (entity == null) {
            Log.e(TAG, "Module not found: $moduleId")
            return@withContext LoadResult(moduleId, false, error = "模块未注册")
        }

        if (entity.state != ModuleState.VERIFIED.name && entity.state != ModuleState.DOWNLOADED.name) {
            Log.e(TAG, "Module not ready for loading: $moduleId, state=${entity.state}")
            return@withContext LoadResult(moduleId, false, error = "模块未就绪，当前状态: ${entity.state}")
        }

        if (entity.localPath.isEmpty()) {
            Log.e(TAG, "Module local path is empty: $moduleId")
            return@withContext LoadResult(moduleId, false, error = "模块文件路径为空")
        }

        val loadResult = moduleLoader.loadModule(moduleId, entity.localPath, entity.entryClass)

        if (loadResult.success) {
            moduleDao.updateState(moduleId, ModuleState.LOADED.name)
            Log.d(TAG, "Module loaded and state updated: $moduleId")
        } else {
            moduleDao.updateState(moduleId, ModuleState.ERROR.name)
            Log.e(TAG, "Module load failed: $moduleId, error=${loadResult.error}")
        }

        loadResult
    }

    fun startModule(moduleId: String): Boolean {
        val module = moduleLoader.getLoadedModule(moduleId)
        if (module == null) {
            Log.w(TAG, "Cannot start module, not loaded: $moduleId")
            return false
        }
        return try {
            module.start()
            Log.d(TAG, "Module started: $moduleId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start module: $moduleId", e)
            false
        }
    }

    fun stopModule(moduleId: String): Boolean {
        val module = moduleLoader.getLoadedModule(moduleId)
        if (module == null) {
            Log.w(TAG, "Cannot stop module, not loaded: $moduleId")
            return false
        }
        return try {
            module.stop()
            Log.d(TAG, "Module stopped: $moduleId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop module: $moduleId", e)
            false
        }
    }

    suspend fun unloadModule(moduleId: String) {
        moduleLoader.unloadModule(moduleId)
        moduleDao.updateState(moduleId, ModuleState.VERIFIED.name)
        Log.d(TAG, "Module unloaded and state reverted: $moduleId")
    }

    fun getLoadedModule(moduleId: String): ModuleInterface? {
        return moduleLoader.getLoadedModule(moduleId)
    }

    fun isModuleLoaded(moduleId: String): Boolean {
        return moduleLoader.isModuleLoaded(moduleId)
    }

    fun getModuleResources(moduleId: String): ModuleResourceLoader.ModuleResources? {
        return moduleLoader.let { loader ->
            loader.getResourceLoader().getResources(moduleId)
        }
    }

    suspend fun cancelDownload(moduleId: String) {
        downloader.cancel(moduleId)
        moduleDao.updateState(moduleId, ModuleState.NOT_DOWNLOADED.name)
        Log.d(TAG, "Download cancelled: $moduleId")
    }

    suspend fun removeModule(moduleId: String) {
        if (moduleLoader.isModuleLoaded(moduleId)) {
            moduleLoader.unloadModule(moduleId)
        }
        downloader.cancel(moduleId)
        cacheManager.cleanModule(moduleId)
        Log.d(TAG, "Module removed: $moduleId")
    }

    suspend fun isModuleReady(moduleId: String): Boolean {
        return cacheManager.isModuleReady(moduleId)
    }

    suspend fun getModuleEntity(moduleId: String): ModuleEntity? {
        return cacheManager.getModuleEntity(moduleId)
    }

    suspend fun getModuleFile(moduleId: String): java.io.File? {
        val entity = moduleDao.getModuleById(moduleId) ?: return null
        return cacheManager.getModuleFile(moduleId, entity.versionCode)
    }

    suspend fun needsUpdate(moduleId: String, remoteVersionCode: Int): Boolean {
        return cacheManager.needsUpdate(moduleId, remoteVersionCode)
    }

    suspend fun getCacheSize(): Long {
        return cacheManager.getCacheSize()
    }

    suspend fun cleanAllCaches() {
        moduleLoader.getAllLoadedModuleIds().forEach { moduleId ->
            moduleLoader.unloadModule(moduleId)
        }
        cacheManager.cleanAllModules()
    }

    suspend fun registerModule(moduleInfo: ModuleInfo) {
        val existing = moduleDao.getModuleById(moduleInfo.moduleId)
        if (existing == null || existing.versionCode < moduleInfo.versionCode) {
            val entity = ModuleEntity(
                moduleId = moduleInfo.moduleId,
                moduleName = moduleInfo.moduleName,
                versionCode = moduleInfo.versionCode,
                versionName = moduleInfo.versionName,
                downloadUrl = moduleInfo.downloadUrl,
                fileSize = moduleInfo.fileSize,
                sha256 = moduleInfo.sha256,
                entryClass = moduleInfo.entryClass,
                minAppVersion = moduleInfo.minAppVersion,
                description = moduleInfo.description
            )
            moduleDao.insertOrUpdate(entity)
            Log.d(TAG, "Module registered: ${moduleInfo.moduleId} v${moduleInfo.versionName}")
        }
    }
}
