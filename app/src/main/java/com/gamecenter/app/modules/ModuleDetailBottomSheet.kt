package com.gamecenter.app.modules

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

/**
 * 模块详情 BottomSheet（Batch 20）。
 *
 * 使用方式：
 *   ModuleDetailBottomSheet.show(supportFragmentManager, module) { action ->
 *       // action: ACTION_DOWNLOAD / ACTION_OPEN / ACTION_UPDATE / ACTION_ENABLE / ACTION_UNINSTALL
 *   }
 */
class ModuleDetailBottomSheet : BottomSheetDialogFragment() {

    private var module: ModuleManifest? = null
    private var isInstalled: Boolean = false
    private var isDownloading: Boolean = false
    private var isBuiltIn: Boolean = false
    private var hasUpdate: Boolean = false
    private var installedVersion: Int = 0
    private var onAction: ((Int) -> Unit)? = null
    // P3-12: 相似模块列表（由外部注入；为空时隐藏该区域）
    private var siblings: List<ModuleManifest> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_module_detail, null)
        dialog.setContentView(view)
        bindViews(view)
        return dialog
    }

    private fun bindViews(view: View) {
        val module = this.module ?: return
        val context = view.context

        // 图标容器渐变背景
        val gradientRes = when (module.storeCategory) {
            "game" -> R.drawable.module_category_game_gradient
            "browser" -> R.drawable.module_category_browser_gradient
            "tools" -> R.drawable.module_category_tools_gradient
            "ai" -> R.drawable.module_category_ai_gradient
            "vpn" -> R.drawable.module_category_vpn_gradient
            else -> R.drawable.module_category_other_gradient
        }
        val iconContainer = view.findViewById<FrameLayout>(R.id.detailIconContainer)
        iconContainer.setBackgroundResource(gradientRes)

        // 图标
        val iconRes = resolveIconRes(context, module)
        view.findViewById<ImageView>(R.id.detailIcon).setImageResource(iconRes)

        // 名称 + 内置标签
        view.findViewById<TextView>(R.id.detailName).text = module.name
        val builtInChip = view.findViewById<Chip>(R.id.detailBuiltInChip)
        builtInChip.visibility = if (isBuiltIn) View.VISIBLE else View.GONE

        // 版本
        view.findViewById<TextView>(R.id.detailVersion).text = "v${module.versionName}"

        // 介绍
        view.findViewById<TextView>(R.id.detailDesc).text = module.description

        // 信息表格
        view.findViewById<TextView>(R.id.detailVersionValue).text = "${module.versionName} (${module.versionCode})"
        view.findViewById<TextView>(R.id.detailSizeValue).text = if (module.builtIn) {
            context.getString(R.string.module_builtin)
        } else {
            formatFileSize(module.fileSize)
        }
        val categoryLabelRes = when (module.storeCategory) {
            "game" -> R.string.store_category_games
            "browser" -> R.string.store_category_browser
            "tools" -> R.string.store_category_tools
            "ai" -> R.string.store_category_ai
            "vpn" -> R.string.store_category_vpn
            else -> R.string.store_category_games
        }
        view.findViewById<TextView>(R.id.detailCategoryValue).text = context.getString(categoryLabelRes)

        // 已安装版本
        val installedVersionRow = view.findViewById<View>(R.id.detailInstalledVersionRow)
        if (isInstalled && !isBuiltIn) {
            installedVersionRow.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.detailInstalledVersionValue).text = "v$installedVersion"
        } else {
            installedVersionRow.visibility = View.GONE
        }

        // 当前状态文本
        val statusValue = view.findViewById<TextView>(R.id.detailStatusValue)
        statusValue.text = when {
            isDownloading -> context.getString(R.string.module_status_downloading, 0)
            hasUpdate -> context.getString(R.string.module_status_update_available, installedVersion, module.versionCode)
            isInstalled -> context.getString(R.string.module_status_installed)
            isBuiltIn -> context.getString(R.string.module_builtin)
            else -> context.getString(R.string.module_status_not_installed)
        }
        val statusColor = when {
            isDownloading || hasUpdate -> ContextCompat.getColor(context, R.color.warning)
            isInstalled -> ContextCompat.getColor(context, R.color.success)
            isBuiltIn -> ContextCompat.getColor(context, R.color.info)
            else -> ContextCompat.getColor(context, R.color.md_theme_on_surface_variant)
        }
        statusValue.setTextColor(statusColor)

        // 操作按钮
        val primaryBtn = view.findViewById<MaterialButton>(R.id.detailPrimaryBtn)
        val secondaryBtn = view.findViewById<MaterialButton>(R.id.detailSecondaryBtn)

        when {
            isDownloading -> {
                primaryBtn.text = context.getString(R.string.module_detail_cancel_download)
                primaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_DOWNLOAD); dismiss() }
                secondaryBtn.visibility = View.GONE
            }
            hasUpdate -> {
                primaryBtn.text = context.getString(R.string.module_detail_update)
                primaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_UPDATE); dismiss() }
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.text = context.getString(R.string.module_detail_uninstall)
                secondaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_UNINSTALL); dismiss() }
            }
            isInstalled -> {
                primaryBtn.text = context.getString(R.string.module_detail_open)
                primaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_OPEN); dismiss() }
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.text = context.getString(R.string.module_detail_uninstall)
                secondaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_UNINSTALL); dismiss() }
            }
            isBuiltIn -> {
                primaryBtn.text = context.getString(R.string.module_detail_enable)
                primaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_ENABLE); dismiss() }
                secondaryBtn.visibility = View.GONE
            }
            else -> {
                primaryBtn.text = context.getString(R.string.module_detail_download)
                primaryBtn.setOnClickListener { onAction?.invoke(ModuleAdapter.ACTION_DOWNLOAD); dismiss() }
                secondaryBtn.visibility = View.GONE
            }
        }

        // 关闭按钮
        view.findViewById<ImageButton>(R.id.detailCloseBtn).setOnClickListener { dismiss() }

        // Batch 21: 截图轮播（feature flag 控制）
        val screenshotsSection = view.findViewById<View>(R.id.detailScreenshotsSection)
        if (BuildConfig.MODULE_STORE_DETAIL_ENHANCE) {
            screenshotsSection.visibility = View.VISIBLE
            val screenshotsRv = view.findViewById<RecyclerView>(R.id.detailScreenshots)
            screenshotsRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            screenshotsRv.adapter = ModuleScreenshotAdapter(context, module)
        } else {
            screenshotsSection.visibility = View.GONE
        }

        // Batch 21: 更新日志（mock 数据，按 versionCode 稳定生成）
        val changelogSection = view.findViewById<View>(R.id.detailChangelogSection)
        if (BuildConfig.MODULE_STORE_DETAIL_ENHANCE) {
            changelogSection.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.detailChangelogValue).text = generateChangelog(module)
        } else {
            changelogSection.visibility = View.GONE
        }

        // Batch 21: 权限说明
        val permissionsSection = view.findViewById<View>(R.id.detailPermissionsSection)
        if (BuildConfig.MODULE_STORE_DETAIL_ENHANCE) {
            permissionsSection.visibility = View.VISIBLE
            bindPermissions(view.findViewById<LinearLayout>(R.id.detailPermissionsList), module, context)
        } else {
            permissionsSection.visibility = View.GONE
        }

        // P3-12 (MODULE_STORE_ENHANCE): 用户评分区域
        bindRating(view, module, context)

        // P3-12: 相似模块推荐
        bindSimilarModules(view, module, context)

        // P3-12: 历史版本日志
        bindVersionHistory(view, module, context)
    }

    /**
     * P3-12: 绑定用户评分区域。
     * 5 颗星点击即评分，使用 ModuleRatingStore 持久化（1~5 星）。
     */
    private fun bindRating(view: View, module: ModuleManifest, context: android.content.Context) {
        val store = ModuleRatingStore(context)
        val container = view.findViewById<LinearLayout>(R.id.detailRatingStarsContainer) ?: return
        val tvValue = view.findViewById<TextView>(R.id.detailRatingValue) ?: return
        val tvHint = view.findViewById<TextView>(R.id.detailRatingHint) ?: return
        container.removeAllViews()
        val userRating = store.getRating(module.id)
        val density = context.resources.displayMetrics.density
        val starSize = (20 * density).toInt()

        // 显示综合评分（用户评分或 mock 3.8~5.0）
        val baseRating = if (userRating > 0) userRating.toFloat()
                else 3.8f + (Math.abs(module.id.hashCode()) % 13) * 0.1f
        tvValue.text = String.format("%.1f", baseRating)
        tvHint.text = if (userRating > 0)
                context.getString(R.string.module_detail_rating_done, userRating)
            else context.getString(R.string.module_detail_rating_hint)

        for (i in 1..5) {
            val star = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply {
                    marginEnd = (2 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_rating_star)
                if (i <= userRating || (userRating == 0 && i <= Math.round(baseRating))) {
                    alpha = 1.0f
                    colorFilter = android.graphics.PorterDuffColorFilter(
                            ContextCompat.getColor(context, R.color.warning),
                            android.graphics.PorterDuff.Mode.SRC_ATOP)
                } else {
                    alpha = 0.3f
                    colorFilter = android.graphics.PorterDuffColorFilter(
                            ContextCompat.getColor(context, R.color.md_theme_on_surface_variant),
                            android.graphics.PorterDuff.Mode.SRC_ATOP)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    store.setRating(module.id, i)
                    Toast.makeText(context,
                            context.getString(R.string.module_detail_rating_thanks, i),
                            Toast.LENGTH_SHORT).show()
                    bindRating(view, module, context)
                }
            }
            container.addView(star)
        }
    }

    /**
     * P3-12: 绑定相似模块推荐（横向列表，最多展示 4 个）。
     * 数据源：siblings 列表（已由外部按 storeCategory 过滤）。
     */
    private fun bindSimilarModules(view: View, module: ModuleManifest, context: android.content.Context) {
        val section = view.findViewById<View>(R.id.detailSimilarSection) ?: return
        val rv = view.findViewById<RecyclerView>(R.id.detailSimilarList) ?: return
        // 过滤掉自身和 base framework，最多 4 个
        val list = siblings.filter { it.id != module.id && !it.isBaseFramework }.take(4)
        if (list.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = SimilarModuleAdapter(context, list) { sibling ->
            dismiss()
            // 递归打开相似模块的详情
            val ctx = context
            val installed = ModuleManager.isModuleInstalled(ctx, sibling.id)
            val installedVer = ModuleManager.getInstalledVersionCode(ctx, sibling.id)
            show(parentFragmentManager, sibling, installed, false, installedVer) { _ -> }
        }
    }

    /**
     * P3-12: 绑定历史版本日志。
     * 优先使用服务器下发的 changelog，否则按版本号回退生成 3 条历史。
     */
    private fun bindVersionHistory(view: View, module: ModuleManifest, context: android.content.Context) {
        val section = view.findViewById<View>(R.id.detailVersionHistorySection) ?: return
        val container = view.findViewById<LinearLayout>(R.id.detailVersionHistoryList) ?: return
        container.removeAllViews()

        // 生成最近 3 个版本（v{N}, v{N-1}, v{N-2}），最新版使用 module.changelog 或 mock
        val currentVersion = module.versionCode.coerceAtLeast(1)
        val padding = context.resources.getDimensionPixelSize(R.dimen.gm_spacing_1)
        val versionsToShow = (0..2).map { offset ->
            val vc = (currentVersion - offset).coerceAtLeast(1)
            val versionName = if (offset == 0) module.versionName else "v${vc}"
            val notes = if (offset == 0 && module.changelog.isNotEmpty()) {
                module.changelog
            } else {
                generateMockVersionNotes(module, vc, offset)
            }
            versionName to notes
        }

        versionsToShow.forEach { (versionName, notes) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, padding, 0, padding)
            }
            val tvVersion = TextView(context).apply {
                text = versionName
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val tvNotes = TextView(context).apply {
                text = notes
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface_variant))
                textSize = 11f
                setPadding(0, padding / 2, 0, 0)
            }
            row.addView(tvVersion)
            row.addView(tvNotes)
            container.addView(row)
        }
        section.visibility = View.VISIBLE
    }

    /** P3-12: 按 storeCategory 与版本号生成历史版本说明（兜底）。 */
    private fun generateMockVersionNotes(module: ModuleManifest, versionCode: Int, offset: Int): String {
        val sb = StringBuilder()
        when (module.storeCategory) {
            "game" -> {
                sb.append("• 优化 AI 难度梯度\n")
                sb.append("• 修复若干已知问题")
            }
            "browser" -> {
                sb.append("• 性能与稳定性优化")
            }
            "tools" -> {
                sb.append("• 修复偶现崩溃")
            }
            else -> {
                sb.append("• 功能优化")
            }
        }
        return sb.toString()
    }

    /** P3-12: 相似模块横向列表适配器。 */
    private class SimilarModuleAdapter(
        private val context: android.content.Context,
        private val items: List<ModuleManifest>,
        private val onClick: (ModuleManifest) -> Unit
    ) : RecyclerView.Adapter<SimilarModuleAdapter.VH>() {

        class VH(val container: LinearLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val density = context.resources.displayMetrics.density
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                        (140 * density).toInt(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * density).toInt() }
                setBackgroundResource(R.drawable.bg_tool_icon_circle)
                setPadding((12 * density).toInt(), (12 * density).toInt(),
                        (12 * density).toInt(), (12 * density).toInt())
                isClickable = true
                isFocusable = true
            }
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.container.removeAllViews()
            val density = context.resources.displayMetrics.density
            val tvName = TextView(context).apply {
                text = item.name
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val tvDesc = TextView(context).apply {
                text = item.description
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface_variant))
                textSize = 10f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, (4 * density).toInt(), 0, 0)
            }
            holder.container.addView(tvName)
            holder.container.addView(tvDesc)
            holder.container.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    /**
     * P1.4: 更新日志优先级：
     * 1. 服务器目录下发的 `module.changelog`（非空时直接使用）
     * 2. 按 storeCategory 生成的本地 mock 数据（兜底）
     */
    private fun generateChangelog(module: ModuleManifest): String {
        // 优先使用服务器下发的更新日志（catalog.json 中 module.changelog 字段）
        if (module.changelog.isNotEmpty()) {
            return module.changelog
        }
        val sb = StringBuilder()
        sb.append("v${module.versionName} (build ${module.versionCode})\n")
        when (module.storeCategory) {
            "game" -> {
                sb.append("• 新增 ${module.gameId.ifEmpty { module.id }} 玩法模式\n")
                sb.append("• 优化 AI 难度梯度\n")
                sb.append("• 修复若干已知问题\n")
            }
            "browser" -> {
                sb.append("• 升级 WebView 内核\n")
                sb.append("• 新增手势导航与阅读模式\n")
                sb.append("• 优化多标签性能\n")
            }
            "tools" -> {
                sb.append("• 新增工具组件\n")
                sb.append("• 优化扫描速度\n")
                sb.append("• 修复偶现崩溃\n")
            }
            "ai" -> {
                sb.append("• 接入新模型\n")
                sb.append("• 改进 OCR 准确率\n")
                sb.append("• 优化长文本摘要\n")
            }
            "vpn" -> {
                sb.append("• 升级代理协议\n")
                sb.append("• 优化节点选择策略\n")
                sb.append("• 修复重连问题\n")
            }
            else -> {
                sb.append("• 功能优化\n")
                sb.append("• 稳定性提升\n")
            }
        }
        sb.append("• 适配 Android 15 边缘到边缘显示")
        return sb.toString()
    }

    /**
     * P1.4: 权限说明优先级：
     * 1. 服务器目录下发的 `module.permissionsDescription`（List<String>，非空时直接渲染）
     * 2. 按 storeCategory 生成的本地默认权限（兜底）
     */
    private fun bindPermissions(container: LinearLayout, module: ModuleManifest, context: android.content.Context) {
        container.removeAllViews()

        // 服务器下发权限说明时直接渲染每条条目
        if (module.permissionsDescription.isNotEmpty()) {
            module.permissionsDescription.forEach { line ->
                val item = TextView(context).apply {
                    text = line
                    setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface_variant))
                    textSize = 11f
                    setPadding(0, context.resources.getDimensionPixelSize(R.dimen.gm_spacing_1), 0, 0)
                    gravity = Gravity.START
                }
                container.addView(item)
            }
            return
        }

        // 兜底：按分类生成默认权限条目
        val permissions = mutableListOf<Int>()
        // 所有模块都需要网络权限
        permissions.add(R.string.module_detail_perm_internet)
        // 游戏类需要存储（保存进度），其他模块也需要存储（缓存资源）
        permissions.add(R.string.module_detail_perm_storage)
        // 浏览器 / VPN / AI 类需要通知权限
        if (module.storeCategory in listOf("browser", "vpn", "ai", "tools")) {
            permissions.add(R.string.module_detail_perm_notifications)
        }
        permissions.forEach { resId ->
            val item = TextView(context).apply {
                text = context.getString(resId)
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface_variant))
                textSize = 11f
                setPadding(0, context.resources.getDimensionPixelSize(R.dimen.gm_spacing_1), 0, 0)
                gravity = Gravity.START
            }
            container.addView(item)
        }
    }

    private fun resolveIconRes(context: android.content.Context, module: ModuleManifest): Int {
        val gameId = module.gameId.ifEmpty { module.id }
        if (gameId.isNotEmpty()) {
            val pkg = context.packageName
            val direct = context.resources.getIdentifier("ic_$gameId", "drawable", pkg)
            if (direct != 0) return direct
            val prefixed = context.resources.getIdentifier("ic_game_$gameId", "drawable", pkg)
            if (prefixed != 0) return prefixed
        }
        return when (module.storeCategory) {
            "game" -> R.drawable.ic_games
            "browser" -> R.drawable.ic_browser
            "tools" -> R.drawable.ic_tools
            "ai" -> R.drawable.ic_ai
            "vpn" -> R.drawable.ic_vpn
            else -> android.R.drawable.ic_menu_gallery
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            module: ModuleManifest,
            isInstalled: Boolean,
            isDownloading: Boolean,
            installedVersion: Int,
            onAction: (Int) -> Unit
        ) {
            val sheet = ModuleDetailBottomSheet()
            sheet.module = module
            sheet.isInstalled = isInstalled
            sheet.isDownloading = isDownloading
            sheet.isBuiltIn = module.builtIn
            sheet.installedVersion = installedVersion
            sheet.hasUpdate = isInstalled && !module.builtIn && installedVersion < module.versionCode && installedVersion > 0
            sheet.onAction = onAction
            // P3-12: 自动注入相似模块（同分类，最多 6 个）
            sheet.siblings = ModuleManager.getSimilarModules(module.id, 6)
            sheet.show(fragmentManager, "ModuleDetailBottomSheet")
        }

        /** 简化版重载：自动从 ModuleManager 查询安装状态 */
        fun show(
            fragmentManager: FragmentManager,
            module: ModuleManifest,
            onAction: (Int) -> Unit
        ) {
            // 用 context 从 fragmentManager 中获取
            val context = fragmentManager.fragments.firstOrNull()?.requireContext()
            val isInstalled = context != null && ModuleManager.isModuleInstalled(context, module.id)
            val installedVersion = context?.let { ModuleManager.getInstalledVersionCode(it, module.id) } ?: 0
            show(fragmentManager, module, isInstalled, false, installedVersion, onAction)
        }
    }
}
