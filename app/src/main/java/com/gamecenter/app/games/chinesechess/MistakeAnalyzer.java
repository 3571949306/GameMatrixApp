package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋 AI 错误分析器。
 *
 * <p>分析用户走法与最佳走法的差异，判断错误类型并生成针对性的解释和改进建议。
 * 评分体系与 {@link ChineseChessAI} 保持一致，以确保分析结论的准确性。</p>
 *
 * <p>错误分类：
 * <ul>
 *   <li>{@link MistakeType#TACTICAL_ERROR} — 战术错误：丢子、被将军后无法化解、送将等</li>
 *   <li>{@link MistakeType#STRATEGIC_ERROR} — 战略错误：出子慢、位置被动、违反开局原则等</li>
 *   <li>{@link MistakeType#POSITIONAL_ERROR} — 位置错误：棋子位置不当、机动性受限等</li>
 *   <li>{@link MistakeType#NO_MISTAKE} — 走法无明显问题</li>
 * </ul>
 * </p>
 *
 * @author MiMoCode
 * @since 2026-07-23
 */
public class MistakeAnalyzer {

    // ==================== 常量 ====================

    /** 棋子基础价值（索引为棋子类型 0..7） — 与 ChineseChessAI 保持一致 */
    private static final int[] PIECE_VALUES = {
        0,     // 未使用
        10000, // 帅/将
        200,   // 仕/士
        200,   // 相/象
        400,   // 马
        900,   // 车
        450,   // 炮
        100    // 兵/卒
    };

    /** 棋子类型常量 */
    private static final int KING = 1;
    private static final int ADVISOR = 2;
    private static final int BISHOP = 3;
    private static final int KNIGHT = 4;
    private static final int ROOK = 5;
    private static final int CANNON = 6;
    private static final int PAWN = 7;

    /** 评分阈值：低于此差值视为无明显错误 */
    private static final int NO_MISTAKE_THRESHOLD = 80;

    /** 战术错误阈值：评分差值超过此值大概率为战术错误 */
    private static final int TACTICAL_THRESHOLD = 300;

    /** 战略错误阈值：评分差值在此范围内可能为战略错误 */
    private static final int STRATEGIC_THRESHOLD = 150;

    // ==================== 错误类型枚举 ====================

    public enum MistakeType {
        TACTICAL_ERROR,    // 战术错误（丢子、被将死等）
        STRATEGIC_ERROR,   // 战略错误（位置不好、出子慢等）
        POSITIONAL_ERROR,  // 位置错误（棋子位置不当）
        NO_MISTAKE         // 不是错误
    }

    // ==================== 公共方法 ====================

    /**
     * 分析用户的走法是否为错误，并与最佳走法进行对比。
     *
     * @param board     当前棋盘状态（10×9，正值=红方，负值=黑方）
     * @param userMove  用户走法 [fromRow, fromCol, toRow, toCol]
     * @param bestMove  AI 推荐的最佳走法 [fromRow, fromCol, toRow, toCol]
     * @param difficulty 难度等级（1-4），影响错误判定的灵敏度
     * @return 分析结果
     */
    public MistakeResult analyzeMistake(int[][] board, int[] userMove,
                                        int[] bestMove, int difficulty) {
        if (board == null || userMove == null || bestMove == null) {
            return new MistakeResult(MistakeType.NO_MISTAKE, 0, "参数无效", null);
        }

        // 1. 评估当前局面
        int currentScore = evaluateBoard(board);

        // 2. 模拟用户走法并评估
        int[][] boardAfterUser = copyBoard(board);
        int userCaptured = boardAfterUser[userMove[2]][userMove[3]];
        boardAfterUser[userMove[2]][userMove[3]] = boardAfterUser[userMove[0]][userMove[1]];
        boardAfterUser[userMove[0]][userMove[1]] = 0;
        int userScore = evaluateBoard(boardAfterUser);

        // 3. 模拟最佳走法并评估
        int[][] boardAfterBest = copyBoard(board);
        int bestCaptured = boardAfterBest[bestMove[2]][bestMove[3]];
        boardAfterBest[bestMove[2]][bestMove[3]] = boardAfterBest[bestMove[0]][bestMove[1]];
        boardAfterBest[bestMove[0]][bestMove[1]] = 0;
        int bestScore = evaluateBoard(boardAfterBest);

        // 4. 计算评分差值（从用户视角，正数表示用户走法更差）
        int scoreDiff = (currentScore - userScore) - (currentScore - bestScore);
        // 简化：userScoreDelta = currentScore - userScore（正值=用户走后局面变差）
        // bestScoreDelta = currentScore - bestScore
        // scoreDiff = userScoreDelta - bestScoreDelta = bestScore - userScore
        scoreDiff = bestScore - userScore;

        // 5. 如果差值很小，判定为无错误
        int adjustedThreshold = NO_MISTAKE_THRESHOLD - (difficulty - 1) * 15;
        if (scoreDiff <= adjustedThreshold) {
            String explanation = generateNoMistakeExplanation(board, userMove, bestMove, scoreDiff);
            return new MistakeResult(MistakeType.NO_MISTAKE, scoreDiff, explanation, bestMove);
        }

        // 6. 判断错误类型
        MistakeType type = classifyMistake(board, userMove, bestMove, userCaptured,
                bestCaptured, scoreDiff, boardAfterUser, boardAfterBest);

        // 7. 生成解释
        String explanation = generateExplanation(type, userMove, bestMove,
                userCaptured, bestCaptured, scoreDiff, board);

        return new MistakeResult(type, scoreDiff, explanation, bestMove);
    }

    // ==================== 错误分类 ====================

    /**
     * 根据多维度特征判断错误类型。
     */
    private MistakeType classifyMistake(int[][] board, int[] userMove, int[] bestMove,
                                         int userCaptured, int bestCaptured,
                                         int scoreDiff, int[][] boardAfterUser,
                                         int[][] boardAfterBest) {
        // 维度1：战术特征检测
        boolean hasTacticalSign = detectTacticalError(board, userMove, userCaptured,
                scoreDiff, boardAfterUser);

        // 维度2：战略特征检测
        boolean hasStrategicSign = detectStrategicError(board, userMove, bestMove,
                scoreDiff, boardAfterUser);

        // 维度3：位置特征检测
        boolean hasPositionalSign = detectPositionalError(board, userMove, bestMove,
                scoreDiff, boardAfterUser);

        // 综合判定：战术优先，其次战略，最后位置
        if (hasTacticalSign && scoreDiff >= TACTICAL_THRESHOLD) {
            return MistakeType.TACTICAL_ERROR;
        }
        if (hasStrategicSign && scoreDiff >= STRATEGIC_THRESHOLD) {
            return MistakeType.STRATEGIC_ERROR;
        }
        if (hasPositionalSign) {
            return MistakeType.POSITIONAL_ERROR;
        }

        // 回退：根据评分差值大小推断
        if (scoreDiff >= TACTICAL_THRESHOLD) {
            return MistakeType.TACTICAL_ERROR;
        }
        if (scoreDiff >= STRATEGIC_THRESHOLD) {
            return MistakeType.STRATEGIC_ERROR;
        }
        return MistakeType.POSITIONAL_ERROR;
    }

    /**
     * 检测战术错误特征：丢子、被将军后无力化解、送将。
     */
    private boolean detectTacticalError(int[][] board, int[] move, int captured,
                                         int scoreDiff, int[][] boardAfter) {
        int piece = board[move[0]][move[1]];
        int pieceType = Math.abs(piece);
        int pieceValue = PIECE_VALUES[pieceType];

        // 特征1：用自己的棋子走到一个会被立即吃掉的位置（送子）
        int targetPiece = board[move[2]][move[3]];
        boolean isCapture = targetPiece != 0;
        if (!isCapture) {
            // 非吃子走法，检查目标位置是否会被对方立即吃掉
            int side = piece > 0 ? 1 : -1;
            if (isAttackedBySide(boardAfter, move[2], move[3], -side)) {
                return true; // 送子
            }
        }

        // 特征2：吃子后被反吃，且损失更大（兑子亏损）
        if (isCapture) {
            int capturedValue = PIECE_VALUES[Math.abs(targetPiece)];
            int side = piece > 0 ? 1 : -1;
            if (isAttackedBySide(boardAfter, move[2], move[3], -side)) {
                // 吃子后会被反吃
                if (capturedValue > pieceValue) {
                    return true; // 以小吃大后被反吃，严重战术失误
                }
                // 同等或以大吃小后被反吃，也可能是战术问题
                if (scoreDiff >= TACTICAL_THRESHOLD) {
                    return true;
                }
            }
        }

        // 特征3：走子后己方被将军
        int side = piece > 0 ? 1 : -1;
        if (isInCheck(boardAfter, side)) {
            return true; // 走子后被将军，可能是送将
        }

        // 特征4：丢掉高价值棋子（车、马、炮）
        if (scoreDiff >= PIECE_VALUES[ROOK]) {
            return true;
        }

        return false;
    }

    /**
     * 检测战略错误特征：出子慢、违反开局原则、子力协调差。
     */
    private boolean detectStrategicError(int[][] board, int[] userMove, int[] bestMove,
                                          int scoreDiff, int[][] boardAfter) {
        int piece = board[userMove[0]][userMove[1]];
        int pieceType = Math.abs(piece);
        int side = piece > 0 ? 1 : -1;

        // 特征1：重复移动同一棋子（开局阶段出子慢）
        if (pieceType == KNIGHT || pieceType == ROOK || pieceType == CANNON) {
            int fromRow = userMove[0], fromCol = userMove[1];
            boolean isStartingPosition = isStartingPosition(pieceType, fromRow, fromCol, side);
            if (isStartingPosition && scoreDiff >= STRATEGIC_THRESHOLD) {
                return true; // 开局阶段没有优先出动大子
            }
        }

        // 特征2：走子方向消极（往回走或原地踏步式走法）
        int fromRow = userMove[0], toRow = userMove[2];
        if (side > 0) { // 红方
            if (pieceType == PAWN || pieceType == KNIGHT || pieceType == ROOK) {
                if (toRow > fromRow && scoreDiff >= STRATEGIC_THRESHOLD) {
                    return true; // 红方大子往后退
                }
            }
        } else { // 黑方
            if (pieceType == PAWN || pieceType == KNIGHT || pieceType == ROOK) {
                if (toRow < fromRow && scoreDiff >= STRATEGIC_THRESHOLD) {
                    return true; // 黑方大子往后退
                }
            }
        }

        // 特征3：最佳走法与用户走法在完全不同的区域（战略判断失误）
        int bestCenterDist = Math.abs(bestMove[1] - 4) + Math.abs(bestMove[3] - 4);
        int userCenterDist = Math.abs(userMove[1] - 4) + Math.abs(userMove[3] - 4);
        if (bestCenterDist < userCenterDist - 2 && scoreDiff >= STRATEGIC_THRESHOLD) {
            return true; // 最佳走法控制中路，用户走法偏边
        }

        return false;
    }

    /**
     * 检测位置错误特征：棋子位置不当、机动性差。
     */
    private boolean detectPositionalError(int[][] board, int[] userMove, int[] bestMove,
                                           int scoreDiff, int[][] boardAfter) {
        int piece = board[userMove[0]][userMove[1]];
        int pieceType = Math.abs(piece);
        int side = piece > 0 ? 1 : -1;

        // 特征1：棋子走到边线或角落（机动性受限）
        int toCol = userMove[3];
        if ((pieceType == KNIGHT || pieceType == ROOK) && (toCol == 0 || toCol == 8)) {
            if (scoreDiff > NO_MISTAKE_THRESHOLD) {
                return true;
            }
        }

        // 特征2：马腿被憋（马的特殊位置问题）
        if (pieceType == KNIGHT) {
            int fromRow = userMove[0], fromCol = userMove[1];
            int toRow = userMove[2], toCol2 = userMove[3];
            int dr = toRow - fromRow, dc = toCol2 - fromCol;
            int blockR = fromRow + dr / 2, blockC = fromCol + dc / 2;
            if (board[blockR][blockC] != 0) {
                // 注意：这里检测的是用户走法是否被蹩马腿（不应该发生，因为是合法走法）
                // 但我们可以检查目标位置的马是否被蹩
            }
        }

        // 特征3：相/象飞到了不恰当的位置
        if (pieceType == BISHOP) {
            // 检查是否堵住了其他棋子的通路
            int toRow = userMove[2];
            boolean isOwnHalf = side > 0 ? (toRow >= 5) : (toRow <= 4);
            if (!isOwnHalf) {
                return true; // 相/象飞过河，严重位置错误
            }
        }

        // 特征4：仕/士走到不当位置（影响将/帅的安全）
        if (pieceType == ADVISOR) {
            int toRow = userMove[2];
            boolean inPalace = side > 0 ? (toRow >= 7 && toRow <= 9) : (toRow >= 0 && toRow <= 2);
            if (!inPalace) {
                return true; // 仕/士离开九宫，位置错误
            }
        }

        // 特征5：兵/卒过早进入不利位置
        if (pieceType == PAWN) {
            int toRow = userMove[2];
            boolean crossed = side > 0 ? (toRow <= 4) : (toRow >= 5);
            if (crossed) {
                // 过河兵，检查是否孤军深入
                int toCol3 = userMove[3];
                if ((toCol3 == 0 || toCol3 == 8) && scoreDiff > NO_MISTAKE_THRESHOLD) {
                    return true; // 过河兵走到边线，位置不佳
                }
            }
        }

        return false;
    }

    // ==================== 解释生成 ====================

    /**
     * 生成错误解释文本。
     */
    public String generateExplanation(MistakeType type, int[] userMove, int[] bestMove) {
        return generateExplanation(type, userMove, bestMove, 0, 0, 0, null);
    }

    /**
     * 生成详细的错误解释文本。
     */
    private String generateExplanation(MistakeType type, int[] userMove, int[] bestMove,
                                        int userCaptured, int bestCaptured,
                                        int scoreDiff, int[][] board) {
        StringBuilder sb = new StringBuilder();

        switch (type) {
            case TACTICAL_ERROR:
                sb.append("战术错误：");
                appendTacticalExplanation(sb, board, userMove, bestMove,
                        userCaptured, bestCaptured, scoreDiff);
                break;
            case STRATEGIC_ERROR:
                sb.append("战略错误：");
                appendStrategicExplanation(sb, board, userMove, bestMove, scoreDiff);
                break;
            case POSITIONAL_ERROR:
                sb.append("位置错误：");
                appendPositionalExplanation(sb, board, userMove, bestMove, scoreDiff);
                break;
            default:
                sb.append("这步棋没有明显问题。");
                break;
        }

        // 附加改进建议
        if (type != MistakeType.NO_MISTAKE) {
            sb.append("\n建议：");
            appendImprovementSuggestion(sb, type, board, userMove, bestMove);
        }

        return sb.toString();
    }

    private void appendTacticalExplanation(StringBuilder sb, int[][] board,
                                            int[] userMove, int[] bestMove,
                                            int userCaptured, int bestCaptured,
                                            int scoreDiff) {
        int piece = board != null ? board[userMove[0]][userMove[1]] : 0;
        int pieceType = Math.abs(piece);
        String pieceName = getPieceName(pieceType);

        if (scoreDiff >= PIECE_VALUES[ROOK]) {
            sb.append("这步棋导致严重的子力损失。");
        } else if (scoreDiff >= PIECE_VALUES[KNIGHT]) {
            sb.append("这步棋损失了一个中等价值的棋子。");
        } else {
            sb.append("这步棋在战术上吃亏了。");
        }

        if (userCaptured != 0) {
            sb.append("你吃掉了对方的").append(getPieceName(Math.abs(userCaptured))).append("，");
        } else {
            sb.append("你走了").append(pieceName).append("，");
        }

        sb.append("但更好的走法是");
        int bestPiece = board != null ? board[bestMove[0]][bestMove[1]] : 0;
        sb.append(getPieceName(Math.abs(bestPiece))).append("走到")
          .append(posToStr(bestMove[2], bestMove[3]));
        sb.append("。这样可以避免子力损失并保持局面优势。");
    }

    private void appendStrategicExplanation(StringBuilder sb, int[][] board,
                                            int[] userMove, int[] bestMove,
                                            int scoreDiff) {
        int piece = board != null ? board[userMove[0]][userMove[1]] : 0;
        int pieceType = Math.abs(piece);

        sb.append("这步棋在战略上不够理想。");

        if (pieceType == KNIGHT || pieceType == ROOK || pieceType == CANNON) {
            sb.append("开局阶段应优先出动大子（车、马、炮），控制要道。");
        } else if (pieceType == PAWN) {
            sb.append("兵/卒的推进时机和方向需要结合全局形势判断。");
        } else {
            sb.append("棋子的调动应服务于全局战略目标。");
        }

        int bestPiece = board != null ? board[bestMove[0]][bestMove[1]] : 0;
        sb.append("建议走").append(getPieceName(Math.abs(bestPiece))).append("到")
          .append(posToStr(bestMove[2], bestMove[3]));
        sb.append("，这样可以更好地控制局面。");
    }

    private void appendPositionalExplanation(StringBuilder sb, int[][] board,
                                              int[] userMove, int[] bestMove,
                                              int scoreDiff) {
        int piece = board != null ? board[userMove[0]][userMove[1]] : 0;
        int pieceType = Math.abs(piece);

        sb.append("这步棋的棋子位置不够理想。");

        switch (pieceType) {
            case KNIGHT:
                sb.append("马应尽量跳到中心位置，避免边线马（蹩马腿也会限制机动性）。");
                break;
            case ROOK:
                sb.append("车应占据要道（肋道或巡河线），保持高度机动性。");
                break;
            case CANNON:
                sb.append("炮需要炮架才能发挥威力，应寻找合适的架炮位置。");
                break;
            case BISHOP:
                sb.append("相/象应在己方半场内活动，守护中路和底线。");
                break;
            case ADVISOR:
                sb.append("仕/士应在九宫内贴身护卫将/帅。");
                break;
            case PAWN:
                sb.append("过河兵应选择能控制要点的位置，避免孤军深入边线。");
                break;
            default:
                sb.append("棋子应占据更有利的位置以发挥最大效能。");
                break;
        }

        int bestPiece = board != null ? board[bestMove[0]][bestMove[1]] : 0;
        sb.append("建议将").append(getPieceName(Math.abs(bestPiece)))
          .append("移到").append(posToStr(bestMove[2], bestMove[3]));
        sb.append("。");
    }

    private String generateNoMistakeExplanation(int[][] board, int[] userMove,
                                                 int[] bestMove, int scoreDiff) {
        if (scoreDiff <= 0) {
            return "这步棋走得很好，与最佳走法相当。";
        } else if (scoreDiff <= 30) {
            return "这步棋基本正确，只有微小的优化空间。";
        } else {
            return "这步棋没有明显问题，不过还有稍好的选择。";
        }
    }

    private void appendImprovementSuggestion(StringBuilder sb, MistakeType type,
                                              int[][] board, int[] userMove, int[] bestMove) {
        int bestPiece = board != null ? board[bestMove[0]][bestMove[1]] : 0;
        int pieceType = Math.abs(bestPiece);

        switch (type) {
            case TACTICAL_ERROR:
                sb.append("在走子前，仔细检查目标位置是否安全，");
                sb.append("以及吃子后是否会被对方反吃。");
                sb.append("推荐走法：").append(getPieceName(pieceType))
                  .append("从").append(posToStr(bestMove[0], bestMove[1]))
                  .append("到").append(posToStr(bestMove[2], bestMove[3]));
                break;
            case STRATEGIC_ERROR:
                sb.append("注意全局协调，优先出动大子，控制棋盘要道。");
                sb.append("推荐走法：").append(getPieceName(pieceType))
                  .append("从").append(posToStr(bestMove[0], bestMove[1]))
                  .append("到").append(posToStr(bestMove[2], bestMove[3]));
                break;
            case POSITIONAL_ERROR:
                sb.append("关注棋子的位置质量，确保每个棋子都能发挥最大效能。");
                sb.append("推荐走法：").append(getPieceName(pieceType))
                  .append("从").append(posToStr(bestMove[0], bestMove[1]))
                  .append("到").append(posToStr(bestMove[2], bestMove[3]));
                break;
            default:
                break;
        }
    }

    // ==================== 位置评估（与 ChineseChessAI 一致） ====================

    /**
     * 评估棋盘分数（正数对红方有利，负数对黑方有利）。
     * 综合子力价值与位置价值。
     */
    private int evaluateBoard(int[][] board) {
        int score = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                int type = Math.abs(piece);
                int value = PIECE_VALUES[type];
                value += getPositionBonus(type, r, c, piece > 0);
                if (piece > 0) score += value;
                else score -= value;
            }
        }
        // 机动性近似
        score += (countPseudoMoves(board, 1) - countPseudoMoves(board, -1)) * 2;
        return score;
    }

    private int getPositionBonus(int type, int row, int col, boolean isRed) {
        int centerCol = 4 - Math.abs(col - 4);
        switch (type) {
            case PAWN: {
                int bonus = 0;
                boolean crossed = isRed ? (row <= 4) : (row >= 5);
                if (crossed) bonus += 60;
                int advance = isRed ? (4 - row) : (row - 5);
                if (advance > 0) bonus += advance * 10;
                if (col >= 3 && col <= 5) bonus += 12;
                return bonus;
            }
            case KNIGHT: {
                int bonus = centerCol * 10;
                int advance = isRed ? (4 - row) : (row - 5);
                if (advance > 0) bonus += advance * 6;
                if (col == 0 || col == 8) bonus -= 12;
                if (row == 0 || row == 9) bonus -= 6;
                return bonus;
            }
            case ROOK:
                return (col >= 3 && col <= 5 ? 10 : 0)
                        + ((isRed ? (4 - row) : (row - 5)) > 0 ? 3 : 0);
            case CANNON:
                return (col >= 2 && col <= 6 ? 10 : 0)
                        + ((isRed ? (4 - row) : (row - 5)) > 0 ? 2 : 0);
            case ADVISOR:
                return (col == 4 ? 10 : (col == 3 || col == 5 ? 4 : 0));
            case BISHOP:
                return (isRed ? (row >= 4 ? 8 : 0) : (row <= 5 ? 8 : 0))
                        + centerCol * 2;
            case KING:
                return (col == 4 ? 8 : 0)
                        + (isRed ? (row >= 4 ? 4 : 0) : (row <= 5 ? 4 : 0));
        }
        return 0;
    }

    // ==================== 走法生成（轻量版） ====================

    private int countPseudoMoves(int[][] board, int side) {
        int count = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side > 0 && piece < 0) continue;
                if (side < 0 && piece > 0) continue;
                count += generatePieceMoves(board, r, c, piece).size();
            }
        }
        return count;
    }

    private List<int[]> generatePieceMoves(int[][] board, int fromR, int fromC, int piece) {
        List<int[]> moves = new ArrayList<>();
        int type = Math.abs(piece);
        boolean isRed = piece > 0;

        switch (type) {
            case KING:
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                break;
            case ADVISOR:
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC + 1, isRed);
                break;
            case BISHOP:
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC + 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC + 2, isRed);
                break;
            case KNIGHT:
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC + 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC + 2, isRed);
                break;
            case ROOK:
                addLineMoves(board, moves, fromR, fromC, isRed);
                break;
            case CANNON:
                addCannonMoves(board, moves, fromR, fromC, isRed);
                break;
            case PAWN:
                if (isRed) {
                    addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC, isRed);
                    if (fromR <= 4) {
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                    }
                } else {
                    addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC, isRed);
                    if (fromR >= 5) {
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                    }
                }
                break;
        }
        return moves;
    }

    private void addMoveIfValid(int[][] board, List<int[]> moves,
                                 int fromR, int fromC, int toR, int toC, boolean isRed) {
        if (toR < 0 || toR >= 10 || toC < 0 || toC >= 9) return;
        int target = board[toR][toC];
        if (isRed && target > 0) return;
        if (!isRed && target < 0) return;
        moves.add(new int[]{fromR, fromC, toR, toC});
    }

    private void addLineMoves(int[][] board, List<int[]> moves, int fromR, int fromC, boolean isRed) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            for (int i = 1; i < 10; i++) {
                int r = fromR + d[0] * i, c = fromC + d[1] * i;
                if (r < 0 || r >= 10 || c < 0 || c >= 9) break;
                int target = board[r][c];
                if (target == 0) {
                    moves.add(new int[]{fromR, fromC, r, c});
                } else {
                    if ((isRed && target < 0) || (!isRed && target > 0)) {
                        moves.add(new int[]{fromR, fromC, r, c});
                    }
                    break;
                }
            }
        }
    }

    private void addCannonMoves(int[][] board, List<int[]> moves, int fromR, int fromC, boolean isRed) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            boolean foundPlatform = false;
            for (int i = 1; i < 10; i++) {
                int r = fromR + d[0] * i, c = fromC + d[1] * i;
                if (r < 0 || r >= 10 || c < 0 || c >= 9) break;
                int target = board[r][c];
                if (!foundPlatform) {
                    if (target == 0) {
                        moves.add(new int[]{fromR, fromC, r, c});
                    } else {
                        foundPlatform = true;
                    }
                } else {
                    if (target != 0) {
                        if ((isRed && target < 0) || (!isRed && target > 0)) {
                            moves.add(new int[]{fromR, fromC, r, c});
                        }
                        break;
                    }
                }
            }
        }
    }

    // ==================== 攻击检测 ====================

    /**
     * 检查 (tr,tc) 是否被 side 方的棋子攻击。
     */
    private boolean isAttackedBySide(int[][] board, int tr, int tc, int side) {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side > 0 && piece < 0) continue;
                if (side < 0 && piece > 0) continue;
                if (attacksSquare(board, r, c, tr, tc)) return true;
            }
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

    private static int[] findKing(int[][] b, int side) {
        int target = side > 0 ? KING : -KING;
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
        // 白脸将（两将照面）
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

    // ==================== 工具方法 ====================

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    private boolean isStartingPosition(int pieceType, int row, int col, int side) {
        switch (pieceType) {
            case KNIGHT:
                return side > 0 ? (row == 9 && (col == 1 || col == 7))
                        : (row == 0 && (col == 1 || col == 7));
            case ROOK:
                return side > 0 ? (row == 9 && (col == 0 || col == 8))
                        : (row == 0 && (col == 0 || col == 8));
            case CANNON:
                return side > 0 ? (row == 7 && (col == 1 || col == 7))
                        : (row == 2 && (col == 1 || col == 7));
            default:
                return false;
        }
    }

    private static String getPieceName(int type) {
        switch (type) {
            case KING:   return "将/帅";
            case ADVISOR: return "仕/士";
            case BISHOP:  return "相/象";
            case KNIGHT:  return "马";
            case ROOK:    return "车";
            case CANNON:  return "炮";
            case PAWN:    return "兵/卒";
            default:      return "未知棋子";
        }
    }

    private static String posToStr(int row, int col) {
        return "(" + row + "," + col + ")";
    }
}
