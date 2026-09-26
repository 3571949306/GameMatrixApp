
package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.save.DoudizhuSnapshot;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主游戏状态管理器。
 *
 * <p>集中管理斗地主一局游戏的全部状态数据，包括游戏阶段、当前回合、地主索引、
 * 各座位的手牌/出牌/不出状态等。提供状态变更方法并通过 {@link GameStateListener}
 * 回调通知外部观察者。</p>
 *
 * <p>你可以把这个类想象成"记分板"——它记录着一局牌的所有关键信息：
 * 谁是地主、轮到谁出牌、每个人出了什么牌、谁选择不出等等。
 * 当记分板上的信息变化时，它会通知所有关注它的人（观察者模式）。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用观察者模式（{@link GameStateListener}），将状态变更与 UI/网络逻辑解耦
 *       （就像记分板变化时自动通知观众，而不需要观众一直盯着看）</li>
 *   <li>三个座位（0=玩家，1=左边，2=右边）的手牌和出牌分别存储，便于独立管理</li>
 *   <li>叫地主升级为 1/2/3 分叫分制（P3）：叫分最高者成为地主；本轮无人叫分时
 *       通过 {@code onBidRoundPassed} 通知控制器重新发牌或保底开局（上限/兜底在控制器）</li>
 *   <li>桌面清理条件：除最后出牌者外其他玩家都"不出"，此时清空桌面并给予自由出牌权</li>
 *   <li>支持快照捕获/恢复（{@link #captureSnapshot()}/{@link #restoreFrom}），
 *       供进程被杀后恢复对局</li>
 * </ul>
 */
public class DouDiZhuGameStateManager {

    /** 游戏状态常量：大厅/等待中 */
    public static final int STATE_LOBBY = 0;
    /** 游戏状态常量：叫地主阶段 */
    public static final int STATE_BIDDING = 1;
    /** 游戏状态常量：出牌阶段 */
    public static final int STATE_PLAYING = 2;
    /** 游戏状态常量：游戏结束 */
    public static final int STATE_GAME_OVER = 3;

    /** 当前游戏阶段 */
    private int gameState = STATE_LOBBY;
    /** 当前轮到的座位索引（0/1/2） */
    private int currentTurn = 0;
    /** 地主的座位索引，-1 表示尚未确定 */
    private int landlordIndex = -1;
    /** 赢家的座位索引，-1 表示尚未确定 */
    private int winnerIndex = -1;
    /** 最后出牌的玩家座位索引，-1 表示尚无人出牌 */
    private int lastPlayerWhoPlayed = -1;
    /** 当前叫地主的轮次对应的座位索引 */
    private int bidTurn = 0;
    /** 当前最高叫分（0=无人叫，1~3 为有效叫分） */
    private int highestBid = 0;
    /** 当前最高叫分的座位索引，-1 表示尚无人叫分 */
    private int highestBidSeat = -1;
    /** 本轮叫分已进行的人数（每人最多叫一次，0~3） */
    private int bidPlacedCount = 0;
    /** 全员不叫导致的重新发牌次数 */
    private int redealCount = 0;
    /** 最终叫分（1~3），地主确定后写入；未确定为 0 */
    private int bidScore = 0;
    /** 各座位累计出牌手数（春天/反春判定用） */
    private int[] playCounts = new int[3];
    /** 各座位是否"不出"的标记数组 */
    private boolean[] playerPassed = new boolean[]{false, false, false};

    /** 座位 0（玩家）的手牌 */
    private List<Card> playerHandCards = new ArrayList<>();
    /** 座位 1 的手牌 */
    private List<Card> seat1Cards = new ArrayList<>();
    /** 座位 2 的手牌 */
    private List<Card> seat2Cards = new ArrayList<>();
    /** 底牌（三张） */
    private List<Card> bottomCards = new ArrayList<>();
    /** 座位 0 已出的牌 */
    private List<Card> playerPlayedCards = new ArrayList<>();
    /** 座位 1 已出的牌 */
    private List<Card> seat1PlayedCards = new ArrayList<>();
    /** 座位 2 已出的牌 */
    private List<Card> seat2PlayedCards = new ArrayList<>();
    /** 各座位的剩余手牌数 */
    private int[] handCounts = new int[]{17, 17, 17};

    /**
     * 游戏状态变更监听器。
     *
     * <p>当游戏状态发生关键变更时（阶段切换、回合变更、叫分流过、地主确定、游戏结束），
     * 通过此接口通知外部观察者。</p>
     */
    public interface GameStateListener {
        /** 游戏阶段变更时回调 */
        void onStateChanged(int newState);
        /** 当前回合变更时回调 */
        void onTurnChanged(int newTurn);
        /** 本轮叫分全部流过（无人叫分），控制器应决定重发或保底 */
        void onBidRoundPassed();
        /** 地主确定时回调 */
        void onLandlordSet(int landlordIndex);
        /** 游戏结束时回调 */
        void onGameOver(int winnerIndex);
    }

    /** 状态变更监听器实例 */
    private GameStateListener listener;

    /**
     * 设置游戏状态监听器。
     *
     * @param listener 状态变更监听器
     */
    public void setListener(GameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 获取当前游戏阶段。
     *
     * @return 游戏状态常量（STATE_LOBBY/STATE_BIDDING/STATE_PLAYING/STATE_GAME_OVER）
     */
    public int getGameState() {
        return gameState;
    }

    /**
     * 获取当前轮到的座位索引。
     *
     * @return 座位索引（0/1/2）
     */
    public int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * 获取地主的座位索引。
     *
     * @return 地主座位索引，-1 表示尚未确定
     */
    public int getLandlordIndex() {
        return landlordIndex;
    }

    /**
     * 获取赢家的座位索引。
     *
     * @return 赢家座位索引，-1 表示尚未确定
     */
    public int getWinnerIndex() {
        return winnerIndex;
    }

    /**
     * 获取最后出牌的玩家座位索引。
     *
     * @return 座位索引，-1 表示尚无人出牌
     */
    public int getLastPlayerWhoPlayed() {
        return lastPlayerWhoPlayed;
    }

    /**
     * 获取当前叫地主轮次对应的座位索引。
     *
     * @return 座位索引
     */
    public int getBidTurn() {
        return bidTurn;
    }

    /**
     * 获取当前最高叫分。
     *
     * @return 最高叫分（0=无人叫，1~3）
     */
    public int getHighestBid() {
        return highestBid;
    }

    /**
     * 获取当前最高叫分的座位索引。
     *
     * @return 座位索引，-1 表示尚无人叫分
     */
    public int getHighestBidSeat() {
        return highestBidSeat;
    }

    /**
     * 获取本轮叫分已进行的人数。
     *
     * @return 已叫分人数（0~3）
     */
    public int getBidPlacedCount() {
        return bidPlacedCount;
    }

    /**
     * 获取最终叫分（叫分倍数）。
     *
     * @return 叫分 1~3；地主尚未确定为 0
     */
    public int getBidScore() {
        return bidScore;
    }

    /**
     * 获取全员不叫导致的重新发牌次数。
     *
     * @return 重新发牌次数
     */
    public int getRedealCount() {
        return redealCount;
    }

    /**
     * 获取各座位累计出牌手数（春天/反春判定用）。
     *
     * @return 长度 3 的手数数组
     */
    public int[] getPlayCounts() {
        return playCounts;
    }

    /**
     * 获取各座位是否"不出"的标记数组。
     *
     * @return 布尔数组，索引对应座位号
     */
    public boolean[] getPlayerPassed() {
        return playerPassed;
    }

    /**
     * 获取座位 0（玩家）的手牌。
     *
     * @return 手牌列表
     */
    public List<Card> getPlayerHandCards() {
        return playerHandCards;
    }

    /**
     * 获取座位 1 的手牌。
     *
     * @return 手牌列表
     */
    public List<Card> getSeat1Cards() {
        return seat1Cards;
    }

    /**
     * 获取座位 2 的手牌。
     *
     * @return 手牌列表
     */
    public List<Card> getSeat2Cards() {
        return seat2Cards;
    }

    /**
     * 获取底牌。
     *
     * @return 底牌列表（三张）
     */
    public List<Card> getBottomCards() {
        return bottomCards;
    }

    /**
     * 获取座位 0 已出的牌。
     *
     * @return 已出的牌列表
     */
    public List<Card> getPlayerPlayedCards() {
        return playerPlayedCards;
    }

    /**
     * 获取座位 1 已出的牌。
     *
     * @return 已出的牌列表
     */
    public List<Card> getSeat1PlayedCards() {
        return seat1PlayedCards;
    }

    /**
     * 获取座位 2 已出的牌。
     *
     * @return 已出的牌列表
     */
    public List<Card> getSeat2PlayedCards() {
        return seat2PlayedCards;
    }

    /**
     * 获取各座位的剩余手牌数。
     *
     * @return 手牌数数组，索引对应座位号
     */
    public int[] getHandCounts() {
        return handCounts;
    }

    /**
     * 开始新一局游戏。
     *
     * <p>执行洗牌发牌，重置所有游戏状态，随机选择起始叫地主玩家，
     * 并通知监听器状态变更。</p>
     */
    public void startGame() {
        gameState = STATE_BIDDING;
        winnerIndex = -1;
        playerPassed = new boolean[]{false, false, false};
        highestBid = 0;
        highestBidSeat = -1;
        bidPlacedCount = 0;
        bidScore = 0;
        playCounts = new int[3];

        // 洗牌发牌：返回四个列表，前三个为各座位手牌，第四个为底牌
        List<Card>[] dealt = GameRuleUtil.shuffleAndDeal();
        playerHandCards = dealt[0];
        seat1Cards = dealt[1];
        seat2Cards = dealt[2];
        bottomCards = dealt[3];

        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
        handCounts = new int[]{playerHandCards.size(), seat1Cards.size(), seat2Cards.size()};

        // 随机选择起始叫地主的玩家
        currentTurn = (int) (Math.random() * 3);
        bidTurn = currentTurn;

        if (listener != null) {
            listener.onStateChanged(gameState);
            listener.onTurnChanged(currentTurn);
        }
    }

    /**
     * 记录一次叫分（P3 叫分制）。
     *
     * <p>有效叫分（高于当前最高分）时更新最高分与最高分座位；叫 3 分（顶格）时
     * 立即定地主（标准规则：顶格叫分后后手无需再表态），否则在叫分人数达到 3 人时
     * 定地主（最高分者）并进入出牌阶段。
     * 由控制器先行校验 {@code BidPolicy.isValidBid} 后调用。</p>
     *
     * @param seatIndex 叫分座位
     * @param bid       叫分（1~3，已保证高于当前最高分）
     */
    public void recordBid(int seatIndex, int bid) {
        highestBid = bid;
        highestBidSeat = seatIndex;
        bidPlacedCount++;
        if (bid >= 3) {
            // 顶格叫分：立即定地主，不再轮转（P3 复查项 E）
            finalizeLandlord(seatIndex);
            return;
        }
        if (bidPlacedCount >= 3) {
            finalizeLandlord(highestBidSeat);
        } else {
            advanceBidTurn();
        }
    }

    /**
     * 叫分流过（不叫/叫分无效）。
     *
     * <p>本轮 3 人都已表态：有人叫过分 → 最高分者立即定地主；
     * 全部流过 → 通知控制器重新发牌（控制器实现上限与保底逻辑）。
     * 未满 3 人则轮转到下一个玩家。</p>
     */
    public void passBid() {
        bidPlacedCount++;
        if (bidPlacedCount >= 3) {
            if (highestBid > 0) {
                finalizeLandlord(highestBidSeat);
            } else if (listener != null) {
                listener.onBidRoundPassed();
            }
            return;
        }
        advanceBidTurn();
    }

    /**
     * 全员不叫后的重新发牌入口（控制器依上限决定调用）。
     */
    public void redeal() {
        redealCount++;
        startGame();
    }

    /**
     * 保底强制开局（重新发牌达上限后）：起始叫分座位强制以 1 分叫地主。
     *
     * @param seatIndex 强制成为地主的座位
     */
    public void forceStartWithBid(int seatIndex) {
        highestBid = 1;
        highestBidSeat = seatIndex;
        finalizeLandlord(seatIndex);
    }

    /**
     * 推进叫分轮转到下一个玩家。
     */
    private void advanceBidTurn() {
        currentTurn = (currentTurn + 1) % 3;
        bidTurn = currentTurn;

        if (listener != null) {
            listener.onTurnChanged(currentTurn);
        }
    }

    /**
     * 定地主：写入最终叫分、把底牌交给地主并进入出牌阶段。
     *
     * @param seatIndex 地主座位
     */
    private void finalizeLandlord(int seatIndex) {
        bidScore = Math.max(1, highestBid);
        setLandlord(seatIndex);
        startPlayingPhase();
    }

    /**
     * 设置地主。
     *
     * <p>将底牌加入地主手牌，并按权重升序排序。更新手牌计数。</p>
     *
     * @param seatIndex 地主的座位索引
     */
    public void setLandlord(int seatIndex) {
        landlordIndex = seatIndex;
        List<Card> hand = getSeatHandCards(seatIndex);
        // 地主获得底牌
        hand.addAll(bottomCards);
        GameRuleUtil.sortCardsByWeightAscending(hand);
        handCounts[seatIndex] = hand.size();

        if (listener != null) {
            listener.onLandlordSet(landlordIndex);
        }
    }

    /**
     * 进入出牌阶段。
     *
     * <p>由地主先出牌，清空所有已出的牌和"不出"标记。</p>
     */
    public void startPlayingPhase() {
        gameState = STATE_PLAYING;
        currentTurn = landlordIndex;
        lastPlayerWhoPlayed = -1;
        playerPassed = new boolean[]{false, false, false};

        clearAllPlayedCards();

        if (listener != null) {
            listener.onStateChanged(gameState);
            listener.onTurnChanged(currentTurn);
        }
    }

    /**
     * 执行出牌操作。
     *
     * <p>从手牌中移除已出的牌，更新出牌记录和"不出"状态。
     * 如果出牌后手牌为空，则判定该玩家获胜；否则轮转到下一个玩家。</p>
     *
     * @param seatIndex 出牌的座位索引
     * @param cards 出的牌列表
     */
    public void executePlay(int seatIndex, List<Card> cards) {
        List<Card> hand = getSeatHandCards(seatIndex);
        for (Card card : cards) {
            hand.remove(card);
        }
        handCounts[seatIndex] = hand.size();
        // 累计出牌手数（春天/反春判定：农民全程未出牌 / 地主只出过首发）
        if (seatIndex >= 0 && seatIndex < 3) {
            playCounts[seatIndex]++;
        }

        // 有人出牌后，重置所有"不出"标记
        playerPassed = new boolean[]{false, false, false};
        lastPlayerWhoPlayed = seatIndex;
        setSeatPlayedCards(seatIndex, new ArrayList<>(cards));
        // 清空其他座位的上一手出牌：牌面只显示"当前这一手"，
        // 否则上一位玩家的旧牌会跨轮残留显示
        if (seatIndex != 0) playerPlayedCards = new ArrayList<>();
        if (seatIndex != 1) seat1PlayedCards = new ArrayList<>();
        if (seatIndex != 2) seat2PlayedCards = new ArrayList<>();

        // 手牌出完，该玩家获胜
        if (hand.isEmpty()) {
            checkGameOver(seatIndex);
            return;
        }

        switchToNextPlayer();
    }

    /**
     * 轮转到下一个玩家。
     *
     * <p>按座位顺序循环（0→1→2→0），并通知监听器回合变更。</p>
     */
    public void switchToNextPlayer() {
        currentTurn = (currentTurn + 1) % 3;
        if (listener != null) {
            listener.onTurnChanged(currentTurn);
        }
    }

    /**
     * 检查并清理桌面。
     *
     * <p>当除最后出牌者外的所有玩家都"不出"时，清空桌面上的出牌记录，
     * 并将出牌权交还给最后出牌的玩家（获得自由出牌权）。</p>
     *
     * @return true 表示桌面已清理，false 表示尚未满足清理条件
     */
    public boolean checkAndClearTable() {
        if (!shouldClearTable(playerPassed, lastPlayerWhoPlayed, 3)) return false;
        clearAllPlayedCards();
        playerPassed = new boolean[]{false, false, false};
        // 最后出牌者获得自由出牌权
        currentTurn = lastPlayerWhoPlayed;
        if (listener != null) {
            listener.onTurnChanged(currentTurn);
        }
        return true;
    }

    /**
     * 判断是否满足桌面清理条件。
     *
     * <p>当除最后出牌者外的所有玩家都选择了"不出"时，应清理桌面。</p>
     *
     * @param playerPassed 各座位是否"不出"的布尔数组
     * @param lastPlayerWhoPlayed 最后出牌者的座位索引
     * @param totalSeats 总座位数
     * @return true 表示满足清理条件
     */
    private boolean shouldClearTable(boolean[] playerPassed, int lastPlayerWhoPlayed, int totalSeats) {
        if (lastPlayerWhoPlayed < 0) return false;
        int passes = 0;
        for (boolean passed : playerPassed) {
            if (passed) passes++;
        }
        // 其他所有玩家都"不出"
        return passes == totalSeats - 1;
    }

    /**
     * 检查游戏是否结束。
     *
     * <p>当某位玩家手牌出完时，该玩家即为赢家，游戏进入结束状态。</p>
     *
     * @param winnerIndex 赢家的座位索引
     */
    public void checkGameOver(int winnerIndex) {
        gameState = STATE_GAME_OVER;
        this.winnerIndex = winnerIndex;
        if (listener != null) {
            listener.onStateChanged(gameState);
            listener.onGameOver(winnerIndex);
        }
    }

    /**
     * 重置所有游戏状态到初始值。
     *
     * <p>清空所有手牌、出牌、底牌，重置回合和状态标记，
     * 回到大厅状态。</p>
     */
    public void resetGameState() {
        gameState = STATE_LOBBY;
        currentTurn = 0;
        landlordIndex = -1;
        winnerIndex = -1;
        lastPlayerWhoPlayed = -1;
        bidTurn = 0;
        highestBid = 0;
        highestBidSeat = -1;
        bidPlacedCount = 0;
        redealCount = 0;
        bidScore = 0;
        playCounts = new int[3];
        playerPassed = new boolean[]{false, false, false};
        playerHandCards = new ArrayList<>();
        seat1Cards = new ArrayList<>();
        seat2Cards = new ArrayList<>();
        bottomCards = new ArrayList<>();
        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
        handCounts = new int[]{17, 17, 17};

        if (listener != null) {
            listener.onStateChanged(gameState);
        }
    }

    /**
     * 根据座位索引获取对应的手牌列表。
     *
     * @param seatIndex 座位索引（0/1/2）
     * @return 对应座位的手牌列表，无效索引返回空列表
     */
    private List<Card> getSeatHandCards(int seatIndex) {
        switch (seatIndex) {
            case 0: return playerHandCards;
            case 1: return seat1Cards;
            case 2: return seat2Cards;
            default: return new ArrayList<>();
        }
    }

    /**
     * 设置指定座位已出的牌。
     *
     * @param seatIndex 座位索引（0/1/2）
     * @param cards 已出的牌列表
     */
    private void setSeatPlayedCards(int seatIndex, List<Card> cards) {
        switch (seatIndex) {
            case 0: playerPlayedCards = cards; break;
            case 1: seat1PlayedCards = cards; break;
            case 2: seat2PlayedCards = cards; break;
        }
    }

    /**
     * 清空所有座位已出的牌。
     */
    private void clearAllPlayedCards() {
        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
    }

    /**
     * 获取当前桌面上最后出的牌。
     *
     * <p>用于判断下家出牌时是否需要压过上家。如果最后出牌者已"不出"
     * 或尚无人出牌，则返回 null（表示自由出牌）。</p>
     *
     * @return 最后出的牌列表，null 表示自由出牌
     */
    public List<Card> getLastPlayedCards() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= 3) return null;
        if (playerPassed[lastPlayerWhoPlayed]) return null;
        List<Card> played = getSeatPlayedCards(lastPlayerWhoPlayed);
        if (played == null || played.isEmpty()) return null;
        return played;
    }

    /**
     * 根据座位索引获取对应的已出牌列表。
     *
     * @param seatIndex 座位索引（0/1/2）
     * @return 已出的牌列表
     */
    private List<Card> getSeatPlayedCards(int seatIndex) {
        switch (seatIndex) {
            case 0: return playerPlayedCards;
            case 1: return seat1PlayedCards;
            case 2: return seat2PlayedCards;
            default: return new ArrayList<>();
        }
    }

    /**
     * 设置指定座位的"不出"状态。
     *
     * @param seatIndex 座位索引
     * @param passed true 表示该玩家选择"不出"
     */
    public void setPlayerPassed(int seatIndex, boolean passed) {
        playerPassed[seatIndex] = passed;
    }

    /**
     * 清空指定座位已出的牌。
     *
     * @param seatIndex 座位索引
     */
    public void clearSeatPlayedCards(int seatIndex) {
        setSeatPlayedCards(seatIndex, new ArrayList<>());
    }

    // ============ 存档快照（P3） ============

    /**
     * 捕获当前对局状态快照（手牌/底牌/叫分/回合/桌面牌/"不出"标记）。
     *
     * <p>倍数因子（炸弹数）、难度、开始时间等控制器侧状态由控制器补齐后一并持久化。</p>
     *
     * @return 快照对象；大厅态返回 null
     */
    public DoudizhuSnapshot captureSnapshot() {
        if (gameState == STATE_LOBBY) return null;
        DoudizhuSnapshot s = new DoudizhuSnapshot();
        s.phase = gameState;
        s.currentTurn = currentTurn;
        s.landlordSeat = landlordIndex;
        s.highestBid = highestBid;
        s.bidPlacedCount = bidPlacedCount;
        s.redealCount = redealCount;
        s.bidScore = bidScore;
        s.playCounts = new int[]{playCounts[0], playCounts[1], playCounts[2]};
        s.passed = new boolean[]{playerPassed[0], playerPassed[1], playerPassed[2]};
        s.hands = new List[]{
                new ArrayList<>(playerHandCards),
                new ArrayList<>(seat1Cards),
                new ArrayList<>(seat2Cards)};
        s.bottomCards = new ArrayList<>(bottomCards);
        List<Card> last = getLastPlayedCards();
        s.lastPlayed = last == null ? null : new ArrayList<>(last);
        s.lastPlayerSeat = last == null ? -1 : lastPlayerWhoPlayed;
        return s;
    }

    /**
     * 从快照恢复对局状态（进程被杀后重进恢复）。
     *
     * <p>直接写入各字段并回调状态/回合变更，不重复发牌、不重复加底牌
     * （快照中的地主手牌已包含底牌）。快照数据由调用方先行反序列化校验。</p>
     *
     * @param snapshot 已反序列化的快照
     */
    public void restoreFrom(DoudizhuSnapshot snapshot) {
        gameState = snapshot.phase;
        currentTurn = snapshot.currentTurn;
        landlordIndex = snapshot.landlordSeat;
        highestBid = snapshot.highestBid;
        bidPlacedCount = snapshot.bidPlacedCount;
        redealCount = snapshot.redealCount;
        bidScore = snapshot.bidScore;
        playCounts = new int[]{snapshot.playCounts[0], snapshot.playCounts[1],
                snapshot.playCounts[2]};
        playerPassed = new boolean[]{snapshot.passed[0], snapshot.passed[1], snapshot.passed[2]};

        playerHandCards = new ArrayList<>(snapshot.hands[0]);
        seat1Cards = new ArrayList<>(snapshot.hands[1]);
        seat2Cards = new ArrayList<>(snapshot.hands[2]);
        bottomCards = new ArrayList<>(snapshot.bottomCards);
        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
        handCounts = new int[]{
                playerHandCards.size(), seat1Cards.size(), seat2Cards.size()};
        winnerIndex = -1;

        // 恢复桌面当前一手：该手牌已从出牌者手牌中移除，必须连同出牌者一起还原，
        // 否则恢复后该玩家的这些牌会凭空丢失
        if (snapshot.lastPlayed != null && !snapshot.lastPlayed.isEmpty()
                && snapshot.lastPlayerSeat >= 0 && snapshot.lastPlayerSeat < 3) {
            setSeatPlayedCards(snapshot.lastPlayerSeat, new ArrayList<>(snapshot.lastPlayed));
            lastPlayerWhoPlayed = snapshot.lastPlayerSeat;
        } else {
            lastPlayerWhoPlayed = -1;
        }

        if (listener != null) {
            listener.onStateChanged(gameState);
            listener.onTurnChanged(currentTurn);
        }
    }

    // ============ 测试支撑 ============

    /**
     * 把叫分起始回合拨到指定座位（仅测试用：消除随机起始座位的不确定性）。
     *
     * @param seat 指定座位（0/1/2）
     */
    public void restoreBidTurnForTest(int seat) {
        if (gameState != STATE_BIDDING) return;
        currentTurn = Math.max(0, Math.min(2, seat));
        bidTurn = currentTurn;
    }
}
