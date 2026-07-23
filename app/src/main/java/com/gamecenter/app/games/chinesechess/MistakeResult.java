package com.gamecenter.app.games.chinesechess;

/**
 * 棋步错误分析结果。
 *
 * <p>封装 {@link MistakeAnalyzer} 对用户走法的分析结论，包括错误类型、评分差值、
 * 针对性解释文本和更优走法建议。</p>
 *
 * @author MiMoCode
 * @since 2026-07-23
 */
public class MistakeResult {

    /** 错误类型 */
    public final MistakeAnalyzer.MistakeType type;

    /** 用户走法与最佳走法的评分差值（正数表示用户走法更差） */
    public final int scoreDiff;

    /** 针对性错误解释文本 */
    public final String explanation;

    /** 更优走法建议 [fromRow, fromCol, toRow, toCol]（可能为 null） */
    public final int[] betterMove;

    public MistakeResult(MistakeAnalyzer.MistakeType type, int scoreDiff,
                         String explanation, int[] betterMove) {
        this.type = type;
        this.scoreDiff = scoreDiff;
        this.explanation = explanation;
        this.betterMove = betterMove;
    }

    /** 是否为实际错误（非 NO_MISTAKE） */
    public boolean hasMistake() {
        return type != MistakeAnalyzer.MistakeType.NO_MISTAKE;
    }

    @Override
    public String toString() {
        return "MistakeResult{type=" + type + ", scoreDiff=" + scoreDiff
                + ", explanation='" + explanation + "'}";
    }
}
