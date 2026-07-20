package com.gamecenter.app.modules.store

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManifest
import com.gamecenter.app.modules.store.model.StoreSection

/**
 * 商店区块渲染器宿主回调（P2.4）。
 *
 * Renderer 不直接操作模块文件、不直接发起下载、不直接处理点击路由，
 * 所有这些动作都通过 Host 回调到 ModuleStoreActivity / StoreViewModel 统一处理。
 *
 * 这样设计的目的：
 * - Renderer 保持薄层，便于后续迁移到动态 APK
 * - 动作集中路由，便于白名单校验和审计
 * - Activity 仍持有全部业务逻辑，不破坏现有功能
 */
interface StoreRendererHost {
    /** 宿主 Context（通常为 Activity） */
    val hostContext: Context

    /** 当前模块列表（已合并 catalog 与本地的最终展示列表） */
    fun currentModules(): List<ModuleManifest>

    /** 当前已安装模块 ID 集合 */
    fun installedModuleIds(): Set<String>

    /** 触发动作路由（白名单见 StoreActionRouter） */
    fun dispatchAction(action: String, params: Map<String, String>)

    /** 重新拉取目录与 UI 配置（用户点击刷新按钮时触发） */
    fun triggerRefresh()

    /** 切换当前分类（用户点击 category_tabs 时触发） */
    fun switchCategory(categoryId: String)
}

/**
 * 商店区块渲染器接口（P2.4）。
 *
 * 实现方负责把一个 [StoreSection] 应用到宿主视图上：
 * - 显示/隐藏对应区块
 * - 应用 columns / order / params
 * - 绑定点击事件，通过 [StoreRendererHost.dispatchAction] 路由
 *
 * 严禁在 Renderer 中：
 * - 直接 startActivity / 反射类
 * - 直接操作模块文件 / 发起下载
 * - 解析 params 中的 Intent URI / 类名 / Shell / JavaScript
 */
interface StoreSectionRenderer {
    /** 该 Renderer 支持的 section type */
    fun supports(type: String): Boolean

    /**
     * 渲染 section 到容器。
     *
     * @param section 待渲染的区块配置
     * @param container Activity 中的根容器（各 Renderer 根据 section.id 查找自己的视图）
     * @param host 宿主回调
     * @return true 表示渲染成功；false 表示该区块应被隐藏（如 enabled=false 或视图不存在）
     */
    fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean
}

/**
 * Renderer 注册表。
 *
 * - 未知 type 仅记录日志并跳过，不导致整个页面崩溃
 * - 单个区块渲染失败由 try-catch 兜底
 * - 重复 section.id 取第一个（按 order 排序后）
 *
 * Feature flag STORE_SECTION_RENDERER 关闭时，Activity 不调用 dispatchRender，
 * 仍走原有硬编码布局路径。
 */
object StoreSectionRendererRegistry {

    private const val TAG = "StoreSectionRenderer"

    private val renderers: List<StoreSectionRenderer> = listOf(
        HeroBannerRenderer(),
        SearchBarRenderer(),
        NoticeRenderer(),
        CategoryTabsRenderer(),
        SectionTitleRenderer(),
        ModuleListRenderer(),
        ModuleGridRenderer(),
        UpdateSectionRenderer(),
        InstalledSectionRenderer()
    )

    /** 已注册的 Renderer 数量（便于测试和日志） */
    val registeredCount: Int get() = renderers.size

    /**
     * 按 sections 列表顺序渲染所有区块到 container。
     *
     * @param sections 按 order 排序且 enabled=true 的 section 列表（调用方预处理）
     * @param container Activity 根容器
     * @param host 宿主回调
     */
    fun dispatchRender(
        sections: List<StoreSection>,
        container: ViewGroup,
        host: StoreRendererHost
    ) {
        val processedIds = mutableSetOf<String>()
        for (section in sections) {
            // 重复 section.id 处理：仅渲染第一个
            if (!processedIds.add(section.id)) {
                Log.w(TAG, "重复 section.id=${section.id}，跳过后续渲染")
                continue
            }
            if (!section.enabled) continue

            val renderer = renderers.firstOrNull { it.supports(section.type) }
            if (renderer == null) {
                Log.w(TAG, "未知 section type=${section.type} (id=${section.id})，跳过")
                continue
            }

            try {
                renderer.render(section, container, host)
            } catch (e: Exception) {
                Log.e(TAG, "渲染区块失败 type=${section.type} id=${section.id}: ${e.message}", e)
                // 单个区块渲染失败不影响其他区块
            }
        }
    }

    /** 测试用：判断某个 type 是否已注册 Renderer */
    fun isSupported(type: String): Boolean = renderers.any { it.supports(type) }
}

// ====== 9 个 Renderer 实现 ======

/**
 * Hero Banner 渲染器。
 *
 * 该 Renderer 是薄层：仅负责根据 section.enabled 控制容器的显示/隐藏，
 * 实际的 Banner 数据加载、轮播、点击路由仍由 ModuleStoreActivity 的现有逻辑处理。
 *
 * 这样设计的目的是：不破坏 P1.3 已完成的 HeroBannerAdapter 远程化逻辑，
 * 仅让服务器能通过 store-ui.json 控制 Banner 区块的显示/隐藏/顺序。
 */
class HeroBannerRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "hero_banner"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val view = container.findViewById<View>(R.id.heroBannerContainer) ?: return false
        view.visibility = if (section.enabled) View.VISIBLE else View.GONE
        // order 通过父容器的子视图排序由 Activity 统一处理（避免 Renderer 直接操作 ViewGroup 顺序导致与现有逻辑冲突）
        return true
    }
}

/**
 * 搜索栏渲染器。
 *
 * 控制 R.id.moduleSearchLayout 的显示/隐藏。
 */
class SearchBarRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "search_bar"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        // 优先查找专门的搜索布局容器，找不到时尝试搜索输入框的父容器
        val view = container.findViewById<View>(R.id.moduleSearchLayout)
            ?: (container.findViewById<View>(R.id.etModuleSearch)?.parent as? View)
            ?: return false
        view.visibility = if (section.enabled) View.VISIBLE else View.GONE
        return true
    }
}

/**
 * 公告条渲染器（notice）。
 *
 * 第一版仅显示 params.text 文本，不支持 dismissable 的持久化（点击关闭仅在内存中隐藏）。
 * 后续可扩展为远程公告系统。
 */
class NoticeRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "notice"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        // 当前布局中没有专门的 notice 容器，第一版先不渲染（仅记录日志）
        // 后续若需支持，应在 activity_module_store.xml 中新增 R.id.noticeContainer
        if (section.enabled && section.params["text"]?.isNotEmpty() == true) {
            Log.d("NoticeRenderer", "公告: ${section.params["text"]} (当前布局未提供容器，跳过)")
        }
        return false // 表示当前未实际渲染
    }
}

/**
 * 分类 Tabs 渲染器。
 *
 * 控制 R.id.moduleCategoryTabs 的显示/隐藏。
 * 点击事件由 Activity 的现有 setupCategoryTabs 处理，Renderer 不重复绑定。
 */
class CategoryTabsRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "category_tabs"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val view = container.findViewById<View>(R.id.moduleCategoryTabs) ?: return false
        view.visibility = if (section.enabled) View.VISIBLE else View.GONE
        return true
    }
}

/**
 * 区块标题渲染器（section_title）。
 *
 * 第一版仅记录日志，实际标题由 Activity 在 module_grid 上方硬编码显示。
 * 后续可扩展为动态标题组件。
 */
class SectionTitleRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "section_title"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val text = section.params["text"]
        if (text.isNullOrEmpty()) return false
        // 当前布局没有专门的 section_title 容器，第一版先不渲染
        Log.d("SectionTitleRenderer", "区块标题: $text (当前布局未提供容器，跳过)")
        return false
    }
}

/**
 * 模块列表渲染器（单列纵向）。
 *
 * 第一版仅切换 RecyclerView 的 LayoutManager 为 LinearLayoutManager，
 * 复用现有 ModuleAdapter，不重复创建 Adapter。
 */
class ModuleListRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "module_list"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val rv = container.findViewById<RecyclerView>(R.id.moduleRecyclerView) ?: return false
        rv.visibility = if (section.enabled) View.VISIBLE else View.GONE
        if (section.enabled) {
            rv.layoutManager = LinearLayoutManager(host.hostContext)
        }
        return true
    }
}

/**
 * 模块网格渲染器（多列）。
 *
 * 切换 RecyclerView 的 LayoutManager 为 GridLayoutManager，
 * columns 合法范围 [1, 4]，非法值回退到 DEFAULT_COLUMNS=2。
 */
class ModuleGridRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "module_grid"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val rv = container.findViewById<RecyclerView>(R.id.moduleRecyclerView) ?: return false
        rv.visibility = if (section.enabled) View.VISIBLE else View.GONE
        if (section.enabled) {
            val cols = if (section.columns in StoreSection.MIN_COLUMNS..StoreSection.MAX_COLUMNS) {
                section.columns
            } else {
                StoreSection.DEFAULT_COLUMNS
            }
            rv.layoutManager = GridLayoutManager(host.hostContext, cols)
        }
        return true
    }
}

/**
 * 更新可用区块渲染器。
 *
 * 第一版仅记录日志，实际更新统计由 Activity 的 updateStatsBar 处理。
 * 后续可扩展为独立的"可更新模块"区块。
 */
class UpdateSectionRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "update_section"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        // 第一版：控制 statsBar 中"可更新"统计行的显示/隐藏
        val view = container.findViewById<View>(R.id.statUpdateCount) ?: return false
        view.visibility = if (section.enabled) View.VISIBLE else View.GONE
        val maxItems = section.params["maxItems"]?.toIntOrNull()
        Log.d("UpdateSectionRenderer", "更新区块 enabled=${section.enabled} maxItems=$maxItems")
        return true
    }
}

/**
 * 已安装区块渲染器。
 *
 * 第一版仅记录日志，实际已安装管理由 Activity 的 CATEGORY_INSTALLED tab 处理。
 */
class InstalledSectionRenderer : StoreSectionRenderer {
    override fun supports(type: String): Boolean = type == "installed_section"

    override fun render(section: StoreSection, container: ViewGroup, host: StoreRendererHost): Boolean {
        val maxItems = section.params["maxItems"]?.toIntOrNull()
        Log.d("InstalledSectionRenderer", "已安装区块 enabled=${section.enabled} maxItems=$maxItems")
        // 第一版不做实际渲染（已安装管理通过 CATEGORY_INSTALLED tab 入口）
        return false
    }
}
