package com.gamecenter.app.modules

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.gamecenter.app.modules.store.model.StoreHeroBanner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton

/**
 * Hero Banner 多卡片轮播 Adapter（Batch 21 + P1.3 远程化）。
 *
 * P1.3 改造：
 * - 接受 [HeroBannerItem] 列表，每项包含 ModuleManifest + 可选的 StoreHeroBanner
 * - 当 StoreHeroBanner 非空时，使用 banner 的 title/subtitle/imageUrl 覆盖模块默认字段
 * - 图片加载失败时显示本地占位图（模块图标），不崩溃
 * - moduleId 无效（module 为 null）时，Adapter 跳过点击或显示 Toast，不崩溃
 * - Banner 点击统一通过 onItemClick/onActionClick 回调处理（不直接操作模块文件）
 */
class HeroBannerAdapter(
    private val context: Context,
    private val onItemClick: (ModuleManifest) -> Unit,
    private val onActionClick: (ModuleManifest) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.HeroViewHolder>() {

    private val items = mutableListOf<HeroBannerItem>()

    /** 三套渐变背景资源循环使用 */
    private val gradientRes = intArrayOf(
        R.drawable.module_hero_gradient,
        R.drawable.module_hero_gradient_2,
        R.drawable.module_hero_gradient_3
    )

    /** 三套 badge 文案循环使用 */
    private val badgeTextRes = intArrayOf(
        R.string.module_hero_badge,
        R.string.module_hero_badge_hot,
        R.string.module_hero_badge_new
    )

    /** 兼容旧 API：直接接收 ModuleManifest 列表 */
    fun submit(list: List<ModuleManifest>) {
        items.clear()
        items.addAll(list.map { HeroBannerItem(module = it, banner = null) })
        notifyDataSetChanged()
    }

    /** P1.3: 接收 StoreHeroBanner + ModuleManifest 配对列表 */
    fun submitItems(list: List<HeroBannerItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ModuleManifest? = items.getOrNull(position)?.module

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_hero_banner, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val item = items[position]
        val gradientIndex = position % gradientRes.size
        holder.bind(item, gradientRes[gradientIndex], badgeTextRes[gradientIndex])
    }

    override fun getItemCount(): Int = items.size

    inner class HeroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bgView: View = view.findViewById(R.id.heroBgView)
        private val badge: TextView = view.findViewById(R.id.heroBadge)
        private val title: TextView = view.findViewById(R.id.heroTitle)
        private val desc: TextView = view.findViewById(R.id.heroDesc)
        private val actionBtn: MaterialButton = view.findViewById(R.id.heroActionBtn)
        private val icon: ImageView = view.findViewById(R.id.heroIcon)

        fun bind(item: HeroBannerItem, gradient: Int, badgeRes: Int) {
            val module = item.module
            val banner = item.banner
            bgView.setBackgroundResource(gradient)
            badge.text = context.getString(badgeRes)

            // P1.3: 标题优先用 banner.title，为空则回退 module.name
            title.text = banner?.title?.ifEmpty { null } ?: module.name
            // P1.3: 副标题优先用 banner.subtitle，为空则回退 module.description
            desc.text = banner?.subtitle?.ifEmpty { null } ?: module.description

            // P1.3: 图片加载 — banner.imageUrl 非空时用 Glide 加载，失败回退模块图标
            val imageUrl = banner?.imageUrl?.ifEmpty { null }
            if (imageUrl != null) {
                Glide.with(context)
                    .load(imageUrl)
                    .placeholder(resolveIconRes(module))
                    .error(resolveIconRes(module))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?, model: Any?,
                            target: Target<Drawable>, isFirstResource: Boolean
                        ): Boolean {
                            // 加载失败时 Glide 会自动显示 error 占位图，此处仅记录
                            return false
                        }
                        override fun onResourceReady(
                            resource: Drawable, model: Any,
                            target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                        ): Boolean = false
                    })
                    .into(icon)
            } else {
                icon.setImageResource(resolveIconRes(module))
            }

            // 根据模块状态显示按钮文案
            val isInstalled = ModuleManager.isModuleInstalled(context, module.id)
            actionBtn.text = if (isInstalled) {
                context.getString(R.string.module_hero_action_open)
            } else if (module.builtIn) {
                context.getString(R.string.module_hero_action_enable)
            } else {
                context.getString(R.string.module_hero_action_download)
            }

            itemView.setOnClickListener { onItemClick(module) }
            actionBtn.setOnClickListener { onActionClick(module) }
        }

        private fun resolveIconRes(module: ModuleManifest): Int {
            return when {
                module.gameId.isNotEmpty() -> {
                    val resId = context.resources.getIdentifier(
                        "ic_${module.gameId}", "drawable", context.packageName
                    )
                    if (resId != 0) resId else {
                        val altResId = context.resources.getIdentifier(
                            "ic_game_${module.gameId}", "drawable", context.packageName
                        )
                        if (altResId != 0) altResId else getCategoryIcon(module.storeCategory)
                    }
                }
                else -> getCategoryIcon(module.storeCategory)
            }
        }

        private fun getCategoryIcon(category: String): Int = when (category) {
            "game" -> R.drawable.ic_games
            "browser" -> R.drawable.ic_browser
            "tools" -> R.drawable.ic_tools
            "ai" -> R.drawable.ic_ai
            "vpn" -> R.drawable.ic_vpn
            else -> R.drawable.ic_games
        }
    }
}

/**
 * Hero Banner 列表项：包装 ModuleManifest + 可选的远程 Banner 元数据。
 *
 * - module：用于点击路由、图标回退、按钮状态判断（必须非空）
 * - banner：远程 Banner 配置（可为 null，表示使用模块自身字段）
 */
data class HeroBannerItem(
    val module: ModuleManifest,
    val banner: StoreHeroBanner? = null
)
