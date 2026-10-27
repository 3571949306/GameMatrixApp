package com.gamecenter.app.vpn.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.vpn.model.Node

/**
 * 节点列表适配器 — 纯代码构建高质感 Card 样式 Item，无 XML 依赖。
 * 完美解决主题颜色解析 Bug，支持深浅色自适应、协议微徽标与删除手势。
 */
class NodeAdapter(
    private val nodes: List<Node>,
    private val onNodeClick: (Node) -> Unit,
    private val onNodeDelete: ((Node) -> Unit)? = null
) : RecyclerView.Adapter<NodeAdapter.VH>() {

    inner class VH(
        val card: FrameLayout,
        val tvName: TextView,
        val tvInfo: TextView,
        val tvStatus: TextView,
        val tvBadge: TextView
    ) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val dp = ctx.resources.displayMetrics.density
        val isDark = isDarkMode(ctx)

        val textColorPrimary = resolveColor(ctx, android.R.attr.textColorPrimary, if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        val textColorSecondary = resolveColor(ctx, android.R.attr.textColorSecondary, if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        val primaryColor = resolveColor(ctx, android.R.attr.colorPrimary, Color.parseColor("#6750A4"))

        // 最外层 Card 容器
        val card = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (4 * dp).toInt(), 0, (6 * dp).toInt())
            }
            val cardBg = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(if (isDark) Color.parseColor("#1F1E26") else Color.parseColor("#F7F5FA"))
                setStroke((1 * dp).toInt(), if (isDark) Color.parseColor("#332F3D") else Color.parseColor("#E7E0EC"))
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#206750A4")),
                cardBg,
                null
            )
            isClickable = true
            isFocusable = true
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 协议标签徽章 (如 SS, VM, TL)
        val tvBadge = TextView(ctx).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            gravity = Gravity.CENTER
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(if (isDark) Color.parseColor("#2C263B") else Color.parseColor("#EADDFF"))
            }
            val badgeLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * dp).toInt()
            }
            layoutParams = badgeLp
        }
        row.addView(tvBadge)

        // 文本信息列
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvName = TextView(ctx).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColorPrimary)
        }
        textCol.addView(tvName)

        val tvInfo = TextView(ctx).apply {
            textSize = 12f
            setTextColor(textColorSecondary)
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
        textCol.addView(tvInfo)

        val tvStatus = TextView(ctx).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#4CAF50"))
        }
        textCol.addView(tvStatus)
        row.addView(textCol)

        // 删除按钮
        if (onNodeDelete != null) {
            val delBtn = ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setColorFilter(Color.parseColor("#E57373"))
                val s = (36 * dp).toInt()
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = (8 * dp).toInt()
                }
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#20FF0000")),
                    null,
                    null
                )
                isClickable = true
                isFocusable = true
            }
            row.addView(delBtn)
        }

        card.addView(row)
        return VH(card, tvName, tvInfo, tvStatus, tvBadge)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = nodes[position]
        holder.tvName.text = node.name
        holder.tvInfo.text = "${node.address}:${node.port}"
        holder.tvBadge.text = node.type.name.take(4).uppercase()
        holder.tvStatus.text = "● 待命未连接"
        holder.card.setOnClickListener { onNodeClick(node) }

        if (onNodeDelete != null) {
            val row = holder.card.getChildAt(0) as? LinearLayout
            val delBtn = row?.getChildAt(2) as? ImageView
            delBtn?.setOnClickListener { onNodeDelete(node) }
        }
    }

    override fun getItemCount(): Int = nodes.size

    private fun resolveColor(ctx: Context, attr: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else if (typedValue.resourceId != 0) {
                ContextCompat.getColor(ctx, typedValue.resourceId)
            } else {
                fallback
            }
        } else {
            fallback
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
