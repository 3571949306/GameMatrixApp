package com.gamecenter.app.games;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P3-10 (DAILY_RECOMMENDER): 基于偏好的每日推荐算法。
 *
 * <p>推荐打分规则（综合分越高越优先）：</p>
 * <ol>
 *   <li>收藏加成：已收藏的游戏 +30 分</li>
 *   <li>频次加成：累计游玩次数 × 5 分（上限 50 分）</li>
 *   <li>胜率加成：胜率 50% 以上的游戏按胜率 × 20 分（鼓励继续挑战）</li>
 *   <li>新鲜度：从未玩过的游戏 +15 分（避免总推同一款）</li>
 *   <li>分类多样性：与昨日推荐不同分类 +10 分</li>
 *   <li>日期扰动：以日期为 seed 的伪随机分（±20），保证同日稳定但跨日有变化</li>
 * </ol>
 *
 * <p>调用 {@link #recommend(Context, String, int)} 获取 Top N 推荐。</p>
 */
public final class GameRecommender {

    /** 收藏加成 */
    private static final int FAVORITE_BONUS = 30;
    /** 频次加成系数（每次 +5，上限 50） */
    private static final int PLAY_COUNT_BONUS_PER = 5;
    private static final int PLAY_COUNT_BONUS_MAX = 50;
    /** 新鲜度加成 */
    private static final int NEVER_PLAYED_BONUS = 15;
    /** 分类多样性加成 */
    private static final int DIFFERENT_CATEGORY_BONUS = 10;
    /** 日期扰动范围 */
    private static final int DAILY_JITTER_RANGE = 20;
    /** 高胜率加成系数 */
    private static final int WIN_RATE_BONUS_MULTIPLIER = 20;
    private static final float HIGH_WIN_RATE_THRESHOLD = 0.5f;

    private GameRecommender() {}

    /**
     * 推荐游戏列表。
     * @param context 上下文
     * @param yesterdayCategory 昨日推荐的游戏分类（可为 null）
     * @param topN 返回前 N 个（>=1）
     * @return 按综合分降序的推荐列表；输入为空时返回空列表
     */
    @NonNull
    public static List<ScoredGame> recommend(@NonNull Context context,
                                             @Nullable String yesterdayCategory,
                                             int topN) {
        List<GameRegistry.Entry> allEntries = collectAllEntries(context);
        if (allEntries.isEmpty() || topN <= 0) return new ArrayList<>();

        GameUsageStore store = new GameUsageStore(context);
        Set<String> favorites = store.getFavoriteIds();
        Calendar cal = Calendar.getInstance();
        long dayKey = cal.get(Calendar.YEAR) * 10000L
                + (cal.get(Calendar.MONTH) + 1) * 100L
                + cal.get(Calendar.DAY_OF_MONTH);
        Random rng = new Random(dayKey);

        List<ScoredGame> scored = new ArrayList<>(allEntries.size());
        for (GameRegistry.Entry entry : allEntries) {
            int score = 0;
            String id = entry.id != null ? entry.id : "";

            // 1. 收藏加成
            if (favorites.contains(id)) score += FAVORITE_BONUS;

            // 2. 频次加成
            int playCount = store.getPlayCount(id);
            score += Math.min(playCount * PLAY_COUNT_BONUS_PER, PLAY_COUNT_BONUS_MAX);

            // 3. 胜率加成
            int win = store.getWinCount(id);
            int loss = store.getLossCount(id);
            int total = win + loss;
            if (total > 0) {
                float winRate = (float) win / total;
                if (winRate >= HIGH_WIN_RATE_THRESHOLD) {
                    score += (int) (winRate * WIN_RATE_BONUS_MULTIPLIER);
                }
            }

            // 4. 新鲜度加成
            if (playCount == 0) score += NEVER_PLAYED_BONUS;

            // 5. 分类多样性
            if (yesterdayCategory != null && !yesterdayCategory.equals(entry.category)) {
                score += DIFFERENT_CATEGORY_BONUS;
            }

            // 6. 日期扰动（±DAILY_JITTER_RANGE）
            score += rng.nextInt(DAILY_JITTER_RANGE * 2 + 1) - DAILY_JITTER_RANGE;

            scored.add(new ScoredGame(entry, score));
        }

        Collections.sort(scored, new Comparator<ScoredGame>() {
            @Override
            public int compare(ScoredGame a, ScoredGame b) {
                return Integer.compare(b.score, a.score);
            }
        });

        int n = Math.min(topN, scored.size());
        return new ArrayList<>(scored.subList(0, n));
    }

    /** 便捷方法：取 Top 1 推荐。 */
    @Nullable
    public static GameRegistry.Entry recommendOne(@NonNull Context context,
                                                  @Nullable String yesterdayCategory) {
        List<ScoredGame> list = recommend(context, yesterdayCategory, 1);
        return list.isEmpty() ? null : list.get(0).entry;
    }

    @NonNull
    private static List<GameRegistry.Entry> collectAllEntries(@NonNull Context context) {
        List<GameRegistry.Entry> result = new ArrayList<>();
        try {
            List<GameRegistry.Category> categories = GameRegistry.getCategories(context);
            Set<String> seen = new LinkedHashSet<>();
            for (GameRegistry.Category cat : categories) {
                for (GameRegistry.Entry entry : cat.games) {
                    if (entry.id == null || seen.contains(entry.id)) continue;
                    seen.add(entry.id);
                    result.add(entry);
                }
            }
        } catch (Exception ignored) {
            // 返回空列表
        }
        return result;
    }

    /** 带分数的推荐结果。 */
    public static class ScoredGame {
        public final GameRegistry.Entry entry;
        public final int score;

        ScoredGame(GameRegistry.Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
