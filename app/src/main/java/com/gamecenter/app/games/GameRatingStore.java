package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 * 游戏用户评分存储（Batch 11-1 / GAME_RATING_SYSTEM）。
 *
 * <p>存储位置：与 {@link GameUsageStore} 同一个 {@code game_usage} SharedPreferences 文件，
 * key 前缀 {@link #KEY_RATING_PREFIX} + gameId，取值 1~5（0 表示未评分）。
 * 这样设计的好处是评分数据与收藏、战绩数据在同一个文件中，便于统一备份/恢复。</p>
 *
 * <p>线程安全：{@link SharedPreferences} 自身线程安全，本类仅做薄封装。</p>
 */
public final class GameRatingStore {

    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_RATING_PREFIX = "user_rating_";

    private final SharedPreferences prefs;

    public GameRatingStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 设置某游戏的用户评分。
     *
     * @param gameId 游戏 ID
     * @param stars  星级 0~5（0 等同于清除评分）
     */
    public void setRating(@NonNull String gameId, @IntRange(from = 0, to = 5) int stars) {
        prefs.edit().putInt(KEY_RATING_PREFIX + gameId, stars).apply();
    }

    /** 清除评分。等价于 {@link #setRating(String, int)} 传 0。 */
    public void clearRating(@NonNull String gameId) {
        prefs.edit().remove(KEY_RATING_PREFIX + gameId).apply();
    }

    /**
     * 获取某游戏的用户评分。
     *
     * @return 0 表示未评分，否则 1~5
     */
    public int getRating(@NonNull String gameId) {
        return prefs.getInt(KEY_RATING_PREFIX + gameId, 0);
    }

    /** 是否已对该游戏评分。 */
    public boolean hasRating(@NonNull String gameId) {
        return getRating(gameId) > 0;
    }
}
