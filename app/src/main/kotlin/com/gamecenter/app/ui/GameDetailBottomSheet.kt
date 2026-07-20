package com.gamecenter.app.ui

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRatingStore
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

/**
 * 游戏详情底部表（Feature: GAME_DETAIL_SHEET）。
 *
 * 展示游戏图标、名称、分类、描述、战绩（总对局/胜负/时长/最高分/上次游玩）、
 * 收藏切换、"立即开始"主按钮。
 *
 * 通过 [BuildConfig.GAME_DETAIL_SHEET] 控制（调用方在 [com.gamecenter.app.GamesFragment] 中已判断）。
 *
 * Batch 11-1 (GAME_RATING_SYSTEM)：增加 5 星用户评分区块，与 [GameRatingStore] 协同。
 */
class GameDetailBottomSheet(
    private val entry: GameRegistry.Entry,
    private val onPlay: OnPlayListener,
    private val onFavoriteToggled: OnFavoriteToggledListener = OnFavoriteToggledListener {},
    private val onRatingChanged: OnRatingChangedListener = OnRatingChangedListener { _, _ -> }
) : BottomSheetDialogFragment() {

    /** 点击"立即开始"回调。Java 友好 SAM 接口。 */
    fun interface OnPlayListener {
        fun onPlay(entry: GameRegistry.Entry)
    }

    /** 收藏状态切换回调。Java 友好 SAM 接口。 */
    fun interface OnFavoriteToggledListener {
        fun onToggled()
    }

    /** 评分变化回调。Java 友好 SAM 接口（Batch 11-1）。 */
    fun interface OnRatingChangedListener {
        fun onChanged(gameId: String, stars: Int)
    }

    override fun getTheme(): Int = R.style.Theme_GameMatrix_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_game_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx: Context = view.context
        val store = GameUsageStore(ctx)

        // 图标 + 标题 + 分类
        val ivIcon = view.findViewById<ImageView>(R.id.iv_detail_icon)
        if (entry.iconRes != 0) {
            ivIcon.setImageResource(entry.iconRes)
        }
        view.findViewById<TextView>(R.id.tv_detail_name).text = entry.name

        val tvCategory = view.findViewById<TextView>(R.id.tv_detail_category)
        tvCategory.text = categoryLabel(entry.categoryKey)
        tvCategory.setBackgroundResource(categoryBackground(entry.categoryKey))

        // 描述
        view.findViewById<TextView>(R.id.tv_detail_desc).text = entry.desc

        // 战绩
        bindStats(view, store)

        // 收藏按钮
        val btnFav = view.findViewById<ImageView>(R.id.btn_detail_favorite)
        updateFavoriteIcon(btnFav, store.isFavorite(entry.id))
        btnFav.setOnClickListener {
            val wasFav = store.isFavorite(entry.id)
            store.toggleFavorite(entry.id)
            val nowFav = !wasFav
            updateFavoriteIcon(btnFav, nowFav)
            Toast.makeText(
                ctx,
                if (nowFav) R.string.game_detail_favorite_add
                else R.string.game_detail_favorite_remove,
                Toast.LENGTH_SHORT
            ).show()
            onFavoriteToggled.onToggled()
        }

        // 立即开始
        view.findViewById<MaterialButton>(R.id.btn_detail_play).setOnClickListener {
            dismissAllowingStateLoss()
            onPlay.onPlay(entry)
        }

        // Batch 11-1 (GAME_RATING_SYSTEM): 用户评分区块
        if (BuildConfig.GAME_RATING_SYSTEM) {
            bindUserRating(view)
        }
    }

    /** Batch 11-1: 绑定评分区块。 */
    private fun bindUserRating(view: View) {
        val ctx = view.context
        val section = view.findViewById<View>(R.id.section_user_rating) ?: return
        section.visibility = View.VISIBLE

        val ratingStore = GameRatingStore(ctx)
        val stars = arrayOfNulls<ImageView>(5)
        stars[0] = view.findViewById(R.id.star_1)
        stars[1] = view.findViewById(R.id.star_2)
        stars[2] = view.findViewById(R.id.star_3)
        stars[3] = view.findViewById(R.id.star_4)
        stars[4] = view.findViewById(R.id.star_5)
        val tvStatus = view.findViewById<TextView>(R.id.tv_rating_status)
        val btnClear = view.findViewById<TextView>(R.id.btn_clear_rating)

        fun refreshStars(current: Int) {
            for (i in 0 until 5) {
                stars[i]?.setImageResource(
                    if (i < current) R.drawable.ic_star_filled
                    else R.drawable.ic_star_border
                )
            }
            tvStatus.text = if (current > 0) {
                ctx.getString(R.string.game_rating_my_rating_label) + ": " +
                    ctx.getString(R.string.game_rating_user_format, current)
            } else {
                ctx.getString(R.string.game_rating_dialog_subtitle)
            }
        }

        refreshStars(ratingStore.getRating(entry.id))

        for (i in 0 until 5) {
            stars[i]?.setOnClickListener {
                val newStars = i + 1
                ratingStore.setRating(entry.id, newStars)
                refreshStars(newStars)
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.game_rating_saved, newStars),
                    Toast.LENGTH_SHORT
                ).show()
                onRatingChanged.onChanged(entry.id, newStars)
            }
        }

        btnClear.setOnClickListener {
            ratingStore.clearRating(entry.id)
            refreshStars(0)
            Toast.makeText(
                ctx,
                R.string.game_rating_cleared,
                Toast.LENGTH_SHORT
            ).show()
            onRatingChanged.onChanged(entry.id, 0)
        }
    }

    private fun bindStats(view: View, store: GameUsageStore) {
        val ctx = view.context
        val playCount = store.getPlayCount(entry.id)
        val wins = store.getWinCount(entry.id)
        val losses = store.getLossCount(entry.id)
        val playTimeMs = store.getTotalPlayTimeMs(entry.id)
        val lastPlayed = store.getLastPlayedAt(entry.id)
        val highScore = store.getHighScore(entry.id)

        view.findViewById<TextView>(R.id.tv_detail_play_count).text =
            ctx.getString(R.string.game_detail_play_count_format, playCount)

        view.findViewById<TextView>(R.id.tv_detail_win_loss).text =
            ctx.getString(R.string.game_detail_win_loss_format, wins, losses)

        view.findViewById<TextView>(R.id.tv_detail_play_time).text =
            ctx.getString(R.string.game_detail_play_time_format, formatDuration(ctx, playTimeMs))

        val tvHigh = view.findViewById<TextView>(R.id.tv_detail_high_score)
        if (highScore > 0) {
            tvHigh.visibility = View.VISIBLE
            tvHigh.text = ctx.getString(R.string.game_detail_high_score_format, highScore)
        } else {
            tvHigh.visibility = View.GONE
        }

        val tvLast = view.findViewById<TextView>(R.id.tv_detail_last_played)
        if (lastPlayed > 0) {
            tvLast.text = ctx.getString(
                R.string.game_detail_last_played_format,
                DateUtils.getRelativeTimeSpanString(
                    lastPlayed,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            )
        } else {
            tvLast.text = ctx.getString(R.string.game_detail_last_played_never)
        }
    }

    private fun updateFavoriteIcon(btn: ImageView, isFavorite: Boolean) {
        btn.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite
        )
    }

    private fun categoryLabel(key: String): String {
        val ctx = context ?: return key
        return when (key) {
            GameRegistry.CATEGORY_CLASSICS -> ctx.getString(R.string.category_classics)
            GameRegistry.CATEGORY_PUZZLE -> ctx.getString(R.string.category_puzzle)
            GameRegistry.CATEGORY_CASUAL -> ctx.getString(R.string.category_casual)
            else -> key
        }
    }

    private fun categoryBackground(key: String): Int {
        return when (key) {
            GameRegistry.CATEGORY_CLASSICS -> R.drawable.bg_category_tag_classics
            GameRegistry.CATEGORY_PUZZLE -> R.drawable.bg_category_tag_puzzle
            GameRegistry.CATEGORY_CASUAL -> R.drawable.bg_category_tag_casual
            else -> R.drawable.bg_category_tag_classics
        }
    }

    private fun formatDuration(ctx: Context, ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        return ctx.getString(R.string.game_detail_play_time_now_format, minutes.toInt())
    }
}
