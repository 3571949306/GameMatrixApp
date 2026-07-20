package com.gamecenter.app.modules

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.google.android.material.button.MaterialButton

/**
 * Hero Banner 多卡片轮播 Adapter（Batch 21）。
 *
 * 每张卡片展示一个推荐模块，使用不同色系渐变背景增强视觉冲击力。
 */
class HeroBannerAdapter(
    private val context: Context,
    private val onItemClick: (ModuleManifest) -> Unit,
    private val onActionClick: (ModuleManifest) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.HeroViewHolder>() {

    private val items = mutableListOf<ModuleManifest>()

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

    fun submit(list: List<ModuleManifest>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ModuleManifest? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_hero_banner, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val module = items[position]
        val gradientIndex = position % gradientRes.size
        holder.bind(module, gradientRes[gradientIndex], badgeTextRes[gradientIndex])
    }

    override fun getItemCount(): Int = items.size

    inner class HeroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bgView: View = view.findViewById(R.id.heroBgView)
        private val badge: TextView = view.findViewById(R.id.heroBadge)
        private val title: TextView = view.findViewById(R.id.heroTitle)
        private val desc: TextView = view.findViewById(R.id.heroDesc)
        private val actionBtn: MaterialButton = view.findViewById(R.id.heroActionBtn)
        private val icon: ImageView = view.findViewById(R.id.heroIcon)

        fun bind(module: ModuleManifest, gradient: Int, badgeRes: Int) {
            bgView.setBackgroundResource(gradient)
            badge.text = context.getString(badgeRes)
            title.text = module.name
            desc.text = module.description
            icon.setImageResource(resolveIconRes(module))

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
            // 复用 ModuleAdapter 的图标解析逻辑（三级回退）
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
