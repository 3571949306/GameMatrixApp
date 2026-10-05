package com.gamecenter.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.gamecenter.app.MainActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.achievement.DailyChallengeManager;
import com.gamecenter.app.games.coin.CoinWallet;
import com.gamecenter.app.ui.GameLongPressMenu;

/**
 * 桌面小组件（2x2）"GameMatrix"：金币余额 / 今日挑战进度 / 今日游玩时长。
 *
 * <p>刷新策略：appwidget-provider 的 updatePeriodMillis=0（省电），
 * 依赖 app 打开时的 {@link #pushUpdate(Context)}（MainActivity.onResume 调用）
 * 与系统的增删/首次放置回调（APPWIDGET_UPDATE）。</p>
 *
 * <p>点击路由复用 MainActivity 既有约定：标题区与整体默认 →
 * {@code MainActivity.EXTRA_NAV_TAB="games_hall"}（底部导航稳定贡献 ID）；
 * 挑战行单独 → 桌面快捷方式同款直达游戏（GameLongPressMenu.EXTRA_GAME_ID，
 * 已完成时同样直达）。</p>
 */
public class GameMatrixWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "GameMatrixWidget";
    /** 底部导航稳定贡献 ID：游戏大厅（与 MainActivity.handleNavTabIntent 的兜底一致）。 */
    private static final String TAB_GAMES_HALL = "games_hall";
    /** 同一目标 Activity 的两个 PendingIntent 必须用不同 requestCode 区分。 */
    private static final int REQUEST_CODE_HALL = 0;
    private static final int REQUEST_CODE_CHALLENGE = 1;
    private static final int PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildRemoteViews(context));
    }

    /**
     * 主动刷新本 provider 的全部实例（MainActivity.onResume 调用）。
     * 未添加任何小组件时直接返回，零开销。
     */
    public static void pushUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, GameMatrixWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        manager.updateAppWidget(ids, buildRemoteViews(context));
    }

    /**
     * 数据拼装门面（供单测）：委托纯 Java 的 GameMatrixWidgetLines，
     * scripts/verify_widget.py 直接编译测试该纯逻辑类。
     */
    static String[] buildLines(long balance, String gameName, int progress, int target,
            boolean completed, long todayPlayMs) {
        return GameMatrixWidgetLines.buildLines(balance, gameName, progress, target,
                completed, todayPlayMs);
    }

    private static RemoteViews buildRemoteViews(Context context) {
        long balance = 0L;
        DailyChallengeManager.Challenge challenge = null;
        long todayPlayMs = 0L;
        try {
            balance = new CoinWallet(context).balance();
            challenge = DailyChallengeManager.getInstance(context).getTodayChallenge();
            todayPlayMs = new GameUsageStore(context).getTodayPlayTimeMs();
        } catch (Exception e) {
            // 数据层异常不允许炸掉 onUpdate（launcher 侧会显示"无法加载小组件"）：留日志 + 占位零值
            Log.w(TAG, "read widget data failed, fallback to zeros", e);
        }
        String gameId = challenge != null ? challenge.gameId : null;
        String gameName = challenge != null ? challenge.gameName : "";
        int progress = challenge != null ? challenge.progress : 0;
        int target = challenge != null ? challenge.target : 0;
        boolean completed = challenge != null && challenge.completed;
        String[] lines = buildLines(balance, gameName, progress, target, completed, todayPlayMs);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_game_matrix);
        views.setTextViewText(R.id.widget_coin_line, lines[0]);
        views.setTextViewText(R.id.widget_challenge_line, lines[1]);
        views.setTextViewText(R.id.widget_playtime_line, lines[2]);

        // 标题区 + 整体默认点击 → 游戏大厅（onCreate/onNewIntent 的 handleNavTabIntent 消费）
        PendingIntent hallPi = PendingIntent.getActivity(context, REQUEST_CODE_HALL,
                new Intent(context, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_NAV_TAB, TAB_GAMES_HALL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PENDING_FLAGS);
        views.setOnClickPendingIntent(R.id.widget_root, hallPi);
        views.setOnClickPendingIntent(R.id.widget_title, hallPi);

        // 挑战行单独点击 → 直达挑战游戏（与桌面快捷方式同一路由；已完成时也用直达游戏）
        Intent challengeIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (gameId != null) {
            challengeIntent.putExtra(GameLongPressMenu.EXTRA_GAME_ID, gameId);
            challengeIntent.putExtra(GameLongPressMenu.EXTRA_GAME_NAME, gameName);
        }
        views.setOnClickPendingIntent(R.id.widget_challenge_line,
                PendingIntent.getActivity(context, REQUEST_CODE_CHALLENGE, challengeIntent,
                        PENDING_FLAGS));
        return views;
    }
}
