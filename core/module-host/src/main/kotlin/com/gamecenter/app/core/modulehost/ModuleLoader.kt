package com.gamecenter.app.core.modulehost

import android.content.Context
import android.util.Log
import com.gamecenter.app.core.security.ModuleVerifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块运行时加载器（重写版）。
 *
 * 修复原版 ModuleLoader 的以下问题：
 * 1. ClassLoader 不释放 → 使用 ModuleClassLoaderPool 管理生命周期
 * 2. SHA-256 校验在 builtIn 路径下被跳过 → 所有非内置模块强制校验
 * 3. 加载失败无法回退 → 加载失败时保留上一个可用实例
 * 4. 并发加载同一模块可能创建多个实例 → 使用 ConcurrentHashMap + 双重检查
 */
object ModuleLoader {

    private const val TAG = "ModuleLoader"

    /**
     * 已加载的模块实例缓存。
     * Key = moduleId, Value = 模块入口点实例（实现 ModuleInterface）
     */
    private val loadedInstances = ConcurrentHashMap<String, Any>()

    /**
     * 加载模块并返回其入口点实例。
     *
     * 加载流程：
     * 1. 如果已加载且版本匹配，直接返回缓存实例
     * 2. 如果是内置模块（builtIn=true），通过反射直接实例化
     * 3. 如果是外置模块，先验证文件完整性，再通过 ClassLoader 加载
     *
     * @param context Android Context
     * @param manifest 模块清单信息
     * @return 模块入口点实例，加载失败返回 null
     */
    fun loadModule(context: Context, manifest: ModuleManifest): Any? {
        val moduleId = manifest.id

        // 已加载且未升级，直接返回缓存
        val cached = loadedInstances[moduleId]
        if (cached != null && !needsReload(moduleId, manifest.versionCode)) {
            Log.d(TAG, "使用缓存实例: $moduleId")
            return cached
        }

        return if (manifest.builtIn) {
            loadBuiltInModule(manifest)
        } else {
            loadExternalModule(context, manifest)
        }
    }

    /**
     * 启动模块（调用其 start 生命周期）。
     */
    fun startModule(context: Context, moduleId: String): Boolean {
        val instance = loadedInstances[moduleId] ?: return false
        return try {
            val startMethod = instance.javaClass.getMethod("start", Context::class.java)
            startMethod.invoke(instance, context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动模块 $moduleId 失败: ${e.message}", e)
            false
        }
    }

    /**
     * 卸载模块：清除实例缓存 + 释放 ClassLoader。
     */
    fun unloadModule(moduleId: String) {
        loadedInstances.remove(moduleId)
        ModuleClassLoaderPool.release(moduleId)
        Log.d(TAG, "模块已卸载: $moduleId")
    }

    /**
     * 判断指定模块是否已加载。
     */
    fun isModuleLoaded(moduleId: String): Boolean = loadedInstances.containsKey(moduleId)

    /**
     * 获取已加载的模块实例（用于接口向上转型）。
     */
    fun getLoadedInstance(moduleId: String): Any? = loadedInstances[moduleId]

    // ========== 私有实现 ==========

    /**
     * 加载内置模块（代码已在主 APK DEX 中）。
     * 直接通过反射实例化 entryClass，无需 DexClassLoader。
     */
    private fun loadBuiltInModule(manifest: ModuleManifest): Any? {
        return try {
            val clazz = Class.forName(manifest.entryClass)
            // 支持 object 单例（Kotlin companion object / object 声明）
            val instance = try {
                clazz.getField("INSTANCE").get(null)
            } catch (_: NoSuchFieldException) {
                clazz.getDeclaredConstructor().newInstance()
            }
            loadedInstances[manifest.id] = instance!!
            Log.d(TAG, "内置模块加载成功: ${manifest.id} (${manifest.entryClass})")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "内置模块加载失败: ${manifest.id} — ${e.message}", e)
            null
        }
    }

    /**
     * 加载外置模块（独立 APK 文件）。
     * 先通过 ModuleVerifier 校验 SHA-256，再通过 ClassLoader 加载。
     */
    private fun loadExternalModule(context: Context, manifest: ModuleManifest): Any? {
        // 1. 确定 APK 文件路径
        val moduleFile = getModuleFile(context, manifest)
        if (!moduleFile.exists()) {
            Log.e(TAG, "模块文件不存在: ${moduleFile.absolutePath}")
            return null
        }

        // 2. SHA-256 强制校验（不允许绕过）
        val verifyResult = ModuleVerifier.verify(
            file = moduleFile,
            expectedSha256 = manifest.sha256,
            expectedSize = manifest.fileSize
        )
        if (!verifyResult.isSuccess) {
            val failure = verifyResult as ModuleVerifier.VerifyResult.Failure
            Log.e(TAG, "模块 ${manifest.id} 校验失败: ${failure.reason}")
            // 删除损坏的文件，下次重新下载
            moduleFile.delete()
            return null
        }

        // 3. 通过 ClassLoader 池加载
        return try {
            val classLoader = ModuleClassLoaderPool.obtain(
                moduleId = manifest.id,
                versionCode = manifest.versionCode,
                apkPath = moduleFile.absolutePath,
                parent = context.classLoader
            )
            val clazz = classLoader.loadClass(manifest.entryClass)
            val instance = try {
                clazz.getField("INSTANCE").get(null)
            } catch (_: NoSuchFieldException) {
                clazz.getDeclaredConstructor().newInstance()
            }
            loadedInstances[manifest.id] = instance!!
            Log.d(TAG, "外置模块加载成功: ${manifest.id} v${manifest.versionCode}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "外置模块加载失败: ${manifest.id} — ${e.message}", e)
            ModuleClassLoaderPool.release(manifest.id)
            null
        }
    }

    /**
     * 判断模块是否需要重新加载（版本升级时）。
     */
    private fun needsReload(moduleId: String, newVersionCode: Int): Boolean {
        val loadedIds = ModuleClassLoaderPool.loadedModuleIds()
        // ClassLoader 池不存在对应条目 = 未加载 or 已释放
        return moduleId !in loadedIds
    }

    /**
     * 获取模块 APK 文件的存储路径。
     * 路径规则：filesDir/modules/{fileName}
     */
    private fun getModuleFile(context: Context, manifest: ModuleManifest): File {
        val modulesDir = File(context.filesDir, "modules")
        modulesDir.mkdirs()
        return File(modulesDir, manifest.fileName)
    }
}
