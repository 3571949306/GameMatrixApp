package com.gamecenter.app.games.rating;

/**
 * 标准 Elo 棋力等级分计算器（纯 Java，无 Android 依赖，便于 javac 直跑回归测试）。
 *
 * <p>每局对战 AI 后按标准 Elo 公式更新玩家 rating，AI 各难度档代表固定 rating
 * （见 {@link #aiRatingForTier(int)}）。Android 侧的 SP 持久化门面是
 * {@link RatingStore}，全部可测逻辑收敛在本类（与 AdaptiveAdvisor/CoinLedger
 * 的"纯逻辑类 + Android 门面"分层惯例一致）。</p>
 */
public final class EloCalculator {

    /** 初始/默认棋力分。 */
    public static final int DEFAULT_RATING = 1000;

    /** K 因子：每局最大分值波动的一半（同分对局胜/负各变动 K/2=12 分）。 */
    public static final double K_FACTOR = 24.0;

    /** 棋力分下限（更新结果与入参 rating 均钳制在该区间内）。 */
    static final int MIN_RATING = 100;

    /** 棋力分上限。 */
    static final int MAX_RATING = 3000;

    private EloCalculator() {
    }

    /**
     * 期望胜率：E = 1 / (1 + 10^((opponent - player)/400))。
     *
     * <p>同分 E=0.5；玩家比对手低 400 分 E≈0.0909；高 400 分 E≈0.909。
     * 入参 rating 越界时先钳到 [{@value #MIN_RATING}, {@value #MAX_RATING}] 再计算。</p>
     *
     * @param playerRating   玩家当前 rating
     * @param opponentRating 对手 rating（AI 档位取 {@link #aiRatingForTier(int)}）
     * @return 期望得分，恒在 (0, 1) 内
     */
    public static double expectedScore(int playerRating, int opponentRating) {
        double exponent = (clampRating(opponentRating) - clampRating(playerRating)) / 400.0;
        return 1.0 / (1.0 + Math.pow(10.0, exponent));
    }

    /**
     * 一局结束后更新玩家 rating。
     *
     * <p>公式：new = player + K * (score - expected)；结果取整（{@link Math#round}）
     * 后钳制到 [{@value #MIN_RATING}, {@value #MAX_RATING}]。rating 边界入参防御：
     * player/opponent 越界时先钳到该区间再参与计算，不抛异常。</p>
     *
     * @param playerRating   玩家当前 rating
     * @param opponentRating 对手 rating
     * @param score          本局得分：胜=1.0，和=0.5，负=0.0
     * @return 更新后的 rating，恒在 [{@value #MIN_RATING}, {@value #MAX_RATING}] 内
     */
    public static int newRating(int playerRating, int opponentRating, double score) {
        double expected = expectedScore(playerRating, opponentRating);
        double updated = clampRating(playerRating) + K_FACTOR * (score - expected);
        int rounded = (int) Math.round(updated);
        return Math.max(MIN_RATING, Math.min(MAX_RATING, rounded));
    }

    /**
     * AI 难度档位 → 代表棋力分映射（设计值）。
     *
     * <p>1→800、2→1200、3→1600、4→2000。这是按各档搜索深度的相对强度设定的
     * 设计值而非实测棋力，仅用于 Elo 计分的一致性基准；tier 越界钳到 1..4。
     * 纯逻辑放在本类（可被纯 javac 回归测试覆盖），{@link RatingStore#aiRatingForTier}
     * 是它的 Android 门面委托。</p>
     *
     * @param tier AI 难度档位（1..4，越界取边界档）
     * @return 该档 AI 的代表 rating
     */
    public static int aiRatingForTier(int tier) {
        int clamped = Math.max(1, Math.min(tier, 4));
        switch (clamped) {
            case 1:
                return 800;
            case 2:
                return 1200;
            case 3:
                return 1600;
            case 4:
            default:
                return 2000;
        }
    }

    /** rating 入参防御：钳到 [{@value #MIN_RATING}, {@value #MAX_RATING}]。 */
    private static int clampRating(int rating) {
        return Math.max(MIN_RATING, Math.min(MAX_RATING, rating));
    }
}
