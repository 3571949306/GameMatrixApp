package com.gamecenter.app.games.tetris;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.gamecenter.app.games.R;

/**
 * 俄罗斯方块游戏自定义绘制 View
 *
 * <p>负责将 TetrisGame 的状态渲染到屏幕上，包括棋盘、当前方块、
 * 下一个方块预览、得分/等级显示和游戏结束画面。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>根据 View 尺寸动态计算格子大小，棋盘占左侧60%宽度、90%高度</li>
 *   <li>右侧面板显示下一个方块预览、得分和等级</li>
 *   <li>已锁定方块使用对应颜色绘制，带深色边框增强立体感</li>
 *   <li>游戏结束时绘制半透明遮罩和提示文字</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘区域占 View 宽度的3/5，高度占9/10，右侧留空给信息面板</li>
 *   <li>方块边框颜色比填充色暗60个色阶，模拟3D凸起效果</li>
 *   <li>下一个方块预览使用缩小到70%的格子尺寸绘制</li>
 * </ul>
 */
public class TetrisView extends View {

    /** 游戏逻辑实例 */
    private TetrisGame game;

    /** 每个格子的像素大小 */
    private int cellSize;

    /** 棋盘在 View 中的水平偏移量 */
    private float offsetX;

    /** 棋盘在 View 中的垂直偏移量 */
    private float offsetY;

    /** 背景画笔 */
    private Paint bgPaint;

    /** 网格线画笔 */
    private Paint gridPaint;

    /** 方块填充画笔 */
    private Paint blockPaint;

    /** 方块边框画笔 */
    private Paint blockBorderPaint;

    /** 游戏结束文字画笔 */
    private Paint textPaint;

    /** 下一个方块预览画笔 */
    private Paint nextPiecePaint;

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public TetrisView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML 布局 inflate 时调用）。
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public TetrisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔。
     *
     * <p>创建背景（深蓝黑色）、网格线（深蓝灰色）、方块、边框、文字和预览画笔。
     * 所有画笔在此一次性创建，避免 onDraw 中的对象分配。</p>
     */
    private void init() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.rgb(30, 30, 50));
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint();
        gridPaint.setColor(Color.rgb(50, 50, 80));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        blockPaint = new Paint();
        blockPaint.setStyle(Paint.Style.FILL);
        blockPaint.setAntiAlias(true);

        blockBorderPaint = new Paint();
        blockBorderPaint.setStyle(Paint.Style.STROKE);
        blockBorderPaint.setAntiAlias(true);
        blockBorderPaint.setStrokeWidth(2f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        nextPiecePaint = new Paint();
        nextPiecePaint.setStyle(Paint.Style.FILL);
        nextPiecePaint.setAntiAlias(true);
    }

    /**
     * 设置游戏逻辑实例并触发重绘。
     *
     * @param game TetrisGame 实例
     */
    public void setGame(TetrisGame game) {
        this.game = game;
        invalidate();
    }

    /**
     * View 尺寸变化时重新计算格子大小和偏移量。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    /**
     * 根据当前 View 尺寸重新计算格子大小和棋盘偏移量。
     *
     * <p>棋盘区域占 View 宽度的3/5、高度的9/10，
     * 取宽高方向上较小的格子尺寸以确保棋盘完整显示，
     * 然后计算偏移量使棋盘在分配区域内居中。</p>
     *
     * @param w View 宽度
     * @param h View 高度
     */
    private void recalcDimensions(int w, int h) {
        int boardWidth = w * 3 / 5;
        int boardHeight = h * 9 / 10;
        cellSize = Math.min(boardWidth / TetrisGame.COLS, boardHeight / TetrisGame.ROWS);
        offsetX = (w - cellSize * TetrisGame.COLS) / 2f;
        offsetY = (h - cellSize * TetrisGame.ROWS) / 2f;
    }

    /**
     * 绘制游戏画面。
     *
     * <p>绘制顺序：背景 → 棋盘 → 当前方块 → 下一个方块预览 → 得分 → 游戏结束遮罩。</p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        if (game == null) return;

        drawBoard(canvas);
        drawCurrentPiece(canvas);
        drawNextPiece(canvas);
        drawScore(canvas);

        if (game.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    /**
     * 绘制棋盘（网格线 + 已锁定的方块）。
     *
     * <p>先绘制网格线，再绘制已锁定的方块。
     * 棋盘值为-1的格子为空，>=0 的格子绘制对应颜色的方块。</p>
     *
     * @param canvas 画布
     */
    private void drawBoard(Canvas canvas) {
        int[][] board = game.getBoard();

        for (int row = 0; row < TetrisGame.ROWS; row++) {
            for (int col = 0; col < TetrisGame.COLS; col++) {
                float left = offsetX + col * cellSize;
                float top = offsetY + row * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                canvas.drawRect(left, top, right, bottom, gridPaint);

                // 绘制已锁定的方块
                if (board[row][col] >= 0) {
                    drawBlock(canvas, left, top, right, bottom, board[row][col]);
                }
            }
        }
    }

    /**
     * 绘制当前正在下落的方块。
     *
     * <p>根据方块形状和位置，绘制每个有效格子。
     * 只绘制在棋盘可见范围内（y>=0）的部分。</p>
     *
     * @param canvas 画布
     */
    private void drawCurrentPiece(Canvas canvas) {
        int[][] piece = game.getCurrentPiece();
        int colorIndex = game.getCurrentColor();
        int px = game.getCurrentX();
        int py = game.getCurrentY();

        for (int row = 0; row < piece.length; row++) {
            for (int col = 0; col < piece[row].length; col++) {
                if (piece[row][col] != 0) {
                    int x = px + col;
                    int y = py + row;
                    // 只绘制棋盘可见范围内的部分
                    if (y >= 0) {
                        float left = offsetX + x * cellSize;
                        float top = offsetY + y * cellSize;
                        float right = left + cellSize;
                        float bottom = top + cellSize;
                        drawBlock(canvas, left, top, right, bottom, colorIndex);
                    }
                }
            }
        }
    }

    /**
     * 绘制单个方块格子。
     *
     * <p>使用指定颜色索引绘制填充圆角矩形，
     * 边框颜色比填充色暗60个色阶（最低为0），模拟3D凸起效果。</p>
     *
     * @param canvas    画布
     * @param left      左边界
     * @param top       上边界
     * @param right     右边界
     * @param bottom    下边界
     * @param colorIndex 颜色索引（对应 COLORS 数组）
     */
    private void drawBlock(Canvas canvas, float left, float top, float right, float bottom, int colorIndex) {
        int[] color = TetrisGame.COLORS[colorIndex];
        blockPaint.setColor(Color.rgb(color[0], color[1], color[2]));
        // 边框颜色比填充色暗60个色阶，增强立体感
        blockBorderPaint.setColor(Color.rgb(
                Math.max(0, color[0] - 60),
                Math.max(0, color[1] - 60),
                Math.max(0, color[2] - 60)));

        RectF rect = new RectF(left + 1, top + 1, right - 1, bottom - 1);
        canvas.drawRoundRect(rect, 4, 4, blockPaint);
        canvas.drawRoundRect(rect, 4, 4, blockBorderPaint);
    }

    /**
     * 绘制右侧信息面板（下一个方块预览 + 得分 + 等级）。
     *
     * <p>面板位于棋盘右侧，包含：
     * <ul>
     *   <li>"NEXT" 标签 + 下一个方块预览（缩小到70%尺寸）</li>
     *   <li>"SCORE" 标签 + 得分（金色）</li>
     *   <li>"LEVEL" 标签 + 等级（青色）</li>
     * </ul>
     * </p>
     *
     * @param canvas 画布
     */
    private void drawNextPiece(Canvas canvas) {
        int panelX = (int) (offsetX + TetrisGame.COLS * cellSize + 20);
        int panelY = (int) offsetY;

        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(24);
        labelPaint.setAntiAlias(true);
        canvas.drawText("NEXT", panelX + 50, panelY + 30, labelPaint);

        int[][] nextPiece = TetrisGame.TETROMINOS[game.getNextPieceType()];
        int nextColor = game.getNextColor();
        int[] color = TetrisGame.COLORS[nextColor];

        nextPiecePaint.setColor(Color.rgb(color[0], color[1], color[2]));

        // 预览方块使用缩小到70%的格子尺寸
        float blockSize = cellSize * 0.7f;
        int pieceWidth = nextPiece[0].length;
        int pieceHeight = nextPiece.length;

        // 水平居中对齐（以4格宽度为基准）
        float startX = panelX + (4 - pieceWidth) * blockSize / 2;
        float startY = panelY + 50;

        for (int row = 0; row < pieceHeight; row++) {
            for (int col = 0; col < pieceWidth; col++) {
                if (nextPiece[row][col] != 0) {
                    float left = startX + col * blockSize;
                    float top = startY + row * blockSize;
                    RectF rect = new RectF(left, top, left + blockSize - 2, top + blockSize - 2);
                    canvas.drawRoundRect(rect, 3, 3, nextPiecePaint);
                }
            }
        }

        Paint scoreLabel = new Paint();
        scoreLabel.setColor(Color.WHITE);
        scoreLabel.setTextSize(24);
        scoreLabel.setAntiAlias(true);
        canvas.drawText("SCORE", panelX + 50, panelY + 180, scoreLabel);

        Paint scorePaint = new Paint();
        scorePaint.setColor(Color.rgb(255, 215, 0));
        scorePaint.setTextSize(32);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setAntiAlias(true);
        canvas.drawText(String.valueOf(game.getScore()), panelX + 50, panelY + 220, scorePaint);

        Paint levelPaint = new Paint();
        levelPaint.setColor(Color.WHITE);
        levelPaint.setTextSize(24);
        levelPaint.setAntiAlias(true);
        canvas.drawText("LEVEL", panelX + 50, panelY + 270, levelPaint);

        Paint levelNumPaint = new Paint();
        levelNumPaint.setColor(Color.rgb(0, 255, 255));
        levelNumPaint.setTextSize(32);
        levelNumPaint.setTextAlign(Paint.Align.CENTER);
        levelNumPaint.setAntiAlias(true);
        canvas.drawText(String.valueOf(game.getLevel()), panelX + 50, panelY + 310, levelNumPaint);
    }

    /**
     * 绘制得分信息（当前为空实现，得分已在 drawNextPiece 中绘制）。
     *
     * @param canvas 画布
     */
    private void drawScore(Canvas canvas) {
    }

    /**
     * 绘制游戏结束画面。
     *
     * <p>绘制半透明黑色遮罩，居中显示"GAME OVER"和重新开始提示。</p>
     *
     * @param canvas 画布
     */
    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint();
        overlay.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f - 30, textPaint);

        Paint restartPaint = new Paint();
        restartPaint.setColor(Color.WHITE);
        restartPaint.setTextSize(24);
        restartPaint.setTextAlign(Paint.Align.CENTER);
        restartPaint.setAntiAlias(true);
        canvas.drawText("Tap to restart", getWidth() / 2f, getHeight() / 2f + 20, restartPaint);
    }
}
