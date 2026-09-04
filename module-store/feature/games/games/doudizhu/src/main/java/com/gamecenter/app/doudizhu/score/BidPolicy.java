package com.gamecenter.app.doudizhu.score;

/**
 * 叫分档位决策（纯静态，无 Android 依赖，可单测）。
 *
 * <p>对齐改造计划 P3 / 决策点 D1：叫地主从"叫/不叫"升级为 1/2/3 分三档。</p>
 *
 * <p>规则：
 * <ul>
 *   <li>后手叫分必须严格高于当前最高叫分（1→2→3），否则视为不叫（流过）</li>
 *   <li>全员不叫 → 重新发牌（上限 {@link #MAX_REDEAL_TIMES} 次），超限后强制按 1 分开局
 *       （文档 D3 兜底：随机指定替换为保底叫分，保证游戏必然继续）</li>
 *   <li>手牌评分（0-3 档意向）低于或等于当前最高叫分的不再加叫</li>
 * </ul>
 */
public final class BidPolicy {

    /** 最低叫分 */
    public static final int MIN_BID = 1;
    /** 最高叫分 */
    public static final int MAX_BID = 3;
    /** 全员不叫时最多重新发牌次数，超限后强制保底开局 */
    public static final int MAX_REDEAL_TIMES = 3;

    /** 保底叫分（重新发牌超限后强制使用的叫分） */
    public static final int FORCED_BID = 1;

    private BidPolicy() {}

    /**
     * 校验一次叫分是否有效：在 1~3 范围内且严格高于当前最高叫分。
     *
     * @param bid          本次叫分（1/2/3；0 表示不叫）
     * @param highestBidSoFar 当前最高叫分（无人叫时为 0）
     * @return true 表示叫分有效
     */
    public static boolean isValidBid(int bid, int highestBidSoFar) {
        if (bid < MIN_BID || bid > MAX_BID) return false;
        return bid > highestBidSoFar;
    }

    /**
     * AI 叫分决策：给定手牌意向档位与当前最高叫分，返回 AI 的实际叫分。
     *
     * <p>策略：意向档位必须严格高于当前最高叫分才加叫，否则不叫（返回 0）。
     * 简单档再抬一档阈值（保守叫分，对齐 P2 难度差异约定）。</p>
     *
     * @param handIntent     手牌意向档位（0-3，来自 AiBrain.evaluateBidScore）
     * @param highestBidSoFar 当前最高叫分（无人叫时为 0）
     * @param easyDifficulty  是否简单档（简单档叫分更保守）
     * @return 实际叫分（1/2/3），0 表示不叫
     */
    public static int decideAiBid(int handIntent, int highestBidSoFar, boolean easyDifficulty) {
        int intent = Math.max(0, Math.min(MAX_BID, handIntent));
        if (easyDifficulty && intent > 0) {
            intent--;
        }
        if (intent <= highestBidSoFar) return 0;
        return intent;
    }

    /**
     * 是否应重新发牌：一轮（3 人）全部不叫且未超重发上限。
     *
     * @param passCount        本轮连续不叫人数（0-3）
     * @param redealCount      已重新发牌次数
     * @return true 表示重新发牌；false 表示走保底强制开局
     */
    public static boolean shouldRedeal(int passCount, int redealCount) {
        if (passCount < 3) return false;
        return redealCount < MAX_REDEAL_TIMES;
    }
}
