package com.gamecenter.app.tetris;

import android.content.Context;
import android.content.res.Configuration;
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
 * 俄罗斯方块游戏视图（模块化版本）。
 *
 * <p>从 app 模块复制并改包名为 com.gamecenter.app.tetris，
 * 将 R 资源引用替换为硬编码值以适配动态加载场景，
 * 颜色支持浅色/深色主题。</p>
 */
public class TetrisView extends View {

    // ==================== 常量 ====================

    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int BASE_DROP_INTERVAL_MS = 800;

    // ==================== 方块定义 ====================

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

    private static final int[] TETROMINO_COLORS = {
        0xFF00BCD4, // I - 青色
        0xFFFFEB3B, // O - 黄色
        0xFF9C27B0, // T - 紫色
        0xFFFF9800, // L - 橙色
        0xFF2196F3, // J - 蓝色
        0xFF4CAF50, // S - 绿色
        0xFFF44336  // Z - 红色
    };

    // ==================== 颜色（硬编码，支持浅色/深色主题） ====================

    private int colorBg;
    private int colorGrid;
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

    public interface OnPieceRotateListener {
        void onPieceRotated();
    }

    public interface OnPieceLandListener {
        void onPieceLanded();
    }

    // ==================== 游戏状态 ====================

    private final int[][] grid = new int[ROWS][COLS];
    private int currentPiece;
    private int currentRotation;
    private int pieceX, pieceY;
    private int nextPiece;
    private int score = 0;
    private int lines = 0;
    private int level = 1;
    private boolean running = false;
    private boolean gameOver = false;
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
        applyThemeColors();
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

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        colorBg = isDark ? 0xFF0E1016 : 0xFF1B1B1F;
        colorGrid = isDark ? 0xFF2A2E3A : 0xFF2C2C30;
        paintBg.setColor(colorBg);
        paintGrid.setColor(colorGrid);
    }

    private boolean isNightMode() {
        return (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(grid[rr - 1], 0, grid[rr], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) {
                    grid[0][c] = 0;
                }
                r++;
            }
        }

        if (cleared > 0) {
            lines += cleared;
            int[] points = {0, 100, 300, 500, 800};
            score += points[Math.min(cleared, 4)] * level;
            notifyScore();

            if (linesClearedListener != null) {
                linesClearedListener.onLinesCleared(cleared);
            }

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

        canvas.drawRect(0, 0, viewWidth, viewHeight, paintBg);

        for (int i = 0; i <= COLS; i++) {
            canvas.drawLine(offsetX + i * cellSize, offsetY, offsetX + i * cellSize, offsetY + ROWS * cellSize, paintGrid);
        }
        for (int i = 0; i <= ROWS; i++) {
            canvas.drawLine(offsetX, offsetY + i * cellSize, offsetX + COLS * cellSize, offsetY + i * cellSize, paintGrid);
        }

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] != 0) {
                    drawBlock(canvas, offsetX + c * cellSize, offsetY + r * cellSize, cellSize, TETROMINO_COLORS[grid[r][c] - 1]);
                }
            }
        }

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

        paintText.setTextSize(cellSize * 0.8f);
        float infoY = offsetY + ROWS * cellSize + cellSize * 1.2f;
        canvas.drawText("分数: " + score + "  消行: " + lines + "  等级: " + level, viewWidth / 2f, infoY, paintText);

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
