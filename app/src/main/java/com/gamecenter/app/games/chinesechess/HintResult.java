package com.gamecenter.app.games.chinesechess;

/**
 * 中国象棋 AI 提示结果。
 *
 * <p>封装一次提示计算的完整结果，包括推荐走法、解释文本和计算元信息。</p>
 *
 * @author AI Assistant
 * @since 2026-07-23
 */
public class HintResult {

    private final int[] move;
    private final String explanation;
    private final long computeTimeMs;
    private final int difficulty;

    public HintResult(int[] move, String explanation, long computeTimeMs, int difficulty) {
        this.move = move;
        this.explanation = explanation;
        this.computeTimeMs = computeTimeMs;
        this.difficulty = difficulty;
    }

    /** 推荐走法 [fromR, fromC, toR, toC]，null 表示无合法着法。 */
    public int[] getMove() {
        return move;
    }

    /** 提示解释文本。 */
    public String getExplanation() {
        return explanation;
    }

    /** 计算耗时（毫秒）。 */
    public long getComputeTimeMs() {
        return computeTimeMs;
    }

    /** 使用的 AI 难度等级。 */
    public int getDifficulty() {
        return difficulty;
    }
}
