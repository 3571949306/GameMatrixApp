package com.gamecenter.app.doudizhu;

import static com.gamecenter.app.doudizhu.logic.TestCards.card;
import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.score.BidPolicy;
import com.gamecenter.app.doudizhu.score.ScoreBoard;
import com.gamecenter.app.doudizhu.score.SpringDetector;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * P3 对局流程测试：叫分三档流转、重发兜底、炸弹倍数、反春结算与存档恢复。
 *
 * <p>通过 {@link RecordingScreen} 录制 UiCallback 回调，全部经控制器公开入口
 * （onHumanBid/onAIBid/onHumanPlay/onAIPlay）驱动，不绕过任何校验路径。</p>
 */
public class DoudizhuP3FlowTest {

    /** 录制 UI 回调的测试桩。 */
    private static final class RecordingScreen implements DoudizhuGameController.UiCallback {
        int invalidBids;
        int redeals;
        int forcedLandlords;
        List<ScoreBoard.Settlement> settlements = new ArrayList<>();

        @Override public void onBidControlsChanged(boolean show) {}
        @Override public void onPlayControlsChanged(boolean show, boolean enablePass) {}
        @Override public void onTableSyncRequired() {}
        @Override public void onCardsPlayed(List<Card> cards, CardType type) {}
        @Override public void onInvalidPlay(boolean illegalCombo, boolean cannotBeat) {}
        @Override public void onInvalidBid() { invalidBids++; }
        @Override public void onRedeal(int redealCount) { redeals++; }
        @Override public void onForcedLandlord() { forcedLandlords++; }
        @Override public void onGameFinished(ScoreBoard.Settlement settlement) {
            settlements.add(settlement);
        }
    }

    /** 免随机构造处于叫分阶段的控制器（不发牌），并录制其回调。 */
    private static DoudizhuGameController newControllerInBidding(RecordingScreen ui) {
        DoudizhuGameController c = new DoudizhuGameController();
        c.state().resetGameState();
        c.state().startGame();
        c.attachUi(ui);
        return c;
    }

    /** 按指定 copy 序号取同权重多张牌（花色 = copy%4，确保实例不重复）。 */
    private static List<Card> play(int weight, int... copies) {
        List<Card> out = new ArrayList<>();
        for (int copy : copies) {
            out.add(card(weight, copy));
        }
        return out;
    }

    /**
     * 免随机构造"真实牌量（玩家 20 = 17+底牌 / 各 AI 17）+ 玩家为地主"的出牌阶段局面：
     * 整副牌按固定顺序切分，玩家手牌含确定炸弹 2222，牌量守恒（存档记牌器历史可对账）。
     */
    private static DoudizhuGameController newControllerRealDeal(RecordingScreen ui) {
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        // createFullDeck 顺序：花色优先（SPADE 0-12 / HEART 13-25 / CLUB 26-38 /
        // DIAMOND 39-51），索引 = 花色序 ×13 + 牌值序（3=0 … 2=12），王 52/53
        Card[] deck = Card.createFullDeck();
        st.getPlayerHandCards().clear();
        int[] player = {12, 0, 1, 2, 3,                 // 黑桃 2,3,4,5,6
                13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24}; // 红桃 3..A
        for (int i : player) st.getPlayerHandCards().add(deck[i]);
        st.getSeat1Cards().clear();
        int[] left = {4, 5, 6, 7, 8, 9, 10, 11,         // 黑桃 7..A
                26, 27, 28, 29, 30, 31, 32, 33, 34};    // 梅花 3..10,J
        for (int i : left) st.getSeat1Cards().add(deck[i]);
        st.getSeat2Cards().clear();
        int[] right = {35, 36, 37,                      // 梅花 Q,K,A
                39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, // 方块 3..A
                52, 53};                                // 双王
        for (int i : right) st.getSeat2Cards().add(deck[i]);
        st.getBottomCards().clear();
        int[] bottom = {25, 38, 51};                    // 红桃2 梅花2 方块2（并入玩家成 2222 炸弹）
        for (int i : bottom) st.getBottomCards().add(deck[i]);
        st.setLandlord(Seats.SEAT_PLAYER);
        st.startPlayingPhase();
        return c;
    }

    /** 4 张不同花色的同权重牌（如 2222 / 四个 9）。 */
    private static List<Card> distinctFours(int weight) {
        List<Card> out = new ArrayList<>();
        for (int copy = 0; copy < 4; copy++) {
            out.add(card(weight, copy));
        }
        return out;
    }

    /** 免随机构造"指定地主 + 指定人类手牌 + 已进入出牌阶段"的确定局面。 */
    private static DoudizhuGameController newControllerInPlaying(RecordingScreen ui,
            int landlordSeat, List<Card> humanHand) {
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.setLandlord(landlordSeat);
        st.getPlayerHandCards().clear();
        st.getPlayerHandCards().addAll(humanHand);
        st.getSeat1Cards().clear();
        st.getSeat1Cards().addAll(of(3, 1, 9, 3, 10, 2));
        st.getSeat2Cards().clear();
        st.getSeat2Cards().addAll(of(4, 1, 5, 1));
        st.startPlayingPhase();
        return c;
    }

    // ============ 叫分三档流转 ============

    /** 三人叫分 1→2→3：最高分者成地主，叫分写入倍数，底牌入手。 */
    @Test
    public void highestBidderBecomesLandlord() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);

        c.onHumanBid(1);
        assertEquals("叫分后轮到下一家", Seats.SEAT_LEFT_AI, st.getCurrentTurn());
        assertEquals(1, st.getHighestBid());

        c.onAIBid(2);
        assertEquals(2, st.getHighestBid());
        assertEquals(Seats.SEAT_LEFT_AI, st.getHighestBidSeat());

        c.onAIBid(3);
        assertEquals("叫满三人后地主确定", Seats.SEAT_RIGHT_AI, st.getLandlordIndex());
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, st.getGameState());
        assertEquals("叫分倍数 = 3", 3, c.getMultiplier());
        assertEquals("地主 20 张（17+3 底牌）", 20, st.getHandCounts()[Seats.SEAT_RIGHT_AI]);
    }

    /**
     * 顶格叫 3 分立即定地主（P3 复查项 E）：后手无需再表态，倍数按 3 计。
     */
    @Test
    public void topBidThreeFinalizesLandlordImmediately() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);

        c.onHumanBid(3);
        assertEquals("顶格叫分必须立即定地主", Seats.SEAT_PLAYER, st.getLandlordIndex());
        assertEquals("直接进入出牌阶段", DouDiZhuGameStateManager.STATE_PLAYING,
                st.getGameState());
        assertEquals("叫分倍数 = 3", 3, c.getMultiplier());
        assertEquals("地主 20 张（17+3 底牌）", 20, st.getHandCounts()[Seats.SEAT_PLAYER]);
        assertEquals("仅 1 人表态", 1, st.getBidPlacedCount());
    }

    /** 人类叫分不高于当前最高分：回调 onInvalidBid 且叫分状态不变。 */
    @Test
    public void invalidHumanBidRejected() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);

        c.onHumanBid(2);
        assertEquals(2, st.getHighestBid());

        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);
        // 重拨回人类后（测试免随机），再叫 2 分应被拒
        c.onHumanBid(2);
        assertEquals("非法叫分必须回调提示", 1, ui.invalidBids);
        assertEquals("叫分状态不变", 2, st.getHighestBid());
        assertEquals("叫分人数不变（该次仍待叫）", 1, st.getBidPlacedCount());

        // 叫分边界：1~3 之外无效
        c.onHumanBid(4);
        assertEquals(2, ui.invalidBids);
        c.onHumanBid(0);
        assertEquals("0 表示不叫，不触发 invalid", 2, ui.invalidBids);
    }

    /** 有人叫 1 分、后两人流过：叫 1 分者成地主并进入出牌阶段。 */
    @Test
    public void lowerBidWinsWhenOthersPass() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);

        c.onHumanBid(1);
        c.onAIBid(0);
        c.onAIBid(0);
        assertEquals("唯一叫分者成地主", Seats.SEAT_PLAYER, st.getLandlordIndex());
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, st.getGameState());
        assertEquals("倍数按叫分 1 计", 1, c.getMultiplier());
        assertEquals("地主 20 张", 20, st.getHandCounts()[Seats.SEAT_PLAYER]);
    }

    /** 全员不叫：重新发牌（redeal），叫分状态清零并回调 UI。 */
    @Test
    public void allPassTriggersRedeal() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.restoreBidTurnForTest(Seats.SEAT_PLAYER);

        c.onHumanBid(0);
        c.onAIBid(0);
        c.onAIBid(0);
        assertEquals("重新发牌一次", 1, st.getRedealCount());
        assertEquals("UI 收到重发回调", 1, ui.redeals);
        assertEquals("重回叫分阶段", DouDiZhuGameStateManager.STATE_BIDDING, st.getGameState());
        assertEquals("最高分清零", 0, st.getHighestBid());
        assertEquals("叫分人数清零", 0, st.getBidPlacedCount());
        assertTrue("重发后每人有 17 张", st.getHandCounts()[0] == 17
                && st.getHandCounts()[1] == 17 && st.getHandCounts()[2] == 17);
    }

    /** 重发达上限后保底强制开局：按 1 分计倍，UI 收到保底回调。 */
    @Test
    public void forcedLandlordAfterRedealLimit() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();

        // 连续 4 轮全员不叫（第 4 轮触发保底）
        for (int round = 0; round < 4; round++) {
            st.restoreBidTurnForTest(Seats.SEAT_PLAYER);
            for (int i = 0; i < 3; i++) {
                int turn = st.getCurrentTurn();
                if (turn == Seats.SEAT_PLAYER) {
                    c.onHumanBid(0);
                } else {
                    c.onAIBid(0);
                }
            }
        }
        assertEquals("重发 3 次达上限", 3, st.getRedealCount());
        assertEquals("UI 收到 3 次重发回调", 3, ui.redeals);
        assertEquals("保底开局回调 1 次", 1, ui.forcedLandlords);
        assertEquals("保底进入出牌阶段", DouDiZhuGameStateManager.STATE_PLAYING,
                st.getGameState());
        assertEquals("保底按 1 分计倍", 1, st.getBidScore());
        assertEquals(1, c.getMultiplier());
        assertEquals("保底地主手牌 20 张", 20, st.getHandCounts()[st.getLandlordIndex()]);
    }

    // ============ 出牌阶段倍数 ============

    /** 炸弹出牌后倍数翻倍。 */
    @Test
    public void bombDoublesMultiplierDuringPlay() {
        RecordingScreen ui = new RecordingScreen();
        List<Card> hand = distinctFours(15);
        hand.add(card(3, 0)); // 留一张 3，避免打完炸弹直接终局触发春天
        DoudizhuGameController c = newControllerInPlaying(ui, Seats.SEAT_PLAYER, hand);
        DouDiZhuGameStateManager st = c.state();
        assertEquals(1, c.getMultiplier());

        c.onHumanPlay(distinctFours(15));
        assertEquals("炸弹 ×2", 2, c.getMultiplier());
        assertEquals("出牌计入手数", 1, st.getPlayCounts()[Seats.SEAT_PLAYER]);
    }

    // ============ 反春结算 ============

    /**
     * 地主（左 AI）只出首发一手、农民（人类）收完全场 → 反春天，
     * 结算倍数 = 叫分1 × 反春2 = 2，农民赢 +200。
     */
    @Test
    public void antiSpringSettlement() {
        RecordingScreen ui = new RecordingScreen();
        // 地主=左 AI 17 张默认牌；人类手牌固定 8 张 9（压 33 与自由收尾）
        List<Card> humanHand = of(9, 8);
        DoudizhuGameController c = newControllerInPlaying(ui, Seats.SEAT_LEFT_AI, humanHand);
        DouDiZhuGameStateManager st = c.state();
        st.getSeat1Cards().clear();
        st.getSeat1Cards().addAll(of(3, 3)); // 左 AI（地主）：333
        st.getSeat2Cards().clear();
        st.getSeat2Cards().addAll(of(4, 1, 5, 1)); // 右 AI：4 5（压不住 9）

        // 出牌顺序：左 AI(1) → 右 AI(2) → 人类(0)
        // 人类的 8 张 9 = copy 0..7（花色 S,H,C,D,S,H,C,D），每手选不重复的 copy
        // 左 AI 首发 33（地主手数 1）
        c.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 2));
        // 右 AI 流过；人类 99 压过（copy 0,1）
        c.onAIPass(Seats.SEAT_RIGHT_AI);
        c.onHumanPlay(play(9, 0, 1));
        // 左 AI 单 3 压不住 99，流过；右 AI 流过 → 清桌，人类自由出
        c.onAIPass(Seats.SEAT_LEFT_AI);
        c.onAIPass(Seats.SEAT_RIGHT_AI);
        // 人类自由出 999（copy 2,3,4）→ 两家流过 → 清桌
        c.onHumanPlay(play(9, 2, 3, 4));
        c.onAIPass(Seats.SEAT_LEFT_AI);
        c.onAIPass(Seats.SEAT_RIGHT_AI);
        // 人类 99（copy 5,6）→ 两家流过 → 清桌
        c.onHumanPlay(play(9, 5, 6));
        c.onAIPass(Seats.SEAT_LEFT_AI);
        c.onAIPass(Seats.SEAT_RIGHT_AI);
        // 人类收尾单 9（copy 7）→ 两家流过 → 对局结束
        c.onHumanPlay(play(9, 7));
        c.onAIPass(Seats.SEAT_LEFT_AI);
        c.onAIPass(Seats.SEAT_RIGHT_AI);

        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, st.getGameState());
        assertEquals("人类（农民）获胜", Seats.SEAT_PLAYER, st.getWinnerIndex());
        assertEquals("地主=左 AI", Seats.SEAT_LEFT_AI, st.getLandlordIndex());
        assertEquals("地主只出过首发一手 → 反春", 1,
                st.getPlayCounts()[Seats.SEAT_LEFT_AI]);
        assertTrue("反春判定",
                SpringDetector.isAntiSpring(st.getWinnerIndex(),
                        st.getLandlordIndex(), st.getPlayCounts()));
        assertFalse(SpringDetector.isSpring(st.getWinnerIndex(),
                st.getLandlordIndex(), st.getPlayCounts()));
        assertEquals("结算回调恰好一次", 1, ui.settlements.size());
        ScoreBoard.Settlement s = ui.settlements.get(0);
        assertEquals("倍数 = 叫分1 × 反春2", 2, s.multiplier);
        assertTrue("农民（人类）胜", s.humanWon());
        assertEquals("农民赢 +底分100×倍数2 = 200", 200, s.humanScoreDelta);
    }

    /** 反春判定与结算内核（SpringDetector + ScoreBoard 组合口径）。 */
    @Test
    public void antiSpringDetectorAndSettlement() {
        int landlord = Seats.SEAT_LEFT_AI;
        int[] plays = {3, 1, 0}; // 人类 3 手、地主 1 手（首发）、右 AI 0 手
        assertTrue(SpringDetector.isAntiSpring(Seats.SEAT_PLAYER, landlord, plays));
        assertFalse(SpringDetector.isSpring(Seats.SEAT_PLAYER, landlord, plays));

        ScoreBoard board = new ScoreBoard();
        board.setBidScore(1);
        board.applySpring(false, true);
        ScoreBoard.Settlement s = board.settle(landlord, false, false, 0);
        assertEquals("倍数 = 1 × 反春 2", 2, s.multiplier);
        assertTrue("农民（人类）胜", s.humanWon());
        assertEquals("农民赢 +底分100×倍数2 = 200", 200, s.humanScoreDelta);
    }

    /** 地主胜且农民零出牌 → 春天，结算倍数含春天 ×2。 */
    @Test
    public void springSettlement() {
        int landlord = Seats.SEAT_LEFT_AI;
        int[] plays = {0, 3, 0};
        assertTrue(SpringDetector.isSpring(landlord, landlord, plays));

        ScoreBoard board = new ScoreBoard();
        board.setBidScore(2);
        board.registerBomb();
        board.applySpring(true, false);
        ScoreBoard.Settlement s = board.settle(landlord, false, true, 0);
        assertEquals("倍数 = 2 × 2 × 2 = 8", 8, s.multiplier);
        assertFalse("农民（人类）输", s.humanWon());
        assertEquals("农民输 -底分100×倍数8 = -800", -800, s.humanScoreDelta);
    }

    // ============ 存档 ============

    /** 存档序列化→恢复：状态/回合/手牌/倍数/难度/记牌器历史还原。 */
    @Test
    public void saveAndRestoreRoundTrip() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerRealDeal(ui);
        DouDiZhuGameStateManager st = c.state();

        c.onHumanPlay(distinctFours(15));
        String json = c.captureSaveJson();
        assertEquals("存档前倍数 2", 2, c.getMultiplier());
        assertTrue(DoudizhuGameController.canRestore(json));

        DoudizhuGameController restored = new DoudizhuGameController();
        assertTrue(restored.restoreFromSave(json));

        DouDiZhuGameStateManager rst = restored.state();
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, rst.getGameState());
        assertEquals("回合还原", st.getCurrentTurn(), rst.getCurrentTurn());
        assertEquals("难度还原", DoudizhuGameController.DIFFICULTY_NORMAL,
                restored.getDifficulty());
        assertEquals("炸弹倍数还原", 2, restored.getMultiplier());
        List<Card> handBefore = st.getPlayerHandCards();
        List<Card> handAfter = rst.getPlayerHandCards();
        assertEquals("手牌张数还原", handBefore.size(), handAfter.size());
        assertTrue("手牌内容还原", handAfter.containsAll(handBefore)
                && handBefore.containsAll(handAfter));
        assertEquals("记牌器历史还原",
                c.getPlayedHistory().size(), restored.getPlayedHistory().size());
        assertEquals("桌面最后出牌还原（恢复后该手回到出牌者手上）",
                st.getLastPlayerWhoPlayed(), rst.getLastPlayerWhoPlayed());
    }

    /** 损坏存档拒绝恢复；大厅态无可保存内容。 */
    @Test
    public void corruptedSaveRejected() {
        assertFalse(DoudizhuGameController.canRestore("garbage"));
        assertFalse(DoudizhuGameController.canRestore(null));
        DoudizhuGameController c = new DoudizhuGameController();
        assertFalse(c.restoreFromSave("not-a-save"));
        assertNull(c.captureSaveJson());
    }
}
