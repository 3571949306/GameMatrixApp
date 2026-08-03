package com.gamecenter.app.games;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.gamecenter.app.database.AppDatabase;
import com.gamecenter.app.database.dao.GameUsageDao;
import com.gamecenter.app.database.entity.GameUsageEntity;

/**
 * 游戏用户评分存储（Batch 11-1 / GAME_RATING_SYSTEM）。
 *
 * <p>存储位置：Room {@code game_usage} 表的 {@code userRating} 列，
 * 取值 1~5（0 表示未评分）。与战绩数据同表，便于统一备份/恢复。</p>
 *
 * <p>2026-07-31 迁移（ROOM_MIGRATION）：持久化层由 SharedPreferences 切换为 Room。
 * 公共方法签名保持不变，所有读写改为通过 {@link GameUsageDao} 的同步方法。</p>
 *
 * <p>线程安全：Room 的非 suspend 方法在调用线程同步执行，与原 SP 行为一致。</p>
 */
public final class GameRatingStore {

    private final GameUsageDao gameUsageDao;

    public GameRatingStore(@NonNull Context context) {
        gameUsageDao = AppDatabase.getDatabase(context.getApplicationContext()).gameUsageDao();
    }

    /** 确保指定 gameId 的行存在，便于 UPDATE 语句生效。 */
    private void ensureRowExists(@NonNull String gameId) {
        if (gameUsageDao.getByIdSync(gameId) == null) {
            gameUsageDao.upsertSync(new GameUsageEntity(
                    gameId, 0, 0, 0, 0L, 0L, 0, false, 0L));
        }
    }

    /**
     * 设置某游戏的用户评分。
     *
     * @param gameId 游戏 ID
     * @param stars  星级 0~5（0 等同于清除评分）
     */
    public void setRating(@NonNull String gameId, @IntRange(from = 0, to = 5) int stars) {
        ensureRowExists(gameId);
        gameUsageDao.updateRatingSync(gameId, stars);
    }

    /** 清除评分。等价于 {@link #setRating(String, int)} 传 0。 */
    public void clearRating(@NonNull String gameId) {
        gameUsageDao.updateRatingSync(gameId, 0);
    }

    /**
     * 获取某游戏的用户评分。
     *
     * @return 0 表示未评分，否则 1~5
     */
    public int getRating(@NonNull String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getUserRating() : 0;
    }

    /** 是否已对该游戏评分。 */
    public boolean hasRating(@NonNull String gameId) {
        return getRating(gameId) > 0;
    }
}
