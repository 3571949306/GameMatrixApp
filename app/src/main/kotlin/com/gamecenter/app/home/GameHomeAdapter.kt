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
    private val nameLoader: (GameRegistry.Entry) -> String,
    private val callbacks: Callbacks,
) : ListAdapter<GameHomeItem, RecyclerView.ViewHolder>(DIFF) {

    interface Callbacks {
        fun onContinue(entry: GameRegistry.Entry)
        fun onRecent(entry: GameRegistry.Entry)
        fun onTileClick(entry: GameRegistry.Entry)
        fun onTileLongPress(entry: GameRegistry.Entry, anchor: View)
        fun onEmptyAction()
        fun onToggleRecentExpanded()

        /** 每日挑战卡点击：entry 非空直达游戏，null（未绑定游戏）由页面提示兜底。 */
        fun onDailyChallengeClick(entry: GameRegistry.Entry?)
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
        const val TYPE_QUICK_STATS = 6
        const val TYPE_DAILY_CHALLENGE = 7
        const val TYPE_STREAK_SUMMARY = 8
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
        is GameHomeItem.QuickStatsRow -> TYPE_QUICK_STATS
        is GameHomeItem.DailyChallengeCard -> TYPE_DAILY_CHALLENGE
        is GameHomeItem.StreakSummaryCard -> TYPE_STREAK_SUMMARY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CONTINUE, TYPE_RECENT -> RowVH(inf.inflate(R.layout.item_game_home_row, parent, false))
            TYPE_TILE -> TileVH(inf.inflate(R.layout.item_game_library_tile, parent, false))
            TYPE_QUICK_STATS ->
                QuickStatsVH(inf.inflate(R.layout.item_game_home_quick_stats, parent, false))
            TYPE_DAILY_CHALLENGE ->
                DailyChallengeVH(inf.inflate(R.layout.item_game_home_daily_challenge, parent, false))
            TYPE_STREAK_SUMMARY ->
                StreakSummaryVH(inf.inflate(R.layout.item_game_home_streak_summary, parent, false))
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
            is QuickStatsVH -> bindQuickStats(vh, item as GameHomeItem.QuickStatsRow, p)
            is DailyChallengeVH -> bindDailyChallenge(vh, item as GameHomeItem.DailyChallengeCard, p)
            is StreakSummaryVH -> bindStreakSummary(vh, item as GameHomeItem.StreakSummaryCard, p)
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
        vh.name.text = nameLoader(row.entry)
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
        vh.name.text = nameLoader(item.entry)
        vh.name.setTextColor(p?.onSurface ?: vh.name.currentTextColor)
        vh.itemView.setOnClickListener { callbacks.onTileClick(item.entry) }
        vh.itemView.setOnLongClickListener {
            callbacks.onTileLongPress(item.entry, vh.icon)
            true
        }
    }

    /** 快览统计行（批次 2a）：数值主色、标签次色，卡片底色跟 Resolver。 */
    private fun bindQuickStats(
        vh: QuickStatsVH,
        item: GameHomeItem.QuickStatsRow,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        applyCardPalette(vh.itemView, p)
        vh.playtime.text = item.playtimeText
        vh.streak.text = item.streakText
        vh.achievements.text = item.achievementsText
        vh.values().forEach { it.setTextColor(p?.onSurface ?: it.currentTextColor) }
        vh.labels().forEach { it.setTextColor(p?.onSurfaceVariant ?: it.currentTextColor) }
        vh.itemView.setOnClickListener(null)
    }

    /** 每日挑战卡（批次 2a）：进度条主色，点击直达挑战游戏（或提示兜底）。 */
    private fun bindDailyChallenge(
        vh: DailyChallengeVH,
        item: GameHomeItem.DailyChallengeCard,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        applyCardPalette(vh.itemView, p)
        vh.title.text = item.title
        vh.title.setTextColor(p?.onSurface ?: vh.title.currentTextColor)
        vh.status.setTextColor(p?.onSurfaceVariant ?: vh.status.currentTextColor)
        vh.desc.text = item.gameName
        vh.desc.setTextColor(p?.onSurface ?: vh.desc.currentTextColor)
        vh.progress.progress = item.progressPercent
        p?.let {
            vh.progress.progressTintList = android.content.res.ColorStateList.valueOf(it.primary)
            vh.progress.progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(it.surfaceVariant)
            vh.icon.setColorFilter(it.primary)
        }
        vh.progressText.text = item.progressText
        vh.progressText.setTextColor(p?.onSurfaceVariant ?: vh.progressText.currentTextColor)
        vh.itemView.setOnClickListener { callbacks.onDailyChallengeClick(item.entry) }
    }

    /** 连胜概览卡（批次 2a）：当前/最佳两列，静态展示无点击。 */
    private fun bindStreakSummary(
        vh: StreakSummaryVH,
        item: GameHomeItem.StreakSummaryCard,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        applyCardPalette(vh.itemView, p)
        vh.current.text = item.currentDaysText
        vh.best.text = item.bestDaysText
        vh.values().forEach { it.setTextColor(p?.onSurface ?: it.currentTextColor) }
        vh.labels().forEach { it.setTextColor(p?.onSurfaceVariant ?: it.currentTextColor) }
        p?.let { vh.icon.setColorFilter(it.primary) }
        vh.itemView.setOnClickListener(null)
    }

    /** 卡片底色跟 Resolver（palette 缺省时保留主题默认色）。 */
    private fun applyCardPalette(card: View, p: GameHomeThemeResolver.GameHomePalette?) {
        val materialCard = card as? com.google.android.material.card.MaterialCardView ?: return
        p?.let { materialCard.setCardBackgroundColor(it.surface) }
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

    /** 快览统计行（批次 2a）：三格数值 + 三格标签。 */
    class QuickStatsVH(v: View) : RecyclerView.ViewHolder(v) {
        val playtime: TextView = v.findViewById(R.id.tv_stat_playtime_value)
        val streak: TextView = v.findViewById(R.id.tv_stat_streak_value)
        val achievements: TextView = v.findViewById(R.id.tv_stat_achievements_value)
        private val playtimeLabel: TextView = v.findViewById(R.id.tv_stat_playtime_label)
        private val streakLabel: TextView = v.findViewById(R.id.tv_stat_streak_label)
        private val achievementsLabel: TextView = v.findViewById(R.id.tv_stat_achievements_label)

        fun values() = listOf(playtime, streak, achievements)
        fun labels() = listOf(playtimeLabel, streakLabel, achievementsLabel)
    }

    /** 每日挑战卡（批次 2a）：状态 + 游戏名 + 进度条 + 进度文案。 */
    class DailyChallengeVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iv_daily_challenge_icon)
        val title: TextView = v.findViewById(R.id.tv_daily_challenge_title)
        val status: TextView = v.findViewById(R.id.tv_daily_challenge_status)
        val desc: TextView = v.findViewById(R.id.tv_daily_challenge_desc)
        val progress: android.widget.ProgressBar = v.findViewById(R.id.progress_daily_challenge)
        val progressText: TextView = v.findViewById(R.id.tv_daily_challenge_progress)
    }

    /** 连胜概览卡（批次 2a）：当前/最佳两列。 */
    class StreakSummaryVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iv_streak_summary_icon)
        val current: TextView = v.findViewById(R.id.tv_streak_current_value)
        val best: TextView = v.findViewById(R.id.tv_streak_best_value)
        private val currentLabel: TextView = v.findViewById(R.id.tv_streak_current_label)
        private val bestLabel: TextView = v.findViewById(R.id.tv_streak_best_label)

        fun values() = listOf(current, best)
        fun labels() = listOf(currentLabel, bestLabel)
    }

    internal object DIFF : DiffUtil.ItemCallback<GameHomeItem>() {
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
            is GameHomeItem.QuickStatsRow -> "q:quick-stats"
            is GameHomeItem.DailyChallengeCard -> "d:daily-challenge"
            is GameHomeItem.StreakSummaryCard -> "k:streak-summary"
        }
    }
}
