package com.gamecenter.app.doudizhu;

import static com.gamecenter.app.doudizhu.logic.TestCards.card;
import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.save.DoudizhuSnapshot;
import com.gamecenter.app.doudizhu.score.ScoreBoard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * P3 复查修复 D 回归测试：恢复于 AI 回合后 AI 调度必须恢复。
 *
 * <p>链路证实：{@code DouDiZhuGameStateManager.restoreFrom} 末尾回调
 * onStateChanged + onTurnChanged，控制器 {@code scheduleAiIfDue} 在 AI 回合
 * 调用 {@code scheduleAITurn/scheduleAIBid}。JVM 单测中 Handler 为桩
 * （postDelayed 不执行），故经 {@code pendingAiTaskForTest} 取到挂起任务后
 * 手动运行，走真实 AiBrain 决策链路，断言"恢复 → 挂起 → 执行 → 推进"，
 * 出牌与叫分两阶段均覆盖。</p>
 */
public class DoudizhuRestoreAiTurnTest {

    /** 录制 UI 回调的测试桩。 */
    private static final class RecordingScreen implements DoudizhuGameController.UiCallback {
        @Override public void onBidControlsChanged(boolean show) {}
        @Override public void onPlayControlsChanged(boolean show, boolean enablePass) {}
        @Override public void onTableSyncRequired() {}
        @Override public void onCardsPlayed(List<Card> cards, CardType type) {}
        @Override public void onInvalidPlay(boolean illegalCombo, boolean cannotBeat) {}
        @Override public void onInvalidBid() {}
        @Override public void onRedeal(int redealCount) {}
        @Override public void onForcedLandlord() {}
        @Override public void onGameFinished(ScoreBoard.Settlement settlement) {}
    }

    /**
     * 构造"出牌阶段、当前回合=右家 AI（地主，自由出牌）"的真实存档串：
     * 玩家 17 张 / 左 17 张、右家（地主）单张 3 与 4，桌面空。
     */
    private static String playingSaveWithRightAiTurn() {
        DoudizhuSnapshot s = new DoudizhuSnapshot();
        s.phase = DouDiZhuGameStateManager.STATE_PLAYING;
        s.currentTurn = Seats.SEAT_RIGHT_AI;
        s.landlordSeat = Seats.SEAT_RIGHT_AI;
        s.highestBid = 3;
        s.bidPlacedCount = 1;
        s.redealCount = 0;
        s.bidScore = 3;
        s.bombCount = 0;
        s.difficulty = DoudizhuGameController.DIFFICULTY_NORMAL;
        s.startedAtMs = System.currentTimeMillis();
        s.playCounts = new int[]{0, 0, 1};
        s.passed = new boolean[]{false, false, false};

        List<Card> playerHand = new ArrayList<>();
        int[] playerWeights = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 16, 17, 14};
        int copy = 0;
        for (int w : playerWeights) {
            playerHand.add(card(w, copy++ % 4));
        }
        List<Card> leftHand = new ArrayList<>();
        int[] leftWeights = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 16, 17, 14};
        for (int w : leftWeights) {
            leftHand.add(card(w, copy++ % 4));
        }
        s.hands = new List[]{playerHand, leftHand, of(3, 1, 4, 1)};
        s.bottomCards = new ArrayList<>();
        s.lastPlayed = null;
        s.lastPlayerSeat = -1;
        return s.serialize();
    }

    /**
     * 恢复于 AI（右家）出牌回合：恢复时必须已挂起 AI 任务；任务执行后 AI 实际
     * 出牌、回合推进到玩家，对局不静默卡死。
     */
    @Test
    public void restoreIntoAiPlayingTurnSchedulesAi() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = new DoudizhuGameController();
        c.attachUi(ui);

        String save = playingSaveWithRightAiTurn();
        assertTrue(DoudizhuGameController.canRestore(save));
        assertTrue(c.restoreFromSave(save));

        DouDiZhuGameStateManager st = c.state();
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, st.getGameState());
        assertEquals("恢复于右家 AI 回合", Seats.SEAT_RIGHT_AI, st.getCurrentTurn());
        assertNotNull("恢复于 AI 回合必须挂起 AI 任务（否则对局静默卡死）",
                c.pendingAiTaskForTest());

        // 同步执行挂起的 AI 任务（模拟主线程消息循环）：自由出牌回合 AI 必出一张
        int handBefore = st.getSeat2Cards().size();
        c.pendingAiTaskForTest().run();
        List<Card> lastPlayed = st.getLastPlayedCards();
        assertNotNull("AI 必须实际出牌（自由出牌回合不能不出）", lastPlayed);
        assertFalse(lastPlayed.isEmpty());
        assertEquals("AI 手牌减少一张", handBefore - 1, st.getSeat2Cards().size());
        assertEquals("回合推进到玩家", Seats.SEAT_PLAYER, st.getCurrentTurn());
        assertEquals("出牌计入 AI 手数", 2, st.getPlayCounts()[Seats.SEAT_RIGHT_AI]);
    }

    /**
     * 恢复于 AI（左家）叫分回合：恢复时必须已挂起叫分任务；任务执行后 AI 完成表态。
     * 测试手牌含三组炸弹（8888/9999/10101010 + JJ JJ）意向顶格 → 叫 3 分，
     * 顺带覆盖复查项 E（顶格叫分立即定地主）在恢复路径的表现。
     */
    @Test
    public void restoreIntoAiBiddingTurnSchedulesAiBid() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = new DoudizhuGameController();
        c.attachUi(ui);

        // 叫分阶段存档：currentTurn=左家 AI，无人叫过分，三家 16 张手牌 + 3 张底牌
        DoudizhuSnapshot s = new DoudizhuSnapshot();
        s.phase = DouDiZhuGameStateManager.STATE_BIDDING;
        s.currentTurn = Seats.SEAT_LEFT_AI;
        s.landlordSeat = -1;
        s.highestBid = 0;
        s.bidPlacedCount = 0;
        s.redealCount = 0;
        s.bidScore = 0;
        s.bombCount = 0;
        s.difficulty = DoudizhuGameController.DIFFICULTY_NORMAL;
        s.startedAtMs = System.currentTimeMillis();
        s.playCounts = new int[3];
        s.passed = new boolean[3];
        s.hands = new List[]{
                of(3, 4, 5, 4, 6, 4, 7, 4),
                of(8, 4, 9, 4, 10, 4, 11, 4),
                of(12, 4, 13, 4, 14, 4)};
        s.bottomCards = of(16, 1, 17, 1, 15, 3);
        s.lastPlayed = null;
        s.lastPlayerSeat = -1;
        String save = s.serialize();

        assertTrue(DoudizhuGameController.canRestore(save));
        assertTrue(c.restoreFromSave(save));

        DouDiZhuGameStateManager st = c.state();
        assertEquals(DouDiZhuGameStateManager.STATE_BIDDING, st.getGameState());
        assertEquals("恢复于左家 AI 叫分回合", Seats.SEAT_LEFT_AI, st.getCurrentTurn());
        assertNotNull("恢复于 AI 叫分回合必须挂起 AI 叫分任务", c.pendingAiTaskForTest());

        // 同步执行：左家手牌 8888/9999/10101010/JJJJ → 意向 3 分 → 顶格叫分立即定地主
        c.pendingAiTaskForTest().run();
        assertEquals("AI 顶格叫 3 分应立即定地主", Seats.SEAT_LEFT_AI, st.getLandlordIndex());
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, st.getGameState());
        assertEquals("地主（AI）回合已挂起后续出牌任务", Seats.SEAT_LEFT_AI,
                st.getCurrentTurn());
        assertNotNull("定地主后 AI 出牌任务已挂起", c.pendingAiTaskForTest());
    }
}
