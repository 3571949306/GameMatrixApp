package com.gamecenter.app.whack;

import java.util.Random;

/**
 * 打地鼠游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>从宿主 WhackActivity 中提取：分数、计时、地鼠出现间隔、
 * 连击与难度递增。</p>
 */
public class WhackGame {

    public static final int GRID_SIZE = 3;
    public static final int GAME_DURATION_SEC = 30;
    public static final long INITIAL_MOLE_INTERVAL_MS = 1500;
    public static final long MIN_MOLE_INTERVAL_MS = 500;

    private final Random random = new Random();

    private int score = 0;
    private int hitCount = 0;
    private int missCount = 0;
    private int consecutiveHits = 0;
    private int timeRemaining = GAME_DURATION_SEC;
    private int currentMolePos = -1;
    private long moleIntervalMs = INITIAL_MOLE_INTERVAL_MS;
    private boolean moleVisible = false;
    private boolean gameActive = false;
    private long startIntervalMs = INITIAL_MOLE_INTERVAL_MS;

    public void reset(long startInterval) {
        this.startIntervalMs = startInterval;
        score = 0;
        hitCount = 0;
        missCount = 0;
        consecutiveHits = 0;
        timeRemaining = GAME_DURATION_SEC;
        moleIntervalMs = startInterval;
        currentMolePos = -1;
        moleVisible = false;
        gameActive = true;
    }

    /** 难度递减：每 10 秒缩短间隔。 */
    public void onTickSecond() {
        if (!gameActive) return;
        timeRemaining--;
        if (timeRemaining % 10 == 0) {
            moleIntervalMs = Math.max(MIN_MOLE_INTERVAL_MS, moleIntervalMs - 300);
        }
    }

    /**
     * 选择下一个地鼠位置（与当前不同）。
     * @return 新位置 index
     */
    public int nextMolePos() {
        int newPos;
        do {
            newPos = random.nextInt(GRID_SIZE * GRID_SIZE);
        } while (newPos == currentMolePos);
        currentMolePos = newPos;
        moleVisible = true;
        return newPos;
    }

    /** 击中地鼠，返回得分增量。 */
    public int hitMole() {
        if (!moleVisible) return 0;
        moleVisible = false;
        hitCount++;
        consecutiveHits++;
        int gain = 10 + consecutiveHits;
        score += gain;
        return gain;
    }

    /** 地鼠超时未击中。 */
    public void missMole() {
        moleVisible = false;
        missCount++;
        consecutiveHits = 0;
    }

    /** 点击空位（未击中地鼠），重置连击。 */
    public void consecutiveHitsReset() {
        consecutiveHits = 0;
        missCount++;
    }

    public void hideMole() {
        moleVisible = false;
    }

    public void endGame() {
        gameActive = false;
    }

    public int getScore() { return score; }
    public int getTimeRemaining() { return timeRemaining; }
    public int getCurrentMolePos() { return currentMolePos; }
    public boolean isMoleVisible() { return moleVisible; }
    public boolean isGameActive() { return gameActive; }
    public long getMoleIntervalMs() { return moleIntervalMs; }
    public int getConsecutiveHits() { return consecutiveHits; }
}
