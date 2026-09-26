package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import java.util.List;

/**
 * 斗地主规则引擎。
 *
 * <p>提供斗地主核心规则的静态判断方法，包括出牌合法性校验和桌面清理判定。
 * 所有方法均为无状态的静态方法，不持有任何游戏状态，
 * 可被 AI、本地逻辑等多处安全地并发调用。</p>
 *
 * <p>你可以把这个类想象成"裁判手册"——它只负责回答"这样出牌合不合法？"
 * 等规则问题，但不记录任何游戏进度。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用纯静态工具类设计，避免状态耦合，便于单元测试
 *       （就像查字典，不需要先创建一个"字典对象"才能查）</li>
 *   <li>牌型判定与压牌比较委托给 P1 内核 {@code logic.Combo}（唯一真源）</li>
 *   <li>叫地主评估已升级为 {@code ai.AiBrain#evaluateBidScore}（0-3 分，
 *       手数分解估算 + 大牌计数 + 炸弹），本类不再保留旧阈值布尔版</li>
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
}
