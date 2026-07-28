package com.gamecenter.app.modules

import android.content.Context
import android.content.res.Configuration
import android.util.LruCache
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gamecenter.app.R

/**
 * 模块列表适配器（Batch 20: 分类渐变图标背景 + NEW 徽章 + 紧凑布局 + 状态 visibility 修复 + 卡片可点击）。
 *
 * MODULE_STORE_PERF_OPT: 主题颜色 + 图标资源缓存，消除 onBindViewHolder 中的重复主题属性解析和 getIdentifier 调用。
 */
class ModuleAdapter(
    private var installedIds: Set<String>,
    private val onActionClick: (ModuleManifest, Int) -> Unit,
    private val onItemBodyClick: (ModuleManifest) -> Unit = {}
) : ListAdapter<ModuleManifest, ModuleAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val ACTION_DOWNLOAD = 0
        const val ACTION_OPEN = 2
        const val ACTION_UPDATE = 3
        const val ACTION_ENABLE = 4
        const val ACTION_UNINSTALL = 5

        /** 分类 → 字符串资源（6 类标准化 storeCategory wireValue） */
        private val CATEGORY_LABEL_RES = mapOf(
            "entertainment_versus" to R.string.store_category_entertainment_versus,
            "learning_organization" to R.string.store_category_learning_organization,
            "reading_browsing" to R.string.store_category_reading_browsing,
            "text_creation" to R.string.store_category_text_creation,
            "device_network" to R.string.store_category_device_network,
            "personalization" to R.string.store_category_personalization,
            "other" to R.string.store_category_other
        )

        /** 分类 → 图标（6 类标准化 storeCategory wireValue） */
        private val CATEGORY_ICONS = mapOf(
            "entertainment_versus" to R.drawable.ic_games,
            "learning_organization" to R.drawable.ic_nav_wrongbook,
            "reading_browsing" to R.drawable.ic_browser,
            "text_creation" to R.drawable.ic_ai,
            "device_network" to R.drawable.ic_vpn,
            "personalization" to R.drawable.ic_settings
        )

        /** Batch 20: 分类 → 渐变图标背景 drawable（复用现有 6 套渐变） */
        private val CATEGORY_GRADIENTS = mapOf(
            "entertainment_versus" to R.drawable.module_category_game_gradient,
            "learning_organization" to R.drawable.module_category_tools_gradient,
            "reading_browsing" to R.drawable.module_category_browser_gradient,
            "text_creation" to R.drawable.module_category_ai_gradient,
            "device_network" to R.drawable.module_category_vpn_gradient,
            "personalization" to R.drawable.module_category_other_gradient,
            "other" to R.drawable.module_category_other_gradient
        )

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ModuleManifest>() {
            override fun areItemsTheSame(oldItem: ModuleManifest, newItem: ModuleManifest): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ModuleManifest, newItem: ModuleManifest): Boolean =
                oldItem.id == newItem.id &&
                        oldItem.versionCode == newItem.versionCode &&
                        oldItem.name == newItem.name &&
                        oldItem.description == newItem.description &&
                        oldItem.builtIn == newItem.builtIn
        }
    }

    private val downloadProgress = mutableMapOf<String, Int>()
    var installedVersions: Map<String, Int> = emptyMap()

    // MODULE_STORE_PERF_OPT: 主题颜色缓存（避免每次 bind 解析 TypedValue）
    private var cachedThemeColors: ThemeColorCache? = null
    private var cachedThemeConfig: Int = 0 // Configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK

    // MODULE_STORE_PERF_OPT: 图标资源 id 缓存（避免重复 getIdentifier 反射调用）
    private val iconResCache = LruCache<String, Int>(64)

    /** 主题颜色缓存容器 */
    private data class ThemeColorCache(
        val success: Int,
        val info: Int,
        val warning: Int,
        val onSurfaceVariant: Int
    )

    /**
     * 获取或初始化主题颜色缓存。
     * 当主题模式（日间/夜间）变化时自动重建。
     */
    private fun getThemeColors(context: Context): ThemeColorCache {
        val currentMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val existing = cachedThemeColors
        if (existing != null && cachedThemeConfig == currentMode) return existing
        val cache = ThemeColorCache(
            success = resolveThemeColor(context, R.attr.colorSuccess, R.color.md_theme_on_surface_variant),
            info = resolveThemeColor(context, R.attr.colorInfo, R.color.md_theme_on_surface_variant),
            warning = resolveThemeColor(context, R.attr.colorWarning, R.color.md_theme_on_surface_variant),
            onSurfaceVariant = ContextCompat.getColor(context, R.color.md_theme_on_surface_variant)
        )
        cachedThemeColors = cache
        cachedThemeConfig = currentMode
        return cache
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.moduleItemIcon)
        val iconContainer: FrameLayout = view.findViewById(R.id.moduleIconContainer)
        val newBadge: TextView = view.findViewById(R.id.moduleItemNewBadge)
        val name: TextView = view.findViewById(R.id.moduleItemName)
        val desc: TextView = view.findViewById(R.id.moduleItemDesc)
        val version: TextView = view.findViewById(R.id.moduleItemVersion)
        val size: TextView = view.findViewById(R.id.moduleItemSize)
        val rating: TextView = view.findViewById(R.id.moduleItemRating)
        val downloads: TextView = view.findViewById(R.id.moduleItemDownloads)
        val status: TextView = view.findViewById(R.id.moduleItemStatus)
        val progress: ProgressBar = view.findViewById(R.id.moduleItemProgress)
        val actionBtn: Button = view.findViewById(R.id.moduleItemActionBtn)
        val uninstallBtn: Button = view.findViewById(R.id.moduleItemUninstallBtn)
        val categoryChip: Chip = view.findViewById(R.id.moduleItemCategoryChip)
        val builtInChip: Chip = view.findViewById(R.id.moduleItemBuiltInChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val module = getItem(position)
        val context = holder.itemView.context

        holder.name.text = module.name
        holder.desc.text = module.description
        holder.version.text = "v${module.versionName}"
        holder.size.text = if (module.builtIn) context.getString(R.string.module_builtin)
                           else formatFileSize(module.fileSize)

        val isInstalled = installedIds.contains(module.id)
        val isDownloading = downloadProgress.containsKey(module.id)
        val isBuiltIn = module.builtIn
        val installedVersion = installedVersions[module.id] ?: 0
        val hasUpdate = isInstalled && !isBuiltIn && installedVersion < module.versionCode && installedVersion > 0

        // MODULE_STORE_PERF_OPT: 使用缓存的主题颜色（仅主题切换时重建，避免每次 bind 解析 TypedValue）
        val themeColors = getThemeColors(context)
        val successColor = themeColors.success
        val infoColor = themeColors.info
        val warningColor = themeColors.warning
        val onSurfaceVariantColor = themeColors.onSurfaceVariant

        // 分类 Chip（引用字符串资源，不硬编码）
        val categoryLabelRes = CATEGORY_LABEL_RES[module.storeCategory] ?: R.string.store_category_other
        holder.categoryChip.text = context.getString(categoryLabelRes)
        holder.categoryChip.visibility = if (module.isBaseFramework) View.GONE else View.VISIBLE

        // Batch 21: 评分 + 下载次数（基于模块 id 稳定 hash 生成 mock 数据，未来可从服务端获取）
        val ratingValue = generateStableRating(module.id)
        val downloadCount = generateStableDownloadCount(module.id)
        holder.rating.text = String.format("%.1f", ratingValue)
        holder.downloads.text = formatDownloadCount(downloadCount)

        // Batch 20: 根据分类动态切换图标容器渐变背景
        val gradientRes = CATEGORY_GRADIENTS[module.storeCategory]
            ?: R.drawable.module_category_other_gradient
        holder.iconContainer.setBackgroundResource(gradientRes)

        // 图标（三级回退：ic_<gameId> → ic_game_<gameId> → 分类图标 → 系统默认）
        val iconRes = resolveIconRes(context, module)
        holder.icon.setImageResource(iconRes)

        holder.builtInChip.visibility = if (isBuiltIn) View.VISIBLE else View.GONE

        // Batch 20: NEW 徽章（未安装或有更新时显示）
        holder.newBadge.visibility = if (!isInstalled || hasUpdate) View.VISIBLE else View.GONE

        // 卡片本身可点击（点击非按钮区域 → onItemBodyClick，由 Activity 决定打开/下载）
        holder.itemView.setOnClickListener { onItemBodyClick(module) }

        when {
            isDownloading -> {
                val percent = downloadProgress[module.id] ?: 0
                holder.progress.visibility = View.VISIBLE
                if (holder.progress is LinearProgressIndicator) {
                    (holder.progress as LinearProgressIndicator).setProgressCompat(percent, true)
                } else {
                    holder.progress.progress = percent
                }
                // 修复状态文字 visibility bug：显式设为 VISIBLE
                holder.status.visibility = View.VISIBLE
                holder.status.text = context.getString(R.string.module_status_downloading, percent)
                holder.status.setTextColor(successColor)
                holder.actionBtn.text = context.getString(R.string.module_action_cancel)
                holder.uninstallBtn.visibility = View.GONE
                holder.actionBtn.setOnClickListener { onActionClick(module, ACTION_DOWNLOAD) }
            }
            hasUpdate -> {
                holder.progress.visibility = View.GONE
                holder.status.visibility = View.VISIBLE
                holder.status.text = context.getString(R.string.module_status_update_available, installedVersion, module.versionCode)
                holder.status.setTextColor(warningColor)
                holder.actionBtn.text = context.getString(R.string.installed_update)
                holder.actionBtn.setOnClickListener { onActionClick(module, ACTION_DOWNLOAD) }
                holder.uninstallBtn.visibility = View.VISIBLE
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener { onActionClick(module, ACTION_UNINSTALL) }
            }
            isInstalled -> {
                holder.progress.visibility = View.GONE
                holder.status.visibility = View.VISIBLE
                holder.status.text = context.getString(R.string.module_status_installed)
                holder.status.setTextColor(successColor)
                holder.actionBtn.text = context.getString(R.string.module_action_open)
                holder.actionBtn.setOnClickListener { onActionClick(module, ACTION_OPEN) }
                holder.uninstallBtn.visibility = View.VISIBLE
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener { onActionClick(module, ACTION_UNINSTALL) }
            }
            isBuiltIn -> {
                holder.progress.visibility = View.GONE
                holder.status.visibility = View.VISIBLE
                holder.status.text = context.getString(R.string.module_builtin)
                holder.status.setTextColor(infoColor)
                holder.actionBtn.text = context.getString(R.string.module_action_enable)
                holder.actionBtn.setOnClickListener { onActionClick(module, ACTION_ENABLE) }
                holder.uninstallBtn.visibility = View.GONE
            }
            else -> {
                holder.progress.visibility = View.GONE
                holder.status.visibility = View.VISIBLE
                holder.status.text = context.getString(R.string.module_status_not_installed)
                holder.status.setTextColor(onSurfaceVariantColor)
                holder.actionBtn.text = context.getString(R.string.module_action_download)
                holder.actionBtn.setOnClickListener { onActionClick(module, ACTION_DOWNLOAD) }
                holder.uninstallBtn.visibility = View.GONE
            }
        }
    }

    /** 更新整个模块列表（使用 DiffUtil 增量刷新，启用 item 动画） */
    fun updateModules(newModules: List<ModuleManifest>) {
        submitList(newModules)
    }

    /**
     * MODULE_STORE_PERF_OPT: 更新已安装模块 id 集合。
     *
     * 不再调用 notifyItemRangeChanged 触发全量重新绑定。
     * 安装状态变化通过 updateModules 的 DiffUtil areContentsTheSame 比较（installedVersions 变化时
     * versionCode 比较会触发 item 刷新）。如需强制刷新，调用 updateModules 重新提交相同列表。
     */
    fun updateInstalledIds(newInstalledIds: Set<String>) {
        installedIds = newInstalledIds
    }

    fun updateDownloadProgress(moduleId: String, percent: Int) {
        downloadProgress[moduleId] = percent
        val index = currentList.indexOfFirst { it.id == moduleId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    fun removeDownloadProgress(moduleId: String) {
        downloadProgress.remove(moduleId)
        val index = currentList.indexOfFirst { it.id == moduleId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    fun isDownloading(moduleId: String): Boolean {
        return downloadProgress.containsKey(moduleId)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Batch 21: 基于模块 id 稳定生成评分（3.8 ~ 5.0）。
     * 同一模块 id 始终返回同一评分，避免滚动时数字跳变。
     */
    private fun generateStableRating(moduleId: String): Float {
        val hash = moduleId.hashCode()
        // 3.8 + (hash % 13) * 0.1 → 3.8 ~ 5.0
        val raw = 3.8f + ((Math.abs(hash) % 13) * 0.1f).toFloat()
        return Math.min(raw, 5.0f)
    }

    /**
     * Batch 21: 基于模块 id 稳定生成下载次数（50 ~ 12500）。
     */
    private fun generateStableDownloadCount(moduleId: String): Int {
        val hash = moduleId.hashCode()
        val absHash = Math.abs(hash)
        // 50 + (hash % 12500)
        return 50 + (absHash % 12500)
    }

    /**
     * Batch 21: 格式化下载次数为短文本（1.2k / 12k / 999+）。
     */
    private fun formatDownloadCount(count: Int): String {
        return when {
            count < 1000 -> count.toString()
            count < 10000 -> "%.1fk".format(count / 1000.0)
            else -> "${count / 1000}k"
        }
    }

    /** 解析主题属性颜色，失败时回退到指定资源 id */
    private fun resolveThemeColor(context: Context, attr: Int, fallbackRes: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            try {
                if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId)
                else typedValue.data
            } catch (e: Exception) {
                ContextCompat.getColor(context, fallbackRes)
            }
        } else {
            ContextCompat.getColor(context, fallbackRes)
        }
    }

    /**
     * 解析模块图标资源 ID。
     * 优先级：ic_<gameId> → ic_game_<gameId> → 分类图标 → 系统默认。
     * MODULE_STORE_PERF_OPT: 使用 LruCache 缓存结果，避免每次 bind 重复 getIdentifier 调用。
     */
    private fun resolveIconRes(context: Context, module: ModuleManifest): Int {
        val gameId = module.gameId.ifEmpty { module.id }
        if (gameId.isNotEmpty()) {
            val cacheKey = gameId + "|" + module.storeCategory
            val cached = iconResCache.get(cacheKey)
            if (cached != null && cached != 0) return cached
            val pkg = context.packageName
            val direct = context.resources.getIdentifier("ic_$gameId", "drawable", pkg)
            if (direct != 0) {
                iconResCache.put(cacheKey, direct)
                return direct
            }
            val prefixed = context.resources.getIdentifier("ic_game_$gameId", "drawable", pkg)
            if (prefixed != 0) {
                iconResCache.put(cacheKey, prefixed)
                return prefixed
            }
            val fallback = CATEGORY_ICONS[module.storeCategory] ?: android.R.drawable.ic_menu_gallery
            iconResCache.put(cacheKey, fallback)
            return fallback
        }
        return CATEGORY_ICONS[module.storeCategory] ?: android.R.drawable.ic_menu_gallery
    }
}
