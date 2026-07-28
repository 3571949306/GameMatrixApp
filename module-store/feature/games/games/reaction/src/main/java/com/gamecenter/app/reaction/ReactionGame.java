package com.gamecenter.app.reaction;

import java.util.Random;

/**
 * 反应力游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>从宿主 ReactionActivity 中提取：轮次、反应时间统计、
 * 等待/就绪状态、难度递减。</p>
 */
public class ReactionGame {

    private final Random random = new Random();

    private int currentRound = 0;
    private int totalRounds = 0;
    private long lastReactionTimeMs = 0;
    private long bestReactionTimeMs = Long.MAX_VALUE;
    private long totalReactionTimeMs = 0;
    private int fastCount = 0;
    private boolean waitingForGreen = false;
    private boolean greenShown = false;
    private long readyTimeMs = 0;
    private int baseDelayMs = 3000;
    private boolean running = false;

    public void startRound() {
        waitingForGreen = true;
        greenShown = false;
        running = true;
    }

    /** 计算随机延迟后变绿。 */
    public int computeDelayMs() {
        int delay = baseDelayMs + random.nextInt(2000);
        if (currentRound > 3) {
            delay = Math.max(1000, baseDelayMs - (currentRound - 3) * 200) + random.nextInt(1500);
        }
        return delay;
    }

    /** 变绿，记录就绪时间。 */
    public void markReady() {
        readyTimeMs = System.currentTimeMillis();
        greenShown = true;
    }

    /** 点击过早。 */
    public void tooEarly() {
        waitingForGreen = false;
        greenShown = false;
        fastCount = 0;
        running = false;
    }

    /**
     * 正常点击成功，返回反应时间(ms)。
     */
    public long recordHit() {
        long reactionMs = System.currentTimeMillis() - readyTimeMs;
        lastReactionTimeMs = reactionMs;
        waitingForGreen = false;
        greenShown = false;
        currentRound++;
        totalRounds++;
        if (reactionMs < bestReactionTimeMs) {
            bestReactionTimeMs = reactionMs;
        }
        totalReactionTimeMs += reactionMs;
        if (reactionMs < 400) {
            fastCount++;
        } else {
            fastCount = 0;
        }
        running = false;
        return reactionMs;
    }

    public int computeScore(long reactionMs) {
        return Math.max(50 - (int) (reactionMs / 20), 5);
    }

    public void pause() {
        waitingForGreen = false;
        greenShown = false;
        running = false;
    }

    public boolean isWaitingForGreen() { return waitingForGreen; }
    public boolean isGreenShown() { return greenShown; }
    public boolean isRunning() { return running; }
    public int getCurrentRound() { return currentRound; }
    public int getTotalRounds() { return totalRounds; }
    public long getLastReactionTimeMs() { return lastReactionTimeMs; }
    public long getBestReactionTimeMs() { return bestReactionTimeMs; }
    public long getAverageMs() { return totalRounds > 0 ? totalReactionTimeMs / totalRounds : 0; }
    public int getFastCount() { return fastCount; }

    public int getHighScore() {
        return bestReactionTimeMs == Long.MAX_VALUE ? 0 : Math.max(0, 1000 - (int) bestReactionTimeMs);
    }
}
