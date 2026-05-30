package com.gamecenter.app.vpn.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.vpn.model.Node

/**
 * 节点列表适配器 — 纯代码构建 Item View，无 XML 依赖。
 */
class NodeAdapter(
    private val nodes: List<Node>,
    private val onNodeClick: (Node) -> Unit,
    private val onNodeDelete: ((Node) -> Unit)? = null
) : RecyclerView.Adapter<NodeAdapter.VH>() {

    inner class VH(val row: LinearLayout, val tvName: TextView,
                   val tvInfo: TextView, val tvStatus: TextView) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context; val dp = ctx.resources.displayMetrics.density
        val padH = (16 * dp).toInt(); val padV = (12 * dp).toInt()

        val tvName = TextView(ctx).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#212121")) }
        val tvInfo = TextView(ctx).apply { textSize = 13f; setTextColor(Color.parseColor("#757575")) }
        val tvStatus = TextView(ctx).apply { textSize = 12f; setTextColor(Color.parseColor("#4CAF50")) }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(tvName); addView(tvInfo); addView(tvStatus)
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padH, padV, padH, padV)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(textCol)
        }

        if (onNodeDelete != null) {
            val delBtn = ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setColorFilter(Color.RED)
                val s = (40 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER_VERTICAL }
            }
            row.addView(delBtn)
        }

        return VH(row, tvName, tvInfo, tvStatus)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = nodes[position]
        holder.tvName.text = node.name
        holder.tvInfo.text = "${node.type.name} · ${node.address}:${node.port}"
        holder.tvStatus.text = "未连接"
        holder.row.setOnClickListener { onNodeClick(node) }
        if (onNodeDelete != null && holder.row.childCount > 1) {
            holder.row.getChildAt(1).setOnClickListener { onNodeDelete(node) }
        }
    }

    override fun getItemCount() = nodes.size
}