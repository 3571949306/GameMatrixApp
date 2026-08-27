package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.modulehost.ModuleLoader as CoreModuleLoader
import com.gamecenter.app.core.modulehost.ModuleResourceLoader
import java.io.File

/**
 * 模块加载器兼容门面。
 *
 * 模块加载实现已统一收敛到核心层 [CoreModuleLoader]（统一真源）；本对象保留
 * `com.gamecenter.app.modules.ModuleLoader` 类名与全部旧签名，原因：
 * 1. 已发布的动态模块（wrongbook / klotski / chinesechess 等）在编译期以宿主 API
 *    为参照，运行时经父加载器命中宿主该类，删除类名会导致这些模块运行期崩溃；
 * 2. 宿主内 `ModuleManager`、`ModuleShellFragment`、`DynamicGameActivity`、
 *    `ModuleSecurityTest` 等调用面不必逐点改写。
 *
 * 本门面只承担"内置/外置装载编排"（有外置更新的内置模块走外置加载，否则宿主直载）与
 * 失败后的宿主侧清理/回滚，不包含任何 Dex/资源/签名实现。
 */
object ModuleLoader {

    private const val TAG = "ModuleLoader"

    /**
     * 宿主注入清理/回滚回调（首次模块加载前调用一次即可）。
     *
     * @param onVerifyFailure 完整性或证书校验失败：删除损坏文件并清理安装状态（SP）。
     * @param onLoadFailureRollback 加载失败：事务回滚到 last_good。
     */
    fun attachHostCleanup(
        onVerifyFailure: ((manifest: ModuleManifest, file: File) -> Unit)?,
        onLoadFailureRollback: ((manifest: ModuleManifest) -> Unit)?
    ) {
        CoreModuleLoader.onVerifyFailure = onVerifyFailure
        CoreModuleLoader.onLoadFailureRollback = onLoadFailureRollback
    }

    /**
     * 加载模块（内置/外置统一编排入口）。
     *
     * 隔离策略（P1 关闭隔离缺口）：
     * - fileName 为空 → 宿主内嵌代码，允许宿主 classloader 直载（合法内嵌，随宿主发布）；
     * - fileName 非空 → 一律走外置 DexClassLoader 加载（含预装内置 APK），
     *   文件缺失或清单缺少 SHA-256 时**不再回退宿主直载**（缺口已关闭），直接判定失败，
     *   由调用方引导重新提取/下载。
     */
    fun loadModule(context: Context, manifest: ModuleManifest): ModuleInterface? {
        val moduleId = manifest.id
        val appCtx = context.applicationContext
        val moduleFile = ModuleDownloader.getModuleFileCompat(appCtx, manifest)

        return when {
            manifest.fileName.isNotEmpty() -> {
                if (!shouldLoadExternal(manifest, moduleFile.exists())) {
                    Log.e(TAG, "模块文件缺失或清单无 SHA-256，拒绝装载（内置模块亦不允许回退宿主副本）: $moduleId")
                    ModuleManager.removeInstalledModulePublic(appCtx, moduleId)
                    null
                } else {
                    CoreModuleLoader.loadModule(appCtx, manifest, moduleFile) as? ModuleInterface
                }
            }
            manifest.builtIn -> CoreModuleLoader.loadHostEmbeddedModule(manifest) as? ModuleInterface
            else -> {
                // 无文件且非内置：仅清理 SP 安装状态缓存，返回 null。
                Log.e(TAG, "模块文件不存在，清理安装状态: ${moduleFile.absolutePath}")
                ModuleManager.removeInstalledModulePublic(appCtx, moduleId)
                null
            }
        }
    }

    /**
     * 外置装载判定（纯函数，便于单元测试）。
     *
     * 关闭隔离缺口后：只要配置了模块文件（fileName 非空）就必须走外置 DexClassLoader，
     * 且要求文件存在、清单配置了非空 SHA-256（内置模块同样必须配置哈希）。
     */
    internal fun shouldLoadExternal(manifest: ModuleManifest, fileExists: Boolean): Boolean =
        manifest.fileName.isNotEmpty() && fileExists && manifest.sha256.isNotBlank()

    fun startModule(context: Context, moduleId: String): Boolean =
        CoreModuleLoader.startModule(context, moduleId)

    fun stopModule(moduleId: String) = CoreModuleLoader.stopModule(moduleId)

    fun unloadModule(moduleId: String) = CoreModuleLoader.unloadModule(moduleId)

    fun isModuleLoaded(moduleId: String): Boolean = CoreModuleLoader.isModuleLoaded(moduleId)

    fun getLoadedInstance(moduleId: String): Any? = CoreModuleLoader.getLoadedInstance(moduleId)

    /** 返回已加载模块的原始实例（用于跨接口转换，如 FeatureModule）。 */
    fun getLoadedInstanceCompat(moduleId: String): Any? = CoreModuleLoader.getLoadedInstance(moduleId)

    fun getModule(moduleId: String): ModuleInterface? = CoreModuleLoader.getModule(moduleId)

    fun getLoadedModuleIds(): Set<String> = CoreModuleLoader.getLoadedModuleIds()

    /** 获取已加载模块的 ClassLoader（宿主与新版调用名）。 */
    fun getClassLoader(moduleId: String): ClassLoader? = CoreModuleLoader.getClassLoader(moduleId)

    /** 旧调用名兼容：动态模块（Klotski/ChineseChess）使用 INSTANCE.getModuleClassLoader。 */
    fun getModuleClassLoader(moduleId: String): ClassLoader? = CoreModuleLoader.getClassLoader(moduleId)

    /** 获取已加载模块的独立资源（供动态模块 inflate 模块自带布局）。 */
    fun getModuleResources(moduleId: String): ModuleResourceLoader.ModuleResources? =
        CoreModuleLoader.getModuleResources(moduleId)
}