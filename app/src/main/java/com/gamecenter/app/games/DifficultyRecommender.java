package com.gamecenter.app.games;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.List;

/**
 * 难度推荐器（P1-4）。
 * <p>
 * 基于用户在该游戏上的历史胜负记录推荐难度等级：
 * <ul>
 *   <li>对局数 &lt; {@link #MIN_GAMES_FOR_RECOMMENDATION}：不推荐，使用默认</li>
 *   <li>胜率 &lt; {@link #LOW_WIN_RATE_THRESHOLD}：推荐降一级（更简单）</li>
 *   <li>胜率 &gt; {@link #HIGH_WIN_RATE_THRESHOLD}：推荐升一级（更难）</li>
 *   <li>其他：保持默认（{@link DifficultyLevel#recommended} 标记的等级）</li>
 * </ul>
 * </p>
 */
public final class DifficultyRecommender {

    /** 启用推荐所需的最少对局数 */
    public static final int MIN_GAMES_FOR_RECOMMENDATION = 3;

    /** 低胜率阈值（&lt; 此值推荐降级） */
    public static final float LOW_WIN_RATE_THRESHOLD = 0.30f;

    /** 高胜率阈值（&gt; 此值推荐升级） */
    public static final float HIGH_WIN_RATE_THRESHOLD = 0.70f;

    private DifficultyRecommender() {}

    /**
     * 计算推荐难度索引。
     *
     * @param store  游戏数据存储
     * @param gameId 游戏 ID
     * @param levels 难度列表（按从易到难排序）
     * @return 推荐难度索引；若无足够数据返回默认推荐索引
     */
    public static int recommendDifficultyIndex(@NonNull GameUsageStore store,
                                               @NonNull String gameId,
                                               @NonNull List<DifficultyLevel> levels) {
        if (levels.isEmpty()) return 0;

        // 默认推荐索引
        int defaultIdx = 0;
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).recommended) {
                defaultIdx = i;
                break;
            }
        }

        int win = store.getWinCount(gameId);
        int loss = store.getLossCount(gameId);
        int total = win + loss;
        if (total < MIN_GAMES_FOR_RECOMMENDATION) {
            return defaultIdx;
        }

        float winRate = (float) win / total;
        if (winRate < LOW_WIN_RATE_THRESHOLD) {
            // 降级：往易的方向移动一级
            return Math.max(0, defaultIdx - 1);
        } else if (winRate > HIGH_WIN_RATE_THRESHOLD) {
            // 升级：往难的方向移动一级
            return Math.min(levels.size() - 1, defaultIdx + 1);
        }
        return defaultIdx;
    }

    /**
     * 启动游戏时根据推荐结果展示提示 Toast（可选）。
     *
     * @param context     上下文
     * @param store       游戏数据存储
     * @param gameId       游戏 ID
     * @param levels      难度列表
     * @param appliedIdx  实际使用的难度索引
     */
    public static void showRecommendToastIfAny(@NonNull Context context,
                                               @NonNull GameUsageStore store,
                                               @NonNull String gameId,
                                               @NonNull List<DifficultyLevel> levels,
                                               int appliedIdx) {
        if (levels.isEmpty() || appliedIdx < 0 || appliedIdx >= levels.size()) return;

        int win = store.getWinCount(gameId);
        int loss = store.getLossCount(gameId);
        int total = win + loss;
        if (total < MIN_GAMES_FOR_RECOMMENDATION) {
            // 数据不足，静默
            return;
        }

        int defaultIdx = 0;
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).recommended) {
                defaultIdx = i;
                break;
            }
        }
        if (appliedIdx == defaultIdx) return;

        float winRate = (float) win / total;
        String diffName = levels.get(appliedIdx).name;
        if (winRate < LOW_WIN_RATE_THRESHOLD && appliedIdx < defaultIdx) {
            Toast.makeText(context,
                    context.getString(R.string.diff_rec_toast_easier, diffName),
                    Toast.LENGTH_LONG).show();
        } else if (winRate > HIGH_WIN_RATE_THRESHOLD && appliedIdx > defaultIdx) {
            Toast.makeText(context,
                    context.getString(R.string.diff_rec_toast_harder, diffName),
                    Toast.LENGTH_LONG).show();
        }
    }
}
