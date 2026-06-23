package com.gamecenter.app.games.game2048;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.SettingsManager;

import java.util.Random;

/**
 * 2048 游戏视图。
 *
 * <p>Canvas 绘制 4×4 网格，支持滑动手势合并数字方块。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 4×4 网格存储方块值（0=空，其他=数字值）</li>
 *   <li>滑动手势通过 GestureDetector 检测</li>
 *   <li>合并规则：相同数字合并为和，每次滑动只合并一次</li>
 *   <li>新方块在滑动后随机生成（2 或 4），概率由 difficultyFactor 控制</li>
 *   <li>护眼主题配色，不同数字使用不同颜色</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class Game2048View extends View {

    // ==================== 常量 ====================

    private static final int GRID_SIZE = 4;

    // 方块颜色映射
    private static final int[] TILE_COLORS = {
        0xFFEEE4DA, // 2
        0xFFEDE0C8, // 4
        0xFFF2B179, // 8
        0xFFF59563, // 16
        0xFFF67C5F, // 32
        0xFFF65E3B, // 64
        0xFFEDCF72, // 128
        0xFFEDCC61, // 256
        0xFFEDC850, // 512
        0xFFEDC53F, // 1024
        0xFFEDC22E  // 2048
    };

    private static final int[] TEXT_COLORS = {
        0xFF776E65, // 2, 4
        0xFFF9F6F2, // 8+
    };

    // ==================== 颜色 ====================

    private static final int COLOR_BG = 0xFFBBADA0;
    private static final int COLOR_CELL_BG = 0xFFCDC1B4;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    // ==================== 回调接口 ====================

    public interface OnScoreChangeListener { void onScoreChanged(int score); }
    public interface OnTileMergedListener { void onTileMerged(int newValue); }
    public interface OnGameOverListener { void onGameOver(int finalScore); }
    public interface OnWinListener { void onWin(int score); }

    // ==================== 游戏状态 ====================

    private final int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private int score = 0;
    private boolean gameOver = false;
    private boolean won = false;
    private boolean canContinue = false;
    private float difficultyFactor = 0.5f;
    private final Random random = new Random();

    // 2026-06-23: 撤销历史栈（每次移动前 deep copy 入栈，撤销时弹出恢复）
    private final java.util.Deque<int[][]> history = new java.util.ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    // ==================== 工具 ====================

    private GestureDetector gestureDetector;
    private final Paint paintBg = new Paint();
    private final Paint paintCellBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOverlay = new Paint();

    // ==================== 监听器 ====================

    private OnScoreChangeListener scoreChangeListener;
    private OnTileMergedListener tileMergedListener;
    private OnGameOverListener gameOverListener;
    private OnWinListener winListener;

    // ==================== 音效 ====================

    /** 音效播放器（由 Activity 注入，生命周期由 Activity 管理） */
    private SoundPool soundPool;

    /** 已加载的音效 ID（复用 R.raw.ui_turn，滑动与合并共用） */
    private int gameSoundId = 0;

    // ==================== 构造函数 ====================

    public Game2048View(@NonNull Context context) { super(context); init(); }
    public Game2048View(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public Game2048View(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintBg.setColor(COLOR_BG);
        paintCellBg.setColor(COLOR_CELL_BG);
        paintTile.setStyle(Paint.Style.FILL);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
        paintOverlay.setColor(0xCC000000);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float minFling = 50;

                if (Math.abs(dx) < minFling && Math.abs(dy) < minFling) return false;

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) moveRight();
                    else moveLeft();
                } else {
                    if (dy > 0) moveDown();
                    else moveUp();
                }
                return true;
            }
        });
    }

    // ==================== 监听器设置 ====================

    public void setOnScoreChangeListener(OnScoreChangeListener l) { this.scoreChangeListener = l; }
    public void setOnTileMergedListener(OnTileMergedListener l) { this.tileMergedListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }
    public void setOnWinListener(OnWinListener l) { this.winListener = l; }
    public void setDifficultyFactor(float factor) { this.difficultyFactor = Math.max(0.1f, Math.min(1.0f, factor)); }

    /**
     * 注入音效播放器与已加载的音效 ID。
     * <p>由 {@link Game2048Activity} 在初始化时调用，SoundPool 的生命周期由 Activity 管理。</p>
     *
     * @param pool    SoundPool 实例
     * @param soundId 已加载的音效 ID
     */
    public void setSoundPool(SoundPool pool, int soundId) {
        this.soundPool = pool;
        this.gameSoundId = soundId;
    }

    /**
     * 播放游戏音效。
     * <p>播放前检查 {@link SettingsManager#shouldPlayGameSound()} 开关，关闭时静默返回。</p>
     *
     * @param volume 音量（0.0~1.0）
     * @param rate   播放速率（1.0 为正常速率）
     */
    private void playGameSound(float volume, float rate) {
        if (soundPool == null || gameSoundId == 0) return;
        if (!SettingsManager.getInstance(getContext()).shouldPlayGameSound()) return;
        try {
            soundPool.play(gameSoundId, volume, volume, 1, 0, rate);
        } catch (Exception ignored) {
        }
    }

    // ==================== 游戏控制 ====================

    public void startGame() {
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++)
                grid[r][c] = 0;
        score = 0;
        gameOver = false;
        won = false;
        canContinue = false;
        addRandomTile();
        addRandomTile();
        notifyScore();
        invalidate();
    }

    public void pauseGame() { /* 事件驱动，无需暂停 */ }
    public void resumeGame() { /* 事件驱动，无需恢复 */ }
    public void stopGame() { gameOver = true; }

    /** 2026-06-23: 获取当前分数（撤销时用） */
    public int getScore() { return score; }

    // ==================== 移动逻辑 ====================

    private void moveLeft() { if (canMove()) { saveSnapshot(); boolean moved = slideLeft(); if (moved) afterMove(); else history.pollLast(); } }
    private void moveRight() { if (canMove()) { saveSnapshot(); rotateGrid(2); boolean moved = slideLeft(); rotateGrid(2); if (moved) afterMove(); else history.pollLast(); } }
    private void moveUp() { if (canMove()) { saveSnapshot(); rotateGrid(1); boolean moved = slideLeft(); rotateGrid(3); if (moved) afterMove(); else history.pollLast(); } }
    private void moveDown() { if (canMove()) { saveSnapshot(); rotateGrid(3); boolean moved = slideLeft(); rotateGrid(1); if (moved) afterMove(); else history.pollLast(); } }

    /**
     * 2026-06-23: 保存当前 grid 到历史栈（撤销用）。
     * 限制最大 50 步历史，避免内存爆炸。
     */
    private void saveSnapshot() {
        if (history.size() >= MAX_HISTORY) {
            history.pollFirst();
        }
        int[][] copy = new int[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            System.arraycopy(grid[r], 0, copy[r], 0, GRID_SIZE);
        }
        history.addLast(copy);
    }

    /**
     * 2026-06-23: 撤销上一步。返回 true 表示成功撤销，false 表示无可撤销的历史。
     */
    public boolean undo() {
        if (history.isEmpty()) return false;
        int[][] prev = history.pollLast();
        for (int r = 0; r < GRID_SIZE; r++) {
            System.arraycopy(prev[r], 0, grid[r], 0, GRID_SIZE);
        }
        // 撤销后让玩家可以继续（重置 gameOver 但保留新生成的方块）
        gameOver = false;
        won = false;
        canContinue = false;
        invalidate();
        return true;
    }

    private boolean canMove() { return !gameOver || canContinue; }

    /**
     * 向左滑动并合并
     * @return 是否有变化
     */
    private boolean slideLeft() {
        boolean moved = false;
        boolean merged = false;
        for (int r = 0; r < GRID_SIZE; r++) {
            // 压缩：移除空格
            int[] newRow = new int[GRID_SIZE];
            int idx = 0;
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] != 0) {
                    newRow[idx++] = grid[r][c];
                }
            }
            // 合并相邻相同
            for (int c = 0; c < GRID_SIZE - 1; c++) {
                if (newRow[c] != 0 && newRow[c] == newRow[c + 1]) {
                    newRow[c] *= 2;
                    score += newRow[c];
                    if (tileMergedListener != null) tileMergedListener.onTileMerged(newRow[c]);
                    if (newRow[c] == 2048 && !won) { won = true; if (winListener != null) winListener.onWin(score); }
                    // 移除合并后的空位
                    for (int k = c + 1; k < GRID_SIZE - 1; k++) newRow[k] = newRow[k + 1];
                    newRow[GRID_SIZE - 1] = 0;
                    moved = true;
                    merged = true;
                }
            }
            // 再次压缩
            int[] finalRow = new int[GRID_SIZE];
            idx = 0;
            for (int c = 0; c < GRID_SIZE; c++) {
                if (newRow[c] != 0) finalRow[idx++] = newRow[c];
            }
            // 检查变化
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] != finalRow[c]) moved = true;
                grid[r][c] = finalRow[c];
            }
        }
        // 触发音效：合并时播放合并音效，纯滑动时播放滑动音效
        if (moved) {
            if (merged) {
                playGameSound(0.7f, 1.2f);
            } else {
                playGameSound(0.4f, 1.0f);
            }
        }
        return moved;
    }

    /**
     * 旋转网格 90° 顺时针 n 次
     */
    private void rotateGrid(int times) {
        for (int t = 0; t < times; t++) {
            int[][] temp = new int[GRID_SIZE][GRID_SIZE];
            for (int r = 0; r < GRID_SIZE; r++)
                for (int c = 0; c < GRID_SIZE; c++)
                    temp[c][GRID_SIZE - 1 - r] = grid[r][c];
            for (int r = 0; r < GRID_SIZE; r++)
                System.arraycopy(temp[r], 0, grid[r], 0, GRID_SIZE);
        }
    }

    private void afterMove() {
        addRandomTile();
        notifyScore();
        invalidate();
        checkGameOver();
    }

    private void addRandomTile() {
        // 根据难度因子决定出现 4 的概率
        int value = (random.nextFloat() < (0.1f + difficultyFactor * 0.1f)) ? 4 : 2;

        // 找到所有空位
        java.util.List<int[]> empty = new java.util.ArrayList<>();
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++)
                if (grid[r][c] == 0) empty.add(new int[]{r, c});

        if (!empty.isEmpty()) {
            int[] pos = empty.get(random.nextInt(empty.size()));
            grid[pos[0]][pos[1]] = value;
        }
    }

    private void checkGameOver() {
        // 检查是否还有空位
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++)
                if (grid[r][c] == 0) return;

        // 检查是否还有相邻相同
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++) {
                int v = grid[r][c];
                if (c < GRID_SIZE - 1 && v == grid[r][c + 1]) return;
                if (r < GRID_SIZE - 1 && v == grid[r + 1][c]) return;
            }

        gameOver = true;
        if (gameOverListener != null) gameOverListener.onGameOver(score);
    }

    private void notifyScore() {
        if (scoreChangeListener != null) scoreChangeListener.onScoreChanged(score);
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padding = w * 0.03f;
        float cellSize = (w - padding * 2 - (GRID_SIZE + 1) * padding * 0.3f) / GRID_SIZE;
        float startX = padding;
        float startY = (h - cellSize * GRID_SIZE - (GRID_SIZE + 1) * padding * 0.3f) / 2f;
        float gap = padding * 0.3f;

        // 背景
        canvas.drawRoundRect(new RectF(0, 0, w, h), 12, 12, paintBg);

        // 单元格背景和方块
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                float x = startX + c * (cellSize + gap) + gap;
                float y = startY + r * (cellSize + gap) + gap;
                RectF rect = new RectF(x, y, x + cellSize, y + cellSize);

                // 单元格背景
                paintCellBg.setColor(COLOR_CELL_BG);
                canvas.drawRoundRect(rect, 6, 6, paintCellBg);

                // 方块
                int value = grid[r][c];
                if (value > 0) {
                    int colorIdx = Integer.numberOfTrailingZeros(value) - 1; // 2->0, 4->1, 8->2, ...
                    colorIdx = Math.max(0, Math.min(colorIdx, TILE_COLORS.length - 1));
                    paintTile.setColor(TILE_COLORS[colorIdx]);
                    canvas.drawRoundRect(rect, 6, 6, paintTile);

                    // 文字
                    paintText.setColor(value <= 4 ? TEXT_COLORS[0] : TEXT_COLORS[1]);
                    float textSize = cellSize * (value >= 1000 ? 0.35f : value >= 100 ? 0.4f : 0.5f);
                    paintText.setTextSize(textSize);
                    float textY = y + cellSize / 2f - (paintText.ascent() + paintText.descent()) / 2f;
                    canvas.drawText(String.valueOf(value), x + cellSize / 2f, textY, paintText);
                }
            }
        }

        // 分数
        paintText.setColor(0xFFFFFFFF);
        paintText.setTextSize(cellSize * 0.4f);
        canvas.drawText("分数: " + score, w / 2f, startY - gap * 2, paintText);

        // 游戏结束
        if (gameOver && !canContinue) {
            canvas.drawRect(0, 0, w, h, paintOverlay);
            paintText.setTextSize(cellSize * 0.6f);
            canvas.drawText("游戏结束", w / 2f, h / 2f - cellSize * 0.5f, paintText);
            paintText.setTextSize(cellSize * 0.35f);
            canvas.drawText("点击重新开始", w / 2f, h / 2f + cellSize * 0.5f, paintText);
        }
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver && !canContinue && event.getAction() == MotionEvent.ACTION_DOWN) {
            startGame();
            return true;
        }
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return true;
    }
}
