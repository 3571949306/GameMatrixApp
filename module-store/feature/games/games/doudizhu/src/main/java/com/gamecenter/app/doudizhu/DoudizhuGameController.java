package com.gamecenter.app.doudizhu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.save.DoudizhuSaveSink;
import com.gamecenter.app.doudizhu.save.DoudizhuSnapshot;
import com.gamecenter.app.doudizhu.score.BidPolicy;
import com.gamecenter.app.doudizhu.score.ScoreBoard;
import com.gamecenter.app.doudizhu.score.SpringDetector;
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
 * 非法则计入 {@code aiContractViolations} 并记录 {@code DDZ_AI_CONTRACT_VIOLATION} 日志：
 * 跟牌回合按不出处理；自由出牌回合强制兜底为最小合法单牌（自由回合不能不出，
 * 否则对局卡死）。对齐 AGENTS.md 象棋/围棋 AI 契约风格。</p>
 *
 * <p>P3 对局流程：叫分制 1/2/3 三档（{@code onHumanBid(int)}），倍数
 * = 叫分 × 2^炸弹 ×（春天/反春 ×2），对局结束生成 {@link ScoreBoard.Settlement}
 * 结算明细回调 UI；对局状态可经 {@link #captureSaveJson()}/{@link #restoreFromSave}
 * 持久化与恢复（由宿主 Fragment 落到 SaveManager）。</p>
 */
public class DoudizhuGameController implements DouDiZhuGameStateManager.GameStateListener,
        DouDiZhuAIHelper.AICallback {

    private static final String TAG = "DoudizhuCtrl";

    // ============ 难度 ============

    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_NORMAL = 1;
    public static final int DIFFICULTY_HARD = 2;

    /**
     * 难度档位直接映射为 {@code AiBrain} 打法档（0=简单 1=普通 2=困难），
     * 三档打法差异见 {@code ai/AiBrain} 类注释。
     */
    private static final int MIN_DIFFICULTY = DIFFICULTY_EASY;
    private static final int MAX_DIFFICULTY = DIFFICULTY_HARD;

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
        /** 人类叫分无效（不高于当前最高叫分），UI 提示后可重新叫分 */
        void onInvalidBid();
        /** 无人叫分重新发牌（redealCount 为已重发次数，含本次） */
        void onRedeal(int redealCount);
        /** 重发达上限后保底强制开局（按 1 分计倍） */
        void onForcedLandlord();
        /** 对局结束，携带结算明细（胜负/叫分/炸弹/春天/倍数/得分/耗时） */
        void onGameFinished(ScoreBoard.Settlement settlement);
    }

    private final Handler handler;
    private final DouDiZhuGameStateManager stateManager = new DouDiZhuGameStateManager();
    private final DouDiZhuAIHelper aiHelper;
    private final int[] seatTypes = Seats.singlePlayerSeatTypes();

    private int difficulty = DIFFICULTY_NORMAL;
    private UiCallback ui;

    /** 全部已出的牌（含被桌面清理的轮次），用于记牌器计算；仅主线程 Handler 链路访问 */
    private final List<Card> playedHistory = new ArrayList<>();
    /** 当前回合的提示候选（懒生成），随回合重置 */
    private List<List<Card>> currentHints;
    private int hintIndex;
    private boolean gameOverHandled;
    private int aiContractViolations;

    /** 倍数记分板（叫分/炸弹/春天） */
    private ScoreBoard scoreBoard = new ScoreBoard();
    /** 本局开始时间戳（毫秒），存档恢复时还原，用于耗时统计 */
    private long startedAtMs;

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
        this.difficulty = Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, difficulty));
        aiHelper.setDifficulty(this.difficulty);
        playedHistory.clear();
        currentHints = null;
        hintIndex = 0;
        gameOverHandled = false;
        aiContractViolations = 0;
        scoreBoard = new ScoreBoard();
        startedAtMs = System.currentTimeMillis();
        stateManager.resetGameState();
        stateManager.startGame();
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

    /** 当前总倍数（叫分 × 2^炸弹 × 春天加成；春天在对局结束才判定）。 */
    public int getMultiplier() {
        return scoreBoard.totalMultiplier();
    }

    /** 本局是否已结束（用于存档时机判断）。 */
    public boolean isGameOver() {
        return stateManager.getGameState() == DouDiZhuGameStateManager.STATE_GAME_OVER;
    }

    public DouDiZhuGameStateManager state() {
        return stateManager;
    }

    // ============ 人类操作入口（由牌桌视图调用） ============

    /**
     * 人类叫分（P3 叫分制）。
     *
     * @param bid 叫分 1/2/3；0 表示不叫
     */
    public void onHumanBid(int bid) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_BIDDING) return;
        if (seatTypes[stateManager.getCurrentTurn()] != Seats.TYPE_HUMAN) return;
        if (bid > 0 && !BidPolicy.isValidBid(bid, stateManager.getHighestBid())) {
            if (ui != null) ui.onInvalidBid();
            return;
        }
        handleBid(Seats.SEAT_PLAYER, bid);
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

    // ============ 叫分 / 出牌核心流转 ============

    /**
     * 处理一次叫分（人类/AI 共用）。
     *
     * @param seat 叫分座位
     * @param bid  叫分 1/2/3，0 表示不叫
     */
    private void handleBid(int seat, int bid) {
        if (bid > 0) {
            stateManager.recordBid(seat, bid);
        } else {
            stateManager.passBid();
        }
        // recordBid 可能已定地主并进入出牌阶段（onLandlordSet/onStateChanged 已回推）
    }

    private void commitPlay(int seat, List<Card> cards) {
        CardType type = GameRuleUtil.getCardType(cards);
        playedHistory.addAll(cards);
        // 炸弹/王炸倍数 ×2（P3 倍数体系）
        if (type == CardType.BOMB || type == CardType.JOKER_BOMB) {
            scoreBoard.registerBomb();
        }
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
        scheduleAiIfDue();
    }

    /**
     * 当前回合属于 AI 时调度 AI 行动（叫分阶段→叫分，出牌阶段→出牌）。
     *
     * <p>正常流转经 {@link #onTurnChanged} 触发；恢复对局
     * （{@code DouDiZhuGameStateManager.restoreFrom} 末尾同样回调 onTurnChanged）
     * 后恰逢 AI 回合也必须恢复调度，否则对局静默卡死（P3 复查项 D，测试锁定）。</p>
     */
    void scheduleAiIfDue() {
        int state = stateManager.getGameState();
        if (state == DouDiZhuGameStateManager.STATE_BIDDING) {
            if (seatTypes[stateManager.getCurrentTurn()] == Seats.TYPE_AI) {
                aiHelper.scheduleAIBid();
            }
        } else if (state == DouDiZhuGameStateManager.STATE_PLAYING) {
            if (seatTypes[stateManager.getCurrentTurn()] == Seats.TYPE_AI) {
                aiHelper.scheduleAITurn();
            }
        }
    }

    /**
     * 当前挂起的 AI 任务（仅供 JVM 单测：恢复于 AI 回合场景验证调度已挂起）。
     */
    Runnable pendingAiTaskForTest() {
        return aiHelper.peekPendingForTest();
    }

    @Override
    public void onLandlordSet(int landlordIndex) {
        // 叫分写入倍数记分板（P3：倍数 = 叫分 × …）
        scoreBoard.setBidScore(stateManager.getBidScore());
        if (ui != null) {
            ui.onTableSyncRequired();
        }
    }

    /**
     * 本轮叫分全部流过：按 {@code BidPolicy.shouldRedeal} 决定重新发牌或保底开局
     * （文档 D3：重发上限 3 次，超限后强制 1 分开局，防死循环）。
     */
    @Override
    public void onBidRoundPassed() {
        if (BidPolicy.shouldRedeal(stateManager.getBidPlacedCount(),
                stateManager.getRedealCount())) {
            stateManager.redeal();
            if (ui != null) {
                ui.onRedeal(stateManager.getRedealCount());
            }
        } else {
            // 保底：从当前起始座位强制 1 分开局
            stateManager.forceStartWithBid(stateManager.getBidTurn());
            if (ui != null) {
                ui.onForcedLandlord();
            }
        }
    }

    @Override
    public void onGameOver(int winnerIndex) {
        if (gameOverHandled) return;
        gameOverHandled = true;
        aiHelper.cancelPending();

        // 春天/反春判定与结算（P3）
        int landlord = stateManager.getLandlordIndex();
        boolean spring = SpringDetector.isSpring(winnerIndex, landlord,
                stateManager.getPlayCounts());
        boolean antiSpring = SpringDetector.isAntiSpring(winnerIndex, landlord,
                stateManager.getPlayCounts());
        scoreBoard.applySpring(spring, antiSpring);

        boolean humanIsLandlord = landlord == Seats.SEAT_PLAYER;
        boolean landlordWon = winnerIndex == landlord;
        ScoreBoard.Settlement settlement = scoreBoard.settle(landlord, humanIsLandlord,
                landlordWon, System.currentTimeMillis() - startedAtMs);

        pushPhaseToUi();
        if (ui != null) {
            ui.onTableSyncRequired();
            ui.onGameFinished(settlement);
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
    public int getCurrentHighestBid() {
        return stateManager.getHighestBid();
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
    public List<Card> getPlayedHistory() {
        return new ArrayList<>(playedHistory);
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
            } else {
                // 自由出牌回合不能"不出"（桌面永远清不了，对局会卡死）：
                // 强制兜底为最小合法单牌，与 AiBrain 出口自校验语义一致
                List<Card> fallback = smallestLegalSingle(seatIndex);
                if (fallback != null && DouDiZhuRuleEngine.validatePlay(fallback, last)) {
                    commitPlay(seatIndex, fallback);
                } else {
                    // 不可达防御分支：手牌非空时最小单张在自由出牌回合恒合法，
                    // fallback == null 只可能是手牌状态已异常，记日志便于排查
                    Log.w(TAG, "DDZ_AI_FALLBACK_UNREACHABLE seat=" + seatIndex
                            + " fallbackNull=" + (fallback == null));
                }
            }
            return;
        }
        commitPlay(seatIndex, cards);
    }

    /** 指定座位手牌中最小单张（自由出牌回合 AI 非法着法的强制兜底）。 */
    private List<Card> smallestLegalSingle(int seatIndex) {
        List<Card> hand = getSeatHandCards(seatIndex);
        if (hand == null || hand.isEmpty()) return null;
        Card min = hand.get(0);
        for (Card c : hand) {
            if (c.getWeight() < min.getWeight()) min = c;
        }
        List<Card> out = new ArrayList<>();
        out.add(min);
        return out;
    }

    @Override
    public void onAIPass(int seatIndex) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_PLAYING
                || stateManager.getCurrentTurn() != seatIndex) {
            return;
        }
        if (stateManager.getLastPlayedCards() == null) {
            // 自由出牌回合 AI 不可 pass（AiBrain 首发恒有牌可出，此处仅防御）
            Log.w(TAG, "AI pass on free turn, seat=" + seatIndex);
            return;
        }
        passSeat(seatIndex);
    }

    @Override
    public void onAIBid(int bid) {
        if (stateManager.getGameState() != DouDiZhuGameStateManager.STATE_BIDDING) return;
        int seat = stateManager.getCurrentTurn();
        if (seatTypes[seat] != Seats.TYPE_AI) return;
        if (bid > 0 && !BidPolicy.isValidBid(bid, stateManager.getHighestBid())) {
            // AI 契约：叫分不高于当前最高分视为不叫（流过）
            Log.w(TAG, "DDZ_AI_BID_INVALID seat=" + seat + " bid=" + bid);
            bid = 0;
        }
        handleBid(seat, bid);
    }

    /**
     * 校验存档字符串是否可恢复（菜单页"继续对局"按钮显隐用），不改变任何状态。
     *
     * @param saveJson 存档字符串
     * @return true 表示可经 {@link #restoreFromSave} 恢复
     */
    public static boolean canRestore(String saveJson) {
        try {
            DoudizhuSnapshot.deserialize(saveJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============ 存档（P3） ============

    /**
     * 捕获当前对局并序列化为存档 JSON 字符串。
     *
     * <p>由宿主 Fragment 在 onPause 时落到 {@code SaveManager}（auto 槽）；
     * 大厅态/已结束返回 null（调用方应清档）。</p>
     *
     * @return 存档字符串，无可保存的对局时为 null
     */
    public String captureSaveJson() {
        if (!isInGame() || isGameOver()) return null;
        DoudizhuSnapshot snapshot = stateManager.captureSnapshot();
        if (snapshot == null) return null;
        snapshot.bombCount = scoreBoard.getBombCount();
        snapshot.difficulty = difficulty;
        snapshot.startedAtMs = startedAtMs;
        try {
            return snapshot.serialize();
        } catch (DoudizhuSnapshot.SerializeException e) {
            Log.w(TAG, "存档序列化失败", e);
            return null;
        }
    }

    /**
     * 退出牌桌（✕）时的存档处置（P3 复查修复 A）。
     *
     * <p>退出确认弹窗文案承诺"退出前将保存进度"：对局进行中（未结束）经
     * {@link #captureSaveJson()} 取存档并经 sink 落盘；已结束/大厅态则清档。
     * ✕ 退出是同一 Activity 内切视图、不触发宿主 onPause，必须在此显式处置，
     * 否则文案与实际行为不符（且 {@link #shutdown()} 会清空对局状态，
     * 落档必须发生在其之前）。</p>
     *
     * @param sink 存档写入/清档出口（宿主实现，包装 SaveManager）
     */
    public void persistOnExit(DoudizhuSaveSink sink) {
        String json = captureSaveJson();
        if (json != null) {
            sink.save(json);
        } else {
            sink.clear();
        }
    }

    /**
     * 从存档字符串恢复对局（进程被杀后重进）。
     *
     * <p>恢复失败（格式损坏）返回 false，调用方应清档并回到菜单。</p>
     *
     * @param saveJson 存档字符串（{@link #captureSaveJson()} 产出）
     * @return true 表示恢复成功
     */
    public boolean restoreFromSave(String saveJson) {
        DoudizhuSnapshot snapshot;
        try {
            snapshot = DoudizhuSnapshot.deserialize(saveJson);
        } catch (DoudizhuSnapshot.SerializeException e) {
            Log.w(TAG, "存档损坏，放弃恢复", e);
            return false;
        }
        aiHelper.cancelPending();
        aiHelper.setDifficulty(Math.max(MIN_DIFFICULTY,
                Math.min(MAX_DIFFICULTY, snapshot.difficulty)));
        difficulty = aiHelper.getDifficulty();
        playedHistory.clear();
        currentHints = null;
        hintIndex = 0;
        gameOverHandled = false;
        aiContractViolations = 0;
        scoreBoard = new ScoreBoard();
        scoreBoard.setBidScore(snapshot.bidScore > 0 ? snapshot.bidScore : 1);
        for (int i = 0; i < snapshot.bombCount; i++) {
            scoreBoard.registerBomb();
        }
        startedAtMs = snapshot.startedAtMs > 0 ? snapshot.startedAtMs
                : System.currentTimeMillis();
        // 重放已出的牌（记牌器口径）：整副牌 − 三家手牌 − 底牌 = 已出/桌面牌
        playedHistory.addAll(rebuildPlayedHistory(snapshot));

        stateManager.resetGameState();
        stateManager.restoreFrom(snapshot);
        return true;
    }

    /**
     * 整副牌减去快照手牌，重建"已出过的牌"列表（记牌器恢复用）。
     *
     * <p>地主已定时底牌已在地主手牌内（不可重复扣减）；叫分阶段手牌 51 张、
     * 底牌独立，需一并扣减。桌面当前一手已不在任何手牌中，自然计入结果。</p>
     */
    private List<Card> rebuildPlayedHistory(DoudizhuSnapshot snapshot) {
        List<Card> remaining = new ArrayList<>();
        for (List<Card> hand : snapshot.hands) {
            remaining.addAll(hand);
        }
        if (snapshot.landlordSeat < 0) {
            remaining.addAll(snapshot.bottomCards);
        }
        List<Card> played = new ArrayList<>();
        for (Card card : Card.createFullDeck()) {
            // createFullDeck 每张都是新实例，equals 按花色+牌值比较，remove 即逻辑删牌
            if (!remaining.remove(card)) {
                played.add(card);
            }
        }
        return played;
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
        // P4：玩家"不出"在台面中央回显
        view.setPlayerPassed(passed[Seats.SEAT_PLAYER]);

        int[] status = new int[Seats.TOTAL_SEATS];
        if (landlordKnown) {
            int landlord = stateManager.getLandlordIndex();
            for (int i = 0; i < Seats.TOTAL_SEATS; i++) {
                status[i] = (i == landlord) ? 2 : 1;
            }
        }
        // P4 中英混排修正：身份标签改由视图层按 landlordStatus + 资源组装，
        // 控制器不再下发硬编码中文文本（原 setPlayerLabels 入口已废弃）
        view.setAllLandlordStatus(status);
        view.setGamePhase(stateManager.getGameState(),
                stateManager.getGameState() == DouDiZhuGameStateManager.STATE_LOBBY
                        ? -1 : stateManager.getCurrentTurn());
        view.setCurrentTurn(stateManager.getCurrentTurn());
        view.setCardCounterCounts(remainingCounter());
    }

    /** 当前最高叫分（0=无人叫），供桌面 HUD 显示叫分阶段信息。 */
    public int getHighestBid() {
        return stateManager.getHighestBid();
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
