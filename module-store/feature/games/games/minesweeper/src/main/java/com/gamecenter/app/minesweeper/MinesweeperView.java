package com.gamecenter.app.minesweeper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * 扫雷游戏视图（独立 APK 模块版本）。
 *
 * <p>由宿主 com.gamecenter.app.games.minesweeper.MinesweeperView 迁移而来。
 * 自包含游戏逻辑与渲染，无宿主 R 资源依赖，颜色全部硬编码。</p>
 *
 * <p>Canvas 绘制扫雷网格，支持单击翻开和长按标记旗帜。
 * 使用递归展开算法自动展开无雷区域。</p>
 */
public class MinesweeperView extends View {

    // ==================== 常量 ====================

    public static final int DIFF_EASY = 1;
    public static final int DIFF_NORMAL = 2;
    public static final int DIFF_HARD = 3;

    // 格子状态
    private static final int STATE_HIDDEN = 0;
    private static final int STATE_REVEALED = 1;
    private static final int STATE_FLAGGED = 2;

    // 颜色
    private static final int COLOR_BG = 0xFF5B8A72;
    private static final int COLOR_HIDDEN = 0xFFA5D6A7;
    private static final int COLOR_REVEALED = 0xFFE8F5E9;
    private static final int COLOR_MINE = 0xFFF44336;
    private static final int COLOR_BORDER = 0xFF81C784;
    private static final int COLOR_TEXT = 0xFF2D2D2D;
    private static final int COLOR_FLAG = 0xFFF44336;

    // 数字颜色（1-8）
    private static final int[] NUMBER_COLORS = {
        0, // 未使用
        0xFF2196F3, // 1 蓝
        0xFF4CAF50, // 2 绿
        0xFFF44336, // 3 红
        0xFF9C27B0, // 4 紫
        0xFFFF9800, // 5 橙
        0xFF00BCD4, // 6 青
        0xFF795548, // 7 棕
        0xFF607D8B  // 8 灰
    };

    // ==================== 回调接口 ====================

    public interface OnGameWinListener { void onGameWin(long elapsedSeconds); }
    public interface OnGameLoseListener { void onGameLose(); }
    public interface OnCellRevealedListener { void onCellRevealed(int revealedCount); }

    // ==================== 游戏状态 ====================

    private int rows = 9;
    private int cols = 9;
    private int mineCount = 10;
    private int difficulty = DIFF_EASY;

    private boolean[][] mines;      // 是否有雷
    private int[][] adjacentCount;  // 相邻雷数
    private int[][] cellState;      // 格子状态
    private boolean[][] revealed;   // 是否已翻开

    private boolean gameStarted = false;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private boolean firstClick = true;
    private int revealedCount = 0;
    private int flaggedCount = 0;
    private long startTime = 0;

    // ==================== 工具 ====================

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long pressStartTime = 0;
    private int pressRow = -1, pressCol = -1;
    private boolean isLongPress = false;

    // ==================== 绘制工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintCell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint();
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFlag = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ==================== 监听器 ====================

    private OnGameWinListener winListener;
    private OnGameLoseListener loseListener;
    private OnCellRevealedListener cellRevealedListener;

    // ==================== 构造函数 ====================

    public MinesweeperView(@NonNull Context context) { super(context); init(); }
    public MinesweeperView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public MinesweeperView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintBg.setColor(COLOR_BG);
        paintBorder.setColor(COLOR_BORDER);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(1f);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
        paintMine.setColor(COLOR_MINE);
        paintMine.setStyle(Paint.Style.FILL);
        paintFlag.setColor(COLOR_FLAG);
        paintFlag.setStyle(Paint.Style.FILL);
    }

    // ==================== 监听器设置 ====================

    public void setOnGameWinListener(OnGameWinListener l) { this.winListener = l; }
    public void setOnGameLoseListener(OnGameLoseListener l) { this.loseListener = l; }
    public void setOnCellRevealedListener(OnCellRevealedListener l) { this.cellRevealedListener = l; }

    /**
     * 设置难度
     * @param level 1=简单, 2=普通, 3=困难
     */
    public void setDifficulty(int level) {
        this.difficulty = Math.max(1, Math.min(3, level));
        switch (difficulty) {
            case DIFF_EASY:   rows = 9;  cols = 9;  mineCount = 10; break;
            case DIFF_NORMAL: rows = 16; cols = 16; mineCount = 40; break;
            case DIFF_HARD:   rows = 16; cols = 30; mineCount = 99; break;
        }
    }

    public int getDifficulty() { return difficulty; }
    public int getMineCount() { return mineCount; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isGameOver() { return gameOver; }
    public int getFlaggedCount() { return flaggedCount; }

    // ==================== 游戏控制 ====================

    public void startGame() {
        mines = new boolean[rows][cols];
        adjacentCount = new int[rows][cols];
        cellState = new int[rows][cols];
        revealed = new boolean[rows][cols];
        gameStarted = true;
        gameOver = false;
        gameWon = false;
        firstClick = true;
        revealedCount = 0;
        flaggedCount = 0;
        startTime = 0;
        invalidate();
    }

    public void pauseGame() { /* 事件驱动 */ }
    public void resumeGame() { /* 事件驱动 */ }
    public void stopGame() { gameStarted = false; }

    // ==================== 逻辑 ====================

    /**
     * 放置地雷（首次点击后调用，确保点击位置无雷）
     */
    private void placeMines(int safeRow, int safeCol) {
        int placed = 0;
        while (placed < mineCount) {
            int r = random.nextInt(rows);
            int c = random.nextInt(cols);
            // 安全区：点击位置及周围 8 格不放雷
            if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) continue;
            if (mines[r][c]) continue;
            mines[r][c] = true;
            placed++;
        }

        // 计算相邻雷数
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mines[r][c]) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && mines[nr][nc]) {
                            count++;
                        }
                    }
                }
                adjacentCount[r][c] = count;
            }
        }
    }

    /**
     * 翻开格子（递归展开）
     */
    private void revealCell(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        if (revealed[r][c] || cellState[r][c] == STATE_FLAGGED) return;

        revealed[r][c] = true;
        cellState[r][c] = STATE_REVEALED;
        revealedCount++;

        // 如果是空白格（相邻雷数为 0），递归展开周围
        if (adjacentCount[r][c] == 0 && !mines[r][c]) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    revealCell(r + dr, c + dc);
                }
            }
        }
    }

    /**
     * 踩雷处理
     */
    private void hitMine() {
        gameOver = true;
        // 揭示所有雷
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mines[r][c]) {
                    revealed[r][c] = true;
                    cellState[r][c] = STATE_REVEALED;
                }
            }
        }
        invalidate();
        if (loseListener != null) loseListener.onGameLose();
    }

    /**
     * 检查胜利条件
     */
    private void checkWin() {
        int totalSafe = rows * cols - mineCount;
        if (revealedCount >= totalSafe) {
            gameOver = true;
            gameWon = true;
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            invalidate();
            if (winListener != null) winListener.onGameWin(elapsed);
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || !gameStarted) return;

        float cellSize = Math.min((float) w / cols, (float) h / rows);
        float offsetX = (w - cellSize * cols) / 2f;
        float offsetY = (h - cellSize * rows) / 2f;

        // 背景
        canvas.drawRect(0, 0, w, h, paintBg);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = offsetX + c * cellSize;
                float y = offsetY + r * cellSize;
                RectF rect = new RectF(x + 1, y + 1, x + cellSize - 1, y + cellSize - 1);

                if (revealed[r][c]) {
                    // 已翻开
                    paintCell.setColor(COLOR_REVEALED);
                    canvas.drawRoundRect(rect, 2, 2, paintCell);

                    if (mines[r][c]) {
                        // 地雷
                        float radius = cellSize * 0.25f;
                        canvas.drawCircle(x + cellSize / 2f, y + cellSize / 2f, radius, paintMine);
                    } else if (adjacentCount[r][c] > 0) {
                        // 数字
                        paintText.setColor(NUMBER_COLORS[adjacentCount[r][c]]);
                        paintText.setTextSize(cellSize * 0.6f);
                        float textY = y + cellSize / 2f - (paintText.ascent() + paintText.descent()) / 2f;
                        canvas.drawText(String.valueOf(adjacentCount[r][c]), x + cellSize / 2f, textY, paintText);
                    }
                } else {
                    // 未翻开
                    paintCell.setColor(COLOR_HIDDEN);
                    canvas.drawRoundRect(rect, 2, 2, paintCell);

                    if (cellState[r][c] == STATE_FLAGGED) {
                        // 旗帜标记
                        paintText.setColor(COLOR_FLAG);
                        paintText.setTextSize(cellSize * 0.6f);
                        float textY = y + cellSize / 2f - (paintText.ascent() + paintText.descent()) / 2f;
                        canvas.drawText("🚩", x + cellSize / 2f, textY, paintText);
                    }
                }

                // 边框
                canvas.drawRect(rect, paintBorder);
            }
        }
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver || !gameStarted) return true;

        float cellSize = Math.min((float) getWidth() / cols, (float) getHeight() / rows);
        float offsetX = (getWidth() - cellSize * cols) / 2f;
        float offsetY = (getHeight() - cellSize * rows) / 2f;

        int col = (int) ((event.getX() - offsetX) / cellSize);
        int row = (int) ((event.getY() - offsetY) / cellSize);

        if (row < 0 || row >= rows || col < 0 || col >= cols) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressStartTime = System.currentTimeMillis();
                pressRow = row;
                pressCol = col;
                isLongPress = false;
                // 设置长按检测
                handler.postDelayed(() -> {
                    if (pressRow == row && pressCol == col && !gameOver) {
                        isLongPress = true;
                        toggleFlag(row, col);
                    }
                }, 500);
                break;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacksAndMessages(null);
                if (!isLongPress && pressRow == row && pressCol == col) {
                    handleCellClick(row, col);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacksAndMessages(null);
                break;
        }
        return true;
    }

    private void handleCellClick(int r, int c) {
        if (cellState[r][c] == STATE_FLAGGED) return;

        if (firstClick) {
            firstClick = false;
            startTime = System.currentTimeMillis();
            placeMines(r, c);
        }

        if (mines[r][c]) {
            hitMine();
            return;
        }

        revealCell(r, c);
        if (cellRevealedListener != null) cellRevealedListener.onCellRevealed(revealedCount);
        checkWin();
        invalidate();
    }

    private void toggleFlag(int r, int c) {
        if (revealed[r][c]) return;
        if (cellState[r][c] == STATE_FLAGGED) {
            cellState[r][c] = STATE_HIDDEN;
            flaggedCount--;
        } else {
            cellState[r][c] = STATE_FLAGGED;
            flaggedCount++;
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }
}
