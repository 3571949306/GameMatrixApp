package com.gamecenter.app.games;

import android.content.Context;

import androidx.annotation.NonNull;

import com.gamecenter.app.database.AppDatabase;
import com.gamecenter.app.database.dao.AchievementDao;
import com.gamecenter.app.database.entity.AchievementEntity;
import com.gamecenter.app.games.config.GameConfigLoader;
import com.gamecenter.app.games.model.AchievementDef;
import com.gamecenter.app.games.model.GameConfig;
import com.gamecenter.app.games.model.enums.AchievementLevel;

import java.util.List;

/**
 * 成就点数计算器（P1-5）。
 * <p>
 * 遍历所有游戏配置中的成就定义，按 {@link AchievementLevel#getPoints()} 累加：
 * <ul>
 *   <li>未解锁：不计点数</li>
 *   <li>已解锁：按等级点数累加</li>
 * </ul>
 * </p>
 * <p>数据源：Room achievements 表（与 AchievementManager 写入一致）。</p>
 */
public final class AchievementPointsCalculator {

    private AchievementPointsCalculator() {}

    /** 计算结果。 */
    public static final class Result {
        public int totalPoints;
        public int bronzeCount;
        public int silverCount;
        public int goldCount;
        public int platinumCount;
        public int unlockedCount;
        public int totalCount;
    }

    @NonNull
    public static Result calculate(@NonNull Context context) {
        Result r = new Result();
        try {
            GameConfigLoader loader = new GameConfigLoader(context);
            List<GameConfig> configs = loader.loadAllConfigs();
            AchievementDao dao = AppDatabase.getDatabase(context).achievementDao();

            for (GameConfig config : configs) {
                if (config.achievements == null || config.achievements.isEmpty()) continue;
                for (AchievementDef def : config.achievements) {
                    r.totalCount++;
                    String fullKey = def.getFullId(config.gameId);
                    AchievementEntity entity = dao.getByIdSync(fullKey);
                    boolean unlocked = entity != null && entity.getUnlocked();
                    if (!unlocked) continue;
                    r.unlockedCount++;
                    AchievementLevel level = def.level != null ? def.level : AchievementLevel.BRONZE;
                    switch (level) {
                        case BRONZE:
                            r.bronzeCount++;
                            r.totalPoints += level.getPoints();
                            break;
                        case SILVER:
                            r.silverCount++;
                            r.totalPoints += level.getPoints();
                            break;
                        case GOLD:
                            r.goldCount++;
                            r.totalPoints += level.getPoints();
                            break;
                        case PLATINUM:
                            r.platinumCount++;
                            r.totalPoints += level.getPoints();
                            break;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("AchievementPointsCalculator", "成就点数计算失败", e);
        }
        return r;
    }
}
