package com.gamecenter.app.doudizhu;

import static com.gamecenter.app.doudizhu.logic.TestCards.card;
import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.save.DoudizhuSaveSink;
import com.gamecenter.app.doudizhu.score.ScoreBoard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * P3 复查修复 A 回归测试：退出牌桌（✕）时存档处置与弹窗文案一致。
 *
 * <p>弹窗文案承诺"对局进行中，退出前将保存进度"——修复前 exitToMenu 只按
 * keepSave 决定是否清档、从不落盘（✕ 退出不触发 onPause，仅有的写档点不生效）。
 * 修复后经 {@code DoudizhuGameController#persistOnExit} 统一处置：
 * 对局进行中落盘、已结束/大厅态清档。本测试用 {@link RecordingSink} 模拟
 * SaveManager（依赖 Context 无法 JVM 单测），断言 captureSaveJson 结果落盘
 * 且落盘内容可恢复。</p>
 */
public class DoudizhuExitSaveTest {

    /** 模拟 SaveManager 的录制桩。 */
    private static final class RecordingSink implements DoudizhuSaveSink {
        String saved;
        int clears;

        @Override public void save(String json) {
            saved = json;
        }

        @Override public void clear() {
            clears++;
        }
    }

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

    /** 免随机构造叫分阶段控制器。 */
    private static DoudizhuGameController newControllerInBidding(RecordingScreen ui) {
        DoudizhuGameController c = new DoudizhuGameController();
        c.state().resetGameState();
        c.state().startGame();
        c.attachUi(ui);
        return c;
    }

    /** 4 张不同花色的同权重牌（2222）。 */
    private static List<Card> distinctFours(int weight) {
        List<Card> out = new ArrayList<>();
        for (int copy = 0; copy < 4; copy++) {
            out.add(card(weight, copy));
        }
        return out;
    }

    /** 对局进行中退出：captureSaveJson 结果必须落盘（模拟 SaveManager），且落盘内容可恢复。 */
    @Test
    public void exitWhilePlayingSavesProgress() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        st.setLandlord(Seats.SEAT_PLAYER);
        st.startPlayingPhase();
        st.getPlayerHandCards().addAll(distinctFours(15)); // 追加炸弹，倍数可断言
        c.onHumanPlay(distinctFours(15));                  // 炸弹 ×2，制造非平凡局面

        RecordingSink sink = new RecordingSink();
        c.persistOnExit(sink);

        assertNotNull("对局进行中退出必须落存档（与弹窗文案一致）", sink.saved);
        assertEquals("不得误清档", 0, sink.clears);
        assertTrue("落盘内容必须可恢复", DoudizhuGameController.canRestore(sink.saved));
        assertEquals("落盘即 captureSaveJson 的结果", c.captureSaveJson(), sink.saved);

        // 落盘存档可被新控制器完整恢复（菜单"继续对局"路径）
        DoudizhuGameController restored = new DoudizhuGameController();
        assertTrue(restored.restoreFromSave(sink.saved));
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, restored.state().getGameState());
        assertEquals("炸弹倍数随存档落盘", 2, restored.getMultiplier());
    }

    /** 对局已结束退出：不得再落新档，走清档路径。 */
    @Test
    public void exitAfterGameOverClearsSave() {
        RecordingScreen ui = new RecordingScreen();
        DoudizhuGameController c = newControllerInBidding(ui);
        DouDiZhuGameStateManager st = c.state();
        // 地主=左 AI，手牌只留 1 张 3：AI 出完即胜，制造 GAME_OVER 局面
        st.setLandlord(Seats.SEAT_LEFT_AI);
        st.getSeat1Cards().clear();
        st.getSeat1Cards().add(card(3, 0)); // 与下方出牌同一张（黑桃 3）
        st.startPlayingPhase();
        c.onAIPlay(Seats.SEAT_LEFT_AI, of(3, 1));

        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, st.getGameState());
        assertNull("对局已结束无可保存内容", c.captureSaveJson());

        RecordingSink sink = new RecordingSink();
        c.persistOnExit(sink);
        assertNull("已结束对局不得落新档", sink.saved);
        assertEquals("必须清档", 1, sink.clears);
    }

    /** 大厅态退出（无对局）：走清档路径。 */
    @Test
    public void exitFromLobbyClearsSave() {
        DoudizhuGameController c = new DoudizhuGameController();
        assertFalse(c.isInGame());

        RecordingSink sink = new RecordingSink();
        c.persistOnExit(sink);
        assertNull(sink.saved);
        assertEquals("大厅态退出必须清档", 1, sink.clears);
    }
}
