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

        /** 今日精选卡点击（批次 2b）：直达推荐游戏。 */
        fun onTodayFeaturedClick(entry: GameRegistry.Entry)

        /** 时长提醒卡“知道了”（批次 2b）：会话级 dismiss 后刷新。 */
        fun onPlaytimeReminderDismiss()

        /** 最近成就横幅关闭（批次 2b）：当日不再显示后刷新。 */
        fun onRecentAchievementDismiss()
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
        const val TYPE_RECENT_ACHIEVEMENT = 9
        const val TYPE_TODAY_FEATURED = 10
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
        is GameHomeItem.RecentAchievementBanner -> TYPE_RECENT_ACHIEVEMENT
        is GameHomeItem.TodayFeaturedCard -> TYPE_TODAY_FEATURED
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
            TYPE_RECENT_ACHIEVEMENT ->
                RecentAchievementVH(
                    inf.inflate(R.layout.item_game_home_recent_achievement, parent, false)
                )
            TYPE_TODAY_FEATURED ->
                TodayFeaturedVH(inf.inflate(R.layout.item_game_home_today_featured, parent, false))
            TYPE_HEALTH ->
                PlaytimeReminderVH(
                    inf.inflate(R.layout.item_game_home_playtime_reminder, parent, false)
                )
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
            is PlaytimeReminderVH ->
                bindPlaytimeReminder(vh, item as GameHomeItem.HealthReminder)
            is RecentAchievementVH ->
                bindRecentAchievement(vh, item as GameHomeItem.RecentAchievementBanner)
            is TodayFeaturedVH ->
                bindTodayFeatured(vh, item as GameHomeItem.TodayFeaturedCard, p)
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
                    // 批次 2b：展开态“收起”文案走 StateBuilder 注入的资源（GameHomeStrings.collapse）
                    vh.action.text = if (item.expanded) item.collapseText else ""
                    vh.itemView.setOnClickListener { callbacks.onToggleRecentExpanded() }
                } else {
                    vh.itemView.setOnClickListener(null)
                }
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

    /**
     * 今日时长提醒卡（批次 2b，对齐 legacy Batch 11-3）：MILD/SEVERE 渐变分档，
     * 文案白色（渐变底固定视觉，与 legacy 一致），“知道了”走会话 dismiss 回调。
     */
    private fun bindPlaytimeReminder(vh: PlaytimeReminderVH, item: GameHomeItem.HealthReminder) {
        vh.itemView.setBackgroundResource(
            if (item.severe) R.drawable.bg_playtime_reminder_gradient_severe
            else R.drawable.bg_playtime_reminder_gradient_mild
        )
        vh.today.text = item.text
        vh.desc.text = item.desc
        vh.desc.visibility = if (item.desc.isEmpty()) View.GONE else View.VISIBLE
        vh.dismiss.setOnClickListener { callbacks.onPlaytimeReminderDismiss() }
    }

    /** 最近解锁成就横幅（批次 2b，对齐 legacy Batch 12-2）：金黄渐变 + 白字，仅关闭可点。 */
    private fun bindRecentAchievement(
        vh: RecentAchievementVH,
        item: GameHomeItem.RecentAchievementBanner
    ) {
        vh.name.text = item.name
        vh.desc.text = item.desc
        vh.desc.visibility = if (item.desc.isEmpty()) View.GONE else View.VISIBLE
        vh.dismiss.setOnClickListener { callbacks.onRecentAchievementDismiss() }
        vh.itemView.setOnClickListener(null)
    }

    /** 今日精选推荐卡（批次 2b）：底色跟 Resolver，整卡点击直达推荐游戏。 */
    private fun bindTodayFeatured(
        vh: TodayFeaturedVH,
        item: GameHomeItem.TodayFeaturedCard,
        p: GameHomeThemeResolver.GameHomePalette?
    ) {
        applyCardPalette(vh.itemView, p)
        vh.icon.setImageDrawable(iconLoader(item.entry))
        vh.name.text = nameLoader(item.entry)
        vh.name.setTextColor(p?.onSurface ?: vh.name.currentTextColor)
        vh.title.setTextColor(p?.onSurfaceVariant ?: vh.title.currentTextColor)
        vh.cta.setTextColor(p?.primary ?: vh.cta.currentTextColor)
        vh.desc.text = item.desc
        vh.desc.setTextColor(p?.onSurfaceVariant ?: vh.desc.currentTextColor)
        vh.desc.visibility = if (item.desc.isEmpty()) View.GONE else View.VISIBLE
        vh.itemView.setOnClickListener { callbacks.onTodayFeaturedClick(item.entry) }
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

    /** 今日时长提醒卡（批次 2b）：分档渐变底 + 今日分钟数 + 档位建议 + 知道了。 */
    class PlaytimeReminderVH(v: View) : RecyclerView.ViewHolder(v) {
        val today: TextView = v.findViewById(R.id.tv_playtime_reminder_today)
        val desc: TextView = v.findViewById(R.id.tv_playtime_reminder_desc)
        val dismiss: TextView = v.findViewById(R.id.btn_playtime_reminder_dismiss)
    }

    /** 最近解锁成就横幅（批次 2b）：成就名 + 描述 + 关闭。 */
    class RecentAchievementVH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_recent_achievement_name)
        val desc: TextView = v.findViewById(R.id.tv_recent_achievement_desc)
        val dismiss: View = v.findViewById(R.id.btn_recent_achievement_dismiss)
    }

    /** 今日精选推荐卡（批次 2b）：推荐语标题 + 游戏名 + 描述 + 立即挑战。 */
    class TodayFeaturedVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iv_today_featured_icon)
        val title: TextView = v.findViewById(R.id.tv_today_featured_title)
        val name: TextView = v.findViewById(R.id.tv_today_featured_name)
        val desc: TextView = v.findViewById(R.id.tv_today_featured_desc)
        val cta: TextView = v.findViewById(R.id.tv_today_featured_cta)
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
            is GameHomeItem.RecentAchievementBanner -> "a:recent-achievement"
            is GameHomeItem.TodayFeaturedCard -> "f:today-featured"
        }
    }
}
