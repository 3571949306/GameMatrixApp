package com.gamecenter.app.games.tetris;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * 俄罗斯方块游戏视图。
 *
 * <p>Canvas 绘制俄罗斯方块，使用定时器驱动方块下落。
 * 支持手势滑动控制左右移动、下滑加速和旋转。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 10×20 网格（标准俄罗斯方块尺寸）</li>
 *   <li>7 种标准方块（I, O, T, L, J, S, Z），每种有旋转状态</li>
 *   <li>使用 Handler 定时器驱动下落，速度随等级递增</li>
 *   <li>消行检测：满行消除，支持同时消多行</li>
 *   <li>护眼主题配色</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class TetrisView extends View {

    // ==================== 常量 ====================

    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int BASE_DROP_INTERVAL_MS = 800;

    // ==================== 方块定义 ====================

    // 每种方块用 4x4 矩阵定义，4 个旋转状态
    private static final int[][][][] TETROMINOES = {
        // I
        {{{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
         {{0,0,1,0},{0,0,1,0},{0,0,1,0},{0,0,1,0}},
         {{0,0,0,0},{0,0,0,0},{1,1,1,1},{0,0,0,0}},
         {{0,1,0,0},{0,1,0,0},{0,1,0,0},{0,1,0,0}}},
        // O
        {{{1,1},{1,1}},
         {{1,1},{1,1}},
         {{1,1},{1,1}},
         {{1,1},{1,1}}},
        // T
        {{{0,1,0},{1,1,1},{0,0,0}},
         {{0,1,0},{0,1,1},{0,1,0}},
         {{0,0,0},{1,1,1},{0,1,0}},
         {{0,1,0},{1,1,0},{0,1,0}}},
        // L
        {{{0,0,1},{1,1,1},{0,0,0}},
         {{0,1,0},{0,1,0},{0,1,1}},
         {{0,0,0},{1,1,1},{1,0,0}},
         {{1,1,0},{0,1,0},{0,1,0}}},
        // J
        {{{1,0,0},{1,1,1},{0,0,0}},
         {{0,1,1},{0,1,0},{0,1,0}},
         {{0,0,0},{1,1,1},{0,0,1}},
         {{0,1,0},{0,1,0},{1,1,0}}},
        // S
        {{{0,1,1},{1,1,0},{0,0,0}},
         {{0,1,0},{0,1,1},{0,0,1}},
         {{0,0,0},{0,1,1},{1,1,0}},
         {{1,0,0},{1,1,0},{0,1,0}}},
        // Z
        {{{1,1,0},{0,1,1},{0,0,0}},
         {{0,0,1},{0,1,1},{0,1,0}},
         {{0,0,0},{1,1,0},{0,1,1}},
         {{0,1,0},{1,1,0},{1,0,0}}}
    };

    // 方块颜色
    private static final int[] TETROMINO_COLORS = {
        0xFF00BCD4, // I - 青色
        0xFFFFEB3B, // O - 黄色
        0xFF9C27B0, // T - 紫色
        0xFFFF9800, // L - 橙色
        0xFF2196F3, // J - 蓝色
        0xFF4CAF50, // S - 绿色
        0xFFF44336  // Z - 红色
    };

    // ==================== 颜色 ====================

    private static final int COLOR_BG = 0xFF1A1A2E;
    private static final int COLOR_GRID = 0xFF2A2A4E;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_OVERLAY = 0xCC000000;

    // ==================== 回调接口 ====================

    public interface OnScoreChangeListener {
        void onScoreChanged(int score);
    }

    public interface OnLinesClearedListener {
        void onLinesCleared(int lines);
    }

    public interface OnLevelChangeListener {
        void onLevelChanged(int level);
    }

    public interface OnGameOverListener {
        void onGameOver(int finalScore);
    }

    /** 方块旋转成功回调 */
    public interface OnPieceRotateListener {
        void onPieceRotated();
    }

    /** 方块落地（锁定）回调 */
    public interface OnPieceLandListener {
        void onPieceLanded();
    }

    // ==================== 游戏状态 ====================

    /** 游戏网格（0=空，1-7=方块颜色索引） */
    private final int[][] grid = new int[ROWS][COLS];

    /** 当前方块类型（0-6） */
    private int currentPiece;

    /** 当前方块旋转状态（0-3） */
    private int currentRotation;

    /** 当前方块位置（左上角） */
    private int pieceX, pieceY;

    /** 下一个方块类型 */
    private int nextPiece;

    /** 分数 */
    private int score = 0;

    /** 消行数 */
    private int lines = 0;

    /** 等级 */
    private int level = 1;

    /** 游戏运行标志 */
    private boolean running = false;

    /** 游戏结束标志 */
    private boolean gameOver = false;

    /** 速度因子 */
    private float speedFactor = 0.5f;

    // ==================== 工具 ====================

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable dropRunnable;
    private GestureDetector gestureDetector;

    // ==================== 绘制工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintGrid = new Paint();
    private final Paint paintBlock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOverlay = new Paint();
    private final Paint paintGhost = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ==================== 监听器 ====================

    private OnScoreChangeListener scoreChangeListener;
    private OnLinesClearedListener linesClearedListener;
    private OnLevelChangeListener levelChangeListener;
    private OnGameOverListener gameOverListener;
    private OnPieceRotateListener pieceRotateListener;
    private OnPieceLandListener pieceLandListener;

    // ==================== 构造函数 ====================

    public TetrisView(@NonNull Context context) {
        super(context);
        init();
    }

    public TetrisView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TetrisView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintBg.setColor(COLOR_BG);
        paintGrid.setColor(COLOR_GRID);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f);
        paintText.setColor(COLOR_TEXT);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintOverlay.setColor(COLOR_OVERLAY);
        paintGhost.setStyle(Paint.Style.STROKE);
        paintGhost.setStrokeWidth(2f);
        paintGhost.setColor(0x55FFFFFF);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) moveRight();
                    else moveLeft();
                } else {
                    if (dy > 0) moveDown();
                    else rotate();
                }
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                rotate();
                return true;
            }
        });
    }

    // ==================== 监听器设置 ====================

    public void setOnScoreChangeListener(OnScoreChangeListener l) { this.scoreChangeListener = l; }
    public void setOnLinesClearedListener(OnLinesClearedListener l) { this.linesClearedListener = l; }
    public void setOnLevelChangeListener(OnLevelChangeListener l) { this.levelChangeListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }
    public void setOnPieceRotateListener(OnPieceRotateListener l) { this.pieceRotateListener = l; }
    public void setOnPieceLandListener(OnPieceLandListener l) { this.pieceLandListener = l; }
    public void setSpeedFactor(float factor) { this.speedFactor = Math.max(0.1f, Math.min(1.0f, factor)); }

    // ==================== 游戏控制 ====================

    public void startGame() {
        // 清空网格
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = 0;
            }
        }
        score = 0;
        lines = 0;
        level = 1;
        gameOver = false;
        running = true;

        nextPiece = random.nextInt(7);
        spawnPiece();

        notifyScore();
        scheduleDrop();
        invalidate();
    }

    public void pauseGame() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void resumeGame() {
        if (!gameOver) {
            running = true;
            scheduleDrop();
        }
    }

    public void stopGame() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    // ==================== 游戏逻辑 ====================

    private void spawnPiece() {
        currentPiece = nextPiece;
        nextPiece = random.nextInt(7);
        currentRotation = 0;
        int[][] shape = TETROMINOES[currentPiece][0];
        pieceX = (COLS - shape[0].length) / 2;
        pieceY = 0;

        if (!isValidPosition(currentPiece, currentRotation, pieceX, pieceY)) {
            onGameOver();
        }
    }

    private void scheduleDrop() {
        if (!running || gameOver) return;
        handler.removeCallbacksAndMessages(null);
        int interval = (int) (BASE_DROP_INTERVAL_MS * (1.0f - speedFactor * 0.5f) / level);
        interval = Math.max(50, interval);
        dropRunnable = this::drop;
        handler.postDelayed(dropRunnable, interval);
    }

    private void drop() {
        if (!running || gameOver) return;
        if (moveDown()) {
            scheduleDrop();
        }
    }

    private boolean moveLeft() {
        if (!running || gameOver) return false;
        if (isValidPosition(currentPiece, currentRotation, pieceX - 1, pieceY)) {
            pieceX--;
            invalidate();
            return true;
        }
        return false;
    }

    private boolean moveRight() {
        if (!running || gameOver) return false;
        if (isValidPosition(currentPiece, currentRotation, pieceX + 1, pieceY)) {
            pieceX++;
            invalidate();
            return true;
        }
        return false;
    }

    private boolean moveDown() {
        if (!running || gameOver) return false;
        if (isValidPosition(currentPiece, currentRotation, pieceX, pieceY + 1)) {
            pieceY++;
            invalidate();
            return true;
        } else {
            lockPiece();
            clearLines();
            spawnPiece();
            invalidate();
            return true;
        }
    }

    private void rotate() {
        if (!running || gameOver) return;
        int newRotation = (currentRotation + 1) % 4;
        if (isValidPosition(currentPiece, newRotation, pieceX, pieceY)) {
            currentRotation = newRotation;
            invalidate();
            if (pieceRotateListener != null) {
                pieceRotateListener.onPieceRotated();
            }
        }
    }

    private boolean isValidPosition(int piece, int rotation, int x, int y) {
        int[][] shape = TETROMINOES[piece][rotation];
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int newX = x + c;
                    int newY = y + r;
                    if (newX < 0 || newX >= COLS || newY < 0 || newY >= ROWS) return false;
                    if (grid[newY][newX] != 0) return false;
                }
            }
        }
        return true;
    }

    private void lockPiece() {
        int[][] shape = TETROMINOES[currentPiece][currentRotation];
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int gx = pieceX + c;
                    int gy = pieceY + r;
                    if (gy >= 0 && gy < ROWS && gx >= 0 && gx < COLS) {
                        grid[gy][gx] = currentPiece + 1;
                    }
                }
            }
        }
        if (pieceLandListener != null) {
            pieceLandListener.onPieceLanded();
        }
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                // 下移所有上方行
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(grid[rr - 1], 0, grid[rr], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) {
                    grid[0][c] = 0;
                }
                r++; // 重新检查当前行
            }
        }

        if (cleared > 0) {
            lines += cleared;
            // 计分：1行=100, 2行=300, 3行=500, 4行=800
            int[] points = {0, 100, 300, 500, 800};
            score += points[Math.min(cleared, 4)] * level;
            notifyScore();

            if (linesClearedListener != null) {
                linesClearedListener.onLinesCleared(cleared);
            }

            // 升级：每 10 行升一级
            int newLevel = lines / 10 + 1;
            if (newLevel > level) {
                level = newLevel;
                if (levelChangeListener != null) {
                    levelChangeListener.onLevelChanged(level);
                }
            }
        }
    }

    private void onGameOver() {
        gameOver = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        invalidate();
        if (gameOverListener != null) {
            gameOverListener.onGameOver(score);
        }
    }

    private void notifyScore() {
        if (scoreChangeListener != null) {
            scoreChangeListener.onScoreChanged(score);
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float cellSize = Math.min((float) viewWidth / COLS, (float) viewHeight / (ROWS + 2));
        float offsetX = (viewWidth - cellSize * COLS) / 2f;
        float offsetY = cellSize;

        // 背景
        canvas.drawRect(0, 0, viewWidth, viewHeight, paintBg);

        // 网格线
        for (int i = 0; i <= COLS; i++) {
            canvas.drawLine(offsetX + i * cellSize, offsetY, offsetX + i * cellSize, offsetY + ROWS * cellSize, paintGrid);
        }
        for (int i = 0; i <= ROWS; i++) {
            canvas.drawLine(offsetX, offsetY + i * cellSize, offsetX + COLS * cellSize, offsetY + i * cellSize, paintGrid);
        }

        // 绘制已锁定的方块
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] != 0) {
                    drawBlock(canvas, offsetX + c * cellSize, offsetY + r * cellSize, cellSize, TETROMINO_COLORS[grid[r][c] - 1]);
                }
            }
        }

        // 绘制当前下落方块
        if (!gameOver) {
            int[][] shape = TETROMINOES[currentPiece][currentRotation];
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        drawBlock(canvas, offsetX + (pieceX + c) * cellSize, offsetY + (pieceY + r) * cellSize, cellSize, TETROMINO_COLORS[currentPiece]);
                    }
                }
            }
        }

        // 绘制信息
        paintText.setTextSize(cellSize * 0.8f);
        float infoY = offsetY + ROWS * cellSize + cellSize * 1.2f;
        canvas.drawText("分数: " + score + "  消行: " + lines + "  等级: " + level, viewWidth / 2f, infoY, paintText);

        // 游戏结束
        if (gameOver) {
            canvas.drawRect(0, 0, viewWidth, viewHeight, paintOverlay);
            paintText.setTextSize(cellSize * 2f);
            canvas.drawText("游戏结束", viewWidth / 2f, viewHeight / 2f - cellSize, paintText);
            paintText.setTextSize(cellSize * 1.2f);
            canvas.drawText("最终分数: " + score, viewWidth / 2f, viewHeight / 2f + cellSize, paintText);
            canvas.drawText("点击重新开始", viewWidth / 2f, viewHeight / 2f + cellSize * 3, paintText);
        }
    }

    private void drawBlock(Canvas canvas, float x, float y, float size, int color) {
        float padding = size * 0.05f;
        paintBlock.setColor(color);
        canvas.drawRoundRect(
                new RectF(x + padding, y + padding, x + size - padding, y + size - padding),
                size * 0.1f, size * 0.1f, paintBlock);
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver && event.getAction() == MotionEvent.ACTION_DOWN) {
            startGame();
            return true;
        }
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }
}
