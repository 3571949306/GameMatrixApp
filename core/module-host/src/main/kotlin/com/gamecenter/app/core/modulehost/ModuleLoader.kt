package com.gamecenter.app.core.modulehost

import android.content.Context
import android.util.Log
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.common.ModuleRegistry
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.core.security.ModuleVerifier
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块运行时加载器（统一真源）。
 *
 * 重写版，收敛宿主各历史加载器（app/modules/ModuleLoader、app/modular/ModuleLoader）
 * 的能力并集中到核心层：
 * 1. 所有模块实例缓存集中在 [loaded]，携带版本、ClassLoader 与资源，避免重复建立；
 * 2. 外置模块强制 SHA-256 + 文件大小校验，APK 发布证书钉扎（[ModuleSignatureVerifier]），
 *    缺失/损坏由 [onVerifyFailure] 回调通知宿主清理；
 * 3. 加载失败保留上一可用实例、并可通过 [onLoadFailureRollback] 回滚 last_good；
 * 4. 通过 [ModuleClassLoaderPool] 管理 ClassLoader 生命周期（API 26+ 显式 close）；
 * 5. 入口实例化后执行 ModuleInterface.init 并注册到 [ModuleRegistry]。
 *
 * 内置模块（宿主内嵌代码）与"内置但被外置更新"的判定由宿主层 [loadModule] 编排，
 * 本类只负责两种确定性装载路径：
 * - [loadModule]：给定已存在模块文件的外置装载（含完整校验）；
 * - [loadBuiltInModule]：宿主 classloader 直载（不重复校验）。
 */
object ModuleLoader {

    private const val TAG = "ModuleLoader"

    /** 已加载模块条目：实例 + 版本 + ClassLoader + 资源。 */
    class LoadedEntry(
        val versionCode: Int,
        val instance: Any,
        val classLoader: ClassLoader?,
        val resources: ModuleResourceLoader.ModuleResources?
    )

    private val loaded = ConcurrentHashMap<String, LoadedEntry>()

    /**
     * 资源加载器单例。模块加载由 CoreModulePreloader 串行线程与 ModuleManager 后台线程驱动，
     * 与原实现保持一致的并发语义即可。
     */
    private var resourceLoaderInstance: ModuleResourceLoader? = null

    /**
     * 完整性/证书校验失败回调（宿主注入）：负责删除损坏文件并清理安装状态（如 SP 缓存）。
     */
    @Volatile
    var onVerifyFailure: ((manifest: ModuleManifest, file: File) -> Unit)? = null

    /**
     * 加载失败回滚回调（宿主注入）：负责事务回滚到 last_good。
     */
    @Volatile
    var onLoadFailureRollback: ((manifest: ModuleManifest) -> Unit)? = null

    private fun resourceLoader(context: Context): ModuleResourceLoader {
        val current = resourceLoaderInstance
        if (current != null) return current
        synchronized(this) {
            return resourceLoaderInstance
                ?: ModuleResourceLoader(context.applicationContext)
                    .also { resourceLoaderInstance = it }
        }
    }

    /**
     * 外置模块装载（要求文件已存在并通过编排判定）。
     *
     * @param context Android Context
     * @param manifest 模块清单
     * @param moduleFile 已定位的模块 APK/DEX 文件（current/ 或兼容目录）
     * @return 模块入口实例，失败返回 null
     */
    fun loadModule(context: Context, manifest: ModuleManifest, moduleFile: File): Any? {
        val moduleId = manifest.id

        loaded[moduleId]?.let { cached ->
            if (cached.versionCode >= manifest.versionCode) {
                Log.d(TAG, "使用缓存实例: $moduleId")
                return cached.instance
            }
            Log.d(TAG, "模块 $moduleId 版本变更，先卸载旧实例: ${cached.versionCode} → ${manifest.versionCode}")
            unloadModule(moduleId)
        }

        if (!moduleFile.exists()) {
            Log.e(TAG, "模块文件不存在: ${moduleFile.absolutePath}")
            onVerifyFailure?.invoke(manifest, moduleFile)
            return null
        }

        // 1. 完整性校验：SHA-256 + 文件大小 强制（P1 关闭隔离缺口：内置模块同样必须配置哈希，
        //    清单缺 SHA 直接拒绝，不允许"免检装载"）
        if (manifest.sha256.isBlank()) {
            Log.e(TAG, "模块 $moduleId 清单缺少 SHA-256（内置模块同样必须配置），拒绝装载: ${moduleFile.name}")
            onVerifyFailure?.invoke(manifest, moduleFile)
            return null
        }
        val verifyResult = ModuleVerifier.verify(moduleFile, manifest.sha256, manifest.fileSize)
        if (!verifyResult.isSuccess) {
            val reason = (verifyResult as? ModuleVerifier.VerifyResult.Failure)?.reason ?: "未知原因"
            Log.e(TAG, "模块 $moduleId 校验失败: $reason")
            onVerifyFailure?.invoke(manifest, moduleFile)
            return null
        }

        // 2. APK 发布证书钉扎；.zip 等归档不在此路径（其信任由 Catalog 下载路径断言）
        if (moduleFile.name.endsWith(".apk", ignoreCase = true)) {
            when (val signature = ModuleSignatureVerifier.verify(moduleFile, context)) {
                ModuleSignatureVerifier.Result.Success -> Unit
                is ModuleSignatureVerifier.Result.Warning,
                is ModuleSignatureVerifier.Result.Failure -> {
                    val reason = when (signature) {
                        is ModuleSignatureVerifier.Result.Warning -> signature.reason
                        is ModuleSignatureVerifier.Result.Failure -> signature.reason
                        else -> "未知原因"
                    }
                    Log.e(TAG, "模块 $moduleId 发布证书校验失败: $reason")
                    onVerifyFailure?.invoke(manifest, moduleFile)
                    return null
                }
            }
        }

        // 3. 文件头检查（dex 或 zip/apk），防止任意文件被当作模块装载
        if (!looksLikeDexOrZip(moduleFile)) {
            Log.e(TAG, "模块文件格式无效: ${moduleFile.absolutePath}")
            onLoadFailureRollback?.invoke(manifest)
            return null
        }

        setReadOnly(moduleFile)

        // 4. 经 ClassLoader 池装载并实例化
        return try {
            val classLoader = ModuleClassLoaderPool.obtain(
                moduleId = moduleId,
                versionCode = manifest.versionCode,
                apkPath = moduleFile.absolutePath,
                parent = context.classLoader
            )
            val clazz = classLoader.loadClass(manifest.entryClass)
            val instance = instantiate(clazz)
            if (instance !is ModuleInterface) {
                Log.e(TAG, "入口类未实现 ModuleInterface: ${manifest.entryClass}")
                ModuleClassLoaderPool.release(moduleId)
                onLoadFailureRollback?.invoke(manifest)
                return null
            }
            instance.init(context)

            // 5. 资源加载（失败不阻断模块：无资源模块可继续以宿主资源运行）
            val resources = runCatching {
                resourceLoader(context).loadResources(moduleId, moduleFile.absolutePath)
            }.getOrNull()

            loaded[moduleId] = LoadedEntry(manifest.versionCode, instance, classLoader, resources)
            ModuleRegistry.registerLoadedModule(moduleId, instance)
            ModuleRegistry.registerManifest(manifest)
            Log.d(TAG, "外置模块加载成功: $moduleId v${manifest.versionCode}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "模块加载失败 $moduleId: ${e.message}", e)
            ModuleClassLoaderPool.release(moduleId)
            onLoadFailureRollback?.invoke(manifest)
            null
        }
    }

    /**
     * 宿主内嵌模块装载（仅 fileName 为空的真·内嵌代码）。
     *
     * P1 关闭隔离缺口：fileName 非空的模块（含预装 APK）一律走 [loadModule] 外置
     * DexClassLoader 路径；本路径被误调用时直接拒绝，绝不回退宿主陈旧副本。
     */
    fun loadHostEmbeddedModule(manifest: ModuleManifest): Any? {
        val moduleId = manifest.id
        if (manifest.fileName.isNotEmpty()) {
            Log.e(TAG, "hostEmbedded 路径拒绝 fileName 非空模块（隔离策略）: $moduleId (${manifest.fileName})")
            return null
        }
        loaded[moduleId]?.let { cached ->
            if (cached.versionCode >= manifest.versionCode) return cached.instance
            unloadModule(moduleId)
        }

        return try {
            val clazz = Class.forName(manifest.entryClass)
            val instance = instantiate(clazz)
            if (instance !is ModuleInterface) {
                Log.e(TAG, "Built-in entry does not implement ModuleInterface: ${manifest.entryClass}")
                return null
            }
            val versionCode = if (manifest.builtInVersionCode > 0) manifest.builtInVersionCode else manifest.versionCode
            loaded[moduleId] = LoadedEntry(
                versionCode = versionCode,
                instance = instance,
                classLoader = clazz.classLoader,
                resources = null
            )
            ModuleRegistry.registerLoadedModule(moduleId, instance)
            ModuleRegistry.registerManifest(manifest)
            Log.d(TAG, "内置模块加载成功: $moduleId -> ${manifest.entryClass}")
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Built-in module load failed $moduleId: ${e.message}", e)
            null
        }
    }

    // ========== 生命周期与查询 ==========

    fun startModule(context: Context, moduleId: String): Boolean {
        val entry = loaded[moduleId] ?: return false
        return try {
            ((entry.instance) as? ModuleInterface)?.start(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "模块启动失败 $moduleId: ${e.message}")
            false
        }
    }

    fun stopModule(moduleId: String) {
        (loaded[moduleId]?.instance as? ModuleInterface)?.let { instance ->
            try {
                instance.stop()
                Log.d(TAG, "模块 $moduleId 已停止")
            } catch (e: Exception) {
                Log.w(TAG, "模块停止异常 $moduleId: ${e.message}")
            }
        }
    }

    fun unloadModule(moduleId: String) {
        stopModule(moduleId)
        loaded.remove(moduleId)
        ModuleClassLoaderPool.release(moduleId)
        try {
            ModuleRegistry.unregisterLoadedModule(moduleId)
        } catch (e: Exception) {
            Log.w(TAG, "从 ModuleRegistry 注销模块失败: $moduleId", e)
        }
        Log.d(TAG, "模块已卸载: $moduleId")
    }

    fun isModuleLoaded(moduleId: String): Boolean = loaded.containsKey(moduleId)

    fun getLoadedInstance(moduleId: String): Any? = loaded[moduleId]?.instance

    fun getModule(moduleId: String): ModuleInterface? = loaded[moduleId]?.instance as? ModuleInterface

    fun getLoadedModuleIds(): Set<String> = loaded.keys.toSet()

    fun getClassLoader(moduleId: String): ClassLoader? = loaded[moduleId]?.classLoader

    fun getModuleResources(moduleId: String): ModuleResourceLoader.ModuleResources? =
        loaded[moduleId]?.resources

    // ========== 私有实现 ==========

    /** 兼容普通类（Java 入口）与 Kotlin object（INSTANCE 单例）两种入口形态。 */
    private fun instantiate(clazz: Class<*>): Any {
        return try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            clazz.getField("INSTANCE").get(null)
        }
    }

    /** 只读化模块文件，满足 DexClassLoader 读取要求。 */
    private fun setReadOnly(file: File) {
        try {
            if (file.canWrite()) {
                file.setWritable(false, false)
                file.setReadOnly()
                Log.d(TAG, "模块文件已切换为只读: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "切换模块文件只读失败: ${file.name}")
        }
    }

    /** 校验文件头为 dex（dex\n 035）或 zip/apk（PK\x03\x04）。 */
    private fun looksLikeDexOrZip(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(8)
                val read = input.read(magic)
                if (read < 4) return false
                val isDex = magic[0] == 'd'.code.toByte() &&
                    magic[1] == 'e'.code.toByte() &&
                    magic[2] == 'x'.code.toByte() &&
                    magic[3] == '\n'.code.toByte()
                val isZip = magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()
                isDex || isZip
            }
        } catch (e: Exception) {
            Log.w(TAG, "模块文件头校验异常: ${file.name} - ${e.message}")
            false
        }
    }

    /** 测试辅助：清空全部加载状态。 */
    fun clearForTest() {
        loaded.values.forEach { entry ->
            try {
                (entry.instance as? ModuleInterface)?.stop()
            } catch (_: Exception) {
            }
        }
        loaded.clear()
        ModuleClassLoaderPool.releaseAll()
        try {
            ModuleRegistry.clear()
        } catch (_: Exception) {
        }
    }
}