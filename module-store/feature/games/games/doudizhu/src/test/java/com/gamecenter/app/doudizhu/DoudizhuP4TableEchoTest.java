package com.gamecenter.app.doudizhu;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.logic.TestCards;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import org.junit.Test;

/**
 * P4 UI 升级回归测试（纯 JVM，锁定桌面回显语义）。
 *
 * <p>P4 把"玩家不出回显 / 阶段与行动座位推送 / 玩家出牌清不出回显"接入了
 * 状态机数据流，本测试在不实例化 Android View 的前提下验证控制器与
 * 状态机侧的语义不被回归：</p>
 * <ul>
 *   <li>玩家跟牌后出牌回显数据（getPlayerPlayedCards）与"不出"标记互斥；</li>
 *   <li>玩家"不出"且未清桌时，passed[0] 为真、桌面保留上家牌（供中央"不出"回显）；</li>
 *   <li>任何人出牌后全部"不出"标记重置（回显位让位给新的一手牌）。</li>
 * </ul>
 */
public class DoudizhuP4TableEchoTest {

    /**
     * 构造"左家 AI 为地主、已进入出牌阶段"的局面；
     * 玩家手牌固化为两张散牌（4/6），出 4 后仍有余牌，避免出完即终局。
     */
    private static DoudizhuGameController newControllerWithHumanCard() {
        DoudizhuGameController controller = DoudizhuGameControllerTest.newControllerInBidding();
        controller.state().setLandlord(Seats.SEAT_LEFT_AI);
        controller.state().getPlayerHandCards().clear();
        controller.state().getPlayerHandCards().add(TestCards.card(4, 0));
        controller.state().getPlayerHandCards().add(TestCards.card(6, 0));
        controller.state().startPlayingPhase();
        return controller;
    }

    /** 玩家跟牌：出牌回显指向玩家刚出的那一手，"不出"标记全清（回显互斥）。 */
    @Test
    public void humanPlayEchoesCenterAndClearsPassedFlags() {
        DoudizhuGameController controller = newControllerWithHumanCard();

        // 左家首出单 3，右家不出 → 轮到玩家跟牌
        controller.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 1));
        controller.onAIPass(Seats.SEAT_RIGHT_AI);
        assertEquals(Seats.SEAT_PLAYER, controller.state().getCurrentTurn());

        // 玩家跟 4：回显必须指向玩家，且所有"不出"标记重置
        controller.onHumanPlay(of(4, 1));

        assertEquals(CardType.SINGLE, GameRuleUtil.getCardType(
                controller.state().getPlayerPlayedCards()));
        assertEquals(1, controller.state().getPlayerPlayedCards().size());
        assertEquals(4, controller.state().getPlayerPlayedCards().get(0).getWeight());
        boolean[] passed = controller.state().getPlayerPassed();
        assertFalse("出牌后玩家'不出'标记必须重置（回显让位）", passed[Seats.SEAT_PLAYER]);
        assertFalse(passed[Seats.SEAT_LEFT_AI]);
        assertFalse(passed[Seats.SEAT_RIGHT_AI]);
        // AI 侧回显被清空（牌面只显示当前这一手）
        assertTrue(controller.state().getSeat1PlayedCards().isEmpty());
        assertTrue(controller.state().getSeat2PlayedCards().isEmpty());
    }

    /** 玩家"不出"未清桌：passed[0] 为真、上家牌保留，供桌面"不出"回显与跟牌参照。 */
    @Test
    public void humanPassKeepsLastPlayedForEcho() {
        DoudizhuGameController controller = newControllerWithHumanCard();

        // 左家出 3，右家跟 5（右家手牌两张，出 5 后仍有余牌不终局），玩家选择不出
        controller.state().getSeat2Cards().clear();
        controller.state().getSeat2Cards().add(TestCards.card(5, 0));
        controller.state().getSeat2Cards().add(TestCards.card(5, 1));
        controller.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 1));
        assertEquals(Seats.SEAT_RIGHT_AI, controller.state().getCurrentTurn());
        controller.onAIPlay(Seats.SEAT_RIGHT_AI, of(5, 1));
        assertEquals(Seats.SEAT_PLAYER, controller.state().getCurrentTurn());

        controller.onHumanPass();

        boolean[] passed = controller.state().getPlayerPassed();
        assertTrue("玩家'不出'必须入档（中央回显依据）", passed[Seats.SEAT_PLAYER]);
        // 未满足清桌（仅 1 人不出），桌面保留右家的 5 供参照
        assertEquals(1, controller.state().getLastPlayedCards().size());
        assertEquals(5, controller.state().getLastPlayedCards().get(0).getWeight());
        assertEquals("桌面牌仍归属右家（出牌位不回显玩家牌）",
                Seats.SEAT_RIGHT_AI, controller.state().getLastPlayerWhoPlayed());
        assertTrue("玩家未出牌，中央出牌回显应为空",
                controller.state().getPlayerPlayedCards().isEmpty());
    }

    /** 玩家"不出"后 AI 再出牌：全部"不出"标记重置，玩家回显位清空。 */
    @Test
    public void nextAiPlayResetsHumanPassEcho() {
        DoudizhuGameController controller = newControllerWithHumanCard();

        controller.state().getSeat2Cards().clear();
        controller.state().getSeat2Cards().add(TestCards.card(5, 0));
        controller.state().getSeat2Cards().add(TestCards.card(5, 1));
        controller.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 1));
        controller.onAIPlay(Seats.SEAT_RIGHT_AI, of(5, 1));
        controller.onHumanPass();
        assertTrue(controller.state().getPlayerPassed()[Seats.SEAT_PLAYER]);

        // 玩家不出后轮到左家（3→5→pass），左家再出 6
        controller.state().getSeat1Cards().clear();
        controller.state().getSeat1Cards().add(TestCards.card(6, 0));
        controller.onAIPlay(Seats.SEAT_LEFT_AI, of(6, 1));

        boolean[] passed = controller.state().getPlayerPassed();
        assertFalse("新的一手牌必须重置全部'不出'标记", passed[Seats.SEAT_PLAYER]);
        assertFalse(passed[Seats.SEAT_LEFT_AI]);
        assertFalse(passed[Seats.SEAT_RIGHT_AI]);
    }
}
