package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import dalvik.system.DexClassLoader
import java.io.File

object ModuleLoader {

    private const val TAG = "ModuleLoader"

    private val loadedModules = mutableMapOf<String, ModuleInterface>()
    private val classLoaders = mutableMapOf<String, ClassLoader>()
    private val resourceLoaders = mutableMapOf<String, com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources>()

    fun loadModule(context: Context, manifest: ModuleManifest): ModuleInterface? {
        if (manifest.entryClass.isEmpty()) {
            Log.d(TAG, "模块 ${manifest.id} 没有动态入口类，跳过 Dex 加载")
            return null
        }

        // P3: 优先从 current/ 读取已安装的模块，兼容旧 modules/ 目录
        val moduleFile = ModuleDownloader.getModuleFileCompat(context, manifest)
        if (manifest.builtIn && (
                !moduleFile.exists() ||
                ModuleManager.getInstalledVersionCode(context, manifest.id) <= manifest.builtInVersionCode
            )
        ) {
            return loadBuiltInModule(context, manifest)
        }
        if (!moduleFile.exists()) {
            Log.e(TAG, "模块文件不存在: ${moduleFile.absolutePath}")
            // BUG-007 修复：文件不存在时主动清理 SP 中的安装状态缓存，避免脏数据持续存在。
            // 之前仅记录日志后返回，导致 SP 仍认为该模块已安装，模块商店统计与实际不符。
            if (!manifest.builtIn) {
                ModuleManager.removeInstalledModulePublic(context, manifest.id)
            }
            return null
        }

        val installedVersion = ModuleManager.getInstalledVersionCode(context, manifest.id)
        val alreadyLoaded = loadedModules.containsKey(manifest.id)
        if (alreadyLoaded && installedVersion >= manifest.versionCode) {
            Log.d(TAG, "模块 ${manifest.id} 已加载且版本一致，返回缓存实例")
            return loadedModules[manifest.id]
        }

        if (alreadyLoaded) {
            Log.d(TAG, "模块 ${manifest.id} 版本变更($installedVersion → ${manifest.versionCode})，重新加载")
            unloadModule(manifest.id)
        }

        clearOptimizedDex(context, moduleFile)

        // Batch 21 安全修复：内置模块允许空 SHA 跳过校验；非内置模块必须配置且匹配 SHA-256
        if (!ModuleVerifier.verifySha256(moduleFile, manifest.sha256, allowEmpty = manifest.builtIn)) {
            Log.e(TAG, "模块 SHA-256 校验失败: ${manifest.id}")
            if (manifest.sha256.isNotEmpty()) {
                moduleFile.delete()
            }
            // BUG-007 修复：SHA-256 校验失败并删除文件后，同步清理 SP 安装状态缓存。
            // 之前仅删除文件不清理 SP，下次 ensureInstalledCache 仍会因 SP 残留而认为模块已安装。
            if (!manifest.builtIn) {
                ModuleManager.removeInstalledModulePublic(context, manifest.id)
            }
            return null
        }

        if (!verifyApkSignature(context, moduleFile)) {
            Log.e(TAG, "模块签名比对失败，拒绝装载: ${manifest.id}")
            moduleFile.delete()
            // BUG-007 修复：签名校验失败并删除文件后，同步清理 SP 安装状态缓存。
            if (!manifest.builtIn) {
                ModuleManager.removeInstalledModulePublic(context, manifest.id)
            }
            return null
        }

        if (!ModuleVerifier.verifyDexFile(moduleFile)) {
            Log.e(TAG, "模块文件格式无效: ${manifest.id}")
            // P3: 加载失败时尝试回滚到 last_good 版本
            attemptRollback(context, manifest, "Dex文件验证失败")
            return null
        }

        prepareDexFileForLoading(moduleFile)

        return try {
            val optimizedDir = File(context.cacheDir, "modules_opt")
            if (!optimizedDir.exists()) optimizedDir.mkdirs()

            val libraryDir = File(context.filesDir, "modules_lib")
            if (!libraryDir.exists()) libraryDir.mkdirs()

            val classLoader = DexClassLoader(
                moduleFile.absolutePath,
                optimizedDir.absolutePath,
                libraryDir.absolutePath,
                context.classLoader
            )

            val entryClass = classLoader.loadClass(manifest.entryClass)
            val instance = entryClass.getDeclaredConstructor().newInstance()

            if (instance !is ModuleInterface) {
                Log.e(TAG, "入口类未实现 ModuleInterface: ${manifest.entryClass}")
                // P3: 加载失败时尝试回滚到 last_good 版本
                attemptRollback(context, manifest, "入口类未实现 ModuleInterface")
                return null
            }

            instance.init(context)

            try {
                val resLoader = com.gamecenter.app.modular.ModuleResourceLoader(context)
                val res = resLoader.loadResources(manifest.id, moduleFile.absolutePath)
                if (res != null) {
                    resourceLoaders[manifest.id] = res
                    Log.d(TAG, "模块 ${manifest.id} 资源加载成功")
                }
            } catch (e: Exception) {
                Log.e(TAG, "模块 ${manifest.id} 资源加载失败: ${e.message}", e)
            }

            loadedModules[manifest.id] = instance
            classLoaders[manifest.id] = classLoader

            // P1/P4: 注册到模块注册中心
            try {
                com.gamecenter.app.core.common.ModuleRegistry.registerLoadedModule(manifest.id, instance)
                com.gamecenter.app.core.common.ModuleRegistry.registerManifest(manifest)
            } catch (e: Exception) {
                Log.w(TAG, "注册模块到 ModuleRegistry 失败: ${manifest.id}", e)
            }

            Log.d(TAG, "模块 ${manifest.id} 加载成功: ${manifest.entryClass}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "模块加载失败 ${manifest.id}: ${e.message}", e)
            // P3: 加载失败时尝试回滚到 last_good 版本
            attemptRollback(context, manifest, "加载异常: ${e.message}")
            null
        }
    }
    
    /**
     * P3: 尝试回滚模块到 last_good 版本。
     * 
     * @param context 上下文
     * @param manifest 模块清单
     * @param reason 回滚原因（用于日志）
     */
    private fun attemptRollback(context: Context, manifest: ModuleManifest, reason: String) {
        if (!BuildConfig.ENABLE_TRANSACTIONAL_INSTALL) {
            Log.d(TAG, "事务安装已禁用，跳过回滚: ${manifest.id}")
            return
        }
        
        Log.w(TAG, "模块加载失败，尝试回滚: ${manifest.id}, 原因: $reason")
        
        val rollbackSuccess = com.gamecenter.app.modules.store.TransactionInstaller.rollback(context, manifest)
        if (rollbackSuccess) {
            Log.d(TAG, "模块回滚成功: ${manifest.id}")
        } else {
            Log.e(TAG, "模块回滚失败: ${manifest.id}")
        }
    }

    private fun prepareDexFileForLoading(moduleFile: File) {
        if (!moduleFile.extension.equals("apk", ignoreCase = true) &&
            !moduleFile.extension.equals("dex", ignoreCase = true)) {
            return
        }
        if (moduleFile.canWrite()) {
            moduleFile.setWritable(false, false)
            moduleFile.setReadOnly()
            Log.d(TAG, "模块文件已切换为只读以允许 DexClassLoader 加载: ${moduleFile.name}")
        }
    }

    private fun clearOptimizedDex(context: Context, moduleFile: File) {
        try {
            val optimizedDir = File(context.cacheDir, "modules_opt")
            if (optimizedDir.exists()) {
                val baseName = moduleFile.nameWithoutExtension
                optimizedDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(baseName)) {
                        file.delete()
                        Log.d(TAG, "清除优化DEX缓存: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清除优化DEX缓存失败: ${e.message}")
        }
    }

    fun startModule(context: Context, moduleId: String): Boolean {
        val module = loadedModules[moduleId] ?: return false
        return try {
            module.start(context)
            Log.d(TAG, "模块 $moduleId 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "模块启动失败 $moduleId: ${e.message}")
            false
        }
    }

    private fun loadBuiltInModule(context: Context, manifest: ModuleManifest): ModuleInterface? {
        if (manifest.entryClass.isEmpty()) return null
        return try {
            val entryClass = context.classLoader.loadClass(manifest.entryClass)
            val instance = entryClass.getDeclaredConstructor().newInstance()

            if (instance !is ModuleInterface) {
                Log.e(TAG, "Built-in entry does not implement ModuleInterface: ${manifest.entryClass}")
                return null
            }

            instance.init(context)
            loadedModules[manifest.id] = instance
            classLoaders[manifest.id] = context.classLoader

            // P1/P4: 注册到模块注册中心（内置模块同样需要贡献导航等能力）
            try {
                com.gamecenter.app.core.common.ModuleRegistry.registerLoadedModule(manifest.id, instance)
                com.gamecenter.app.core.common.ModuleRegistry.registerManifest(manifest)
            } catch (e: Exception) {
                Log.w(TAG, "注册内置模块到 ModuleRegistry 失败: ${manifest.id}", e)
            }

            Log.d(TAG, "Built-in module loaded: ${manifest.id} -> ${manifest.entryClass}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Built-in module load failed ${manifest.id}: ${e.message}", e)
            null
        }
    }

    fun stopModule(moduleId: String) {
        val module = loadedModules[moduleId] ?: return
        try {
            module.stop()
            Log.d(TAG, "模块 $moduleId 已停止")
        } catch (e: Exception) {
            Log.w(TAG, "模块停止异常 $moduleId: ${e.message}")
        }
    }

    fun unloadModule(moduleId: String) {
        stopModule(moduleId)
        loadedModules.remove(moduleId)
        classLoaders.remove(moduleId)
        resourceLoaders.remove(moduleId)
        // P1/P4: 从模块注册中心注销
        try {
            com.gamecenter.app.core.common.ModuleRegistry.unregisterLoadedModule(moduleId)
        } catch (e: Exception) {
            Log.w(TAG, "从 ModuleRegistry 注销模块失败: $moduleId", e)
        }
        Log.d(TAG, "模块 $moduleId 已卸载")
    }

    fun getModuleResources(moduleId: String): com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources? {
        return resourceLoaders[moduleId]
    }

    /** 获取已加载模块的 DexClassLoader，用于启动模块内的 Activity */
    fun getModuleClassLoader(moduleId: String): ClassLoader? = classLoaders[moduleId]

    fun getModule(moduleId: String): ModuleInterface? {
        return loadedModules[moduleId]
    }

    /** 返回已加载模块的原始实例（用于跨接口转换，如 FeatureModule） */
    fun getLoadedInstance(moduleId: String): Any? {
        return loadedModules[moduleId]
    }

    fun isModuleLoaded(moduleId: String): Boolean {
        return loadedModules.containsKey(moduleId)
    }

    fun getLoadedModuleIds(): Set<String> {
        return loadedModules.keys.toSet()
    }

    fun getClassLoader(moduleId: String): ClassLoader? {
        return classLoaders[moduleId]
    }

    private fun verifyApkSignature(context: Context, apkFile: File): Boolean {
        return when (val result = ModuleSignatureVerifier.verify(apkFile, context)) {
            ModuleSignatureVerifier.Result.Success -> true
            is ModuleSignatureVerifier.Result.Failure -> {
                Log.e(TAG, "模块发布证书校验失败: ${result.reason}")
                false
            }
            is ModuleSignatureVerifier.Result.Warning -> {
                Log.e(TAG, "模块发布证书校验告警按失败处理: ${result.reason}")
                false
            }
        }
    }
}
