package com.gamecenter.app.games.reaction;

import java.util.Random;

/**
 * 反应力挑战游戏的核心逻辑类
 * <p>
 * 管理游戏状态机（IDLE → WAITING → READY → TAPPED/TOO_SOON），
 * 记录每轮反应时间，计算平均和最佳成绩。
 * <p>
 * 关键设计决策：
 * - 使用状态机模式管理游戏流程，状态转换清晰明确
 * - 等待延迟由外部（Activity 的 Handler）驱动，游戏逻辑本身不持有定时器，便于生命周期管理
 * - 共 5 轮测试，完成后自动重置可重新开始
 */
public class ReactionGame {

    /**
     * 游戏状态枚举
     * <ul>
     *   <li>IDLE - 初始/空闲状态，等待玩家点击开始</li>
     *   <li>WAITING - 等待状态，屏幕变红，等待随机延迟后变绿</li>
     *   <li>READY - 就绪状态，屏幕变绿，等待玩家点击</li>
     *   <li>TAPPED - 已点击状态，记录了反应时间</li>
     *   <li>TOO_SOON - 过早点击状态，玩家在变绿前就点击了</li>
     * </ul>
     */
    public enum State {
        IDLE, WAITING, READY, TAPPED, TOO_SOON
    }

    /**
     * 状态变化监听器接口，用于通知 UI 层更新
     */
    public interface OnStateChangeListener {
        void onStateChanged(State state);
    }

    /** 总测试轮数 */
    private static final int MAX_ROUNDS = 5;
    /** 最小等待延迟（毫秒），变绿前的最短等待时间 */
    private static final long MIN_WAIT_MS = 1000;
    /** 最大等待延迟（毫秒），变绿前的最长等待时间 */
    private static final long MAX_WAIT_MS = 4000;

    private State state;
    /** 已完成的轮次数 */
    private int round;
    /** 变绿时刻的时间戳，用于计算反应时间 */
    private long readyTime;
    /** 玩家点击时刻的时间戳 */
    private long tapTime;
    /** 每轮的反应时间记录（毫秒） */
    private long[] results;
    private final Random random;
    private OnStateChangeListener listener;

    /**
     * 构造函数，初始化随机数生成器和结果数组
     */
    public ReactionGame() {
        random = new Random();
        results = new long[MAX_ROUNDS];
        state = State.IDLE;
        round = 0;
    }

    /**
     * 重置游戏状态，清空所有轮次记录
     */
    public void reset() {
        state = State.IDLE;
        round = 0;
        readyTime = 0;
        tapTime = 0;
        for (int i = 0; i < results.length; i++) results[i] = 0;
    }

    /**
     * 超时回调，由外部 Handler 在等待延迟到达后调用
     * <p>
     * 将状态从 WAITING 切换为 READY，记录变绿时刻。
     * 如果当前不是 WAITING 状态则忽略（防止重复回调）。
     */
    public void onTimeout() {
        if (state != State.WAITING) return;
        state = State.READY;
        readyTime = System.currentTimeMillis();
        notifyListener();
    }

    /**
     * 处理玩家点击事件，根据当前状态执行不同逻辑
     * <p>
     * 状态转换规则：
     * - IDLE → 开始新一轮（进入 WAITING）
     * - WAITING → 过早点击（进入 TOO_SOON）
     * - READY → 记录反应时间（进入 TAPPED）
     * - TAPPED → 若已完成5轮则重置，否则开始新一轮
     * - TOO_SOON → 重新开始新一轮
     */
    public void onTap() {
        switch (state) {
            case IDLE:
                startRound();
                return;
            case WAITING:
                // 在变绿前点击，判定为过早
                state = State.TOO_SOON;
                notifyListener();
                return;
            case READY:
                // 记录反应时间 = 点击时刻 - 变绿时刻
                tapTime = System.currentTimeMillis();
                long ms = tapTime - readyTime;
                results[round] = ms;
                round++;
                state = State.TAPPED;
                notifyListener();
                return;
            case TAPPED:
                if (round >= MAX_ROUNDS) {
                    // 5轮完成，重置游戏
                    reset();
                    notifyListener();
                } else {
                    startRound();
                }
                return;
            case TOO_SOON:
                // 过早点击后，重新开始新一轮
                startRound();
                return;
        }
    }

    /**
     * 开始新一轮，进入 WAITING 状态
     * <p>
     * 重置本轮的时间戳，通知监听器以触发延迟调度。
     */
    private void startRound() {
        state = State.WAITING;
        readyTime = 0;
        tapTime = 0;
        notifyListener();
    }

    /**
     * 通知状态变化监听器
     */
    private void notifyListener() {
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    /**
     * 设置状态变化监听器
     *
     * @param listener 状态变化监听器
     */
    public void setOnStateChangeListener(OnStateChangeListener listener) {
        this.listener = listener;
    }

    /**
     * 获取本轮的随机等待延迟时间
     * <p>
     * 返回 [MIN_WAIT_MS, MAX_WAIT_MS] 范围内的随机值，
     * 即玩家需要等待 1~4 秒后屏幕才会变绿。
     *
     * @return 等待延迟（毫秒）
     */
    public long getWaitingDelay() {
        return MIN_WAIT_MS + random.nextLong() % (MAX_WAIT_MS - MIN_WAIT_MS + 1);
    }

    /**
     * 获取当前游戏状态
     *
     * @return 当前状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取已完成的轮次数
     *
     * @return 轮次数（0~5）
     */
    public int getRound() {
        return round;
    }

    /**
     * 获取当前轮次的反应时间
     *
     * @return 反应时间（毫秒），若尚未点击则返回 0
     */
    public long getCurrentResult() {
        if (tapTime > 0) return tapTime - readyTime;
        return 0;
    }

    /**
     * 计算所有已完成轮次的平均反应时间
     *
     * @return 平均反应时间（毫秒），无有效数据时返回 0
     */
    public double getAverage() {
        if (round == 0) return 0;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < round; i++) {
            if (results[i] > 0) {
                sum += results[i];
                count++;
            }
        }
        return count == 0 ? 0 : (double) sum / count;
    }

    /**
     * 获取所有轮次中的最佳（最短）反应时间
     *
     * @return 最佳反应时间（毫秒），无有效数据时返回 0
     */
    public long getBest() {
        long best = Long.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < results.length; i++) {
            if (results[i] > 0 && results[i] < best) {
                best = results[i];
                found = true;
            }
        }
        return found ? best : 0;
    }
}
