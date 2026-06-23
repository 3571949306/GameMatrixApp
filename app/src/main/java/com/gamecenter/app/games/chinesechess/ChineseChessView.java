package com.gamecenter.app.games.chinesechess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋棋盘视图（UI 升级版）。
 *
 * <p>Canvas 绘制 9×10 象棋棋盘，支持棋子点击选择和移动。
 * 实现完整的棋子走法规则和将死检测。</p>
 *
 * <p>UI 升级要点：
 * <ul>
 *   <li>棋盘颜色资源化（支持深浅主题）</li>
 *   <li>棋子绘制升级：径向渐变背景 + 阴影 + 双圈边框</li>
 *   <li>添加"楚河汉界"文字</li>
 *   <li>添加列号坐标（1-9）</li>
 *   <li>选中棋子高亮 + 可移动位置提示（绿点）</li>
 *   <li>最后一手标记（红点）</li>
 *   <li>AI 思考指示</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 2.0
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

    // ==================== 回调接口 ====================

    public interface OnPlayerMoveListener { void onPlayerMove(); }
    public interface OnGameOverListener { void onGameOver(int winner); } // 1=红胜, 2=黑胜
    /** 落子音效回调：玩家或 AI 每次成功落子后触发，由 Activity 播放音效。 */
    public interface OnMoveSoundListener { void onMoveSound(); }

    // ==================== 游戏状态 ====================

    /** 棋盘状态：board[row][col]，正=红方，负=黑方，0=空 */
    private final int[][] board = new int[ROWS][COLS];

    /** 当前轮到哪方（1=红方/玩家，2=黑方/AI） */
    private int currentSide = 1;

    /** 选中的棋子位置 */
    private int selectedRow = -1, selectedCol = -1;

    /** 游戏是否结束 */
    private boolean gameOver = false;

    /** 最后一手落子位置（用于标记） */
    private int lastMoveFromRow = -1, lastMoveFromCol = -1;
    private int lastMoveToRow = -1, lastMoveToCol = -1;

    /** AI 是否正在思考 */
    private boolean aiThinking = false;

    /** 可移动位置列表（选中棋子后计算） */
    private final List<int[]> validMoves = new ArrayList<>();

    /** 走法历史栈，用于悔棋。每条记录：{fromRow, fromCol, toRow, toCol, capturedPiece} */
    private final List<int[]> moveHistory = new ArrayList<>();

    // ==================== 工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPiece = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPieceText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSelect = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLastMove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRiverText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCoord = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAiThinking = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ==================== 监听器 ====================

    private OnPlayerMoveListener playerMoveListener;
    private OnGameOverListener gameOverListener;
    private OnMoveSoundListener moveSoundListener;

    // ==================== 构造函数 ====================

    public ChineseChessView(@NonNull Context context) { super(context); init(); }
    public ChineseChessView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public ChineseChessView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(1.5f);
        paintPiece.setStyle(Paint.Style.FILL);
        paintPieceText.setTextAlign(Paint.Align.CENTER);
        paintPieceText.setFakeBoldText(true);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(2f);
        paintSelect.setStyle(Paint.Style.FILL);
        paintHint.setStyle(Paint.Style.FILL);
        paintLastMove.setStyle(Paint.Style.FILL);
        paintRiverText.setTextAlign(Paint.Align.CENTER);
        paintRiverText.setFakeBoldText(true);
        paintCoord.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 从资源加载颜色（支持深浅主题）。
     */
    private int color(int resId) {
        return getContext().getColor(resId);
    }

    // ==================== 监听器设置 ====================

    public void setOnPlayerMoveListener(OnPlayerMoveListener l) { this.playerMoveListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }
    public void setOnMoveSoundListener(OnMoveSoundListener l) { this.moveSoundListener = l; }

    /**
     * 设置 AI 思考状态（用于绘制思考指示）。
     */
    public void setAiThinking(boolean thinking) {
        this.aiThinking = thinking;
        invalidate();
    }

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

        // 记录走法历史（用于悔棋）
        moveHistory.add(new int[]{fromRow, fromCol, toRow, toCol, captured});

        // 记录最后一手
        lastMoveFromRow = fromRow;
        lastMoveFromCol = fromCol;
        lastMoveToRow = toRow;
        lastMoveToCol = toCol;

        // 落子音效
        if (moveSoundListener != null) moveSoundListener.onMoveSound();

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
        validMoves.clear();
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
        lastMoveFromRow = -1;
        lastMoveFromCol = -1;
        lastMoveToRow = -1;
        lastMoveToCol = -1;
        validMoves.clear();
        moveHistory.clear();
        invalidate();
    }

    /**
     * 悔棋：撤销最近的一步或两步（玩家+AI各一步）。
     * 仅在非 AI 思考、非游戏结束、且有走法历史时可用。
     *
     * @return true 表示悔棋成功
     */
    public boolean undoMove() {
        if (gameOver || aiThinking) return false;
        if (moveHistory.isEmpty()) return false;

        // 当前轮到玩家（红方），说明上一步是 AI 走的，需要撤销两步（AI + 玩家）
        // 当前轮到 AI（黑方），说明上一步是玩家走的，AI 还未走，只需撤销一步
        int stepsToUndo = (currentSide == 1) ? 2 : 1;
        if (stepsToUndo > moveHistory.size()) {
            stepsToUndo = moveHistory.size();
        }

        for (int i = 0; i < stepsToUndo; i++) {
            int[] last = moveHistory.remove(moveHistory.size() - 1);
            int fromR = last[0], fromC = last[1];
            int toR = last[2], toC = last[3];
            int captured = last[4];
            // 还原棋子位置
            board[fromR][fromC] = board[toR][toC];
            board[toR][toC] = captured;
        }

        // 更新最后一手标记
        if (!moveHistory.isEmpty()) {
            int[] last = moveHistory.get(moveHistory.size() - 1);
            lastMoveFromRow = last[0];
            lastMoveFromCol = last[1];
            lastMoveToRow = last[2];
            lastMoveToCol = last[3];
        } else {
            lastMoveFromRow = -1;
            lastMoveFromCol = -1;
            lastMoveToRow = -1;
            lastMoveToCol = -1;
        }

        // 悔棋后轮到玩家
        currentSide = 1;
        selectedRow = -1;
        selectedCol = -1;
        validMoves.clear();
        invalidate();
        return true;
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

        // 背景（资源化，支持深浅主题）
        int bgColor = color(R.color.chinese_chess_board_bg);
        paintBg.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, paintBg);

        // 棋盘线（资源化）
        int lineColor = color(R.color.chinese_chess_line);
        paintLine.setColor(lineColor);

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
        // 外框（加粗）
        paintLine.setStrokeWidth(3f);
        canvas.drawRect(offsetX, offsetY, offsetX + (COLS - 1) * cellSize, offsetY + (ROWS - 1) * cellSize, paintLine);
        paintLine.setStrokeWidth(1.5f);
        // 九宫斜线
        canvas.drawLine(offsetX + 3 * cellSize, offsetY, offsetX + 5 * cellSize, offsetY + 2 * cellSize, paintLine);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY, offsetX + 3 * cellSize, offsetY + 2 * cellSize, paintLine);
        canvas.drawLine(offsetX + 3 * cellSize, offsetY + 7 * cellSize, offsetX + 5 * cellSize, offsetY + 9 * cellSize, paintLine);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY + 7 * cellSize, offsetX + 3 * cellSize, offsetY + 9 * cellSize, paintLine);

        // 楚河汉界
        int riverColor = color(R.color.chinese_chess_river_text);
        paintRiverText.setColor(riverColor);
        paintRiverText.setTextSize(cellSize * 0.5f);
        float riverY = offsetY + 4.5f * cellSize;
        float riverCenterY = riverY - (paintRiverText.ascent() + paintRiverText.descent()) / 2f;
        // 楚河（左半）
        canvas.drawText("楚 河", offsetX + 1.5f * cellSize, riverCenterY, paintRiverText);
        // 汉界（右半）
        canvas.drawText("漢 界", offsetX + 6.5f * cellSize, riverCenterY, paintRiverText);

        // 列号坐标（顶部和底部）
        int coordColor = color(R.color.chinese_chess_coord);
        paintCoord.setColor(coordColor);
        paintCoord.setTextSize(cellSize * 0.22f);
        String[] redCoords = {"9", "8", "7", "6", "5", "4", "3", "2", "1"}; // 红方视角（下方）
        String[] blackCoords = {"1", "2", "3", "4", "5", "6", "7", "8", "9"}; // 黑方视角（上方）
        for (int c = 0; c < COLS; c++) {
            float x = offsetX + c * cellSize;
            // 顶部（黑方视角）
            float topY = offsetY - cellSize * 0.35f;
            canvas.drawText(blackCoords[c], x, topY - (paintCoord.ascent() + paintCoord.descent()) / 2f, paintCoord);
            // 底部（红方视角）
            float botY = offsetY + 9 * cellSize + cellSize * 0.35f;
            canvas.drawText(redCoords[c], x, botY - (paintCoord.ascent() + paintCoord.descent()) / 2f, paintCoord);
        }

        // 最后一手标记（淡红色圆点）
        if (lastMoveToRow >= 0 && lastMoveToCol >= 0) {
            float lx = offsetX + lastMoveToCol * cellSize;
            float ly = offsetY + lastMoveToRow * cellSize;
            int lastColor = color(R.color.chinese_chess_last_move);
            paintLastMove.setColor(lastColor);
            canvas.drawCircle(lx, ly, cellSize * 0.12f, paintLastMove);
        }

        // 选中高亮 + 可移动位置提示
        if (selectedRow >= 0 && selectedCol >= 0) {
            float sx = offsetX + selectedCol * cellSize;
            float sy = offsetY + selectedRow * cellSize;
            int selColor = color(R.color.chinese_chess_selected);
            paintSelect.setColor(selColor);
            canvas.drawCircle(sx, sy, cellSize * 0.48f, paintSelect);

            // 绘制可移动位置提示（绿点）
            int hintColor = color(R.color.chinese_chess_hint);
            paintHint.setColor(hintColor);
            for (int[] move : validMoves) {
                float hx = offsetX + move[1] * cellSize;
                float hy = offsetY + move[0] * cellSize;
                // 如果目标位置有敌方棋子，画空心圆（吃子提示）；否则画实心圆
                if (board[move[0]][move[1]] != 0) {
                    paintHint.setStyle(Paint.Style.STROKE);
                    paintHint.setStrokeWidth(3f);
                    canvas.drawCircle(hx, hy, cellSize * 0.45f, paintHint);
                    paintHint.setStyle(Paint.Style.FILL);
                } else {
                    canvas.drawCircle(hx, hy, cellSize * 0.15f, paintHint);
                }
            }
        }

        // 绘制棋子（升级：渐变背景 + 阴影 + 双圈边框）
        float pieceRadius = cellSize * 0.42f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;

                float px = offsetX + c * cellSize;
                float py = offsetY + r * cellSize;

                // 棋子阴影
                paintPiece.setShadowLayer(4f, 2f, 2f, 0x66000000);
                paintPiece.setColor(0xFFFFF8E1);
                canvas.drawCircle(px, py, pieceRadius, paintPiece);
                paintPiece.setShadowLayer(0, 0, 0, 0);

                // 棋子径向渐变背景（立体感）
                int pieceBgStart = color(R.color.chinese_chess_piece_bg_start);
                int pieceBgEnd = color(R.color.chinese_chess_piece_bg_end);
                RadialGradient gradient = new RadialGradient(
                        px - pieceRadius * 0.3f, py - pieceRadius * 0.3f, pieceRadius * 1.2f,
                        pieceBgStart, pieceBgEnd,
                        Shader.TileMode.CLAMP);
                paintPiece.setShader(gradient);
                canvas.drawCircle(px, py, pieceRadius, paintPiece);
                paintPiece.setShader(null);

                // 棋子边框（双圈）
                boolean isRed = piece > 0;
                int borderColor = isRed ? color(R.color.chinese_chess_red) : color(R.color.chinese_chess_black);
                paintBorder.setColor(borderColor);
                paintBorder.setStrokeWidth(2.5f);
                canvas.drawCircle(px, py, pieceRadius, paintBorder);
                paintBorder.setStrokeWidth(1.2f);
                canvas.drawCircle(px, py, pieceRadius * 0.85f, paintBorder);

                // 棋子文字
                paintPieceText.setColor(borderColor);
                paintPieceText.setTextSize(pieceRadius * 1.1f);
                String text = getPieceText(piece);
                float textY = py - (paintPieceText.ascent() + paintPieceText.descent()) / 2f;
                canvas.drawText(text, px, textY, paintPieceText);
            }
        }

        // AI 思考指示（顶部）
        if (aiThinking) {
            int thinkColor = color(R.color.chinese_chess_ai_thinking);
            paintAiThinking.setColor(thinkColor);
            paintAiThinking.setStyle(Paint.Style.FILL);
            String thinkText = getContext().getString(R.string.chinese_chess_ai_thinking);
            paintAiThinking.setTextSize(cellSize * 0.28f);
            paintAiThinking.setTextAlign(Paint.Align.CENTER);
            paintAiThinking.setFakeBoldText(true);
            float textWidth = paintAiThinking.measureText(thinkText);
            float padX = cellSize * 0.3f;
            float padY = cellSize * 0.15f;
            float left = (w - textWidth) / 2f - padX;
            float right = (w + textWidth) / 2f + padX;
            float top = offsetY - cellSize * 0.8f;
            float bottom = top + paintAiThinking.getTextSize() + padY * 2;
            RectF bgRect = new RectF(left, top, right, bottom);
            paintAiThinking.setAlpha(60);
            canvas.drawRoundRect(bgRect, cellSize * 0.15f, cellSize * 0.15f, paintAiThinking);
            paintAiThinking.setAlpha(255);
            float textY = top + padY - paintAiThinking.ascent();
            canvas.drawText(thinkText, w / 2f, textY, paintAiThinking);
            paintAiThinking.setFakeBoldText(false);
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
        // AI 思考时禁止操作
        if (aiThinking) return true;

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
                computeValidMoves();
            } else {
                // 尝试移动到目标位置
                if (isValidMove(selectedRow, selectedCol, row, col)) {
                    int captured = board[row][col];
                    board[row][col] = board[selectedRow][selectedCol];
                    board[selectedRow][selectedCol] = 0;

                    // 记录走法历史（用于悔棋）
                    moveHistory.add(new int[]{selectedRow, selectedCol, row, col, captured});

                    // 记录最后一手
                    lastMoveFromRow = selectedRow;
                    lastMoveFromCol = selectedCol;
                    lastMoveToRow = row;
                    lastMoveToCol = col;

                    // 落子音效
                    if (moveSoundListener != null) moveSoundListener.onMoveSound();

                    // 检查是否吃掉对方将
                    if (Math.abs(captured) == KING) {
                        gameOver = true;
                        selectedRow = -1;
                        selectedCol = -1;
                        validMoves.clear();
                        invalidate();
                        if (gameOverListener != null) gameOverListener.onGameOver(1);
                        return true;
                    }

                    currentSide = 2;
                    selectedRow = -1;
                    selectedCol = -1;
                    validMoves.clear();
                    invalidate();

                    // 通知玩家走棋完成，触发 AI
                    if (playerMoveListener != null) playerMoveListener.onPlayerMove();
                } else {
                    selectedRow = -1;
                    selectedCol = -1;
                    validMoves.clear();
                }
            }
        } else {
            // 选择棋子
            if (piece > 0) {
                selectedRow = row;
                selectedCol = col;
                computeValidMoves();
            }
        }

        invalidate();
        return true;
    }

    /**
     * 计算当前选中棋子的所有合法走法（用于绘制提示）。
     */
    private void computeValidMoves() {
        validMoves.clear();
        if (selectedRow < 0 || selectedCol < 0) return;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (r == selectedRow && c == selectedCol) continue;
                if (isValidMove(selectedRow, selectedCol, r, c)) {
                    validMoves.add(new int[]{r, c});
                }
            }
        }
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
