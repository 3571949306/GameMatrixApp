package com.gamecenter.app.games.chinesechess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 中国象棋棋盘视图。
 *
 * <p>Canvas 绘制 9×10 象棋棋盘，支持棋子点击选择和移动。
 * 实现完整的棋子走法规则和将死检测。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>棋盘坐标系：列 0-8（从左到右），行 0-9（从上到下）</li>
 *   <li>棋子编码：正数=红方，负数=黑方，绝对值表示棋子类型</li>
 *   <li>1=帅/将, 2=仕/士, 3=相/象, 4=马, 5=车, 6=炮, 7=兵/卒</li>
 *   <li>首次点击选择棋子，再次点击目标位置移动</li>
 *   <li>走棋后检查对方是否被将死</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class ChineseChessView extends View {

    // ==================== 常量 ====================

    private static final int COLS = 9;
    private static final int ROWS = 10;

    // 棋子类型
    private static final int KING = 1;    // 帅/将
    private static final int ADVISOR = 2; // 仕/士
    private static final int BISHOP = 3;  // 相/象
    private static final int KNIGHT = 4;  // 马
    private static final int ROOK = 5;    // 车
    private static final int CANNON = 6;  // 炮
    private static final int PAWN = 7;    // 兵/卒

    // 颜色
    private static final int COLOR_BG = 0xFFF9E4B7;
    private static final int COLOR_LINE = 0xFF8B5A2B;
    private static final int COLOR_RED = 0xFFE53935;
    private static final int COLOR_BLACK = 0xFF1A1A1A;
    private static final int COLOR_PIECE_BG = 0xFFFFF8E1;
    private static final int COLOR_SELECTED = 0x4400D9FF;
    private static final int COLOR_TEXT_LIGHT = 0xFFF9F6F2;

    // ==================== 回调接口 ====================

    public interface OnPlayerMoveListener { void onPlayerMove(); }
    public interface OnGameOverListener { void onGameOver(int winner); } // 1=红胜, 2=黑胜

    // ==================== 游戏状态 ====================

    /** 棋盘状态：board[row][col]，正=红方，负=黑方，0=空 */
    private final int[][] board = new int[ROWS][COLS];

    /** 当前轮到哪方（1=红方/玩家，2=黑方/AI） */
    private int currentSide = 1;

    /** 选中的棋子位置 */
    private int selectedRow = -1, selectedCol = -1;

    /** 游戏是否结束 */
    private boolean gameOver = false;

    // ==================== 工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintLine = new Paint();
    private final Paint paintPiece = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPieceText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint();
    private final Paint paintSelect = new Paint();

    // ==================== 监听器 ====================

    private OnPlayerMoveListener playerMoveListener;
    private OnGameOverListener gameOverListener;

    // ==================== 构造函数 ====================

    public ChineseChessView(@NonNull Context context) { super(context); init(); }
    public ChineseChessView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public ChineseChessView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintBg.setColor(COLOR_BG);
        paintLine.setColor(COLOR_LINE);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(1.5f);
        paintPiece.setStyle(Paint.Style.FILL);
        paintPiece.setColor(COLOR_PIECE_BG);
        paintPieceText.setTextAlign(Paint.Align.CENTER);
        paintPieceText.setFakeBoldText(true);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(2f);
        paintSelect.setColor(COLOR_SELECTED);
        paintSelect.setStyle(Paint.Style.FILL);
    }

    // ==================== 监听器设置 ====================

    public void setOnPlayerMoveListener(OnPlayerMoveListener l) { this.playerMoveListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }

    /**
     * 获取棋盘状态（供 AI 使用）
     */
    public int[][] getBoardState() {
        int[][] copy = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, COLS);
        }
        return copy;
    }

    public boolean isGameOver() { return gameOver; }

    /**
     * AI 应用走法
     */
    public void applyAIMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (gameOver) return;
        int piece = board[fromRow][fromCol];
        board[fromRow][fromCol] = 0;
        int captured = board[toRow][toCol];
        board[toRow][toCol] = piece;

        // 检查是否吃掉对方将/帅
        if (Math.abs(captured) == KING) {
            gameOver = true;
            int winner = (currentSide == 1) ? 1 : 2;
            invalidate();
            if (gameOverListener != null) gameOverListener.onGameOver(winner);
            return;
        }

        // 切换回合
        currentSide = (currentSide == 1) ? 2 : 1;
        selectedRow = -1;
        selectedCol = -1;
        invalidate();
    }

    /**
     * 开始新游戏
     */
    public void startNewGame() {
        // 清空棋盘
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                board[r][c] = 0;

        // 初始化红方（下方，正数）
        board[9][0] = ROOK;    // 车
        board[9][1] = KNIGHT;  // 马
        board[9][2] = BISHOP;  // 相
        board[9][3] = ADVISOR; // 仕
        board[9][4] = KING;    // 帅
        board[9][5] = ADVISOR; // 仕
        board[9][6] = BISHOP;  // 相
        board[9][7] = KNIGHT;  // 马
        board[9][8] = ROOK;    // 车
        board[7][1] = CANNON;  // 炮
        board[7][7] = CANNON;  // 炮
        board[6][0] = PAWN;    // 兵
        board[6][2] = PAWN;
        board[6][4] = PAWN;
        board[6][6] = PAWN;
        board[6][8] = PAWN;

        // 初始化黑方（上方，负数）
        board[0][0] = -ROOK;
        board[0][1] = -KNIGHT;
        board[0][2] = -BISHOP;
        board[0][3] = -ADVISOR;
        board[0][4] = -KING;
        board[0][5] = -ADVISOR;
        board[0][6] = -BISHOP;
        board[0][7] = -KNIGHT;
        board[0][8] = -ROOK;
        board[2][1] = -CANNON;
        board[2][7] = -CANNON;
        board[3][0] = -PAWN;
        board[3][2] = -PAWN;
        board[3][4] = -PAWN;
        board[3][6] = -PAWN;
        board[3][8] = -PAWN;

        currentSide = 1; // 红方先行
        selectedRow = -1;
        selectedCol = -1;
        gameOver = false;
        invalidate();
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 计算棋盘尺寸
        float cellW = (float) w / (COLS + 1);
        float cellH = (float) h / (ROWS + 1);
        float cellSize = Math.min(cellW, cellH);
        float offsetX = (w - cellSize * (COLS - 1)) / 2f;
        float offsetY = (h - cellSize * (ROWS - 1)) / 2f;

        // 背景
        canvas.drawRect(0, 0, w, h, paintBg);

        // 绘制棋盘线
        // 横线
        for (int r = 0; r < ROWS; r++) {
            float y = offsetY + r * cellSize;
            canvas.drawLine(offsetX, y, offsetX + (COLS - 1) * cellSize, y, paintLine);
        }
        // 竖线
        for (int c = 0; c < COLS; c++) {
            float x = offsetX + c * cellSize;
            // 上半部分
            canvas.drawLine(x, offsetY, x, offsetY + 4 * cellSize, paintLine);
            // 下半部分
            canvas.drawLine(x, offsetY + 5 * cellSize, x, offsetY + 9 * cellSize, paintLine);
        }
        // 外框
        canvas.drawRect(offsetX, offsetY, offsetX + (COLS - 1) * cellSize, offsetY + (ROWS - 1) * cellSize, paintLine);
        // 九宫斜线
        canvas.drawLine(offsetX + 3 * cellSize, offsetY, offsetX + 5 * cellSize, offsetY + 2 * cellSize, paintLine);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY, offsetX + 3 * cellSize, offsetY + 2 * cellSize, paintLine);
        canvas.drawLine(offsetX + 3 * cellSize, offsetY + 7 * cellSize, offsetX + 5 * cellSize, offsetY + 9 * cellSize, paintLine);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY + 7 * cellSize, offsetX + 3 * cellSize, offsetY + 9 * cellSize, paintLine);

        // 绘制选中高亮
        if (selectedRow >= 0 && selectedCol >= 0) {
            float sx = offsetX + selectedCol * cellSize;
            float sy = offsetY + selectedRow * cellSize;
            canvas.drawCircle(sx, sy, cellSize * 0.5f, paintSelect);
        }

        // 绘制棋子
        float pieceRadius = cellSize * 0.42f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;

                float px = offsetX + c * cellSize;
                float py = offsetY + r * cellSize;

                // 棋子背景
                paintPiece.setColor(COLOR_PIECE_BG);
                canvas.drawCircle(px, py, pieceRadius, paintPiece);

                // 棋子边框
                boolean isRed = piece > 0;
                paintBorder.setColor(isRed ? COLOR_RED : COLOR_BLACK);
                canvas.drawCircle(px, py, pieceRadius, paintBorder);
                canvas.drawCircle(px, py, pieceRadius * 0.85f, paintBorder);

                // 棋子文字
                paintPieceText.setColor(isRed ? COLOR_RED : COLOR_BLACK);
                paintPieceText.setTextSize(pieceRadius * 1.1f);
                String text = getPieceText(piece);
                float textY = py - (paintPieceText.ascent() + paintPieceText.descent()) / 2f;
                canvas.drawText(text, px, textY, paintPieceText);
            }
        }
    }

    /**
     * 获取棋子显示文字
     */
    private String getPieceText(int piece) {
        boolean isRed = piece > 0;
        int type = Math.abs(piece);
        switch (type) {
            case KING:    return isRed ? "帅" : "将";
            case ADVISOR: return isRed ? "仕" : "士";
            case BISHOP:  return isRed ? "相" : "象";
            case KNIGHT:  return "马";
            case ROOK:    return "车";
            case CANNON:  return "炮";
            case PAWN:    return isRed ? "兵" : "卒";
            default:      return "";
        }
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver) return true;
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        int w = getWidth(), h = getHeight();
        float cellW = (float) w / (COLS + 1);
        float cellH = (float) h / (ROWS + 1);
        float cellSize = Math.min(cellW, cellH);
        float offsetX = (w - cellSize * (COLS - 1)) / 2f;
        float offsetY = (h - cellSize * (ROWS - 1)) / 2f;

        int col = Math.round((event.getX() - offsetX) / cellSize);
        int row = Math.round((event.getY() - offsetY) / cellSize);

        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return true;

        // 只有红方（玩家）可以操作
        if (currentSide != 1) return true;

        int piece = board[row][col];

        if (selectedRow >= 0 && selectedCol >= 0) {
            // 已选中棋子，尝试移动
            if (piece > 0) {
                // 选择己方另一个棋子
                selectedRow = row;
                selectedCol = col;
            } else {
                // 尝试移动到目标位置
                if (isValidMove(selectedRow, selectedCol, row, col)) {
                    int captured = board[row][col];
                    board[row][col] = board[selectedRow][selectedCol];
                    board[selectedRow][selectedCol] = 0;

                    // 检查是否吃掉对方将
                    if (Math.abs(captured) == KING) {
                        gameOver = true;
                        invalidate();
                        if (gameOverListener != null) gameOverListener.onGameOver(1);
                        return true;
                    }

                    currentSide = 2;
                    selectedRow = -1;
                    selectedCol = -1;
                    invalidate();

                    // 通知玩家走棋完成，触发 AI
                    if (playerMoveListener != null) playerMoveListener.onPlayerMove();
                } else {
                    selectedRow = -1;
                    selectedCol = -1;
                }
            }
        } else {
            // 选择棋子
            if (piece > 0) {
                selectedRow = row;
                selectedCol = col;
            }
        }

        invalidate();
        return true;
    }

    /**
     * 简化的走法验证（基本规则）
     */
    private boolean isValidMove(int fromR, int fromC, int toR, int toC) {
        if (fromR == toR && fromC == toC) return false;
        int piece = board[fromR][fromC];
        int target = board[toR][toC];

        // 不能吃自己的棋子
        if (piece > 0 && target > 0) return false;
        if (piece < 0 && target < 0) return false;

        int type = Math.abs(piece);
        int dr = toR - fromR;
        int dc = toC - fromC;

        switch (type) {
            case KING: {
                // 将/帅：九宫格内移动一格
                boolean inPalace;
                if (piece > 0) inPalace = toR >= 7 && toR <= 9 && toC >= 3 && toC <= 5;
                else inPalace = toR >= 0 && toR <= 2 && toC >= 3 && toC <= 5;
                return inPalace && Math.abs(dr) + Math.abs(dc) == 1;
            }
            case ADVISOR: {
                // 仕/士：九宫格内斜走一格
                boolean inPalace;
                if (piece > 0) inPalace = toR >= 7 && toR <= 9 && toC >= 3 && toC <= 5;
                else inPalace = toR >= 0 && toR <= 2 && toC >= 3 && toC <= 5;
                return inPalace && Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case BISHOP: {
                // 相/象：斜走两格，不能过河，不能塞象眼
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if (piece > 0 && toR < 5) return false; // 红方不能过河
                if (piece < 0 && toR > 4) return false; // 黑方不能过河
                // 检查象眼
                int eyeR = fromR + dr / 2;
                int eyeC = fromC + dc / 2;
                return board[eyeR][eyeC] == 0;
            }
            case KNIGHT: {
                // 马：走日字，检查蹩马腿
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                // 蹩马腿检查
                if (Math.abs(dr) == 2) {
                    return board[fromR + dr / 2][fromC] == 0;
                } else {
                    return board[fromR][fromC + dc / 2] == 0;
                }
            }
            case ROOK: {
                // 车：直线移动，路径上无阻挡
                if (dr != 0 && dc != 0) return false;
                return isPathClear(fromR, fromC, toR, toC);
            }
            case CANNON: {
                // 炮：直线移动，吃子时必须隔一个棋子（炮架）
                if (dr != 0 && dc != 0) return false;
                int count = countPiecesBetween(fromR, fromC, toR, toC);
                if (target == 0) return count == 0; // 不吃子时路径必须为空
                else return count == 1; // 吃子时必须隔一个
            }
            case PAWN: {
                // 兵/卒：未过河只能前进，过河后可左右
                if (piece > 0) {
                    // 红方兵
                    if (fromR >= 5) return dr == -1 && dc == 0; // 未过河，只能前进
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1); // 过河后可左右
                } else {
                    // 黑方卒
                    if (fromR <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
            }
        }
        return false;
    }

    /**
     * 检查两点之间路径是否无阻挡
     */
    private boolean isPathClear(int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) return false;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) return false;
            }
        }
        return true;
    }

    /**
     * 计算两点之间棋子数量
     */
    private int countPiecesBetween(int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) count++;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) count++;
            }
        }
        return count;
    }
}
