package com.gamecenter.app.modular

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

data class LoadResult(
    val moduleId: String,
    val success: Boolean,
    val moduleInterface: ModuleInterface? = null,
    val error: String? = null
)

class ModuleLoader(
    private val context: Context,
    private val resourceLoader: ModuleResourceLoader
) {
    companion object {
        private const val TAG = "ModuleLoader"
        private const val OPTIMIZE_DIR = "dex_opt"
    }

    private val loadedModules = mutableMapOf<String, ModuleInterface>()
    private val classLoaderCache = mutableMapOf<String, DexClassLoader>()

    fun loadModule(moduleId: String, apkPath: String, entryClass: String): LoadResult {
        loadedModules[moduleId]?.let {
            Log.d(TAG, "Module already loaded: $moduleId")
            return LoadResult(moduleId, true, it)
        }

        if (!File(apkPath).exists()) {
            val msg = "APK file not found: $apkPath"
            Log.e(TAG, msg)
            return LoadResult(moduleId, false, error = msg)
        }

        return try {
            val classLoader = createClassLoader(moduleId, apkPath)
            classLoaderCache[moduleId] = classLoader

            val loadedClass = classLoader.loadClass(entryClass)
            val instance = loadedClass.getDeclaredConstructor().newInstance()

            if (instance !is ModuleInterface) {
                val msg = "$entryClass does not implement ModuleInterface"
                Log.e(TAG, msg)
                return LoadResult(moduleId, false, error = msg)
            }

            instance.init(context)

            val moduleResources = resourceLoader.loadResources(moduleId, apkPath)
            if (moduleResources == null) {
                Log.w(TAG, "Resource loading skipped for module: $moduleId")
            }

            loadedModules[moduleId] = instance
            Log.d(TAG, "Module loaded successfully: $moduleId -> $entryClass")
            LoadResult(moduleId, true, instance)
        } catch (e: ClassNotFoundException) {
            val msg = "Entry class not found: $entryClass"
            Log.e(TAG, msg, e)
            LoadResult(moduleId, false, error = msg)
        } catch (e: ClassCastException) {
            val msg = "Entry class does not implement ModuleInterface: $entryClass"
            Log.e(TAG, msg, e)
            LoadResult(moduleId, false, error = msg)
        } catch (e: Exception) {
            val msg = "Failed to load module $moduleId: ${e.message}"
            Log.e(TAG, msg, e)
            LoadResult(moduleId, false, error = msg)
        }
    }

    fun getLoadedModule(moduleId: String): ModuleInterface? {
        return loadedModules[moduleId]
    }

    fun isModuleLoaded(moduleId: String): Boolean {
        return loadedModules.containsKey(moduleId)
    }

    fun unloadModule(moduleId: String) {
        loadedModules[moduleId]?.let { module ->
            try {
                module.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping module $moduleId: ${e.message}")
            }
            loadedModules.remove(moduleId)
        }

        classLoaderCache.remove(moduleId)
        resourceLoader.unloadResources(moduleId)
        Log.d(TAG, "Module unloaded: $moduleId")
    }

    fun getAllLoadedModuleIds(): Set<String> {
        return loadedModules.keys.toSet()
    }

    fun getResourceLoader(): ModuleResourceLoader {
        return resourceLoader
    }

    private fun createClassLoader(moduleId: String, apkPath: String): DexClassLoader {
        val optDir = File(context.filesDir, OPTIMIZE_DIR)
        if (!optDir.exists()) optDir.mkdirs()

        val moduleOptDir = File(optDir, moduleId)
        if (!moduleOptDir.exists()) moduleOptDir.mkdirs()

        val libDir = File(context.filesDir, "lib").apply { mkdirs() }

        val parentClassLoader = context.classLoader

        return DexClassLoader(
            apkPath,
            moduleOptDir.absolutePath,
            libDir.absolutePath,
            parentClassLoader
        )
    }
}
