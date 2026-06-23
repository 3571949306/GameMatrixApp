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
            // ===== 反射加载外部 APK 资源（私有 API） =====
            // 说明：
            // 1. AssetManager.addAssetPath 是 Android 私有 API（@hide 标注），未在公开 SDK 暴露
            // 2. 目前该 API 在所有 Android 版本（含 14/15）均可用，是加载外部 APK 资源的事实标准方案
            // 3. 未来 Android 版本可能通过非 SDK 接口黑名单（darkgreylist/maxtargetapi）封禁该 API，
            //    届时需切换备选方案
            // 4. 备选方案：使用 dalvik.system.PathClassLoader 加载 APK，再通过 PackageManager
            //    获取资源；缺点是效率较低（需解析整个 APK 包信息），且对未安装 APK 支持有限
            // 5. 反射失败时记录详细日志并返回 null，调用方应处理资源加载失败的情况
            @Suppress("DiscouragedPrivateApi")
            val assetManager: AssetManager = try {
                val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod(
                    "addAssetPath", String::class.java
                )
                val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
                // addAssetPath 返回 cookie（正整数）表示成功，返回 -1 或 0 表示路径无效/加载失败
                val result = addAssetPathMethod.invoke(am, apkPath) as? Int ?: -1
                if (result <= 0) {
                    Log.e(
                        TAG,
                        "addAssetPath returned invalid result: $result, apk: $apkPath, module: $moduleId"
                    )
                    return null
                }
                am
            } catch (e: NoSuchMethodException) {
                // 私有 API 在未来 Android 版本可能被封禁，此处捕获后返回 null 避免崩溃
                Log.e(
                    TAG,
                    "AssetManager.addAssetPath not found on this Android version, " +
                        "private API may have been blocked. Module: $moduleId", e
                )
                return null
            } catch (e: IllegalAccessException) {
                Log.e(
                    TAG,
                    "Illegal access to AssetManager.addAssetPath for module: $moduleId", e
                )
                return null
            } catch (e: java.lang.reflect.InvocationTargetException) {
                Log.e(
                    TAG,
                    "AssetManager.addAssetPath threw target exception for module: $moduleId, " +
                        "apk: $apkPath", e
                )
                return null
            } catch (e: InstantiationException) {
                Log.e(
                    TAG,
                    "Failed to instantiate AssetManager for module: $moduleId", e
                )
                return null
            }

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
            // 兜底捕获，确保任何未预期异常不会导致应用崩溃
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
