package com.gamecenter.app.games.adaptive;

/**
 * AI 难度自适应推荐（纯 Java，无 Android 依赖，便于 javac 直跑回归测试）。
 *
 * <p>按玩家近期胜率与样本量给出难度档位推荐。仅供 UI 层渲染"荐"徽标提示，
 * 不自动切换难度——产品规范要求不得跳过用户可见的难度选择。</p>
 */
public final class AdaptiveAdvisor {

    /** 建议方向。 */
    public enum Advice { KEEP, UP, DOWN }

    /** 最小样本量：总对局不足该值时不给建议（样本不足，宁缺勿错）。 */
    static final int MIN_SAMPLE_SIZE = 5;

    private AdaptiveAdvisor() {
    }

    /**
     * 按胜率与样本量给出推荐档位。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>总对局 &lt; {@value #MIN_SAMPLE_SIZE} → {@link Advice#KEEP}
     *       （样本不足，不给建议；该判断先行，天然覆盖 win+loss==0 的除零场景）；</li>
     *   <li>胜率 = win/(win+loss)；</li>
     *   <li>胜率 &ge; 0.6 且 currentTier &lt; maxTier → {@link Advice#UP}；</li>
     *   <li>胜率 &le; 0.4 且 currentTier &gt; 1 → {@link Advice#DOWN}；</li>
     *   <li>其余（含已在顶格/下限无法升降）→ {@link Advice#KEEP}。</li>
     * </ul>
     *
     * @param winCount    近期胜场数
     * @param lossCount   近期负场数
     * @param currentTier 当前选择档位 1..maxTier（越界钳到边界内）
     * @param maxTier     档位上限（如中国象棋为 4；&lt;1 时按单档处理）
     */
    public static Advice advise(int winCount, int lossCount, int currentTier, int maxTier) {
        int total = winCount + lossCount;
        if (total < MIN_SAMPLE_SIZE) {
            return Advice.KEEP;
        }
        int upper = Math.max(1, maxTier);
        int tier = Math.max(1, Math.min(currentTier, upper));
        double winRate = winCount / (double) total;
        if (winRate >= 0.6 && tier < upper) {
            return Advice.UP;
        }
        if (winRate <= 0.4 && tier > 1) {
            return Advice.DOWN;
        }
        return Advice.KEEP;
    }

    /**
     * 推荐档位（{@link #advise} 的便捷形式）。
     *
     * <p>KEEP=currentTier、UP=currentTier+1、DOWN=currentTier-1，再钳 1..maxTier。
     * 调用方只可据此渲染"荐"徽标，不得据此自动切换用户的难度选择。</p>
     *
     * @param winCount    近期胜场数
     * @param lossCount   近期负场数
     * @param currentTier 当前选择档位 1..maxTier（越界钳到边界内）
     * @param maxTier     档位上限（&lt;1 时按单档处理）
     * @return 推荐档位，恒在 1..max(1, maxTier) 内
     */
    public static int recommendedTier(int winCount, int lossCount, int currentTier, int maxTier) {
        int upper = Math.max(1, maxTier);
        int tier = Math.max(1, Math.min(currentTier, upper));
        switch (advise(winCount, lossCount, tier, upper)) {
            case UP:
                return Math.min(tier + 1, upper);
            case DOWN:
                return Math.max(tier - 1, 1);
            case KEEP:
            default:
                return tier;
        }
    }
}
