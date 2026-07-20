package com.gamecenter.app.core.common

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment

/**
 * 模块注册中心（P1 Shell 基础设施）。
 *
 * 统一管理已安装/已加载模块的生命周期、导航贡献和查询。
 * 不直接处理下载安装（由 core:modulestore / app:modules 负责），
 * 只维护"已安装模块能贡献什么"这一运行时视图。
 */
object ModuleRegistry {

    private const val TAG = "ModuleRegistry"

    /** 已安装模块清单映射（ID → ModuleManifest） */
    private val manifests = LinkedHashMap<String, ModuleManifest>()

    /** 已加载模块实例映射（ID → ModuleInterface） */
    private val loadedModules = LinkedHashMap<String, ModuleInterface>()

    /** 导航贡献缓存 */
    private val navigationContributions = mutableListOf<NavigationContributionEntry>()

    data class NavigationContributionEntry(
        val moduleId: String,
        val contribution: ModuleNavigationContribution
    )

    @Synchronized
    fun registerManifest(manifest: ModuleManifest) {
        manifests[manifest.id] = manifest
        Log.d(TAG, "注册模块清单: ${manifest.id} v${manifest.versionCode}")
    }

    @Synchronized
    fun unregisterManifest(moduleId: String) {
        manifests.remove(moduleId)
        loadedModules.remove(moduleId)
        navigationContributions.removeAll { it.moduleId == moduleId }
        Log.d(TAG, "注销模块清单: $moduleId")
    }

    @Synchronized
    fun registerLoadedModule(moduleId: String, instance: ModuleInterface) {
        loadedModules[moduleId] = instance
        Log.d(TAG, "注册已加载模块实例: $moduleId")

        // 收集导航贡献
        if (instance is FeatureModule) {
            // FeatureModule 的贡献需要在 Context 下创建，这里只缓存引用，
            // 实际查询时通过懒加载创建。
        }
    }

    @Synchronized
    fun unregisterLoadedModule(moduleId: String) {
        loadedModules.remove(moduleId)
        navigationContributions.removeAll { it.moduleId == moduleId }
        Log.d(TAG, "注销已加载模块实例: $moduleId")
    }

    @Synchronized
    fun getManifest(moduleId: String): ModuleManifest? = manifests[moduleId]

    @Synchronized
    fun getLoadedModule(moduleId: String): ModuleInterface? = loadedModules[moduleId]

    @Synchronized
    fun getLoadedFeatureModule(moduleId: String): FeatureModule? =
        loadedModules[moduleId] as? FeatureModule

    @Synchronized
    fun getAllManifests(): List<ModuleManifest> = manifests.values.toList()

    @Synchronized
    fun getInstalledModuleIds(): Set<String> = manifests.keys.toSet()

    @Synchronized
    fun isInstalled(moduleId: String): Boolean = manifests.containsKey(moduleId)

    @Synchronized
    fun isLoaded(moduleId: String): Boolean = loadedModules.containsKey(moduleId)

    /**
     * 收集所有已加载 FeatureModule 的导航贡献。
     * 注意：需要在主线程调用，因为会创建 Fragment 实例。
     */
    @Synchronized
    fun collectNavigationContributions(context: Context): List<NavigationContributionEntry> {
        navigationContributions.clear()
        for ((moduleId, instance) in loadedModules) {
            if (instance !is FeatureModule) continue
            try {
                val contributions = instance.getNavigationContributions(context)
                for (contribution in contributions) {
                    if (contribution.isEnabled()) {
                        navigationContributions.add(NavigationContributionEntry(moduleId, contribution))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "收集模块 $moduleId 导航贡献失败: ${e.message}", e)
            }
        }
        return navigationContributions.sortedBy { it.contribution.getOrder() }
    }

    /**
     * 获取指定槽位的导航贡献。
     */
    @Synchronized
    fun getNavigationContributionsForSlot(context: Context, slot: NavigationSlot): List<NavigationContributionEntry> {
        return collectNavigationContributions(context).filter { it.contribution.getSlot() == slot }
    }

    /**
     * P6: 收集所有已加载模块提供的 Unity 启动器。
     *
     * 收集顺序：
     * 1. 已实现 UnityModuleLauncher 接口的已加载模块实例
     * 2. 已实现 FeatureModule 且 createUnityLauncher() 非空的模块
     *
     * 结果按模块 ID 去重，优先保留直接实现 UnityModuleLauncher 的实例。
     */
    @Synchronized
    fun getUnityLaunchers(context: Context): List<UnityModuleLauncher> {
        val result = mutableMapOf<String, UnityModuleLauncher>()

        for ((moduleId, instance) in loadedModules) {
            // 1. 直接实现 UnityModuleLauncher 接口
            if (instance is UnityModuleLauncher) {
                result[moduleId] = instance
                continue
            }

            // 2. 通过 FeatureModule.createUnityLauncher() 提供
            if (instance is FeatureModule) {
                try {
                    val launcher = instance.createUnityLauncher()
                    if (launcher != null && !result.containsKey(moduleId)) {
                        result[moduleId] = launcher
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取模块 $moduleId 的 Unity 启动器失败: ${e.message}", e)
                }
            }
        }

        return result.values.toList()
    }

    /**
     * 创建导航贡献对应的 Fragment。
     */
    fun createFragmentForContribution(context: Context, entry: NavigationContributionEntry): Fragment? {
        return try {
            entry.contribution.createFragment(context)
        } catch (e: Exception) {
            Log.e(TAG, "创建导航 Fragment 失败: ${entry.contribution.getContributionId()}", e)
            null
        }
    }

    @Synchronized
    fun clear() {
        manifests.clear()
        loadedModules.clear()
        navigationContributions.clear()
    }
}
