package com.gamecenter.app.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.ui.GameLauncherHelper

/**
 * Batch 9-1 (GAME_LONG_PRESS_MENU): 游戏卡片长按上下文菜单。
 *
 * 提供 4 项操作：
 * 1. 立即开始 —— 直接调用 GameLauncherHelper 启动游戏
 * 2. 收藏 / 取消收藏 —— 切换 [GameUsageStore] 的收藏状态
 * 3. 分享 —— 通过系统分享面板把游戏名 + 描述分享出去
 * 4. 添加到桌面 —— 通过 [ShortcutManagerCompat] 添加动态快捷方式到桌面
 *
 * 受 [BuildConfig.GAME_LONG_PRESS_MENU] feature flag 控制。
 */
object GameLongPressMenu {

    /** 收藏状态切换回调（BUG-008 修复：用于通知调用方刷新卡片图标，避免长按菜单与卡片状态不同步）。 */
    fun interface OnFavoriteToggledListener {
        fun onToggled()
    }

    /**
     * 弹出长按菜单。
     *
     * @param onFavoriteToggled 可选回调：用户在长按菜单中切换收藏后触发，
     *        调用方（如 [com.gamecenter.app.GamesFragment]）可据此刷新卡片图标。
     *        BUG-008 修复前该回调不存在，长按切换收藏后卡片心形图标不刷新，与 ProfileFragment 显示的收藏数不一致。
     */
    fun show(
        context: Context,
        anchor: android.view.View,
        entry: GameRegistry.Entry,
        onFavoriteToggled: OnFavoriteToggledListener = OnFavoriteToggledListener {}
    ) {
        if (!BuildConfig.GAME_LONG_PRESS_MENU) return
        val popup = PopupMenu(context, anchor)
        val menu = popup.menu
        val store = GameUsageStore(context)
        val isFav = store.isFavorite(entry.id)

        // 1. 立即开始
        menu.add(0, MenuId.PLAY.ordinal, 0, R.string.long_press_play_now)
        // 2. 收藏 / 取消收藏
        menu.add(0, MenuId.FAVORITE.ordinal, 0,
            if (isFav) R.string.long_press_remove_favorite else R.string.long_press_add_favorite)
        // 3. 分享
        menu.add(0, MenuId.SHARE.ordinal, 0, R.string.long_press_share)
        // 4. 添加到桌面
        menu.add(0, MenuId.SHORTCUT.ordinal, 0, R.string.long_press_add_shortcut)

        popup.setOnMenuItemClickListener { item ->
            when (MenuId.values()[item.itemId]) {
                MenuId.PLAY -> {
                    GameLauncherHelper.launchGameWithDialog(context, entry.id)
                    true
                }
                MenuId.FAVORITE -> {
                    // BUG-008 修复：切换后重新读取权威状态，避免 wasFav 与实际写入结果不一致；
                    // 同时通过回调通知调用方刷新卡片图标，避免长按菜单切换后卡片状态滞后。
                    store.toggleFavorite(entry.id)
                    val nowFav = store.isFavorite(entry.id)
                    Toast.makeText(context,
                        if (nowFav) R.string.home_card_favorite_add
                        else R.string.home_card_favorite_remove,
                        Toast.LENGTH_SHORT).show()
                    onFavoriteToggled.onToggled()
                    true
                }
                MenuId.SHARE -> {
                    shareGame(context, entry)
                    true
                }
                MenuId.SHORTCUT -> {
                    addHomeShortcut(context, entry)
                    true
                }
            }
        }
        popup.show()
    }

    /** 通过系统分享面板分享游戏。 */
    private fun shareGame(context: Context, entry: GameRegistry.Entry) {
        val text = context.getString(
            R.string.long_press_share_text_format, entry.name, entry.desc
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, entry.name)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.long_press_share_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.long_press_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 通过 [ShortcutManagerCompat] 添加动态快捷方式到桌面。
     * 需要 Android 8.0+（API 26），低版本回退到 Toast 提示。
     */
    private fun addHomeShortcut(context: Context, entry: GameRegistry.Entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, R.string.long_press_shortcut_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.long_press_shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        // 构造启动 Intent：直接走 GameLauncherHelper 使用的 Activity 入口
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.putExtra(EXTRA_GAME_ID, entry.id)
        launchIntent.putExtra(EXTRA_GAME_NAME, entry.name)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val shortcut = ShortcutInfoCompat.Builder(context, "game_shortcut_${entry.id}")
            .setShortLabel(entry.name)
            .setLongLabel(entry.name)
            .setIcon(IconCompat.createWithResource(context, entry.iconRes))
            .setIntent(launchIntent)
            .build()

        val successCb = PendingIntent.getBroadcast(
            context, /* requestCode = */ entry.id.hashCode(),
            Intent(context, ShortcutResultReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ok = ShortcutManagerCompat.requestPinShortcut(context, shortcut, successCb.intentSender)
        if (ok) {
            Toast.makeText(context,
                context.getString(R.string.long_press_shortcut_added, entry.name),
                Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.long_press_shortcut_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private enum class MenuId { PLAY, FAVORITE, SHARE, SHORTCUT }

    /** 接收桌面快捷方式创建结果广播。 */
    class ShortcutResultReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 创建成功的回调由系统发送，我们仅静默处理，不打扰用户
        }
    }

    /** 主 Activity 用以识别来自快捷方式的启动。 */
    fun extractGameIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(EXTRA_GAME_ID)
    }

    private const val EXTRA_GAME_ID = "extra_long_press_game_id"
    private const val EXTRA_GAME_NAME = "extra_long_press_game_name"
}
