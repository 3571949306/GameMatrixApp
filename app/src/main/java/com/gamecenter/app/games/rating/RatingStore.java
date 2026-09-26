package com.gamecenter.app.games.rating;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 玩家棋力等级分存储（Android 门面层）。
 *
 * <p>SharedPreferences("player_rating")，key = 游戏 id（如 "chinesechess"）→ int rating。
 * 每局对战 AI 终局时经 {@link #recordResult} 更新：读旧 rating →
 * {@link EloCalculator#newRating} 计算 → 写回 SP → 返回新值。
 * 全部计分逻辑在纯 Java 的 {@link EloCalculator}（可 javac 直跑回归测试），
 * 本类只做 Context/SP 适配，与 CoinWallet 的分层惯例一致。</p>
 */
public final class RatingStore {

    private static final String PREFS_NAME = "player_rating";

    private RatingStore() {
    }

    /**
     * 读取指定游戏的玩家棋力分。
     *
     * @param context 任意 Context，内部取 applicationContext，无持有泄漏
     * @param gameId  游戏 id（如 "chinesechess"）
     * @return 当前 rating；未记录过时返回 {@link EloCalculator#DEFAULT_RATING}
     */
    public static int getRating(Context context, String gameId) {
        return prefs(context).getInt(gameId, EloCalculator.DEFAULT_RATING);
    }

    /**
     * 记录一局对战 AI 的结果并更新棋力分。
     *
     * @param context        任意 Context
     * @param gameId          游戏 id（如 "chinesechess"）
     * @param opponentRating 对手（AI）rating，AI 档位可经 {@link #aiRatingForTier(int)} 换算
     * @param score          本局得分：胜=1.0，和=0.5，负=0.0
     * @return 更新后的 rating（已写回 SP）
     */
    public static int recordResult(Context context, String gameId, int opponentRating, double score) {
        SharedPreferences sp = prefs(context);
        int oldRating = sp.getInt(gameId, EloCalculator.DEFAULT_RATING);
        int newRating = EloCalculator.newRating(oldRating, opponentRating, score);
        sp.edit().putInt(gameId, newRating).apply();
        return newRating;
    }

    /**
     * AI 难度档位 → 代表棋力分（委托 {@link EloCalculator#aiRatingForTier(int)} 纯逻辑实现）。
     *
     * <p>1→800、2→1200、3→1600、4→2000（设计值：按各档搜索深度的相对强度设定，
     * 仅用于 Elo 计分的一致性基准）；tier 越界钳到 1..4。</p>
     *
     * @param tier AI 难度档位（1..4，越界取边界档）
     * @return 该档 AI 的代表 rating
     */
    public static int aiRatingForTier(int tier) {
        return EloCalculator.aiRatingForTier(tier);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
