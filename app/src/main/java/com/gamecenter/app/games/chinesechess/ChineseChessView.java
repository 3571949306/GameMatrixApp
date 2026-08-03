package com.gamecenter.app.games.chinesechess;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
    
    private int aiCheckRow = -1;
    private int aiCheckCol = -1;

    /** 当前提示走法 [fromR, fromC, toR, toC] */
    private int[] hintMove;

    /** 增强提示：起点位置 */
    private int hintFromRow = -1, hintFromCol = -1;
    /** 增强提示：终点位置 */
    private int hintToRow = -1, hintToCol = -1;
    /** 增强提示：是否激活 */
    private boolean enhancedHintActive = false;

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
    private final Paint paintCheck = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 木纹背景缓存（按尺寸生成一次） */
    private Bitmap woodBitmap;
    private android.graphics.Shader woodShader;

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
        paintCheck.setStyle(Paint.Style.STROKE);
        paintCheck.setStrokeWidth(4f);
        paintCheck.setColor(0xFFE53935);
        paintCheck.setShadowLayer(8f, 0f, 0f, 0xFFE53935);
        // 无障碍支持
        setContentDescription("中国象棋棋盘，红方先行");
        setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
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
     * 设置选中棋子位置（供外部调用，如提示执行）。
     *
     * @param row 行号（0-9）
     * @param col 列号（0-8）
     */
    public void setSelected(int row, int col) {
        this.selectedRow = row;
        this.selectedCol = col;
        computeValidMoves();
        // 更新无障碍描述
        int piece = (row >= 0 && row < ROWS && col >= 0 && col < COLS) ? board[row][col] : 0;
        String desc = piece != 0
                ? "选中棋子 " + getPieceName(Math.abs(piece), piece > 0) + "，轮到" + (currentSide == 1 ? "红方" : "黑方")
                : "中国象棋棋盘，轮到" + (currentSide == 1 ? "红方" : "黑方");
        setContentDescription(desc);
        invalidate();
    }
    
    public int getCurrentSide() {
        return currentSide;
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

        // 红方被将死/困毙 → 黑方（AI）胜
        if (currentSide == 1 && isDefeated(1)) {
            gameOver = true;
            if (gameOverListener != null) gameOverListener.onGameOver(2);
        }
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

        // 木纹背景层（在棋盘区域内绘制，模拟真实棋盘木纹）
        drawWoodGrain(canvas, w, h, cellSize, offsetX, offsetY);

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

        // 将军指示：被将军的将/帅画红色发光圈
        int[] checkedKing = getCheckedKingPosition();
        if (checkedKing != null) {
            float cx = offsetX + checkedKing[1] * cellSize;
            float cy = offsetY + checkedKing[0] * cellSize;
            canvas.drawCircle(cx, cy, pieceRadius * 1.15f, paintCheck);
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

        // 绘制增强提示（起点绿色高亮 + 终点蓝色高亮 + 箭头）
        if (enhancedHintActive && hintFromRow >= 0 && hintFromCol >= 0 && hintToRow >= 0 && hintToCol >= 0) {
            float fromX = offsetX + hintFromCol * cellSize;
            float fromY = offsetY + hintFromRow * cellSize;
            float toX = offsetX + hintToCol * cellSize;
            float toY = offsetY + hintToRow * cellSize;

            // 起点：绿色半透明圆圈
            Paint paintFromHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintFromHighlight.setColor(Color.argb(120, 76, 175, 80));
            paintFromHighlight.setStyle(Paint.Style.FILL);
            canvas.drawCircle(fromX, fromY, cellSize * 0.48f, paintFromHighlight);
            // 起点：绿色描边
            paintFromHighlight.setColor(Color.argb(200, 76, 175, 80));
            paintFromHighlight.setStyle(Paint.Style.STROKE);
            paintFromHighlight.setStrokeWidth(cellSize * 0.06f);
            canvas.drawCircle(fromX, fromY, cellSize * 0.48f, paintFromHighlight);

            // 终点：蓝色半透明圆圈
            Paint paintToHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintToHighlight.setColor(Color.argb(120, 33, 150, 243));
            paintToHighlight.setStyle(Paint.Style.FILL);
            canvas.drawCircle(toX, toY, cellSize * 0.48f, paintToHighlight);
            // 终点：蓝色描边
            paintToHighlight.setColor(Color.argb(200, 33, 150, 243));
            paintToHighlight.setStyle(Paint.Style.STROKE);
            paintToHighlight.setStrokeWidth(cellSize * 0.06f);
            canvas.drawCircle(toX, toY, cellSize * 0.48f, paintToHighlight);

            // 箭头：从起点到终点
            Paint paintArrow = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintArrow.setColor(Color.argb(220, 76, 175, 80));
            paintArrow.setStyle(Paint.Style.STROKE);
            paintArrow.setStrokeWidth(cellSize * 0.08f);
            paintArrow.setStrokeCap(Paint.Cap.ROUND);

            // 计算箭头方向
            float dx = toX - fromX;
            float dy = toY - fromY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0) {
                float unitX = dx / length;
                float unitY = dy / length;

                // 箭头起点偏移（避免与起点圆圈重叠）
                float arrowStartX = fromX + unitX * cellSize * 0.5f;
                float arrowStartY = fromY + unitY * cellSize * 0.5f;
                // 箭头终点偏移（避免与终点圆圈重叠）
                float arrowEndX = toX - unitX * cellSize * 0.5f;
                float arrowEndY = toY - unitY * cellSize * 0.5f;

                // 绘制箭头线
                canvas.drawLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY, paintArrow);

                // 绘制箭头头部（V形）
                float arrowHeadSize = cellSize * 0.18f;
                float perpX = -unitY * arrowHeadSize;
                float perpY = unitX * arrowHeadSize;
                Paint paintArrowHead = new Paint(Paint.ANTI_ALIAS_FLAG);
                paintArrowHead.setColor(Color.argb(220, 76, 175, 80));
                paintArrowHead.setStyle(Paint.Style.FILL);
                canvas.drawLine(arrowEndX, arrowEndY,
                        arrowEndX - unitX * arrowHeadSize * 1.5f + perpX,
                        arrowEndY - unitY * arrowHeadSize * 1.5f + perpY, paintArrow);
                canvas.drawLine(arrowEndX, arrowEndY,
                        arrowEndX - unitX * arrowHeadSize * 1.5f - perpX,
                        arrowEndY - unitY * arrowHeadSize * 1.5f - perpY, paintArrow);
            }
        }
        // 兼容旧版提示路径
        else if (hintMove != null && hintMove.length == 4) {
            Paint paintHintLegacy = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintHintLegacy.setColor(Color.argb(180, 76, 175, 80)); // 绿色透明
            paintHintLegacy.setStyle(Paint.Style.STROKE);
            paintHintLegacy.setStrokeWidth(cellSize * 0.1f);

            float startX = offsetX + hintMove[1] * cellSize;
            float startY = offsetY + hintMove[0] * cellSize;
            float endX = offsetX + hintMove[3] * cellSize;
            float endY = offsetY + hintMove[2] * cellSize;

            canvas.drawLine(startX, startY, endX, endY, paintHintLegacy);

            // 终点画个圆环
            paintHintLegacy.setStyle(Paint.Style.STROKE);
            paintHintLegacy.setStrokeWidth(cellSize * 0.08f);
            canvas.drawCircle(endX, endY, cellSize * 0.4f, paintHintLegacy);
        }
    }

    /**
     * 绘制棋盘木纹背景：在棋盘矩形区域内绘制细密的木纹线条。
     * 使用 Bitmap 缓存，按尺寸生成一次后复用。
     */
    private void drawWoodGrain(@NonNull Canvas canvas, int w, int h,
                               float cellSize, float offsetX, float offsetY) {
        // 计算棋盘外框
        float boardLeft = offsetX - cellSize * 0.4f;
        float boardTop = offsetY - cellSize * 0.4f;
        float boardRight = offsetX + (COLS - 1) * cellSize + cellSize * 0.4f;
        float boardBottom = offsetY + (ROWS - 1) * cellSize + cellSize * 0.4f;
        int boardW = Math.max(1, (int) (boardRight - boardLeft));
        int boardH = Math.max(1, (int) (boardBottom - boardTop));

        if (woodBitmap == null || woodBitmap.getWidth() != boardW || woodBitmap.getHeight() != boardH) {
            // P0 内存泄漏修复：尺寸变化时先回收旧 Bitmap，避免原生内存堆积。
            // Bitmap 原生内存不计入 Java heap，GC 回收滞后，长会话多次尺寸变化会持续增长 PSS。
            if (woodBitmap != null && !woodBitmap.isRecycled()) {
                woodBitmap.recycle();
            }
            woodBitmap = createWoodBitmap(boardW, boardH);
            woodShader = new android.graphics.BitmapShader(
                    woodBitmap, android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP);
        }

        Paint woodPaint = new Paint();
        woodPaint.setShader(woodShader);
        canvas.drawRect(boardLeft, boardTop, boardRight, boardBottom, woodPaint);
    }

    /**
     * 生成木纹纹理 Bitmap：基底色 + 多条横向木纹 + 节点装饰。
     */
    private Bitmap createWoodBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // 基底渐变（深木色到浅木色，模拟光照）
        Paint base = new Paint();
        LinearGradient bg = new LinearGradient(0, 0, 0, h,
                0xFFD4A574, 0xFFB8895A, Shader.TileMode.CLAMP);
        base.setShader(bg);
        c.drawRect(0, 0, w, h, base);

        // 横向木纹（多条半透明深色线，模拟年轮）
        Paint grain = new Paint();
        grain.setStyle(Paint.Style.STROKE);
        grain.setColor(0x338B5A2B);
        // 使用伪随机但稳定的种子，使每次生成结果一致
        java.util.Random rnd = new java.util.Random(20260623);
        for (int i = 0; i < 24; i++) {
            float y = rnd.nextFloat() * h;
            int alpha = 30 + rnd.nextInt(50);
            // alpha << 24 范围可能超出 int，使用 long 转换避免编译错误
            int colorVal = (alpha << 24) | (0x8B5A2B & 0x00FFFFFF);
            grain.setColor(colorVal);
            grain.setStrokeWidth(0.8f + rnd.nextFloat() * 1.6f);
            // 微微弯曲（用两段近似）
            float midOffset = (rnd.nextFloat() - 0.5f) * w * 0.1f;
            c.drawLine(0, y, w * 0.5f, y + midOffset, grain);
            c.drawLine(w * 0.5f, y + midOffset, w, y + (rnd.nextFloat() - 0.5f) * w * 0.1f, grain);
        }

        // 木纹节点装饰（少数深色椭圆，模拟木节）
        Paint knot = new Paint();
        knot.setColor(0x558B5A2B);
        for (int i = 0; i < 4; i++) {
            float kx = rnd.nextFloat() * w;
            float ky = rnd.nextFloat() * h;
            c.drawOval(kx - 6, ky - 4, kx + 6, ky + 4, knot);
        }

        return bmp;
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
                // 尝试移动到目标位置（同时校验走子后己方将/帅不被将军）
                if (isValidMove(selectedRow, selectedCol, row, col)
                        && isLegalMove(selectedRow, selectedCol, row, col, 1)) {
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

                    // 黑方被将死/困毙 → 红方（玩家）胜，无需再触发 AI
                    if (isDefeated(2)) {
                        gameOver = true;
                        if (gameOverListener != null) gameOverListener.onGameOver(1);
                        return true;
                    }

                    // 通知玩家走棋完成，触发 AI
                    if (playerMoveListener != null) {
                        playerMoveListener.onPlayerMove();
                    }
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

    // ==================== 将军检测与走法格式化 ====================

    /**
     * 找到指定方的将/帅位置。
     *
     * @param side 1=红方，2=黑方
     * @return {row, col} 或 null（被吃）
     */
    @Nullable
    public int[] findKing(int side) {
        int target = (side == 1) ? KING : -KING;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == target) return new int[]{r, c};
            }
        }
        return null;
    }

    /**
     * 检测指定方的将/帅是否正被对方将军。
     *
     * @param side 1=红方，2=黑方
     */
    public boolean isInCheck(int side) {
        return kingInCheck(board, side);
    }

    /**
     * 判断 (fr, fc) 位置的棋子能否走到 (tr, tc)，不考虑将帅面对面规则。
     * 用于将军检测（不模拟"己方阻挡"）。
     */
    private boolean canAttack(int fr, int fc, int tr, int tc) {
        return canAttackOn(board, fr, fc, tr, tc);
    }

    /**
     * 判断 (fr, fc) 位置的棋子能否在棋盘 b 上走到 (tr, tc)。
     * 可作用于任意棋盘副本，用于"走子后己方是否被将军"的合法性校验。
     */
    private static boolean canAttackOn(int[][] b, int fr, int fc, int tr, int tc) {
        if (fr == tr && fc == tc) return false;
        int piece = b[fr][fc];
        int target = b[tr][tc];
        // 不吃自己棋子
        if (piece > 0 && target > 0) return false;
        if (piece < 0 && target < 0) return false;
        int type = Math.abs(piece);
        int dr = tr - fr;
        int dc = tc - fc;

        switch (type) {
            case KING: {
                boolean inPalace;
                if (piece > 0) inPalace = tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5;
                else inPalace = tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5;
                return inPalace && Math.abs(dr) + Math.abs(dc) == 1;
            }
            case ADVISOR: {
                boolean inPalace;
                if (piece > 0) inPalace = tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5;
                else inPalace = tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5;
                return inPalace && Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case BISHOP: {
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if (piece > 0 && tr < 5) return false;
                if (piece < 0 && tr > 4) return false;
                int eyeR = fr + dr / 2;
                int eyeC = fc + dc / 2;
                return b[eyeR][eyeC] == 0;
            }
            case KNIGHT: {
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                if (Math.abs(dr) == 2) return b[fr + dr / 2][fc] == 0;
                return b[fr][fc + dc / 2] == 0;
            }
            case ROOK:
                if (dr != 0 && dc != 0) return false;
                return isPathClearOn(b, fr, fc, tr, tc);
            case CANNON: {
                if (dr != 0 && dc != 0) return false;
                int cnt = countPiecesBetweenOn(b, fr, fc, tr, tc);
                if (target == 0) return cnt == 0;
                return cnt == 1;
            }
            case PAWN: {
                if (piece > 0) {
                    if (fr >= 5) return dr == -1 && dc == 0;
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                } else {
                    if (fr <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
            }
        }
        return false;
    }

    // ==================== 将死 / 困毙检测（P0-2 修复） ====================

    /** 在棋盘 b 上判断两点之间路径是否无阻挡 */
    private static boolean isPathClearOn(int[][] b, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) if (b[r1][c] != 0) return false;
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) if (b[r][c1] != 0) return false;
        }
        return true;
    }

    /** 在棋盘 b 上统计两点之间棋子数量 */
    private static int countPiecesBetweenOn(int[][] b, int r1, int c1, int r2, int c2) {
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

    /** 将/帅是否正被将军（含"白脸将/对脸"规则） */
    private static boolean kingInCheck(int[][] b, int side) {
        int[] king = findKingOn(b, side);
        if (king == null) return false;
        int kr = king[0], kc = king[1];
        int attackerSide = (side == 1) ? 2 : 1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = b[r][c];
                if (piece == 0) continue;
                if (side == 1 && piece > 0) continue;
                if (side == 2 && piece < 0) continue;
                if (canAttackOn(b, r, c, kr, kc)) return true;
            }
        }
        // 白脸将（两将照面）：同一列且无子相隔，视为被将军
        int[] enemyKing = findKingOn(b, attackerSide);
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

    private static int[] findKingOn(int[][] b, int side) {
        int target = (side == 1) ? KING : -KING;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (b[r][c] == target) return new int[]{r, c};
            }
        }
        return null;
    }

    private static int[][] copyBoardArray(int[][] src) {
        int[][] c = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) System.arraycopy(src[r], 0, c[r], 0, COLS);
        return c;
    }

    /**
     * 判断 side 方从 (fr,fc) 走到 (tr,tc) 是否合法（走完后己方将/帅不被将军）。
     */
    private boolean isLegalMove(int fr, int fc, int tr, int tc, int side) {
        int[][] b = copyBoardArray(board);
        b[tr][tc] = b[fr][fc];
        b[fr][fc] = 0;
        return !kingInCheck(b, side);
    }

    /**
     * 判断 side 方在当前局面是否还有合法走法。
     */
    private boolean hasLegalMove(int side) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side == 1 && piece < 0) continue;
                if (side == 2 && piece > 0) continue;
                for (int tr = 0; tr < ROWS; tr++) {
                    for (int tc = 0; tc < COLS; tc++) {
                        if (tr == r && tc == c) continue;
                        if (isValidMove(r, c, tr, tc) && isLegalMove(r, c, tr, tc, side)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断 side 方是否已无合法走法（被将死或困毙，按象棋规则均判负）。
     */
    private boolean isDefeated(int side) {
        return !hasLegalMove(side);
    }

    /**
     * 获取最后一步走法的格式化中文棋谱记录（如 "炮二平五"）。
     * 使用坐标定位+方向描述，符合中国象棋传统记谱。
     *
     * @return 走法字符串，无历史时返回 null
     */
    @Nullable
    public String getLastMoveNotation() {
        if (moveHistory.isEmpty()) return null;
        int[] last = moveHistory.get(moveHistory.size() - 1);
        return formatMove(last[0], last[1], last[2], last[3], last[4]);
    }

    /**
     * 获取最后一步走法坐标。
     * @return [fromRow, fromCol, toRow, toCol]，无走法时返回 null
     */
    @Nullable
    public int[] getLastMove() {
        if (moveHistory.isEmpty()) return null;
        int[] last = moveHistory.get(moveHistory.size() - 1);
        return new int[]{last[0], last[1], last[2], last[3]};
    }

    /**
     * 格式化一步走法为中文棋谱。
     * 规则：棋子名 + 起始位置编号 + 动作（前/平/后）+ 目标位置编号
     * 红方从右到左（己方视角），黑方从左到右（己方视角）。
     */
    private String formatMove(int fromR, int fromC, int toR, int toC, int captured) {
        int piece = board[toR][toC];
        if (piece == 0) piece = captured == 0 ? board[fromR][fromC] : (captured > 0 ? -captured : -captured);
        // piece 在 toR/toC（因为走完了），或者是 captured 的反向
        int displayPiece;
        if (board[toR][toC] != 0) {
            displayPiece = board[toR][toC];
        } else {
            // 走完后该位置应该是有棋子的，否则回退到 captured 反向
            displayPiece = captured > 0 ? -KING : (captured < 0 ? KING : 0);
            if (displayPiece == 0) return "";
        }

        boolean isRed = displayPiece > 0;
        int type = Math.abs(displayPiece);
        String pieceName = getPieceName(type, isRed);

        // 起始编号（红方视角：己方从右到左 9-1；黑方视角：己方从右到左 9-1，但显示为 1-9）
        // 为简化，使用绝对列号 1-9，红方在前
        int fromColNum = isRed ? (9 - fromC) : (fromC + 1);
        int toColNum = isRed ? (9 - toC) : (toC + 1);

        // 起始行的位置描述（红方 1-5 在己方底线，黑方同理）
        // 但中国象棋记谱里用"前/中/后"或具体数字+前后
        String fromRowLabel;
        String toRowLabel;
        if (isRed) {
            fromRowLabel = rowLabelRed(fromR);
            toRowLabel = rowLabelRed(toR);
        } else {
            fromRowLabel = rowLabelBlack(fromR);
            toRowLabel = rowLabelBlack(toR);
        }

        // 动作
        String action;
        if (toR == fromR) {
            action = "平";
        } else if ((isRed && toR < fromR) || (!isRed && toR > fromR)) {
            action = "进";
        } else {
            action = "退";
        }

        // 目标列号或行号
        String target;
        if (toR == fromR) {
            target = String.valueOf(toColNum);
        } else {
            target = String.valueOf(toColNum);
        }

        return pieceName + fromColNum + fromRowLabel + action + target;
    }

    private String rowLabelRed(int r) {
        // 红方行号：第 6-10 行（己方底线为"一"）
        // 但中国象棋实际记谱只用数字编号 1-9 表示列号，行不用"前中后"
        // 为简化：起始用列号 + "前/后" 区分上下
        if (r >= 7) return "前"; // 己方底线附近
        if (r <= 4) return "后"; // 已过河
        return ""; // 河附近
    }

    private String rowLabelBlack(int r) {
        if (r <= 2) return "前";
        if (r >= 5) return "后";
        return "";
    }

    /**
     * 获取棋子中文名（红/黑不同字）。
     */
    public static String getPieceName(int type, boolean isRed) {
        switch (type) {
            case KING: return isRed ? "帅" : "将";
            case ADVISOR: return isRed ? "仕" : "士";
            case BISHOP: return isRed ? "相" : "象";
            case KNIGHT: return "马";
            case ROOK: return "车";
            case CANNON: return "炮";
            case PAWN: return isRed ? "兵" : "卒";
            default: return "";
        }
    }

    /**
     * 获取所有走法的格式化棋谱列表。
     * 通过从初始棋盘重放每一步，确保每步棋子类型识别正确
     * （moveHistory 只存坐标和被吃棋子，不存移动棋子本身）。
     * 格式："棋子(起始行,起始列)→(目标行,目标列)"，行列为 0-based 棋盘坐标。
     *
     * @return 走法字符串列表，空局时返回空列表
     */
    @NonNull
    public List<String> getAllMoveNotations() {
        List<String> result = new ArrayList<>();
        if (moveHistory.isEmpty()) return result;

        int[][] replayBoard = createInitialBoard();
        for (int[] move : moveHistory) {
            int fromR = move[0], fromC = move[1];
            int toR = move[2], toC = move[3];
            int piece = replayBoard[fromR][fromC];
            if (piece == 0) {
                result.add("?");
                continue;
            }
            boolean isRed = piece > 0;
            int type = Math.abs(piece);
            String pieceName = getPieceName(type, isRed);
            result.add(pieceName + "(" + fromR + "," + fromC + ")\u2192(" + toR + "," + toC + ")");
            // 重放走法
            replayBoard[toR][toC] = piece;
            replayBoard[fromR][fromC] = 0;
        }
        return result;
    }

    /**
     * 创建初始棋盘布局的副本（用于重放走法历史）。
     */
    private int[][] createInitialBoard() {
        int[][] b = new int[ROWS][COLS];
        // 红方（下方，正数）
        b[9][0] = ROOK;    b[9][1] = KNIGHT;  b[9][2] = BISHOP;  b[9][3] = ADVISOR; b[9][4] = KING;
        b[9][5] = ADVISOR; b[9][6] = BISHOP;  b[9][7] = KNIGHT;  b[9][8] = ROOK;
        b[7][1] = CANNON;  b[7][7] = CANNON;
        b[6][0] = PAWN;    b[6][2] = PAWN;    b[6][4] = PAWN;    b[6][6] = PAWN;    b[6][8] = PAWN;
        // 黑方（上方，负数）
        b[0][0] = -ROOK;    b[0][1] = -KNIGHT;  b[0][2] = -BISHOP;  b[0][3] = -ADVISOR; b[0][4] = -KING;
        b[0][5] = -ADVISOR; b[0][6] = -BISHOP;  b[0][7] = -KNIGHT;  b[0][8] = -ROOK;
        b[2][1] = -CANNON;  b[2][7] = -CANNON;
        b[3][0] = -PAWN;    b[3][2] = -PAWN;    b[3][4] = -PAWN;    b[3][6] = -PAWN;    b[3][8] = -PAWN;
        return b;
    }

    /**
     * 获取走法总数。
     */
    public int getMoveCount() {
        return moveHistory.size();
    }

    /**
     * 获取已吃的红方棋子列表（被黑方吃的）。
     */
    @NonNull
    public List<int[]> getCapturedRedPieces() {
        // 简化：对比初始布局，找出消失的红方棋子
        return getMissingPieces(true);
    }

    /**
     * 获取已吃的黑方棋子列表（被红方吃的）。
     */
    @NonNull
    public List<int[]> getCapturedBlackPieces() {
        return getMissingPieces(false);
    }

    /**
     * 对比初始布局，找出指定方消失的棋子。
     * @param isRed true=查找红方消失的棋子
     */
    @NonNull
    private List<int[]> getMissingPieces(boolean isRed) {
        int[][] initial = isRed ? INITIAL_RED : INITIAL_BLACK;
        List<int[]> missing = new ArrayList<>();
        for (int[] p : initial) {
            int r = p[0], c = p[1], type = p[2];
            int target = isRed ? type : -type;
            boolean found = false;
            for (int rr = 0; rr < ROWS && !found; rr++) {
                for (int cc = 0; cc < COLS && !found; cc++) {
                    if (board[rr][cc] == target) found = true;
                }
            }
            if (!found) missing.add(new int[]{type, r, c});
        }
        return missing;
    }

    /** 红方初始布局 [row, col, type] */
    private static final int[][] INITIAL_RED = {
            {9, 0, ROOK}, {9, 1, KNIGHT}, {9, 2, BISHOP}, {9, 3, ADVISOR}, {9, 4, KING},
            {9, 5, ADVISOR}, {9, 6, BISHOP}, {9, 7, KNIGHT}, {9, 8, ROOK},
            {7, 1, CANNON}, {7, 7, CANNON},
            {6, 0, PAWN}, {6, 2, PAWN}, {6, 4, PAWN}, {6, 6, PAWN}, {6, 8, PAWN}
    };

    /** 黑方初始布局 [row, col, type] */
    private static final int[][] INITIAL_BLACK = {
            {0, 0, ROOK}, {0, 1, KNIGHT}, {0, 2, BISHOP}, {0, 3, ADVISOR}, {0, 4, KING},
            {0, 5, ADVISOR}, {0, 6, BISHOP}, {0, 7, KNIGHT}, {0, 8, ROOK},
            {2, 1, CANNON}, {2, 7, CANNON},
            {3, 0, PAWN}, {3, 2, PAWN}, {3, 4, PAWN}, {3, 6, PAWN}, {3, 8, PAWN}
    };

    /**
     * 当前正在被将军的棋子位置（用于绘制红色光环）。
     * 返回值：{row, col} 或 null
     */
    @Nullable
    public int[] getCheckedKingPosition() {
        if (gameOver) return null;
        if (isInCheck(1)) return findKing(1);
        if (isInCheck(2)) return findKing(2);
        return null;
    }

    /**
     * 检测"长将"判和：连续 6 步内将军次数过多，简化为：当前回合是连续第 3 次将军 → 提示和棋。
     * 简化版本：仅返回 false，由 Activity 在其他逻辑中处理。
     */
    public boolean isCheckRepeated(int maxConsecutiveChecks) {
        // 简化实现：扫描最近 moveHistory，统计将军次数
        if (moveHistory.isEmpty()) return false;
        // 这里需要重新模拟每步棋的状态，开销大；跳过实现
        return false;
    }
    
    /**
     * 设置提示走法
     *
     * @param move [fromR, fromC, toR, toC]
     */
    public void setHintMove(int[] move) {
        this.hintMove = move;
        invalidate();
    }

    /**
     * 设置增强提示：高亮起点（绿色）和终点（蓝色），绘制箭头。
     *
     * @param move [fromR, fromC, toR, toC]
     */
    public void setEnhancedHint(int[] move) {
        if (move != null && move.length >= 4) {
            this.hintFromRow = move[0];
            this.hintFromCol = move[1];
            this.hintToRow = move[2];
            this.hintToCol = move[3];
            this.enhancedHintActive = true;
        }
        invalidate();
    }

    /**
     * 清除增强提示
     */
    public void clearEnhancedHint() {
        this.enhancedHintActive = false;
        this.hintFromRow = -1;
        this.hintFromCol = -1;
        this.hintToRow = -1;
        this.hintToCol = -1;
        this.hintMove = null;
        invalidate();
    }

    /**
     * 获取棋盘的 cellSize 和偏移量（供外部绘制使用）
     *
     * @return [cellSize, offsetX, offsetY]，如果视图未初始化则返回 null
     */
    @Nullable
    public float[] getBoardMetrics() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return null;
        float cellW = (float) w / (COLS + 1);
        float cellH = (float) h / (ROWS + 1);
        float cellSize = Math.min(cellW, cellH);
        float offsetX = (w - cellSize * (COLS - 1)) / 2f;
        float offsetY = (h - cellSize * (ROWS - 1)) / 2f;
        return new float[]{cellSize, offsetX, offsetY};
    }

    /**
     * 获取棋盘常量 COLS
     */
    public int getCols() { return COLS; }

    /**
     * 获取棋盘常量 ROWS
     */
    public int getRows() { return ROWS; }

    /**
     * P0 内存泄漏修复：View detach 时回收木纹背景 Bitmap，避免原生内存泄漏。
     * 未覆写此方法时，woodBitmap 会一直被本实例引用，直到 View 对象被 GC
     * （Bitmap 原生内存 GC 回收滞后，长会话场景易导致 PSS 持续增长）。
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (woodBitmap != null && !woodBitmap.isRecycled()) {
            woodBitmap.recycle();
        }
        woodBitmap = null;
        woodShader = null;
    }
}
