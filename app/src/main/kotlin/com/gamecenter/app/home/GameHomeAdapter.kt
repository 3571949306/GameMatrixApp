package com.gamecenter.app.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRegistry

/**
 * 游戏库主页多类型 ListAdapter（计划 §6.6）：稳定 ID + DiffUtil。
 * Palette 整体变化允许整表重绑（§6.6 例外条款，见 [setPalette]）。
 */
class GameHomeAdapter(
    private val iconLoader: (GameRegistry.Entry) -> Drawable?,
    private val callbacks: Callbacks,
) : ListAdapter<GameHomeItem, RecyclerView.ViewHolder>(DIFF) {

    interface Callbacks {
        fun onContinue(entry: GameRegistry.Entry)
        fun onRecent(entry: GameRegistry.Entry)
        fun onTileClick(entry: GameRegistry.Entry)
        fun onTileLongPress(entry: GameRegistry.Entry, anchor: View)
        fun onEmptyAction()
        fun onToggleRecentExpanded()
    }

    private var palette: GameHomeThemeResolver.GameHomePalette? = null
    private var boundRecyclerView: RecyclerView? = null

    private companion object {
        const val TYPE_HEALTH = 0
        const val TYPE_CONTINUE = 1
        const val TYPE_SECTION = 2
        const val TYPE_RECENT = 3
        const val TYPE_EMPTY = 4
        const val TYPE_TILE = 5
    }

    fun setPalette(p: GameHomeThemeResolver.GameHomePalette) {
        if (palette == p) return
        palette = p
        // 主题整体切换：允许整表重绑（计划 §6.6 例外条款）
        boundRecyclerView?.let { notifyDataSetChanged() }
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        boundRecyclerView = rv
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GameHomeItem.HealthReminder -> TYPE_HEALTH
        is GameHomeItem.ContinueRow -> TYPE_CONTINUE
        is GameHomeItem.SectionHeader -> TYPE_SECTION
        is GameHomeItem.RecentRow -> TYPE_RECENT
        is GameHomeItem.EmptyState -> TYPE_EMPTY
        is GameHomeItem.GameTile -> TYPE_TILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CONTINUE, TYPE_RECENT -> RowVH(inf.inflate(R.layout.item_game_home_row, parent, false))
            TYPE_TILE -> TileVH(inf.inflate(R.layout.item_game_library_tile, parent, false))
            else -> TextVH(inf.inflate(R.layout.item_game_home_text, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val p = palette
        when (val vh = holder) {
            is RowVH -> bindRow(vh, item as GameHomeItem.EntryRow, item is GameHomeItem.ContinueRow, p)
            is TileVH -> bindTile(vh, item as GameHomeItem.GameTile, p)
            is TextVH -> bindText(vh, item, p)
        }
    }

    private fun bindText(vh: TextVH, item: GameHomeItem, p: GameHomeThemeResolver.GameHomePalette?) {
        val onSurface = p?.onSurface ?: vh.primary.currentTextColor
        val onSurfaceVariant = p?.onSurfaceVariant ?: vh.primary.currentTextColor
        when (item) {
            is GameHomeItem.SectionHeader -> {
                vh.primary.text = item.title
                vh.primary.textSize = 15f
                vh.primary.setTypeface(vh.primary.typeface, android.graphics.Typeface.BOLD)
                vh.primary.setTextColor(onSurface)
                vh.primary.gravity = android.view.Gravity.START
                vh.action.visibility = View.GONE
                vh.primary.contentDescription = item.title
                if (item.expandable) {
                    vh.action.text = if (item.expanded) "收起" else ""
                    vh.itemView.setOnClickListener { callbacks.onToggleRecentExpanded() }
                } else {
                    vh.itemView.setOnClickListener(null)
                }
            }
            is GameHomeItem.HealthReminder -> {
                vh.primary.text = item.text
                vh.primary.textSize = 13f
                vh.primary.setTypeface(vh.primary.typeface, android.graphics.Typeface.NORMAL)
                vh.primary.setTextColor(onSurfaceVariant)
                vh.action.visibility = View.GONE
                vh.itemView.setOnClickListener(null)
            }
            is GameHomeItem.EmptyState -> {
                vh.primary.text = item.message
                vh.primary.textSize = 14f
                vh.primary.setTypeface(vh.primary.typeface, android.graphics.Typeface.NORMAL)
                vh.primary.setTextColor(onSurfaceVariant)
                vh.primary.gravity = android.view.Gravity.CENTER
                if (item.action != null) {
                    vh.action.text = item.action
                    vh.action.visibility = View.VISIBLE
                    vh.itemView.setOnClickListener { callbacks.onEmptyAction() }
                } else {
                    vh.action.visibility = View.GONE
                    vh.itemView.setOnClickListener(null)
                }
            }
            else -> Unit
        }
    }

    private fun bindRow(
        vh: RowVH,
        row: GameHomeItem.EntryRow,
        isContinue: Boolean,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        vh.icon.setImageDrawable(iconLoader(row.entry))
        vh.name.text = row.entry.name
        vh.name.setTextColor(p?.onSurface ?: vh.name.currentTextColor)
        vh.meta.text = row.lastPlayedText
        vh.meta.setTextColor(p?.onSurfaceVariant ?: vh.meta.currentTextColor)
        if (isContinue) {
            vh.action.visibility = View.VISIBLE
            vh.itemView.setOnClickListener { callbacks.onContinue(row.entry) }
        } else {
            vh.action.visibility = View.GONE
            vh.itemView.setOnClickListener { callbacks.onRecent(row.entry) }
        }
    }

    private fun bindTile(
        vh: TileVH,
        item: GameHomeItem.GameTile,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        vh.icon.setImageDrawable(iconLoader(item.entry))
        vh.name.text = item.entry.name
        vh.name.setTextColor(p?.onSurface ?: vh.name.currentTextColor)
        vh.itemView.setOnClickListener { callbacks.onTileClick(item.entry) }
        vh.itemView.setOnLongClickListener {
            callbacks.onTileLongPress(item.entry, vh.icon)
            true
        }
    }

    class TextVH(v: View) : RecyclerView.ViewHolder(v) {
        val primary: TextView = v.findViewById(R.id.tv_text_row_primary)
        val action: TextView = v.findViewById(R.id.tv_text_row_action)
    }

    class RowVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iv_row_icon)
        val name: TextView = v.findViewById(R.id.tv_row_name)
        val meta: TextView = v.findViewById(R.id.tv_row_meta)
        val action: TextView = v.findViewById(R.id.tv_row_action)
    }

    class TileVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iv_tile_icon)
        val name: TextView = v.findViewById(R.id.tv_tile_name)
    }

    private object DIFF : DiffUtil.ItemCallback<GameHomeItem>() {
        override fun areItemsTheSame(old: GameHomeItem, new: GameHomeItem): Boolean =
            stableId(old) == stableId(new)

        override fun areContentsTheSame(old: GameHomeItem, new: GameHomeItem): Boolean = old == new

        private fun stableId(item: GameHomeItem): String = when (item) {
            is GameHomeItem.HealthReminder -> "health"
            is GameHomeItem.ContinueRow -> "c:${item.entry.id}"
            is GameHomeItem.SectionHeader -> "s:${item.title}:${item.expanded}"
            is GameHomeItem.RecentRow -> "r:${item.entry.id}"
            is GameHomeItem.EmptyState -> "e:${item.message}:${item.action ?: ""}"
            is GameHomeItem.GameTile -> "t:${item.entry.id}"
        }
    }
}
