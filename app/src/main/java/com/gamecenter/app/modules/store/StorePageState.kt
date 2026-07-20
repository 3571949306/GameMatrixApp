package com.gamecenter.app.modules.store

import com.gamecenter.app.modules.ModuleManifest
import com.gamecenter.app.modules.store.model.StoreCatalog
import com.gamecenter.app.modules.store.model.StoreUiConfig

/**
 * 商店页面状态（P2.6）。
 *
 * 由 [StoreViewModel] 持有，Activity 通过观察 ViewModel 获取最新状态。
 *
 * 设计原则：
 * - 不可变 data class，每次状态变化生成新实例
 * - 包含 UI 渲染所需的全部数据：目录、UI 配置、模块列表、当前分类、搜索词
 * - 不包含 View 引用，便于后续迁移到动态 APK
 */
data class StorePageState(
    /** 当前远程目录（可能为 null，表示尚未加载或全部降级失败） */
    val catalog: StoreCatalog? = null,
    /** 当前 UI 配置（始终非空，降级时使用 defaultConfig） */
    val uiConfig: StoreUiConfig = StoreUiConfig.defaultConfig(),
    /** 当前展示的模块列表（已合并 catalog 与本地，已应用搜索/筛选/排序） */
    val modules: List<ModuleManifest> = emptyList(),
    /** 当前选中的分类 ID */
    val currentCategory: String = "game",
    /** 当前搜索关键词 */
    val searchKeyword: String = "",
    /** 是否正在加载 */
    val isLoading: Boolean = false,
    /** 错误信息（null 表示无错误） */
    val error: String? = null,
    /** 已安装模块 ID 集合 */
    val installedModuleIds: Set<String> = emptySet()
) {
    /** 是否有可更新模块 */
    val hasUpdates: Boolean
        get() = modules.any { it.versionCode > 0 && it.versionCode in 1..Int.MAX_VALUE }

    /** 推荐模块（featured=true 且 enabled=true） */
    val featuredModules: List<ModuleManifest>
        get() = modules.filter { it.featured && it.enabled }
}
