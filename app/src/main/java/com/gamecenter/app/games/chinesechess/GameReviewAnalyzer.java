package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋对局复盘分析器。
 *
 * <p>对一局完整对局进行逐步分析，对比每步走法与 AI 最佳走法的评分差异，
 * 识别好棋与错误，并生成复盘总结和改进建议。</p>
 *
 * <p>分析流程：
 * <ol>
 *   <li>从初始棋盘开始，逐步模拟对局中的每步走法；</li>
 *   <li>对每步走法调用 {@link ChineseChessAI} 计算最佳走法及评分；</li>
 *   <li>使用 {@link MistakeAnalyzer} 识别错误类型和解释；</li>
 *   <li>评分差距小于阈值（默认 100）判定为好棋；</li>
 *   <li>汇总所有分析结果生成 {@link ReviewResult}。</li>
 * </ol>
 * </p>
 */
public class GameReviewAnalyzer {

    /** 好棋判定阈值：评分差值小于此值视为好棋 */
    private static final int GOOD_MOVE_THRESHOLD = 100;

    /** AI 搜索难度（用于复盘时的最佳走法计算） */
    private final int difficulty;

    private final ChineseChessAI ai;
    private final MistakeAnalyzer mistakeAnalyzer;

    public GameReviewAnalyzer(int difficulty) {
        this.difficulty = Math.max(1, Math.min(4, difficulty));
        this.ai = new ChineseChessAI(this.difficulty);
        this.mistakeAnalyzer = new MistakeAnalyzer();
    }

    /**
     * 分析一局对局，返回完整的复盘结果。
     *
     * @param record 对局记录（包含走法列表和对局元数据）
     * @return 复盘分析结果
     */
    public ReviewResult analyzeGame(GameRecord record) {
        if (record == null || record.getMoves().isEmpty()) {
            return new ReviewResult(
                    record != null ? record.getGameId() : "",
                    new ArrayList<>(), 0, 0, 0,
                    "对局记录为空，无法进行复盘分析。",
                    new ArrayList<>()
            );
        }

        List<MoveAnalysis> analyses = analyzeMoves(record);
        return buildReviewResult(record.getGameId(), analyses);
    }

    /**
     * 逐步分析对局中的每步走法。
     *
     * @param record 对局记录
     * @return 每步走法的分析结果列表
     */
    public List<MoveAnalysis> analyzeMoves(GameRecord record) {
        List<MoveAnalysis> analyses = new ArrayList<>();
        List<int[]> moves = record.getMoves();

        // 从初始棋盘开始
        int[][] board = createInitialBoard();

        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);

            // 确定当前走棋方（偶数步红方，奇数步黑方）
            int side = (i % 2 == 0) ? 1 : -1;

            // 计算当前局面的最佳走法和评分
            int[] bestMove = ai.getBestMove(board, difficulty, side);
            int currentScore = evaluateBoardForSide(board, side);
            int bestScore = currentScore;

            if (bestMove != null) {
                int[][] boardAfterBest = copyBoard(board);
                boardAfterBest[bestMove[2]][bestMove[3]] = boardAfterBest[bestMove[0]][bestMove[1]];
                boardAfterBest[bestMove[0]][bestMove[1]] = 0;
                bestScore = evaluateBoardForSide(boardAfterBest, side);
            }

            // 模拟用户的实际走法并评估
            int score = currentScore;
            if (move.length >= 4 && board[move[0]][move[1]] != 0) {
                int[][] boardAfterUser = copyBoard(board);
                boardAfterUser[move[2]][move[3]] = boardAfterUser[move[0]][move[1]];
                boardAfterUser[move[0]][move[1]] = 0;
                score = evaluateBoardForSide(boardAfterUser, side);
            }

            // 判断是否为好棋
            int scoreDiff = bestScore - score;
            boolean isGoodMove = scoreDiff < GOOD_MOVE_THRESHOLD;

            // 使用 MistakeAnalyzer 识别错误类型
            TacticalPattern pattern = null;
            String explanation;
            if (!isGoodMove && bestMove != null && move.length >= 4) {
                MistakeResult mistakeResult = mistakeAnalyzer.analyzeMistake(
                        board, move, bestMove, difficulty);
                explanation = mistakeResult.explanation;
                // 尝试匹配战术模式
                pattern = detectPattern(board, move, bestMove, side);
            } else if (isGoodMove) {
                explanation = generateGoodMoveExplanation(score, bestScore, board, move, side);
                pattern = detectPattern(board, move, bestMove, side);
            } else {
                explanation = "这步棋没有明显问题。";
            }

            analyses.add(new MoveAnalysis(
                    i, move, isGoodMove, score, bestScore,
                    explanation, pattern, bestMove
            ));

            // 更新棋盘状态（模拟实际走法）
            if (move.length >= 4) {
                board[move[2]][move[3]] = board[move[0]][move[1]];
                board[move[0]][move[1]] = 0;
            }
        }

        return analyses;
    }

    // ==================== 复盘结果构建 ====================

    private ReviewResult buildReviewResult(String gameId, List<MoveAnalysis> analyses) {
        int totalMoves = analyses.size();
        int goodMoves = 0;
        int mistakes = 0;

        for (MoveAnalysis a : analyses) {
            if (a.isGoodMove) {
                goodMoves++;
            } else {
                mistakes++;
            }
        }

        String summary = generateSummary(analyses, totalMoves, goodMoves, mistakes);
        List<String> improvements = generateImprovements(analyses);

        return new ReviewResult(
                gameId, analyses, totalMoves, goodMoves, mistakes,
                summary, improvements
        );
    }

    private String generateSummary(List<MoveAnalysis> analyses,
                                   int totalMoves, int goodMoves, int mistakes) {
        StringBuilder sb = new StringBuilder();
        sb.append("本局共 ").append(totalMoves).append(" 步。\n");
        sb.append("好棋 ").append(goodMoves).append(" 步，");
        sb.append("错误 ").append(mistakes).append(" 步。\n");

        double rate = totalMoves > 0 ? (double) goodMoves / totalMoves * 100 : 0;
        sb.append(String.format(java.util.Locale.US, "好棋率：%.1f%%。\n", rate));

        if (rate >= 80) {
            sb.append("整体表现优秀，走法精准。");
        } else if (rate >= 60) {
            sb.append("表现良好，仍有提升空间。");
        } else if (rate >= 40) {
            sb.append("表现一般，需要加强战术训练。");
        } else {
            sb.append("失误较多，建议多做残局练习和战术题。");
        }

        return sb.toString();
    }

    private List<String> generateImprovements(List<MoveAnalysis> analyses) {
        List<String> improvements = new ArrayList<>();

        // 统计错误类型分布
        int tacticalErrors = 0;
        int strategicErrors = 0;
        int positionalErrors = 0;

        for (MoveAnalysis a : analyses) {
            if (!a.isGoodMove) {
                int diff = a.getScoreDiff();
                if (diff >= 300) {
                    tacticalErrors++;
                } else if (diff >= 150) {
                    strategicErrors++;
                } else {
                    positionalErrors++;
                }
            }
        }

        if (tacticalErrors > 0) {
            improvements.add("战术层面：存在 " + tacticalErrors + " 步战术错误，"
                    + "建议加强子力计算和杀法练习，走子前仔细检查是否会被反吃。");
        }
        if (strategicErrors > 0) {
            improvements.add("战略层面：存在 " + strategicErrors + " 步战略错误，"
                    + "建议注意全局协调，优先出动大子控制要道。");
        }
        if (positionalErrors > 0) {
            improvements.add("位置层面：存在 " + positionalErrors + " 步位置错误，"
                    + "建议关注棋子的位置质量，确保每个棋子都能发挥最大效能。");
        }

        // 检查是否有连续错误
        int maxConsecutiveMistakes = 0;
        int currentStreak = 0;
        for (MoveAnalysis a : analyses) {
            if (!a.isGoodMove) {
                currentStreak++;
                maxConsecutiveMistakes = Math.max(maxConsecutiveMistakes, currentStreak);
            } else {
                currentStreak = 0;
            }
        }
        if (maxConsecutiveMistakes >= 3) {
            improvements.add("连续失误：出现了 " + maxConsecutiveMistakes + " 步连续错误，"
                    + "说明局面判断出现系统性偏差，建议在关键局面多花时间思考。");
        }

        if (improvements.isEmpty()) {
            improvements.add("整体表现不错，继续保持！可以尝试更高难度来进一步提升棋力。");
        }

        return improvements;
    }

    // ==================== 好棋解释生成 ====================

    private String generateGoodMoveExplanation(int score, int bestScore,
                                                int[][] board, int[] move, int side) {
        int scoreDiff = bestScore - score;
        if (scoreDiff <= 0) {
            return "这步棋走得很精准，与 AI 最佳走法一致。";
        } else if (scoreDiff <= 30) {
            return "这步棋基本是最佳走法，只有微小的优化空间。";
        } else {
            return "这步棋质量不错，与最佳走法差距很小。";
        }
    }

    // ==================== 战术模式检测 ====================

    private TacticalPattern detectPattern(int[][] board, int[] move, int[] bestMove, int side) {
        if (move == null || move.length < 4) return null;

        int piece = board[move[0]][move[1]];
        int pieceType = Math.abs(piece);
        int target = board[move[2]][move[3]];

        // 检测将军
        int[][] boardAfter = copyBoard(board);
        boardAfter[move[2]][move[3]] = boardAfter[move[0]][move[1]];
        boardAfter[move[0]][move[1]] = 0;
        int opponentSide = -side;
        if (isInCheck(boardAfter, opponentSide)) {
            // 检测双将
            int[] enemyKing = findKing(boardAfter, opponentSide);
            if (enemyKing != null) {
                int checkCount = 0;
                for (int r = 0; r < 10; r++) {
                    for (int c = 0; c < 9; c++) {
                        int p = boardAfter[r][c];
                        if (p == 0) continue;
                        if ((side > 0 && p > 0) || (side < 0 && p < 0)) continue;
                        if (attacksSquare(boardAfter, r, c, enemyKing[0], enemyKing[1])) {
                            checkCount++;
                        }
                    }
                }
                if (checkCount >= 2) {
                    return TacticalPattern.DOUBLE_CHECK;
                }
            }
            return TacticalPattern.CHECK_BASICS;
        }

        // 检测卧槽马
        if (pieceType == 4) { // 马
            int toRow = move[2], toCol = move[3];
            boolean isTargetPalace = side > 0
                    ? (toRow >= 7 && toRow <= 9 && toCol >= 3 && toCol <= 5)
                    : (toRow >= 0 && toRow <= 2 && toCol >= 3 && toCol <= 5);
            if (isTargetPalace) {
                return TacticalPattern.KNIGHT_CRADLE;
            }
        }

        // 检测铁门栓
        if (pieceType == 6 && target == 0) { // 炮走到空位
            int toCol = move[3];
            if (toCol == 4) { // 中路
                int[] enemyKing = findKing(boardAfter, opponentSide);
                if (enemyKing != null && enemyKing[1] == 4) {
                    return TacticalPattern.IRON_BOLT;
                }
            }
        }

        return null;
    }

    // ==================== 棋盘评估（红方视角） ====================

    private static final int[] PIECE_VALUES = {
            0, 10000, 200, 200, 400, 900, 450, 100
    };

    /**
     * 从指定方视角评估棋盘分数。
     * 返回值为该方的评估分数（越大越好）。
     */
    private int evaluateBoardForSide(int[][] board, int side) {
        int score = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                int type = Math.abs(piece);
                int value = PIECE_VALUES[type];
                if (piece > 0) score += value;
                else score -= value;
            }
        }
        // 红方视角为正，黑方视角需要取反
        return side > 0 ? score : -score;
    }

    // ==================== 初始棋盘 ====================

    private static final int KING = 1, ADVISOR = 2, BISHOP = 3;
    private static final int KNIGHT = 4, ROOK = 5, CANNON = 6, PAWN = 7;

    private int[][] createInitialBoard() {
        int[][] board = new int[10][9];
        // 红方（底部，row 9）
        board[9][0] = ROOK;    board[9][1] = KNIGHT;  board[9][2] = BISHOP;
        board[9][3] = ADVISOR; board[9][4] = KING;    board[9][5] = ADVISOR;
        board[9][6] = BISHOP;  board[9][7] = KNIGHT;  board[9][8] = ROOK;
        board[7][1] = CANNON;  board[7][7] = CANNON;
        board[6][0] = PAWN;    board[6][2] = PAWN;    board[6][4] = PAWN;
        board[6][6] = PAWN;    board[6][8] = PAWN;
        // 黑方（顶部，row 0）
        board[0][0] = -ROOK;   board[0][1] = -KNIGHT; board[0][2] = -BISHOP;
        board[0][3] = -ADVISOR; board[0][4] = -KING;  board[0][5] = -ADVISOR;
        board[0][6] = -BISHOP; board[0][7] = -KNIGHT; board[0][8] = -ROOK;
        board[2][1] = -CANNON; board[2][7] = -CANNON;
        board[3][0] = -PAWN;   board[3][2] = -PAWN;   board[3][4] = -PAWN;
        board[3][6] = -PAWN;   board[3][8] = -PAWN;
        return board;
    }

    // ==================== 棋盘工具 ====================

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    // ==================== 将军/攻击检测（与 ChineseChessAI 一致） ====================

    private static int[] findKing(int[][] b, int side) {
        int target = side > 0 ? 1 : -1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (b[r][c] == target) return new int[]{r, c};
            }
        }
        return null;
    }

    private static boolean isInCheck(int[][] b, int side) {
        int[] king = findKing(b, side);
        if (king == null) return false;
        int kr = king[0], kc = king[1];
        int attacker = side > 0 ? -1 : 1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                if ((side > 0 && p > 0) || (side < 0 && p < 0)) continue;
                if (attacksSquare(b, r, c, kr, kc)) return true;
            }
        }
        int[] enemyKing = findKing(b, attacker);
        if (enemyKing != null && enemyKing[1] == kc) {
            boolean clear = true;
            int lo = Math.min(kr, enemyKing[0]) + 1;
            int hi = Math.max(kr, enemyKing[0]);
            for (int r = lo; r < hi; r++) {
                if (b[r][kc] != 0) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }

    private static boolean attacksSquare(int[][] b, int fr, int fc, int tr, int tc) {
        if (fr == tr && fc == tc) return false;
        int piece = b[fr][fc];
        if (piece == 0) return false;
        int target = b[tr][tc];
        if (piece > 0 && target > 0) return false;
        if (piece < 0 && target < 0) return false;
        int type = Math.abs(piece);
        int dr = tr - fr, dc = tc - fc;
        switch (type) {
            case KING: {
                boolean inPalace = piece > 0
                        ? (tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5)
                        : (tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5);
                return inPalace && Math.abs(dr) + Math.abs(dc) == 1;
            }
            case ADVISOR: {
                boolean inPalace = piece > 0
                        ? (tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5)
                        : (tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5);
                return inPalace && Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case BISHOP:
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if (piece > 0 && tr < 5) return false;
                if (piece < 0 && tr > 4) return false;
                return b[fr + dr / 2][fc + dc / 2] == 0;
            case KNIGHT:
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1)
                        || (Math.abs(dr) == 1 && Math.abs(dc) == 2)))
                    return false;
                if (Math.abs(dr) == 2) return b[fr + dr / 2][fc] == 0;
                return b[fr][fc + dc / 2] == 0;
            case ROOK:
                if (dr != 0 && dc != 0) return false;
                return pathClear(b, fr, fc, tr, tc);
            case CANNON: {
                if (dr != 0 && dc != 0) return false;
                int cnt = piecesBetween(b, fr, fc, tr, tc);
                if (target == 0) return cnt == 0;
                return cnt == 1;
            }
            case PAWN:
                if (piece > 0) {
                    if (fr >= 5) return dr == -1 && dc == 0;
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                } else {
                    if (fr <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
        }
        return false;
    }

    private static boolean pathClear(int[][] b, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) if (b[r1][c] != 0) return false;
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) if (b[r][c1] != 0) return false;
        }
        return true;
    }

    private static int piecesBetween(int[][] b, int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) if (b[r1][c] != 0) count++;
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) if (b[r][c1] != 0) count++;
        }
        return count;
    }
}
