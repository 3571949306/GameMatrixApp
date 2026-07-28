package com.gamecenter.app.ui

import android.content.Context
import android.content.Intent
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
import com.gamecenter.app.games.FavoriteGroupStore
import com.gamecenter.app.games.LeaderboardActivity
import com.gamecenter.app.games.ShareCardGenerator
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
        // BUG-008 修复：收藏按钮的 contentDescription 必须随状态变化，否则无障碍用户无法得知当前是"加入收藏"还是"取消收藏"。
        // 同时修复 toggleFavorite 后用 !wasFav 推断新状态的脆弱逻辑：若 SP 写入失败，UI 会与存储错位。
        // 现改为切换后重新读取权威值，确保 UI 与 GameUsageStore 数据一致（个人中心/游戏大厅读到的是同一份数据）。
        updateFavoriteIcon(btnFav, store.isFavorite(entry.id))
        btnFav.setOnClickListener {
            val wasFav = store.isFavorite(entry.id)
            store.toggleFavorite(entry.id)
            // BUG-008 修复：重新读取权威值，避免推断错误；同时确保 UI 与存储一致
            val nowFav = store.isFavorite(entry.id)
            updateFavoriteIcon(btnFav, nowFav)
            // P0-2 (FAVORITE_GROUPS): 新增收藏时让用户选择分组；取消收藏时清理映射
            if (nowFav && !wasFav) {
                promptSelectGroup(ctx, entry.id)
            } else if (!nowFav && wasFav) {
                FavoriteGroupStore(ctx).removeGame(entry.id)
            }
            // BUG-008 修复：Toast 消息逻辑之前是反的（nowFav=true 显示"加入收藏"，应显示"已加入收藏"）。
            // 现修正为：收藏成功显示"已加入收藏"，取消收藏显示"已取消收藏"。
            Toast.makeText(
                ctx,
                if (nowFav) R.string.game_detail_favorite_added
                else R.string.game_detail_favorite_removed,
                Toast.LENGTH_SHORT
            ).show()
            onFavoriteToggled.onToggled()
        }

        // 立即开始
        view.findViewById<MaterialButton>(R.id.btn_detail_play).setOnClickListener {
            dismissAllowingStateLoss()
            onPlay.onPlay(entry)
        }

        // P0-1 (LEADERBOARD): 查看排行榜
        view.findViewById<MaterialButton>(R.id.btn_detail_leaderboard)?.setOnClickListener {
            val intent = Intent(ctx, LeaderboardActivity::class.java).apply {
                putExtra(LeaderboardActivity.EXTRA_GAME_ID, entry.id)
            }
            try {
                startActivity(intent)
                dismissAllowingStateLoss()
            } catch (e: Exception) {
                Toast.makeText(ctx, R.string.leaderboard_unable_open, Toast.LENGTH_SHORT).show()
            }
        }

        // P0-3 (SHARE_CARD): 分享战绩
        view.findViewById<MaterialButton>(R.id.btn_detail_share)?.setOnClickListener {
            shareGameStats(ctx, store)
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
                val starView = stars[i]
                starView?.setImageResource(
                    if (i < current) R.drawable.ic_star_filled
                    else R.drawable.ic_star_border
                )
                // BUG-006 修复：动态格式化每颗星星的 contentDescription。
                // 之前布局 XML 中静态引用 @string/game_rating_star_desc（="%1$d 星"），
                // 占位符不会被替换，TalkBack 朗读"%1$d 星"，且 5 颗星描述完全相同无法区分。
                // 现按 i+1 格式化为"1 星"~"5 星"，并附加当前是否选中的状态，便于无障碍用户识别。
                val starNum = i + 1
                val starDesc = ctx.getString(R.string.game_rating_star_desc, starNum)
                starView?.contentDescription = if (i < current) {
                    "$starDesc（${ctx.getString(R.string.game_rating_my_rating_label)})"
                } else {
                    starDesc
                }
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
        // BUG-008 修复：同步更新 contentDescription，使无障碍服务能正确朗读当前收藏状态。
        // 之前 contentDescription 为布局 XML 静态值，无论收藏与否都朗读"加入收藏"，与图标状态不符。
        btn.contentDescription = btn.context.getString(
            if (isFavorite) R.string.game_detail_favorite_remove
            else R.string.game_detail_favorite_add
        )
    }

    /** P0-2 (FAVORITE_GROUPS): 收藏成功后弹窗让用户选择分组（可选操作，取消则归入默认分组）。 */
    private fun promptSelectGroup(ctx: Context, gameId: String) {
        val store = FavoriteGroupStore(ctx)
        val groups = store.groups
        if (groups.isEmpty()) return
        val labels = arrayOfNulls<String>(groups.size)
        for (i in groups.indices) {
            labels[i] = groups[i].name
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.favorite_groups_select)
            .setMessage(R.string.favorite_groups_select_hint)
            .setItems(labels) { _, which ->
                if (which in groups.indices) {
                    store.setGameGroup(gameId, groups[which].id)
                }
            }
            .setNegativeButton(R.string.leaderboard_clear_confirm_negative, null)
            .show()
    }

    /** P0-3 (SHARE_CARD): 构建战绩数据并启动分享 Intent。 */
    private fun shareGameStats(ctx: Context, store: GameUsageStore) {
        val data = ShareCardGenerator.Data().apply {
            gameName = entry.name
            gameId = entry.id
            gameIconRes = entry.iconRes
            highScore = store.getHighScore(entry.id)
            playCount = store.getPlayCount(entry.id)
            winCount = store.getWinCount(entry.id)
            lossCount = store.getLossCount(entry.id)
            playTimeMs = store.getTotalPlayTimeMs(entry.id)
        }
        if (!data.hasData()) {
            Toast.makeText(ctx, R.string.share_card_no_data, Toast.LENGTH_SHORT).show()
            return
        }
        // 生成 Bitmap 在子线程，避免阻塞 UI
        Thread {
            val generator = ShareCardGenerator(ctx)
            val intent = generator.buildShareIntent(data)
            activity?.runOnUiThread {
                if (intent != null) {
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, R.string.share_card_save_failed,
                                Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, R.string.share_card_save_failed,
                            Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
