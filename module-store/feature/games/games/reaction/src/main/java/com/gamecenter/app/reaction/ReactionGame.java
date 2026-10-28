package com.gamecenter.app.reaction;

import java.util.Random;

/**
 * 反应力游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>支持 3 档难度分级、段位评估、轮次统计与极速响应计算。</p>
 */
public class ReactionGame {

    public static final int DIFFICULTY_CASUAL = 0;   // 休闲挑战 (2500~4000ms)
    public static final int DIFFICULTY_STANDARD = 1; // 标准竞技 (1500~3000ms)
    public static final int DIFFICULTY_EXTREME = 2;  // 极限闪电 (800~1800ms)

    private final Random random = new Random();

    private int difficulty = DIFFICULTY_STANDARD;
    private int currentRound = 0;
    private int totalRounds = 0;
    private long lastReactionTimeMs = 0;
    private long bestReactionTimeMs = Long.MAX_VALUE;
    private long totalReactionTimeMs = 0;
    private int fastCount = 0;
    private boolean waitingForGreen = false;
    private boolean greenShown = false;
    private long readyTimeMs = 0;
    private int baseDelayMs = 2000;
    private boolean running = false;

    public void setDifficulty(int diff) {
        if (diff < DIFFICULTY_CASUAL || diff > DIFFICULTY_EXTREME) {
            diff = DIFFICULTY_STANDARD;
        }
        this.difficulty = diff;
        switch (diff) {
            case DIFFICULTY_CASUAL:
                this.baseDelayMs = 2500;
                break;
            case DIFFICULTY_EXTREME:
                this.baseDelayMs = 900;
                break;
            case DIFFICULTY_STANDARD:
            default:
                this.baseDelayMs = 1800;
                break;
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void startRound() {
        waitingForGreen = true;
        greenShown = false;
        running = true;
    }

    /** 计算随机延迟后变绿。 */
    public int computeDelayMs() {
        int jitter;
        switch (difficulty) {
            case DIFFICULTY_CASUAL:
                jitter = 1500;
                break;
            case DIFFICULTY_EXTREME:
                jitter = 900;
                break;
            case DIFFICULTY_STANDARD:
            default:
                jitter = 1200;
                break;
        }
        int delay = baseDelayMs + random.nextInt(jitter);
        if (currentRound > 3) {
            delay = Math.max(700, baseDelayMs - (currentRound - 3) * 150) + random.nextInt(jitter);
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
        long reactionMs = Math.max(1, System.currentTimeMillis() - readyTimeMs);
        lastReactionTimeMs = reactionMs;
        waitingForGreen = false;
        greenShown = false;
        currentRound++;
        totalRounds++;
        if (reactionMs < bestReactionTimeMs) {
            bestReactionTimeMs = reactionMs;
        }
        totalReactionTimeMs += reactionMs;
        if (reactionMs < 350) {
            fastCount++;
        } else {
            fastCount = 0;
        }
        running = false;
        return reactionMs;
    }

    public int computeScore(long reactionMs) {
        int base = Math.max(50 - (int) (reactionMs / 20), 5);
        if (difficulty == DIFFICULTY_EXTREME) {
            return (int) (base * 1.5f);
        } else if (difficulty == DIFFICULTY_CASUAL) {
            return (int) (base * 0.8f);
        }
        return base;
    }

    public String getRankTitle(long ms) {
        if (ms <= 0) return "--";
        if (ms < 200) return "⚡ 神仙反应 (闪电级)";
        if (ms < 280) return "🔥 电竞职业 (宗师级)";
        if (ms < 380) return "👍 敏捷先锋 (精锐级)";
        if (ms < 500) return "😊 正常水平 (标准级)";
        return "🐢 稍有迟疑 (摸鱼级)";
    }

    public void resetStats() {
        currentRound = 0;
        totalRounds = 0;
        lastReactionTimeMs = 0;
        bestReactionTimeMs = Long.MAX_VALUE;
        totalReactionTimeMs = 0;
        fastCount = 0;
        waitingForGreen = false;
        greenShown = false;
        running = false;
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

