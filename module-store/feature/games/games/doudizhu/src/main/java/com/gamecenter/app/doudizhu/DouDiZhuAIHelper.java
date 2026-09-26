package com.gamecenter.app.doudizhu;

import android.os.Handler;
import android.util.Log;

import com.gamecenter.app.doudizhu.ai.AiBrain;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.score.BidPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 斗地主 AI 辅助类。
 *
 * <p>负责 AI 玩家的叫地主决策和出牌逻辑调度。通过 {@link AICallback} 回调接口
 * 与对局控制器交互，获取游戏状态并通知出牌/不出/叫地主结果。</p>
 *
 * <p>你可以把这个类想象成AI的"经纪人"——它不亲自做决策（决策交给{@link AiBrain}），
 * 但负责安排AI什么时候行动、延迟多久（模拟思考），以及把AI的决策结果告诉控制器。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>AI 操作通过 Handler 延迟执行（按难度差异化随机延迟，简单档更快、
 *       困难档略慢，见 {@link AiBrain#thinkingDelayMs}），模拟思考时间，提升体验</li>
 *   <li>采用回调模式而非直接持有控制器引用，降低耦合度</li>
 *   <li>叫分决策（P3 叫分制）：手牌评估为 0-3 分意向档位
 *       （{@link AiBrain#evaluateBidScore}），经 {@link BidPolicy#decideAiBid}
 *       与当前最高叫分比较后输出 1/2/3 分或不叫；简单档保守叫分</li>
 *   <li>出牌决策委托给 {@link AiBrain#decidePlay}
 *       （经纪人只负责调度，具体策略交给"军师"）</li>
 *   <li>支持取消待执行的 AI 操作，防止控制器销毁后仍触发回调</li>
 * </ul>
 */
public class DouDiZhuAIHelper {

    private static final String TAG = "DouDiZhuAI";

    /**
     * AI 思考延迟的兜底值（P5 前的固定值，仅保留作兼容常量；
     * 实际调度延迟经 {@link AiBrain#thinkingDelayMs} 按难度差异化）。
     */
    public static final long AI_THINKING_DELAY = 1500L;

    private static final int STATE_BIDDING = 1;
    private static final int STATE_PLAYING = 2;

    /** 主线程 Handler，用于延迟调度 AI 操作 */
    private final Handler handler;

    /** 当前待执行的 AI 思考任务，用于取消操作 */
    private Runnable aiThinkingRunnable;

    /**
     * 难度档位（0=简单 1=普通 2=困难），由对局控制器按用户选择注入。
     * 具体打法差异见 {@link AiBrain}。
     */
    private volatile int difficulty = AiBrain.DIFFICULTY_NORMAL;

    /** AI 决策随机源（简单档随机放弃使用；生产用默认种子，测试可注入固定种子） */
    private final Random random = new Random();

    /** AI 操作回调接口，由对局控制器实现 */
    private final AICallback callback;

    /**
     * AI 操作回调接口。
     *
     * <p>对局控制器需实现此接口，提供游戏状态查询方法和 AI 操作结果通知方法。</p>
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
        /**
         * 本局全部已出的牌（含被桌面清理的轮次），供困难档记牌器使用；
         * 返回副本，null 表示无数据。
         */
        default List<Card> getPlayedHistory() { return new ArrayList<>(); }
        /** AI 决定出牌时回调 */
        void onAIPlay(int seatIndex, List<Card> cards);
        /** AI 决定不出时回调 */
        void onAIPass(int seatIndex);
        /**
         * AI 叫分决策回调（P3 叫分制）：score 为 1/2/3 三档叫分，0 表示不叫。
         */
        void onAIBid(int score);
        /** 获取当前最高叫分（无人叫时为 0），AI 加叫决策用 */
        default int getCurrentHighestBid() { return 0; }
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
     * 注入难度档位（0=简单 1=普通 2=困难，非法值收敛到该范围）。
     *
     * @param difficulty 难度档位
     */
    public void setDifficulty(int difficulty) {
        this.difficulty = Math.max(AiBrain.DIFFICULTY_EASY,
                Math.min(AiBrain.DIFFICULTY_HARD, difficulty));
    }

    /**
     * 获取当前难度档位。
     */
    public int getDifficulty() {
        return difficulty;
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
     * <p>先取消之前待执行的 AI 任务，再按当前难度取一次随机思考延迟
     * （{@link AiBrain#thinkingDelayMs}，P5：简单档更快、困难档略慢）后执行，
     * 模拟 AI 思考过程。</p>
     */
    public void scheduleAITurn() {
        cancelPending();
        aiThinkingRunnable = this::executeAITurn;
        handler.postDelayed(aiThinkingRunnable, nextThinkingDelay());
    }

    /**
     * 调度 AI 叫地主操作。
     *
     * <p>与 {@link #scheduleAITurn()} 类似，按难度差异延迟执行 AI 叫地主决策。</p>
     */
    public void scheduleAIBid() {
        cancelPending();
        aiThinkingRunnable = this::executeAIBid;
        handler.postDelayed(aiThinkingRunnable, nextThinkingDelay());
    }

    /** 本次 AI 行动的思考延迟（按难度档位随机，见 {@link AiBrain#thinkingDelayMs}） */
    private long nextThinkingDelay() {
        return AiBrain.thinkingDelayMs(difficulty, random);
    }

    /**
     * 执行 AI 出牌逻辑。
     *
     * <p>先校验游戏状态和当前回合，然后委托 {@link AiBrain#decidePlay} 决定出牌策略。
     * 如果 AI 决定出牌则回调 {@link AICallback#onAIPlay}，否则回调 {@link AICallback#onAIPass}。</p>
     */
    void executeAITurn() {
        // 双重校验：游戏状态必须是出牌阶段，且当前轮到 AI
        if (callback.getGameState() != STATE_PLAYING) return;
        if (!isAITurn()) return;

        int seatIndex = callback.getCurrentTurn();
        List<Card> aiHand = callback.getSeatHandCards(seatIndex);
        List<Card> previousCards = callback.getLastPlayedCards();

        // 委托 AiBrain 进行出牌决策（带难度档位与记牌器数据）
        AiBrain.GameContext context = buildGameContext(seatIndex);
        List<Card> playedCards = AiBrain.decidePlay(aiHand, previousCards, context,
                difficulty, random, callback.getPlayedHistory());

        if (playedCards != null && !playedCards.isEmpty()) {
            callback.onAIPlay(seatIndex, playedCards);
        } else {
            // AI 无法压过上家或选择不出
            callback.onAIPass(seatIndex);
        }
    }

    private AiBrain.GameContext buildGameContext(int seatIndex) {
        int[] roles = new int[]{AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER};
        int landlordSeat = callback.getLandlordSeat();
        for (int i = 0; i < roles.length; i++) {
            int status = callback.getLandlordStatusForAISeat(i);
            roles[i] = (status == 2 || i == landlordSeat) ? AiBrain.ROLE_LANDLORD
                    : AiBrain.ROLE_FARMER;
        }

        int myRole = roles[Math.max(0, Math.min(seatIndex, roles.length - 1))];
        int teammateSeat = -1;
        int teammateRemainCards = -1;
        if (myRole == AiBrain.ROLE_FARMER) {
            for (int i = 0; i < roles.length; i++) {
                if (i != seatIndex && roles[i] == AiBrain.ROLE_FARMER) {
                    teammateSeat = i;
                    teammateRemainCards = callback.getSeatRemainingCardCount(i);
                    break;
                }
            }
        }

        int landlordRemainCards = landlordSeat >= 0 ? callback.getSeatRemainingCardCount(landlordSeat) : 17;
        int nextSeat = (seatIndex + 1) % roles.length;
        // 敌方最少剩牌：农民看地主；地主看两个农民中较少者
        int enemyRemainMin = landlordRemainCards;
        if (myRole == AiBrain.ROLE_LANDLORD) {
            enemyRemainMin = Integer.MAX_VALUE;
            for (int i = 0; i < roles.length; i++) {
                if (roles[i] == AiBrain.ROLE_FARMER) {
                    enemyRemainMin = Math.min(enemyRemainMin,
                            callback.getSeatRemainingCardCount(i));
                }
            }
            if (enemyRemainMin == Integer.MAX_VALUE) enemyRemainMin = 17;
        }
        return new AiBrain.GameContext(
                myRole,
                seatIndex,
                roles,
                landlordSeat,
                landlordRemainCards,
                callback.getLastPlayerWhoPlayed(),
                teammateSeat,
                teammateRemainCards,
                callback.getSeatRemainingCardCount(nextSeat),
                enemyRemainMin
        );
    }

    /**
     * 执行 AI 叫分决策（P3 叫分制）。
     *
     * <p>基于手牌评估 {@link AiBrain#evaluateBidScore} 得到 0-3 分意向档位，
     * 再经 {@link BidPolicy#decideAiBid} 与当前最高叫分比较：意向不高于当前
     * 最高分则不叫（流过）；简单档阈值再抬一档（保守叫分，对齐 P2 难度差异约定）。</p>
     */
    private void executeAIBid() {
        // 双重校验：游戏状态必须是叫地主阶段，且当前轮到 AI
        if (callback.getGameState() != STATE_BIDDING) return;
        if (!isAITurn()) return;

        int seatIndex = callback.getCurrentTurn();
        List<Card> aiHand = callback.getSeatHandCards(seatIndex);
        int intent = AiBrain.evaluateBidScore(aiHand);
        int bid = BidPolicy.decideAiBid(intent, callback.getCurrentHighestBid(),
                difficulty == AiBrain.DIFFICULTY_EASY);
        callback.onAIBid(bid);
    }

    /**
     * 取消待执行的 AI 操作。
     *
     * <p>在切换玩家、控制器销毁或重新调度时调用，
     * 防止过期的 AI 操作被执行。</p>
     */
    public void cancelPending() {
        if (aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
            aiThinkingRunnable = null;
        }
    }

    /**
     * 当前挂起的 AI 任务（仅供 JVM 单测：单测中 Handler 为桩、postDelayed 不执行，
     * 测试取到任务后手动运行即可走真实 AI 决策链路）。
     */
    Runnable peekPendingForTest() {
        return aiThinkingRunnable;
    }

    /**
     * 评估手牌叫分意向档位（0-3）的静态便捷方法（P3 叫分制）。
     *
     * @param handCards 手牌列表
     * @return 意向档位 0~3（0 表示不建议叫地主）
     */
    public static int evaluateBidIntent(List<Card> handCards) {
        return AiBrain.evaluateBidScore(handCards);
    }
}
