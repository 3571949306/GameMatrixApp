package com.gamecenter.app.modules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.gamecenter.app.R

class ModuleAdapter(
    private var modules: List<ModuleManifest>,
    private var installedIds: Set<String>,
    private val onActionClick: (ModuleManifest, Int) -> Unit
) : RecyclerView.Adapter<ModuleAdapter.ViewHolder>() {

    companion object {
        const val ACTION_DOWNLOAD = 0
        const val ACTION_INSTALL = 1
        const val ACTION_OPEN = 2
        const val ACTION_UPDATE = 3
        const val ACTION_ENABLE = 4
        const val ACTION_UNINSTALL = 5

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
    var installedVersions: Map<String, Int> = emptyMap()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.moduleItemIcon)
        val name: TextView = view.findViewById(R.id.moduleItemName)
        val desc: TextView = view.findViewById(R.id.moduleItemDesc)
        val version: TextView = view.findViewById(R.id.moduleItemVersion)
        val size: TextView = view.findViewById(R.id.moduleItemSize)
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
        val module = modules[position]
        holder.name.text = module.name
        holder.desc.text = module.description
        holder.version.text = "v${module.versionName}"
        holder.size.text = if (module.builtIn) "内置" else formatFileSize(module.fileSize)

        val isInstalled = installedIds.contains(module.id)
        val isDownloading = downloadProgress.containsKey(module.id)
        val isBuiltIn = module.builtIn
        val installedVersion = installedVersions[module.id] ?: 0
        val hasUpdate = isInstalled && !isBuiltIn && installedVersion < module.versionCode && installedVersion > 0

        val successColor = ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
        val infoColor = 0xFF2196F3.toInt()
        val onSurfaceVariantColor = ContextCompat.getColor(holder.itemView.context, R.color.md_theme_light_on_surface_variant)

        val categoryLabel = CATEGORY_LABELS[module.storeCategory] ?: "其他"
        holder.categoryChip.text = categoryLabel
        holder.categoryChip.visibility = if (module.isBaseFramework) View.GONE else View.VISIBLE

        val iconRes = CATEGORY_ICONS[module.storeCategory] ?: android.R.drawable.ic_menu_gallery
        holder.icon.setImageResource(iconRes)

        holder.builtInChip.visibility = if (isBuiltIn) View.VISIBLE else View.GONE

        when {
            isDownloading -> {
                holder.progress.visibility = View.VISIBLE
                holder.progress.progress = downloadProgress[module.id] ?: 0
                holder.status.text = "下载中 ${downloadProgress[module.id] ?: 0}%"
                holder.status.setTextColor(successColor)
                holder.actionBtn.text = "取消"
                holder.uninstallBtn.visibility = View.GONE
                holder.actionBtn.setOnClickListener {
                    onActionClick(module, ACTION_DOWNLOAD)
                }
            }
            hasUpdate -> {
                holder.progress.visibility = View.GONE
                holder.status.text = "有更新 v${installedVersion}→v${module.versionCode}"
                holder.status.setTextColor(0xFFFF9800.toInt())
                holder.actionBtn.text = "更新"
                holder.actionBtn.setOnClickListener {
                    onActionClick(module, ACTION_DOWNLOAD)
                }
                holder.uninstallBtn.visibility = View.VISIBLE
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener {
                    onActionClick(module, ACTION_UNINSTALL)
                }
            }
            isInstalled -> {
                holder.progress.visibility = View.GONE
                holder.status.text = "已安装"
                holder.status.setTextColor(successColor)
                holder.actionBtn.text = "打开"
                holder.actionBtn.setOnClickListener {
                    onActionClick(module, ACTION_OPEN)
                }
                holder.uninstallBtn.visibility = View.VISIBLE
                holder.uninstallBtn.isEnabled = true
                holder.uninstallBtn.setOnClickListener {
                    onActionClick(module, ACTION_UNINSTALL)
                }
            }
            isBuiltIn -> {
                holder.progress.visibility = View.GONE
                holder.status.text = "内置"
                holder.status.setTextColor(infoColor)
                holder.actionBtn.text = "启用"
                holder.actionBtn.setOnClickListener {
                    onActionClick(module, ACTION_ENABLE)
                }
                holder.uninstallBtn.visibility = View.GONE
            }
            else -> {
                holder.progress.visibility = View.GONE
                holder.status.text = "未安装"
                holder.status.setTextColor(onSurfaceVariantColor)
                holder.actionBtn.text = "下载"
                holder.actionBtn.setOnClickListener {
                    onActionClick(module, ACTION_DOWNLOAD)
                }
                holder.uninstallBtn.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = modules.size

    fun updateModules(newModules: List<ModuleManifest>) {
        modules = newModules
        notifyDataSetChanged()
    }

    fun updateInstalledIds(newInstalledIds: Set<String>) {
        installedIds = newInstalledIds
        notifyDataSetChanged()
    }

    fun updateDownloadProgress(moduleId: String, percent: Int) {
        downloadProgress[moduleId] = percent
        val index = modules.indexOfFirst { it.id == moduleId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    fun removeDownloadProgress(moduleId: String) {
        downloadProgress.remove(moduleId)
        val index = modules.indexOfFirst { it.id == moduleId }
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
}
