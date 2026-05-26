package com.gamecenter.app.modular

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.File

class ModuleResourceLoader(
    private val context: Context
) {
    companion object {
        private const val TAG = "ModuleResourceLoader"
    }

    private val resourceCache = mutableMapOf<String, ModuleResources>()

    fun loadResources(moduleId: String, apkPath: String): ModuleResources? {
        resourceCache[moduleId]?.let { return it }

        if (!File(apkPath).exists()) {
            Log.e(TAG, "APK file not found: $apkPath")
            return null
        }

        return try {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getDeclaredMethod(
                "addAssetPath", String::class.java
            )
            addAssetPath.invoke(assetManager, apkPath)

            val hostResources = context.applicationContext.resources
            val displayMetrics = hostResources.displayMetrics
            val config = hostResources.configuration

            @Suppress("DEPRECATION")
            val moduleResources = Resources(assetManager, displayMetrics, config)

            val moduleRes = ModuleResources(
                moduleId = moduleId,
                resources = moduleResources,
                assetManager = assetManager,
                packageName = getModulePackageName(moduleResources)
            )

            resourceCache[moduleId] = moduleRes
            Log.d(TAG, "Resources loaded for module: $moduleId, package: ${moduleRes.packageName}")
            moduleRes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load resources for module: $moduleId", e)
            null
        }
    }

    fun getResources(moduleId: String): ModuleResources? {
        return resourceCache[moduleId]
    }

    fun unloadResources(moduleId: String) {
        resourceCache.remove(moduleId)
        Log.d(TAG, "Resources unloaded for module: $moduleId")
    }

    private fun getModulePackageName(resources: Resources): String {
        return try {
            val appResId = resources.getIdentifier(
                "app_name", "string", "android"
            )
            if (appResId != 0) {
                resources.getResourcePackageName(appResId)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    data class ModuleResources(
        val moduleId: String,
        val resources: Resources,
        val assetManager: AssetManager,
        val packageName: String
    ) {
        fun getString(resName: String): String? {
            return try {
                val resId = resources.getIdentifier(resName, "string", packageName)
                if (resId != 0) resources.getString(resId) else null
            } catch (e: Exception) {
                null
            }
        }

        fun getDrawable(resName: String): Drawable? {
            return try {
                val resId = resources.getIdentifier(resName, "drawable", packageName)
                if (resId != 0) resources.getDrawable(resId, null) else null
            } catch (e: Exception) {
                null
            }
        }

        fun getLayoutResId(resName: String): Int {
            return try {
                resources.getIdentifier(resName, "layout", packageName)
            } catch (e: Exception) {
                0
            }
        }

        fun getColor(resName: String): Int {
            return try {
                val resId = resources.getIdentifier(resName, "color", packageName)
                if (resId != 0) resources.getColor(resId, null) else 0
            } catch (e: Exception) {
                0
            }
        }

        fun getDimension(resName: String): Float {
            return try {
                val resId = resources.getIdentifier(resName, "dimen", packageName)
                if (resId != 0) resources.getDimension(resId) else 0f
            } catch (e: Exception) {
                0f
            }
        }

        fun getResId(resName: String, defType: String): Int {
            return try {
                resources.getIdentifier(resName, defType, packageName)
            } catch (e: Exception) {
                0
            }
        }
    }
}
