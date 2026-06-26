package com.gamecenter.app.modules

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.gamecenter.app.R

class InstalledModuleAdapter(
    private var modules: List<ModuleManifest>,
    private val onActionClick: (ModuleManifest, Int) -> Unit
) : RecyclerView.Adapter<InstalledModuleAdapter.ViewHolder>() {

    companion object {
        const val ACTION_UPDATE = 0
        const val ACTION_UNINSTALL = 1

        private val CATEGORY_LABELS = mapOf(
            "game" to "游戏",
            "browser" to "浏览器",
            "tools" to "工具箱",
            "ai" to "AI助手",
            "vpn" to "VPN",
            "other" to "其他"
        )

        private val CATEGORY_ICONS = mapOf(
            "game" to R.drawable.ic_games,
            "browser" to R.drawable.ic_browser,
            "tools" to R.drawable.ic_tools,
            "ai" to R.drawable.ic_ai,
            "vpn" to R.drawable.ic_settings
        )
    }

    private val downloadProgress = mutableMapOf<String, Int>()
    private var activity: InstalledModulesActivity? = null

    fun setActivity(activity: InstalledModulesActivity) {
        this.activity = activity
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.installedItemIcon)
        val name: TextView = view.findViewById(R.id.installedItemName)
        val version: TextView = view.findViewById(R.id.installedItemVersion)
        val statusChip: Chip = view.findViewById(R.id.installedItemStatusChip)
        val categoryChip: Chip = view.findViewById(R.id.installedItemCategoryChip)
        val builtInChip: Chip = view.findViewById(R.id.installedItemBuiltInChip)
        val progress: ProgressBar = view.findViewById(R.id.installedItemProgress)
        val updateBtn: MaterialButton = view.findViewById(R.id.installedItemUpdateBtn)
        val uninstallBtn: MaterialButton = view.findViewById(R.id.installedItemUninstallBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_module, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val module = modules[position]
        holder.name.text = module.name
        holder.version.text = "v${module.versionName}"

        val isDownloading = downloadProgress.containsKey(module.id)
        val hasUpdate = activity?.hasUpdateForModule(module) == true

        val successColor = ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
        val warningColor = 0xFFFF9800.toInt()
        val infoColor = 0xFF607D8B.toInt()

        val categoryLabel = CATEGORY_LABELS[module.storeCategory] ?: "其他"
        holder.categoryChip.text = categoryLabel
        holder.categoryChip.visibility = View.VISIBLE

        val iconRes = resolveIconRes(holder.itemView.context, module)
        holder.icon.setImageResource(iconRes)

        holder.builtInChip.visibility = if (module.builtIn) View.VISIBLE else View.GONE

        when {
            isDownloading -> {
                holder.progress.visibility = View.VISIBLE
                holder.progress.progress = downloadProgress[module.id] ?: 0
                holder.statusChip.text = "更新中 ${downloadProgress[module.id] ?: 0}%"
                holder.statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(successColor)
                holder.statusChip.setTextColor(android.graphics.Color.WHITE)
                holder.statusChip.visibility = View.VISIBLE
                holder.updateBtn.visibility = View.GONE
                holder.uninstallBtn.isEnabled = false
            }
            module.builtIn -> {
                holder.progress.visibility = View.GONE
                holder.statusChip.text = "内置模块"
                holder.statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(infoColor)
                holder.statusChip.setTextColor(android.graphics.Color.WHITE)
                holder.statusChip.visibility = View.VISIBLE
                holder.updateBtn.visibility = View.GONE
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener {
                    onActionClick(module, ACTION_UNINSTALL)
                }
            }
            else -> {
                holder.progress.visibility = View.GONE
                if (hasUpdate) {
                    holder.statusChip.text = "有更新可用"
                    holder.statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(warningColor)
                    holder.statusChip.setTextColor(android.graphics.Color.WHITE)
                    holder.statusChip.visibility = View.VISIBLE
                } else {
                    holder.statusChip.text = "已是最新版本"
                    holder.statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(successColor)
                    holder.statusChip.setTextColor(android.graphics.Color.WHITE)
                    holder.statusChip.visibility = View.VISIBLE
                }
                holder.updateBtn.visibility = if (hasUpdate) View.VISIBLE else View.GONE
                holder.updateBtn.setOnClickListener {
                    onActionClick(module, ACTION_UPDATE)
                }
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener {
                    onActionClick(module, ACTION_UNINSTALL)
                }
            }
        }
    }

    override fun getItemCount() = modules.size

    /**
     * 解析模块图标资源 ID。
     * 优先级：ic_<gameId> → ic_game_<gameId> → 分类图标 → 系统默认。
     */
    private fun resolveIconRes(context: Context, module: ModuleManifest): Int {
        val gameId = module.gameId.ifEmpty { module.id }
        if (gameId.isNotEmpty()) {
            val pkg = context.packageName
            val direct = context.resources.getIdentifier("ic_$gameId", "drawable", pkg)
            if (direct != 0) return direct
            val prefixed = context.resources.getIdentifier("ic_game_$gameId", "drawable", pkg)
            if (prefixed != 0) return prefixed
        }
        return CATEGORY_ICONS[module.storeCategory] ?: android.R.drawable.ic_menu_gallery
    }

    fun updateInstalledModules(newModules: List<ModuleManifest>) {
        modules = newModules
        notifyDataSetChanged()
    }

    fun updateDownloadProgress(moduleId: String, percent: Int) {
        downloadProgress[moduleId] = percent
        val index = modules.indexOfFirst { it.id == moduleId }
        if (index >= 0) notifyItemChanged(index)
    }

    fun removeDownloadProgress(moduleId: String) {
        downloadProgress.remove(moduleId)
        val index = modules.indexOfFirst { it.id == moduleId }
        if (index >= 0) notifyItemChanged(index)
    }
}
