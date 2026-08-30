package com.gamecenter.app.doudizhu;

import android.os.Handler;
import android.util.Log;

import com.gamecenter.app.doudizhu.model.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主 AI 辅助类。
 *
 * <p>负责 AI 玩家的叫地主决策和出牌逻辑调度。通过 {@link AICallback} 回调接口
 * 与 Activity 交互，获取游戏状态并通知出牌/不出/叫地主结果。</p>
 *
 * <p>你可以把这个类想象成AI的"经纪人"——它不亲自做决策（决策交给AIBot），
 * 但负责安排AI什么时候行动、延迟多久（模拟思考），以及把AI的决策结果告诉Activity。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>AI 操作通过 Handler 延迟执行（1.5 秒），模拟思考时间，提升用户体验
 *       （让AI看起来像在"想"，而不是瞬间出牌）</li>
 *   <li>采用回调模式而非直接持有 Activity 引用，降低耦合度</li>
 *   <li>叫地主决策委托给 {@link DouDiZhuRuleEngine#shouldCallLandlord}，
 *       出牌决策委托给 {@link AIBot#decidePlay}
 *       （经纪人只负责调度，具体策略交给"军师"）</li>
 *   <li>支持取消待执行的 AI 操作，防止 Activity 销毁后仍触发回调</li>
 * </ul>
 */
public class DouDiZhuAIHelper {

    private static final String TAG = "DouDiZhuAI";

    /** AI 模拟思考的延迟时间（毫秒） */
    public static final long AI_THINKING_DELAY = 1500L;

    private static final int STATE_BIDDING = 1;
    private static final int STATE_PLAYING = 2;

    /** 主线程 Handler，用于延迟调度 AI 操作 */
    private final Handler handler;

    /** 当前待执行的 AI 思考任务，用于取消操作 */
    private Runnable aiThinkingRunnable;

    /**
     * 难度因子（&lt;1.0 弱、=1.0 普通、&gt;1.0 强），由对局控制器按用户选择的难度注入。
     * 影响规则见 {@link AIBot#decidePlay(List, List, AIBot.GameContext, float)}。
     */
    private volatile float difficultyFactor = 1.0f;

    /** AI 操作回调接口，由对局控制器实现 */
    private final AICallback callback;

    /**
     * AI 操作回调接口。
     *
     * <p>Activity 需实现此接口，提供游戏状态查询方法和 AI 操作结果通知方法。</p>
     */
    public interface AICallback {
        /** 获取当前游戏状态 */
        int getGameState();
        /** 获取当前轮到的座位索引 */
        int getCurrentTurn();
        /** 获取各座位的类型数组（HOST/REMOTE/AI） */
        int[] getSeatTypes();
        /** 获取指定座位的手牌 */
        List<Card> getSeatHandCards(int seatIndex);
        /** 获取上家出的牌，null 表示自由出牌 */
        List<Card> getLastPlayedCards();
        default int getLandlordSeat() { return -1; }
        default int getLastPlayerWhoPlayed() { return -1; }
        default int getLandlordStatusForAISeat(int seatIndex) { return 0; }
        default int getSeatRemainingCardCount(int seatIndex) {
            List<Card> handCards = getSeatHandCards(seatIndex);
            return handCards == null ? 0 : handCards.size();
        }
        /** AI 决定出牌时回调 */
        void onAIPlay(int seatIndex, List<Card> cards);
        /** AI 决定不出时回调 */
        void onAIPass(int seatIndex);
        /** AI 叫地主决策回调，call=true 表示叫地主 */
        void onAIBid(boolean call);
    }

    /**
     * 构造 AI 辅助类。
     *
     * @param handler 主线程 Handler，用于延迟调度 AI 操作
     * @param callback AI 操作回调接口的实现
     */
    public DouDiZhuAIHelper(Handler handler, AICallback callback) {
        this.handler = handler;
        this.callback = callback;
    }

    /**
     * 注入难度因子（简单 0.6 / 普通 1.0 / 困难 1.05）。
     *
     * @param factor 难度因子
     */
    public void setDifficultyFactor(float factor) {
        this.difficultyFactor = factor;
    }

    /**
     * 判断当前是否轮到 AI 出牌。
     *
     * @return true 表示当前座位是 AI 类型且轮到该座位操作
     */
    public boolean isAITurn() {
        int currentTurn = callback.getCurrentTurn();
        int[] seatTypes = callback.getSeatTypes();
        return currentTurn >= 0 && currentTurn < seatTypes.length
                && seatTypes[currentTurn] == Seats.TYPE_AI;
    }

    /**
     * 调度 AI 出牌操作。
     *
     * <p>先取消之前待执行的 AI 任务，再延迟 {@link #AI_THINKING_DELAY} 毫秒后执行，
     * 模拟 AI 思考过程。</p>
     */
    public void scheduleAITurn() {
        cancelPending();
        aiThinkingRunnable = this::executeAITurn;
        handler.postDelayed(aiThinkingRunnable, AI_THINKING_DELAY);
    }

    /**
     * 调度 AI 叫地主操作。
     *
     * <p>与 {@link #scheduleAITurn()} 类似，延迟执行 AI 叫地主决策。</p>
     */
    public void scheduleAIBid() {
        cancelPending();
        aiThinkingRunnable = this::executeAIBid;
        handler.postDelayed(aiThinkingRunnable, AI_THINKING_DELAY);
    }

    /**
     * 执行 AI 出牌逻辑。
     *
     * <p>先校验游戏状态和当前回合，然后委托 {@link AIBot#decidePlay} 决定出牌策略。
     * 如果 AI 决定出牌则回调 {@link AICallback#onAIPlay}，否则回调 {@link AICallback#onAIPass}。</p>
     */
    void executeAITurn() {
        // 双重校验：游戏状态必须是出牌阶段，且当前轮到 AI
        if (callback.getGameState() != STATE_PLAYING) return;
        if (!isAITurn()) return;

        int seatIndex = callback.getCurrentTurn();
        List<Card> aiHand = callback.getSeatHandCards(seatIndex);
        List<Card> previousCards = callback.getLastPlayedCards();

        // 委托 AIBot 进行出牌决策（带难度因子）
        AIBot.GameContext context = buildGameContext(seatIndex);
        List<Card> playedCards = AIBot.decidePlay(aiHand, previousCards, context, difficultyFactor);

        if (playedCards != null && !playedCards.isEmpty()) {
            callback.onAIPlay(seatIndex, playedCards);
        } else {
            // AI 无法压过上家或选择不出
            callback.onAIPass(seatIndex);
        }
    }

    private AIBot.GameContext buildGameContext(int seatIndex) {
        int[] roles = new int[]{AIBot.ROLE_FARMER, AIBot.ROLE_FARMER, AIBot.ROLE_FARMER};
        int landlordSeat = callback.getLandlordSeat();
        for (int i = 0; i < roles.length; i++) {
            int status = callback.getLandlordStatusForAISeat(i);
            roles[i] = (status == 2 || i == landlordSeat) ? AIBot.ROLE_LANDLORD : AIBot.ROLE_FARMER;
        }

        int myRole = roles[Math.max(0, Math.min(seatIndex, roles.length - 1))];
        int teammateSeat = -1;
        int teammateRemainCards = -1;
        if (myRole == AIBot.ROLE_FARMER) {
            for (int i = 0; i < roles.length; i++) {
                if (i != seatIndex && roles[i] == AIBot.ROLE_FARMER) {
                    teammateSeat = i;
                    teammateRemainCards = callback.getSeatRemainingCardCount(i);
                    break;
                }
            }
        }

        int landlordRemainCards = landlordSeat >= 0 ? callback.getSeatRemainingCardCount(landlordSeat) : 17;
        int nextSeat = (seatIndex + 1) % roles.length;
        return new AIBot.GameContext(
                myRole,
                seatIndex,
                roles,
                landlordSeat,
                landlordRemainCards,
                callback.getLastPlayerWhoPlayed(),
                teammateSeat,
                teammateRemainCards,
                callback.getSeatRemainingCardCount(nextSeat)
        );
    }

    /**
     * 执行 AI 叫地主决策。
     *
     * <p>基于手牌强度评估，委托 {@link DouDiZhuRuleEngine#shouldCallLandlord} 判断
     * 是否应该叫地主。</p>
     */
    private void executeAIBid() {
        // 双重校验：游戏状态必须是叫地主阶段，且当前轮到 AI
        if (callback.getGameState() != STATE_BIDDING) return;
        if (!isAITurn()) return;

        int seatIndex = callback.getCurrentTurn();
        List<Card> aiHand = callback.getSeatHandCards(seatIndex);
        boolean shouldBid = DouDiZhuRuleEngine.shouldCallLandlord(aiHand);
        callback.onAIBid(shouldBid);
    }

    /**
     * 取消待执行的 AI 操作。
     *
     * <p>在切换玩家、Activity 销毁或重新调度时调用，
     * 防止过期的 AI 操作被执行。</p>
     */
    public void cancelPending() {
        if (aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
            aiThinkingRunnable = null;
        }
    }

    /**
     * 根据座位索引获取 AI 手牌。
     *
     * <p>座位 1 对应 aiBotHand0，座位 2 对应 aiBotHand1，
     * 其他座位返回空列表。</p>
     *
     * @param seatIndex 座位索引（1 或 2）
     * @param aiBotHand0 座位 1 的 AI 手牌
     * @param aiBotHand1 座位 2 的 AI 手牌
     * @return 对应座位的 AI 手牌列表
     */
    public static List<Card> getAIHandCards(int seatIndex, List<Card> aiBotHand0, List<Card> aiBotHand1) {
        if (seatIndex == 1) return aiBotHand0;
        if (seatIndex == 2) return aiBotHand1;
        return new ArrayList<>();
    }

    /**
     * 判断是否应该叫地主的静态便捷方法。
     *
     * <p>委托给 {@link DouDiZhuRuleEngine#shouldCallLandlord}，提供统一的调用入口。</p>
     *
     * @param handCards 手牌列表
     * @return true 表示建议叫地主
     */
    public static boolean shouldCallLandlord(List<Card> handCards) {
        return DouDiZhuRuleEngine.shouldCallLandlord(handCards);
    }
}
