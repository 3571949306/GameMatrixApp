package com.gamecenter.app.modular

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModuleCacheManager(
    private val context: Context,
    private val moduleDao: ModuleDao,
    private val downloader: ModuleDownloader
) {
    companion object {
        private const val TAG = "ModuleCacheManager"
        private const val MODULE_DIR = "dynamic_modules"
    }

    private val moduleRootDir: File
        get() = File(context.filesDir, MODULE_DIR)

    suspend fun getModuleFile(moduleId: String, versionCode: Int): File? {
        return withContext(Dispatchers.IO) {
            val entity = moduleDao.getModuleById(moduleId) ?: return@withContext null
            if (entity.state != ModuleState.VERIFIED.name && entity.state != ModuleState.LOADED.name) {
                return@withContext null
            }
            val file = File(entity.localPath)
            if (file.exists() && file.length() > 0) file else null
        }
    }

    suspend fun verifyModule(moduleId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val entity = moduleDao.getModuleById(moduleId) ?: return@withContext false
            val file = File(entity.localPath)

            if (!file.exists()) {
                Log.w(TAG, "Module file not found: ${entity.localPath}")
                moduleDao.updateState(moduleId, ModuleState.ERROR.name)
                return@withContext false
            }

            if (entity.fileSize > 0 && file.length() != entity.fileSize) {
                Log.w(TAG, "File size mismatch for $moduleId: expected=${entity.fileSize}, actual=${file.length()}")
                file.delete()
                moduleDao.updateState(moduleId, ModuleState.ERROR.name)
                return@withContext false
            }

            if (entity.sha256.isNotEmpty()) {
                val actualSha256 = downloader.computeSha256(file)
                if (!entity.sha256.equals(actualSha256, ignoreCase = true)) {
                    Log.w(TAG, "SHA-256 mismatch for $moduleId: expected=${entity.sha256}, actual=$actualSha256")
                    file.delete()
                    moduleDao.updateState(moduleId, ModuleState.ERROR.name)
                    return@withContext false
                }
            }

            moduleDao.updateState(moduleId, ModuleState.VERIFIED.name)
            Log.d(TAG, "Module verified: $moduleId")
            true
        }
    }

    suspend fun cleanModule(moduleId: String) {
        withContext(Dispatchers.IO) {
            val entity = moduleDao.getModuleById(moduleId)
            if (entity != null) {
                val moduleDir = File(moduleRootDir, moduleId)
                if (moduleDir.exists()) {
                    moduleDir.deleteRecursively()
                }
                moduleDao.deleteById(moduleId)
                Log.d(TAG, "Module cleaned: $moduleId")
            }
        }
    }

    suspend fun cleanOldVersions(moduleId: String, keepVersionCode: Int) {
        withContext(Dispatchers.IO) {
            val moduleDir = File(moduleRootDir, moduleId)
            if (!moduleDir.exists()) return@withContext

            moduleDir.listFiles()?.forEach { file ->
                val versionInName = extractVersionFromFileName(file.name)
                if (versionInName != null && versionInName < keepVersionCode) {
                    file.delete()
                    Log.d(TAG, "Cleaned old version file: ${file.name}")
                }
            }
        }
    }

    suspend fun cleanAllModules() {
        withContext(Dispatchers.IO) {
            val allModules = moduleDao.getModulesByState(ModuleState.NOT_DOWNLOADED.name) +
                    moduleDao.getModulesByState(ModuleState.ERROR.name)
            allModules.forEach { entity ->
                cleanModule(entity.moduleId)
            }
            if (moduleRootDir.exists()) {
                moduleRootDir.deleteRecursively()
            }
            Log.d(TAG, "All module caches cleaned")
        }
    }

    suspend fun getCacheSize(): Long {
        return withContext(Dispatchers.IO) {
            if (!moduleRootDir.exists()) return@withContext 0L
            calculateDirSize(moduleRootDir)
        }
    }

    suspend fun needsUpdate(moduleId: String, remoteVersionCode: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val entity = moduleDao.getModuleById(moduleId) ?: return@withContext true
            entity.versionCode < remoteVersionCode
        }
    }

    suspend fun isModuleReady(moduleId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val entity = moduleDao.getModuleById(moduleId) ?: return@withContext false
            entity.state == ModuleState.VERIFIED.name || entity.state == ModuleState.LOADED.name
        }
    }

    suspend fun getModuleEntity(moduleId: String): ModuleEntity? {
        return withContext(Dispatchers.IO) {
            moduleDao.getModuleById(moduleId)
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun extractVersionFromFileName(fileName: String): Int? {
        val regex = Regex("-(\\d+)\\.apk$")
        val match = regex.find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
