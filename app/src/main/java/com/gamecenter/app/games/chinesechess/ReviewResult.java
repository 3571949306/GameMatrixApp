package com.gamecenter.app.games.chinesechess;

import java.util.List;

/**
 * 中国象棋对局复盘结果。
 *
 * <p>封装 {@link GameReviewAnalyzer} 对一局完整对局的分析结论，
 * 包括每步走法分析、统计数据、总结和改进建议。</p>
 */
public class ReviewResult {

    /** 对局 ID */
    public final String gameId;

    /** 每步走法的分析结果列表 */
    public final List<MoveAnalysis> analyses;

    /** 总步数 */
    public final int totalMoves;

    /** 好棋数量（评分差距小于阈值的走法） */
    public final int goodMoves;

    /** 错误数量（评分差距大于等于阈值的走法） */
    public final int mistakes;

    /** 复盘总结文本 */
    public final String summary;

    /** 改进建议列表 */
    public final List<String> improvements;

    public ReviewResult(String gameId, List<MoveAnalysis> analyses,
                        int totalMoves, int goodMoves, int mistakes,
                        String summary, List<String> improvements) {
        this.gameId = gameId;
        this.analyses = analyses;
        this.totalMoves = totalMoves;
        this.goodMoves = goodMoves;
        this.mistakes = mistakes;
        this.summary = summary;
        this.improvements = improvements;
    }

    /** 好棋率（0.0 ~ 1.0） */
    public double getGoodMoveRate() {
        if (totalMoves == 0) return 0.0;
        return (double) goodMoves / totalMoves;
    }

    /** 平均评分差值 */
    public double getAverageScoreDiff() {
        if (analyses.isEmpty()) return 0.0;
        long totalDiff = 0;
        for (MoveAnalysis a : analyses) {
            totalDiff += a.getScoreDiff();
        }
        return (double) totalDiff / analyses.size();
    }

    @Override
    public String toString() {
        return "ReviewResult{gameId='" + gameId
                + "', total=" + totalMoves
                + ", good=" + goodMoves
                + ", mistakes=" + mistakes
                + ", rate=" + String.format(java.util.Locale.US, "%.1f%%", getGoodMoveRate() * 100)
                + "}";
    }
}
