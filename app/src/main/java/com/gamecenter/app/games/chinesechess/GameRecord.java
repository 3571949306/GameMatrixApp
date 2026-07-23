package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 中国象棋对局记录。
 *
 * <p>保存一局完整对局的所有数据，包括走法、评分、提示和错误分析。
 * 支持导出为 PGN 格式字符串用于分享和回放。</p>
 */
public class GameRecord {

    private String gameId;
    private List<int[]> moves;
    private List<Integer> scores;
    private List<HintResult> hints;
    private List<MistakeResult> mistakes;
    private long startTime;
    private long endTime;
    private GameResult result;
    private int difficulty;

    public GameRecord() {
        this.gameId = UUID.randomUUID().toString();
        this.moves = new ArrayList<>();
        this.scores = new ArrayList<>();
        this.hints = new ArrayList<>();
        this.mistakes = new ArrayList<>();
    }

    public GameRecord(String gameId) {
        this.gameId = gameId;
        this.moves = new ArrayList<>();
        this.scores = new ArrayList<>();
        this.hints = new ArrayList<>();
        this.mistakes = new ArrayList<>();
    }

    // ==================== Getters ====================

    public String getGameId() {
        return gameId;
    }

    public List<int[]> getMoves() {
        return moves;
    }

    public List<Integer> getScores() {
        return scores;
    }

    public List<HintResult> getHints() {
        return hints;
    }

    public List<MistakeResult> getMistakes() {
        return mistakes;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public GameResult getResult() {
        return result;
    }

    public int getDifficulty() {
        return difficulty;
    }

    // ==================== Setters ====================

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public void setMoves(List<int[]> moves) {
        this.moves = moves;
    }

    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }

    public void setHints(List<HintResult> hints) {
        this.hints = hints;
    }

    public void setMistakes(List<MistakeResult> mistakes) {
        this.mistakes = mistakes;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    // ==================== 操作方法 ====================

    public void addMove(int[] move) {
        moves.add(move);
    }

    public void addScore(int score) {
        scores.add(score);
    }

    public void addHint(HintResult hint) {
        hints.add(hint);
    }

    public void addMistake(MistakeResult mistake) {
        mistakes.add(mistake);
    }

    public int getMoveCount() {
        return moves.size();
    }

    public long getDuration() {
        if (startTime <= 0) return 0;
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return end - startTime;
    }

    // ==================== PGN 导出 ====================

    /**
     * 将对局记录导出为 PGN 格式字符串。
     *
     * <p>PGN 头标签包含：Event, Site, Date, Round, Player, Result, Difficulty, Moves 等。
     * 走法部分使用中文坐标表示法（如 炮二平五），每步标注序号。</p>
     *
     * @return PGN 格式字符串
     */
    public String toPGN() {
        StringBuilder sb = new StringBuilder();

        // PGN 头标签
        String dateStr = formatTimestamp(startTime);
        sb.append("[Event \"GameMatrix Chinese Chess Game\"]\n");
        sb.append("[Site \"GameMatrix App\"]\n");
        sb.append("[Date \"").append(dateStr).append("\"]\n");
        sb.append("[Round \"1\"]\n");
        sb.append("[White \"Player\"]\n");
        sb.append("[Black \"AI\"]\n");
        sb.append("[Result \"").append(formatResult()).append("\"]\n");
        sb.append("[Difficulty \"").append(difficulty).append("\"]\n");
        sb.append("[MoveCount \"").append(moves.size()).append("\"]\n");
        sb.append("[Duration \"").append(formatDuration()).append("\"]\n");
        sb.append("\n");

        // 走法部分：使用坐标表示法
        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);
            if (i % 2 == 0) {
                sb.append((i / 2 + 1)).append(". ");
            }

            // 格式：棋子名(fromR,fromC)->(toR,toC)
            sb.append(formatMovePGN(move));

            if (i % 2 == 1 || i == moves.size() - 1) {
                sb.append(" ");
            }
        }

        sb.append(formatResult());
        sb.append("\n");

        return sb.toString();
    }

    private String formatMovePGN(int[] move) {
        if (move.length < 4) return "?";
        return "(" + move[0] + "," + move[1] + ")->(" + move[2] + "," + move[3] + ")";
    }

    private String formatResult() {
        if (result == null) return "*";
        switch (result) {
            case WIN: return "1-0";
            case LOSE: return "0-1";
            case DRAW: return "1/2-1/2";
            case TIMEOUT: return "0-1";
            default: return "*";
        }
    }

    private String formatTimestamp(long ts) {
        if (ts <= 0) return "????.??.??";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US);
        return sdf.format(new java.util.Date(ts));
    }

    private String formatDuration() {
        long ms = getDuration();
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%dm%ds", minutes, seconds);
    }

    @Override
    public String toString() {
        return "GameRecord{gameId='" + gameId + "', moves=" + moves.size()
                + ", result=" + result + ", difficulty=" + difficulty + "}";
    }
}
