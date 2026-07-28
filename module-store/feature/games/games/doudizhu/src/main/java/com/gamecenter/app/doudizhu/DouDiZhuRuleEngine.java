package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import java.util.List;

/**
 * 斗地主规则引擎。
 *
 * <p>提供斗地主核心规则的静态判断方法，包括出牌合法性校验、叫地主决策评估、
 * 桌面清理判定和手牌评分。所有方法均为无状态的静态方法，不持有任何游戏状态，
 * 可被 AI、网络校验、本地逻辑等多处安全地并发调用。</p>
 *
 * <p>你可以把这个类想象成"裁判手册"——它只负责回答"这样出牌合不合法？"
 * "这手牌值不值得叫地主？"等规则问题，但不记录任何游戏进度。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用纯静态工具类设计，避免状态耦合，便于单元测试
 *       （就像查字典，不需要先创建一个"字典对象"才能查）</li>
 *   <li>叫地主评分阈值设为 7，经过经验调优平衡了激进与保守策略</li>
 *   <li>炸弹检测基于 rankCounts 数组统计，权重偏移 -3 将 3 映射到索引 0</li>
 * </ul>
 */
public final class DouDiZhuRuleEngine {

    /** 私有构造函数，禁止实例化 */
    private DouDiZhuRuleEngine() {}

    /**
     * 校验出牌是否合法。
     *
     * <p>判断逻辑：先检查选中的牌是否构成合法牌型（非 ERROR），
     * 再检查是否能压过上家出的牌。如果上家没有出牌（previousCards 为 null 或空），
     * 则只需牌型合法即可。</p>
     *
     * @param cards 当前玩家选中的牌，不能为 null 或空
     * @param previousCards 上家出的牌，null 或空表示当前玩家先出（自由出牌）
     * @return true 表示出牌合法，false 表示不合法
     */
    public static boolean validatePlay(List<Card> cards, List<Card> previousCards) {
        if (cards == null || cards.isEmpty()) return false;
        CardType type = GameRuleUtil.getCardType(cards);
        if (type == CardType.ERROR) return false;
        if (previousCards == null || previousCards.isEmpty()) return true;
        return GameRuleUtil.canPlayPass(cards, previousCards);
    }

    /**
     * 评估手牌强度，决定是否应该叫地主。
     *
     * <p>评分规则：
     * <ul>
     *   <li>大王 +8 分</li>
     *   <li>小王 +8 分</li>
     *   <li>每个 2 +2 分</li>
     *   <li>每个 A +1 分</li>
     *   <li>每个炸弹（四张同点数）+6 分</li>
     * </ul>
     * 总分 ≥ 7 时建议叫地主。</p>
     *
     * @param hand 手牌列表
     * @return true 表示建议叫地主，false 表示不建议
     */
    public static boolean shouldCallLandlord(List<Card> hand) {
        if (hand == null || hand.isEmpty()) return false;
        int score = 0;
        for (Card card : hand) {
            if (card.getRank() == Rank.BIG_JOKER) score += 8;
            else if (card.getRank() == Rank.SMALL_JOKER) score += 8;
            else if (card.getRank() == Rank.TWO) score += 2;
            else if (card.getRank() == Rank.ACE) score += 1;
        }
        // 检查炸弹（四张同点数），rankCounts 数组索引通过权重偏移计算
        int[] rankCounts = new int[15];
        for (Card card : hand) {
            // 权重 -3 的偏移：3 的权重为 3，映射到索引 0；A 的权重为 14，映射到索引 11
            int idx = card.getRank().getWeight() - 3;
            if (idx >= 0 && idx < 15) rankCounts[idx]++;
        }
        for (int count : rankCounts) {
            if (count == 4) score += 6;
        }
        // 阈值 7：经验值，平衡了激进与保守策略
        return score >= 7;
    }

    /**
     * 判断桌面是否应该清理（其他所有玩家都已不出）。
     *
     * <p>当除最后出牌者外的所有玩家都选择了"不出"时，桌面清空，
     * 最后出牌者获得自由出牌权。</p>
     *
     * @param playerPassed 各座位是否"不出"的布尔数组
     * @param lastPlayerWhoPlayed 最后出牌者的座位索引
     * @param totalSeats 总座位数（通常为 3）
     * @return true 表示应该清理桌面，false 表示不应该
     */
    public static boolean shouldClearTable(boolean[] playerPassed, int lastPlayerWhoPlayed, int totalSeats) {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= totalSeats) return false;
        if (playerPassed == null || playerPassed.length < totalSeats) return false;
        int passCount = 0;
        // 统计除最后出牌者外，其他玩家中已"不出"的人数
        for (int i = 0; i < totalSeats; i++) {
            if (i != lastPlayerWhoPlayed && playerPassed[i]) passCount++;
        }
        // 当其他所有玩家都"不出"时，清理桌面
        return passCount >= totalSeats - 1;
    }

    /**
     * 计算手牌评分（用于 AI 评估）。
     *
     * <p>评分规则与 {@link #shouldCallLandlord} 相同，但返回原始分数而非布尔值，
     * 便于 AI 进行更细粒度的决策。</p>
     *
     * @param hand 手牌列表
     * @return 手牌评分，0 表示空手牌或无效输入
     */
    public static int evaluateHandScore(List<Card> hand) {
        if (hand == null || hand.isEmpty()) return 0;
        int score = 0;
        for (Card card : hand) {
            if (card.getRank() == Rank.BIG_JOKER) score += 8;
            else if (card.getRank() == Rank.SMALL_JOKER) score += 8;
            else if (card.getRank() == Rank.TWO) score += 2;
            else if (card.getRank() == Rank.ACE) score += 1;
        }
        int[] rankCounts = new int[15];
        for (Card card : hand) {
            int idx = card.getRank().getWeight() - 3;
            if (idx >= 0 && idx < 15) rankCounts[idx]++;
        }
        for (int count : rankCounts) {
            if (count == 4) score += 6;
        }
        return score;
    }
}
