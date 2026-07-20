package com.gamecenter.app.modules

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.bumptech.glide.Glide

/**
 * 模块详情截图适配器（Batch 21 + P1.4 远程化）。
 *
 * P1.4 改造：
 * - 优先使用 manifest.screenshots URL 列表（Glide 加载，失败回退占位）
 * - URL 列表为空时回退到原 mock 逻辑（按 module.id hash 生成 3-5 张占位）
 * - 截图 URL 无效时跳过该位置（不崩溃）
 */
class ModuleScreenshotAdapter(
    private val context: Context,
    private val module: ModuleManifest
) : RecyclerView.Adapter<ModuleScreenshotAdapter.ScreenshotViewHolder>() {

    /** P1.4: 远程截图 URL 列表（来自 catalog.json 的 screenshots 字段） */
    private val remoteUrls: List<String> = module.screenshots.filter { it.isNotEmpty() }

    /** 兜底 mock 数量：按 moduleId 稳定 hash 生成 3 ~ 5 张 */
    private val mockCount: Int = 3 + (Math.abs(module.id.hashCode()) % 3)

    /** 总条目数：有远程 URL 时用 URL 数量，否则用 mock 数量 */
    private val count: Int = if (remoteUrls.isNotEmpty()) remoteUrls.size else mockCount

    /** 渐变背景循环（与 ModuleStoreActivity 中的 Hero Banner 同套） */
    private val gradientRes = intArrayOf(
        R.drawable.module_hero_gradient,
        R.drawable.module_hero_gradient_2,
        R.drawable.module_hero_gradient_3
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module_screenshot, parent, false)
        return ScreenshotViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
        val gradient = gradientRes[position % gradientRes.size]
        // 直接设置 FrameLayout 背景（holder.icon.parent 即为 item_module_screenshot 中的 FrameLayout）
        val parent = holder.icon.parent as? View
        parent?.setBackgroundResource(gradient)

        if (remoteUrls.isNotEmpty() && position < remoteUrls.size) {
            // P1.4: 从 URL 加载截图，失败回退模块图标
            Glide.with(context)
                .load(remoteUrls[position])
                .placeholder(resolveIconRes(module))
                .error(resolveIconRes(module))
                .into(holder.icon)
        } else {
            // Mock 模式：使用模块分类图标
            holder.icon.setImageResource(resolveIconRes(module))
        }
        holder.label.text = String.format("%02d", position + 1)
    }

    override fun getItemCount(): Int = count

    private fun resolveIconRes(module: ModuleManifest): Int {
        val pkg = context.packageName
        val gameId = module.gameId.ifEmpty { module.id }
        if (gameId.isNotEmpty()) {
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

    class ScreenshotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.screenshotIcon)
        val label: TextView = view.findViewById(R.id.screenshotLabel)
    }
}
