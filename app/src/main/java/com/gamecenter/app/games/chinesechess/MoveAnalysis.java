package com.gamecenter.app.games.chinesechess;

/**
 * 单步走法的复盘分析结果。
 *
 * <p>封装一步走法的评分、与最佳走法的对比、解释文本和战术类型识别。</p>
 */
public class MoveAnalysis {

    /** 走法序号（从 0 开始） */
    public final int moveIndex;

    /** 走法坐标 [fromRow, fromCol, toRow, toCol] */
    public final int[] move;

    /** 是否是好棋（评分差距小于阈值） */
    public final boolean isGoodMove;

    /** 实际走法评分（红方视角，越大对红方越有利） */
    public final int score;

    /** 最佳走法评分 */
    public final int bestScore;

    /** 解释文本 */
    public final String explanation;

    /** 识别到的战术类型（可能为 null） */
    public final TacticalPattern pattern;

    /** 最佳走法（可能为 null） */
    public final int[] bestMove;

    public MoveAnalysis(int moveIndex, int[] move, boolean isGoodMove,
                        int score, int bestScore, String explanation,
                        TacticalPattern pattern, int[] bestMove) {
        this.moveIndex = moveIndex;
        this.move = move;
        this.isGoodMove = isGoodMove;
        this.score = score;
        this.bestScore = bestScore;
        this.explanation = explanation;
        this.pattern = pattern;
        this.bestMove = bestMove;
    }

    /** 评分差值（正数表示实际走法不如最佳走法） */
    public int getScoreDiff() {
        return bestScore - score;
    }

    @Override
    public String toString() {
        return "MoveAnalysis{index=" + moveIndex
                + ", good=" + isGoodMove
                + ", score=" + score
                + ", bestScore=" + bestScore
                + ", diff=" + getScoreDiff() + "}";
    }
}
