package com.gamecenter.app.doudizhu;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.logic.Combo;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import org.junit.Test;

import java.util.List;

/**
 * P2 复查回归测试：控制器 AI 契约兜底。
 *
 * <p>修复前：自由出牌回合（last==null）AI 着法非法时直接 return 不推进回合，
 * 理论可卡死对局。修复后强制兜底为手牌最小合法单牌（与 AiBrain 出口自校验
 * 语义一致）；跟牌回合非法着法维持"按不出处理"的原语义。</p>
 */
public class DoudizhuGameControllerTest {

    /** 自由出牌回合注入非法着法：必须计入违约、强制出最小单张、回合正常推进。 */
    @Test
    public void illegalAiMoveOnFreeTurnFallsBackToSmallestSingle() {
        DoudizhuGameController controller = newControllerWithLeftAiLandlord();

        // 前置：左家（地主）自由出牌，桌面无牌
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING,
                controller.state().getGameState());
        assertEquals(Seats.SEAT_LEFT_AI, controller.state().getCurrentTurn());
        assertNull(controller.state().getLastPlayedCards());

        // 注入非法着法：3+9 两张散牌不构成任何合法牌型
        List<Card> illegal = of(3, 1, 9, 1);
        assertEquals("前置条件：注入着法应为非法牌型",
                CardType.ERROR, GameRuleUtil.getCardType(illegal));

        controller.onAIPlay(Seats.SEAT_LEFT_AI, illegal);

        assertEquals("非法着法必须计入 AI 契约违约", 1,
                controller.getAiContractViolations());

        // 兜底生效：强制出手牌最小单张 3，桌面有牌、回合推进到右家
        List<Card> played = controller.state().getSeat1PlayedCards();
        assertNotNull("自由出牌回合必须有出牌", played);
        assertEquals(1, played.size());
        assertEquals(3, played.get(0).getWeight());
        assertEquals(CardType.SINGLE, Combo.of(played).getType());

        List<Card> hand = controller.state().getSeat1Cards();
        assertEquals("手牌应剩 2 张", 2, hand.size());
        assertEquals(Seats.SEAT_RIGHT_AI, controller.state().getCurrentTurn());
        assertEquals(Seats.SEAT_LEFT_AI, controller.state().getLastPlayerWhoPlayed());
        assertEquals("兜底出牌应计入已出牌历史", 1,
                controller.getPlayedHistory().size());
    }

    /** 跟牌回合注入非法着法：维持"按不出处理"语义，回合照样推进（不回归）。 */
    @Test
    public void illegalAiMoveOnFollowTurnStillPasses() {
        DoudizhuGameController controller = newControllerWithLeftAiLandlord();

        // 左家合法首出单张 3 → 轮到右家跟牌
        controller.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 1));
        assertEquals(Seats.SEAT_RIGHT_AI, controller.state().getCurrentTurn());
        assertEquals(1, controller.state().getLastPlayedCards().size());
        int violationsAfterLead = controller.getAiContractViolations();

        // 右家注入非法着法：5+9 两张散牌
        List<Card> illegal = of(5, 1, 9, 1);
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(illegal));

        controller.onAIPlay(Seats.SEAT_RIGHT_AI, illegal);

        assertEquals("非法着法必须计入 AI 契约违约",
                violationsAfterLead + 1, controller.getAiContractViolations());
        // 按不出处理：桌面仍是左家的单 3，手牌未动，回合推进到玩家
        assertEquals(1, controller.state().getLastPlayedCards().size());
        assertEquals(Seats.SEAT_PLAYER, controller.state().getCurrentTurn());
        assertTrue(controller.state().getPlayerPassed()[Seats.SEAT_RIGHT_AI]);
    }

    /**
     * 构造"左家 AI 为地主、已进入出牌阶段（自由出牌轮到左家）"的确定局面，
     * 并把左家手牌固化为 3/5/9 三张散牌。
     */
    private static DoudizhuGameController newControllerWithLeftAiLandlord() {
        DoudizhuGameController controller = newControllerInBidding();
        controller.state().setLandlord(Seats.SEAT_LEFT_AI);
        List<Card> hand = controller.state().getSeat1Cards();
        hand.clear();
        hand.addAll(of(3, 1, 5, 1, 9, 1));
        controller.state().startPlayingPhase();
        return controller;
    }

    /** 构造处于叫分阶段（尚未有人叫分）的控制器（不绑定 UI，AI 回调直接驱动）。 */
    static DoudizhuGameController newControllerInBidding() {
        DoudizhuGameController controller = new DoudizhuGameController();
        controller.state().resetGameState();
        controller.state().startGame();
        return controller;
    }
}
