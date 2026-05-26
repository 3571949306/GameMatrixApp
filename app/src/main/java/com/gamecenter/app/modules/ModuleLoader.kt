package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

object ModuleLoader {

    private const val TAG = "ModuleLoader"

    private val loadedModules = mutableMapOf<String, ModuleInterface>()
    private val classLoaders = mutableMapOf<String, ClassLoader>()
    private val resourceLoaders = mutableMapOf<String, com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources>()

    fun loadModule(context: Context, manifest: ModuleManifest): ModuleInterface? {
        val moduleFile = ModuleDownloader.getModuleFile(context, manifest)
        if (manifest.builtIn && (
                !moduleFile.exists() ||
                ModuleManager.getInstalledVersionCode(context, manifest.id) <= manifest.builtInVersionCode
            )
        ) {
            return loadBuiltInModule(context, manifest)
        }
        if (!moduleFile.exists()) {
            Log.e(TAG, "模块文件不存在: ${moduleFile.absolutePath}")
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

        if (!ModuleVerifier.verifySha256(moduleFile, manifest.sha256)) {
            Log.e(TAG, "模块 SHA-256 校验失败: ${manifest.id}")
            if (manifest.sha256.isNotEmpty()) {
                moduleFile.delete()
            }
            return null
        }

        if (!ModuleVerifier.verifyDexFile(moduleFile)) {
            Log.e(TAG, "模块文件格式无效: ${manifest.id}")
            return null
        }

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

            Log.d(TAG, "模块 ${manifest.id} 加载成功: ${manifest.entryClass}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "模块加载失败 ${manifest.id}: ${e.message}", e)
            null
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
        Log.d(TAG, "模块 $moduleId 已卸载")
    }

    fun getModuleResources(moduleId: String): com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources? {
        return resourceLoaders[moduleId]
    }

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
}
