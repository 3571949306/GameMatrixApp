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
