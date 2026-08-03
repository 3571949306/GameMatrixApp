package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.modules.store.model.StoreCatalog
import com.gamecenter.app.modules.store.model.StoreModule

/**
 * 远程目录权威管理器（P2）。
 *
 * 职责：
 * 1. 将远程/缓存目录作为模块状态的权威来源
 * 2. assets/catalog.json 仅作为"恢复种子"，在没有任何缓存时使用
 * 3. 清理本地已安装但目录中不再存在的模块
 * 4. 标记目录中 required 但未安装的模块
 *
 * 注意：
 * - 远程目录来自 StoreCatalogRepository（已支持 ETag、签名验证、降级策略）
 * - 本类不直接发起网络请求，只消费目录数据
 */
object RemoteCatalogAuthorityManager {

    private const val TAG = "RemoteCatalogAuthority"

    /**
     * 同步本地模块状态与权威目录。
     *
     * @param context 上下文
     * @param catalog 当前权威目录
     * @return 同步结果
     */
    fun synchronizeWithAuthority(context: Context, catalog: StoreCatalog): SyncResult {
        // 冷启动 NPE 修复：catalog.modules 在极端情况下（Java 反射构造、反序列化等）可能为 null，
        // 此处统一兜底为 emptyList，避免 Iterator.next() on null 崩溃。
        val catalogModules = catalog.modules ?: emptyList()
        Log.d(TAG, "开始同步权威目录: catalogV=${catalog.catalogVersion}, modules=${catalogModules.size}")

        val installedIds = runCatching {
            com.gamecenter.app.modules.ModuleManager.getInstalledModuleIds(context)
        }.getOrDefault(emptySet())
        val catalogIds = catalogModules.map { it.id }.toSet()

        val orphanModules = installedIds.filter { it !in catalogIds }
        val requiredMissing = catalogModules.filter {
            it.required && !installedIds.contains(it.id) && it.enabled
        }

        // 清理不再在目录中的模块（如果是基础框架模块则跳过）
        var removedCount = 0
        val allManifests = runCatching {
            com.gamecenter.app.modules.ModuleManager.getManifests()
        }.getOrDefault(emptyMap())
        for (orphanId in orphanModules) {
            val manifest = allManifests[orphanId]
            if (manifest != null && (manifest.isBaseFramework || manifest.builtIn)) {
                Log.d(TAG, "跳过基础/预装模块清理: $orphanId")
                continue
            }
            Log.w(TAG, "模块 $orphanId 不在权威目录中，准备卸载")
            try {
                com.gamecenter.app.modules.ModuleManager.uninstallModule(context, orphanId)
                removedCount++
            } catch (e: Exception) {
                Log.e(TAG, "卸载模块 $orphanId 失败: ${e.message}", e)
            }
        }

        // 注册权威目录到 ModuleRegistry（P1）
        for (module in catalogModules) {
            try {
                val manifest = module.toModuleManifest()
                com.gamecenter.app.core.common.ModuleRegistry.registerManifest(manifest)
            } catch (e: Exception) {
                Log.e(TAG, "注册模块清单失败: ${module.id}", e)
            }
        }

        // 过滤出当前设备可用的模块（兼容 App 版本）
        val hostVersionCode = BuildConfig.VERSION_CODE
        val compatibleModules = catalogModules.filter {
            it.toModuleManifest().isCompatibleWithHost(hostVersionCode)
        }

        Log.d(
            TAG,
            "目录同步完成: 兼容=${compatibleModules.size}, 缺失必装=${requiredMissing.size}, 清理=${removedCount}"
        )

        return SyncResult(
            compatibleModules = compatibleModules,
            requiredMissing = requiredMissing,
            orphanModulesRemoved = removedCount
        )
    }

    /**
     * 判断缓存目录是否可作为权威来源。
     * 当缓存目录版本不低于 assets 种子版本时，优先使用缓存。
     */
    fun isCacheAuthoritative(cachedCatalog: StoreCatalog?, assetSeedCatalog: StoreCatalog?): Boolean {
        if (cachedCatalog == null) return false
        if (assetSeedCatalog == null) return true
        return cachedCatalog.catalogVersion >= assetSeedCatalog.catalogVersion
    }

    data class SyncResult(
        /** 当前设备兼容的模块列表 */
        val compatibleModules: List<StoreModule>,
        /** 必装但未安装的模块列表 */
        val requiredMissing: List<StoreModule>,
        /** 被清理的孤立模块数量 */
        val orphanModulesRemoved: Int
    )
}
