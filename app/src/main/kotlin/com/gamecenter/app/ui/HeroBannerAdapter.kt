package com.gamecenter.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R

/**
 * Batch 8-4 (HOME_HERO_BANNER): 首页英雄横幅 Adapter
 *
 * 配合 ViewPager2 使用，展示一组 [HeroBannerItem]。
 * 每张横幅包含：背景渐变（按 type 取色） + 图标 + 标题 + 副标题 + CTA 标签。
 *
 * 不接入点击跳转逻辑，由调用方在 ViewPager2 / item view 上设置点击监听，
 * 通过 [HeroBannerItem.action] 区分行为。
 */
class HeroBannerAdapter(
    private val context: Context,
    private val items: List<HeroBannerItem>,
    private val onClick: (HeroBannerItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.VH>() {

    init {
        // 配合 ViewPager2 内部 RecyclerView.setHasStableIds，提高滑动稳定性
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].stableId

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hero_banner, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item, context)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Batch 21: item_hero_banner.xml 被模块商店 Hero Banner 复用并升级了 ID
        // 这里同步适配新的 ID（heroBgView / heroIcon / heroTitle / heroDesc / heroActionBtn）
        private val root: View = itemView.findViewById(R.id.heroBgView)
        private val icon: ImageView = itemView.findViewById(R.id.heroIcon)
        private val title: TextView = itemView.findViewById(R.id.heroTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.heroDesc)
        private val cta: TextView = itemView.findViewById(R.id.heroActionBtn)

        fun bind(item: HeroBannerItem, context: Context) {
            // 背景：按 type 取对应 drawable
            root.setBackgroundResource(item.backgroundRes)
            icon.setImageResource(item.iconRes)
            icon.contentDescription = context.getString(item.titleRes)
            title.setText(item.titleRes)
            subtitle.setText(item.subtitleRes)
            cta.setText(item.ctaRes)
        }
    }
}

/**
 * 横幅项数据。
 *
 * @param stableId 稳定 ID，用于 RecyclerView.setHasStableIds
 * @param type 类型，决定背景渐变
 * @param backgroundRes 背景 drawable 资源（包含渐变）
 * @param iconRes 图标资源
 * @param titleRes 标题字符串资源
 * @param subtitleRes 副标题字符串资源
 * @param ctaRes CTA 按钮文字资源
 * @param action 点击行为标识，由调用方解析
 */
data class HeroBannerItem(
    val stableId: Long,
    val type: BannerType,
    val backgroundRes: Int,
    val iconRes: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val ctaRes: Int,
    val action: BannerAction
)

enum class BannerType { DAILY_PICK, EVENT, CHALLENGE }
enum class BannerAction { OPEN_DAILY_CHALLENGE, OPEN_ACHIEVEMENT, OPEN_MODULE_STORE }
