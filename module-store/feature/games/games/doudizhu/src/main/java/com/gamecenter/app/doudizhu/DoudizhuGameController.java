package com.gamecenter.app.doudizhu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主对局控制器（单机版）。
 *
 * <p>接管模块化前宿主 Activity 的全部驱动职责：串起 {@link DouDiZhuGameStateManager}（状态机）、
 * {@link DouDiZhuAIHelper}（AI 调度）与桌面 UI。控制器持有游戏状态、不持有任何 View 引用；
 * View 重建（如旋转屏幕）后通过 {@link #attachUi(UiCallback)} 重新接线并全量回放状态。</p>
 *
 * <p>联机裁剪说明：未来回归联机时，新增"远端消息动作源"实现与 AI 相同的回调语义
 * （在对应座位产生 bid/play/pass 事件），本控制器与规则层、UI 层无需改动。</p>
 *
 * <p>AI 契约：AI 产出的着法一律经 {@link DouDiZhuRuleEngine#validatePlay} 复核，
 * 非法则计入 {@code aiContractViolations} 并记录 {@code DDZ_AI_CONTRACT_VIOLATION} 日志，
 * 按不出处理（对齐 AGENTS.md 象棋/围棋 AI 契约风格）。</p>
 */
public class DoudizhuGameController implements DouDiZhuGameStateManager.GameStateListener,
        DouDiZhuAIHelper.AICallback {

    private static final String TAG = "DoudizhuCtrl";

    // ============ 难度 ============

    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_NORMAL = 1;
    public static final int DIFFICULTY_HARD = 2;

    /**
     * 难度因子映射（供 AIBot 决策随机性/激进度使用）：
     * 简单 = 0.6（低难度失误率），普通 = 1.0，困难 = 1.05（永不随机放弃）。
     */
    private static final float[] DIFFICULTY_FACTORS = {0.6f, 1.0f, 1.05f};

    /** 桌面 UI 回调，由牌桌视图（DoudizhuGameScreen）实现 */
    public interface UiCallback {
        /** 叫地主按钮区显示/隐藏（仅轮到人类叫地主时显示） */
        void onBidControlsChanged(boolean show);
        /** 出牌按钮区显示/隐藏；enablePass=false 表示自由出牌回合（不能不出） */
        void onPlayControlsChanged(boolean show, boolean enablePass);
        /** 要求 UI 全量回放桌面状态 */
        void onTableSyncRequired();
        /** 有座位出牌（用于动画/特效），type 为牌型（含炸弹/王炸/飞机） */
        void onCardsPlayed(List<Card> cards, CardType type);
        /** 人类出牌非法：illegalCombo=牌型不合法；cannotBeat=管不上上家 */
        void onInvalidPlay(boolean illegalCombo, boolean cannotBeat);
        /** 对局结束（含人类胜负结论由 UI 依 winnerIndex/landlordIndex 推导） */
        void onGameFinished(int winnerIndex);
        /** 新一局开始（UI 可播发牌动画） */
        void onDealStart();
    }

    private final Handler handler;
    private final DouDiZhuGameStateManager stateManager = new DouDiZhuGameStateManager();
    private final DouDiZhuAIHelper aiHelper;
    private final int[] seatTypes = Seats.singlePlayerSeatTypes();

    private int difficulty = DIFFICULTY_NORMAL;
    private UiCallback ui;

    /** 全部已出的牌（含被桌面清理的轮次），用于记牌器计算 */
    private final List<Card> playedHistory = new ArrayList<>();
    /** 当前回合的提示候选（懒生成），随回合重置 */
    private List<List<Card>> currentHints;
    private int hintIndex;
    private boolean gameOverHandled;
    private int aiContractViolations;

    public DoudizhuGameController() {
        handler = new Handler(Looper.getMainLooper());
        aiHelper = new DouDiZhuAIHelper(handler, this);
        stateManager.setListener(this);
    }

    // ============ 生命周期 ============

    /** 绑定桌面 UI；若对局已在进行则立即全量回放一次状态。 */
    public void attachUi(UiCallback callback) {
        this.ui = callback;
        pushPhaseToUi();
        if (ui != null) {
            ui.onTableSyncRequired();
        }
    }

    /** 解绑桌面 UI（View 销毁时调用；对局状态保留在控制器内）。 */
    public void detachUi() {
        this.ui = null;
    }

    /** 彻底放弃当前对局并回到大厅态（退出到菜单时调用）。 */
    public void shutdown() {
        aiHelper.cancelPending();
        stateManager.resetGameState();
        playedHistory.clear();
        ui = null;
    }

    /**
     * 开启新一局。
     *
     * @param difficulty {@link #DIFFICULTY_EASY} / {@link #DIFFICULTY_NORMAL} / {@link #DIFFICULTY_HARD}
     */
    public void startNewGame(int difficulty) {
        aiHelper.cancelPending();
        this.difficulty = Math.max(DIFFICULTY_EASY, Math.min(DIFFICULTY_HARD, difficulty));
        aiHelper.setDifficultyFactor(DIFFICULTY_FACTORS[this.difficulty]);
        playedHistory.clear();
        currentHints = null;
        hintIndex = 0;
        gameOverHandled = false;
        aiContractViolations = 0;
        stateManager.resetGameState();
        stateManager.startGame();
        if (ui != null) {
            ui.onDealStart();
        }
    }

    /** 是否有一局正在进行（含叫地主/出牌/结束未退出）。 */
    public boolean isInGame() {
        return stateManager.getGameState() != DouDiZhuGameStateManager.STATE_LOBBY;
    }

    public int getDifficulty() {
        return difficulty;
    }

    /** 本局 AI 契约违规次数（发布验收要求为 0）。 */
    public int getAiContractViolations() {
        return aiContractViolations;
    }

    public DouDiZhuGameStateManager state() {
        return stateManager;
    }

    // ============ 人类操作入口（由牌桌视图调用） ============

    /** 人类叫地主 / 不叫。 */
    public void onHumanBid(boolean call) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_BIDDING) return;
        if (seatTypes[stateManager.getCurrentTurn()] != Seats.TYPE_HUMAN) return;
        handleBid(stateManager.getCurrentTurn(), call);
    }

    /**
     * 人类确认出牌。先做完整合法性校验（牌型 + 压牌），非法时回调 {@code onInvalidPlay} 并不改动状态。
     *
     * @param cards 人类选中的牌
     */
    public void onHumanPlay(List<Card> cards) {
        if (!isHumanPlayTurn()) return;
        if (cards == null || cards.isEmpty()) {
            if (ui != null) ui.onInvalidPlay(true, false);
            return;
        }
        List<Card> last = stateManager.getLastPlayedCards();
        CardType type = GameRuleUtil.getCardType(cards);
        if (type == CardType.ERROR) {
            if (ui != null) ui.onInvalidPlay(true, false);
            return;
        }
        if (last != null && !GameRuleUtil.canPlayPass(cards, last)) {
            if (ui != null) ui.onInvalidPlay(false, true);
            return;
        }
        commitPlay(Seats.SEAT_PLAYER, cards);
    }

    /** 人类选择不出（自由出牌回合不允许）。 */
    public void onHumanPass() {
        if (!isHumanPlayTurn()) return;
        if (stateManager.getLastPlayedCards() == null) return;
        passSeat(Seats.SEAT_PLAYER);
    }

    /**
     * 请求下一条出牌提示（循环返回）。无提示时返回 null。
     */
    public List<Card> nextHint() {
        if (!isHumanPlayTurn()) return null;
        if (currentHints == null) {
            currentHints = GameRuleUtil.findPlayableCombos(
                    stateManager.getPlayerHandCards(), stateManager.getLastPlayedCards());
            hintIndex = 0;
        }
        if (currentHints.isEmpty()) return null;
        List<Card> hint = currentHints.get(hintIndex % currentHints.size());
        hintIndex++;
        return hint;
    }

    private boolean isHumanPlayTurn() {
        return stateManager.getGameState() == DouDiZhuGameStateManager.STATE_PLAYING
                && seatTypes[stateManager.getCurrentTurn()] == Seats.TYPE_HUMAN;
    }

    // ============ 叫地主 / 出牌核心流转 ============

    private void handleBid(int seat, boolean call) {
        if (call) {
            stateManager.setLandlord(seat);
            stateManager.startPlayingPhase();
        } else {
            // 三轮无人叫时 stateManager 内部会随机指定地主并进入出牌阶段
            stateManager.advanceBidTurn();
        }
    }

    private void commitPlay(int seat, List<Card> cards) {
        CardType type = GameRuleUtil.getCardType(cards);
        playedHistory.addAll(cards);
        // executePlay 内部会推进回合并触发 onTurnChanged/onGameOver
        stateManager.executePlay(seat, cards);
        if (ui != null) {
            ui.onCardsPlayed(new ArrayList<>(cards), type);
        }
    }

    private void passSeat(int seat) {
        stateManager.setPlayerPassed(seat, true);
        if (!stateManager.checkAndClearTable()) {
            stateManager.switchToNextPlayer();
        }
        // 清桌时 stateManager 已把回合交还最后出牌者并触发 onTurnChanged
    }

    // ============ GameStateListener（状态机回调） ============

    @Override
    public void onStateChanged(int newState) {
        pushPhaseToUi();
        if (ui != null) {
            ui.onTableSyncRequired();
        }
    }

    @Override
    public void onTurnChanged(int newTurn) {
        pushPhaseToUi();
        if (ui != null) {
            ui.onTableSyncRequired();
        }
        int state = stateManager.getGameState();
        if (state == DouDiZhuGameStateManager.STATE_BIDDING) {
            if (seatTypes[newTurn] == Seats.TYPE_AI) {
                aiHelper.scheduleAIBid();
            }
        } else if (state == DouDiZhuGameStateManager.STATE_PLAYING) {
            if (seatTypes[newTurn] == Seats.TYPE_AI) {
                aiHelper.scheduleAITurn();
            }
        }
    }

    @Override
    public void onLandlordSet(int landlordIndex) {
        if (ui != null) {
            ui.onTableSyncRequired();
        }
    }

    @Override
    public void onGameOver(int winnerIndex) {
        if (gameOverHandled) return;
        gameOverHandled = true;
        aiHelper.cancelPending();
        pushPhaseToUi();
        if (ui != null) {
            ui.onTableSyncRequired();
            ui.onGameFinished(winnerIndex);
        }
    }

    private void pushPhaseToUi() {
        if (ui == null) return;
        int state = stateManager.getGameState();
        if (state == DouDiZhuGameStateManager.STATE_BIDDING) {
            boolean humanTurn = seatTypes[stateManager.getCurrentTurn()] == Seats.TYPE_HUMAN;
            ui.onBidControlsChanged(humanTurn);
            ui.onPlayControlsChanged(false, false);
        } else if (state == DouDiZhuGameStateManager.STATE_PLAYING) {
            ui.onBidControlsChanged(false);
            boolean humanTurn = isHumanPlayTurn();
            boolean freePlay = stateManager.getLastPlayedCards() == null;
            ui.onPlayControlsChanged(humanTurn, humanTurn && !freePlay);
            if (humanTurn) {
                currentHints = null;
                hintIndex = 0;
            }
        } else {
            ui.onBidControlsChanged(false);
            ui.onPlayControlsChanged(false, false);
        }
    }

    // ============ AICallback（AIHelper 数据与结果回传） ============

    @Override
    public int getGameState() {
        return stateManager.getGameState();
    }

    @Override
    public int getCurrentTurn() {
        return stateManager.getCurrentTurn();
    }

    @Override
    public int[] getSeatTypes() {
        return seatTypes;
    }

    @Override
    public List<Card> getSeatHandCards(int seatIndex) {
        switch (seatIndex) {
            case Seats.SEAT_PLAYER: return stateManager.getPlayerHandCards();
            case Seats.SEAT_LEFT_AI: return stateManager.getSeat1Cards();
            case Seats.SEAT_RIGHT_AI: return stateManager.getSeat2Cards();
            default: return new ArrayList<>();
        }
    }

    @Override
    public List<Card> getLastPlayedCards() {
        return stateManager.getLastPlayedCards();
    }

    @Override
    public int getLandlordSeat() {
        return stateManager.getLandlordIndex();
    }

    @Override
    public int getLastPlayerWhoPlayed() {
        return stateManager.getLastPlayerWhoPlayed();
    }

    @Override
    public int getLandlordStatusForAISeat(int seatIndex) {
        int landlord = stateManager.getLandlordIndex();
        if (landlord < 0) return 0;
        return landlord == seatIndex ? 2 : 1;
    }

    @Override
    public int getSeatRemainingCardCount(int seatIndex) {
        List<Card> hand = getSeatHandCards(seatIndex);
        return hand == null ? 0 : hand.size();
    }

    @Override
    public void onAIPlay(int seatIndex, List<Card> cards) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_PLAYING
                || stateManager.getCurrentTurn() != seatIndex) {
            return;
        }
        List<Card> last = stateManager.getLastPlayedCards();
        if (!DouDiZhuRuleEngine.validatePlay(cards, last)) {
            aiContractViolations++;
            Log.w(TAG, "DDZ_AI_CONTRACT_VIOLATION seat=" + seatIndex);
            if (last != null) {
                passSeat(seatIndex);
            }
            return;
        }
        commitPlay(seatIndex, cards);
    }

    @Override
    public void onAIPass(int seatIndex) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_PLAYING
                || stateManager.getCurrentTurn() != seatIndex) {
            return;
        }
        if (stateManager.getLastPlayedCards() == null) {
            // 自由出牌回合 AI 不可 pass（AIBot 首发恒有牌可出，此处仅防御）
            Log.w(TAG, "AI pass on free turn, seat=" + seatIndex);
            return;
        }
        passSeat(seatIndex);
    }

    @Override
    public void onAIBid(boolean call) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_BIDDING) return;
        int seat = stateManager.getCurrentTurn();
        if (seatTypes[seat] != Seats.TYPE_AI) return;
        handleBid(seat, call);
    }

    // ============ 桌面状态回放 ============

    /**
     * 把当前全量对局状态推送到桌面视图（首绑/每步之后/清桌后调用）。
     */
    public void pushTableState(DouDiZhuTableView view) {
        view.setPlayerHandCards(stateManager.getPlayerHandCards());

        boolean landlordKnown = stateManager.getLandlordIndex() >= 0;
        view.setBottomCards(landlordKnown
                ? stateManager.getBottomCards()
                : new ArrayList<Card>());

        int[] handCounts = stateManager.getHandCounts();
        view.setAICardCounts(handCounts[Seats.SEAT_LEFT_AI], handCounts[Seats.SEAT_RIGHT_AI]);

        view.setPlayerPlayedCards(stateManager.getPlayerPlayedCards());
        view.setLeftAIPlayedCards(stateManager.getSeat1PlayedCards());
        view.setRightAIPlayedCards(stateManager.getSeat2PlayedCards());

        boolean[] passed = stateManager.getPlayerPassed();
        view.setPassStates(passed[Seats.SEAT_LEFT_AI], passed[Seats.SEAT_RIGHT_AI]);

        int[] status = new int[Seats.TOTAL_SEATS];
        if (landlordKnown) {
            int landlord = stateManager.getLandlordIndex();
            for (int i = 0; i < Seats.TOTAL_SEATS; i++) {
                status[i] = (i == landlord) ? 2 : 1;
            }
        }
        view.setAllLandlordStatus(status);
        view.setCurrentTurn(stateManager.getCurrentTurn());
        view.setCardCounterCounts(remainingCounter());
        view.setGamePhase(stateManager.getGameState());
        // 中央放大展示当前一手；自由出牌（桌面已清）时不展示
        view.setLastPlayedCards(stateManager.getLastPlayedCards());
    }

    /**
     * 记牌器数据：int[15]，索引 0-12 对应 3~2，13 小王，14 大王；
     * 值为"尚未出现在任何出牌记录中"的剩余张数。
     */
    private int[] remainingCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        for (Card card : playedHistory) {
            int idx = card.getRank().getWeight() - 3;
            if (idx >= 0 && idx < 15) {
                counts[idx]--;
            }
        }
        return counts;
    }
}
