package com.gamecenter.app.games.minesweeper;

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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

/**
 * 扫雷游戏视图。
 *
 * <p>Canvas 绘制扫雷网格，支持单击翻开和长按标记旗帜。
 * 使用递归展开算法自动展开无雷区域。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>三种难度：简单(9×9, 10雷)、普通(16×16, 40雷)、困难(16×30, 99雷)</li>
 *   <li>格子状态：未翻开、已翻开、已标记旗帜、已标记问号</li>
 *   <li>递归展开：点击空白格时，自动展开所有相邻的空白格和数字格</li>
 *   <li>长按 500ms 标记旗帜</li>
 *   <li>首次点击永远不会踩雷（安全区机制）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class MinesweeperView extends View {

    // ==================== 常量 ====================

    private static final int DIFF_EASY = 1;
    private static final int DIFF_NORMAL = 2;
    private static final int DIFF_HARD = 3;

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
    /** 2026-08-23 P2-2：格子状态变化监听（玩家翻开/标记后触发，用于保存续玩存档） */
    public interface OnStateChangeListener { void onStateChanged(); }

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
    /** 2026-08-23 P2-2：格子状态变化监听器（存档保存点） */
    private OnStateChangeListener stateChangeListener;

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

    /** 2026-08-23 P2-2：设置格子状态变化监听器（存档保存点） */
    public void setOnStateChangeListener(OnStateChangeListener l) { this.stateChangeListener = l; }

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

    // ==================== 存档序列化（2026-08-23 P2-2 中断续玩） ====================

    /**
     * 2026-08-23 P2-2：序列化当前局面为 JSON（中断续玩存档用）。
     *
     * <p>包含难度/棋盘尺寸、雷区分布、格子状态（未翻开/已翻开/已标记）、
     * 翻开与标记计数及已用时间；相邻雷数可由雷区重算，不单独保存。</p>
     *
     * @return 局面状态 JSONObject；游戏未开始时返回 null
     */
    public JSONObject serializeState() {
        if (!gameStarted) return null;
        try {
            JSONObject state = new JSONObject();
            state.put("difficulty", difficulty);
            state.put("rows", rows);
            state.put("cols", cols);
            state.put("mineCount", mineCount);
            state.put("firstClick", firstClick);
            state.put("revealedCount", revealedCount);
            state.put("flaggedCount", flaggedCount);
            if (startTime > 0) {
                state.put("elapsedMs", System.currentTimeMillis() - startTime);
            }
            JSONArray mineRows = new JSONArray();
            JSONArray stateRows = new JSONArray();
            for (int r = 0; r < rows; r++) {
                JSONArray mineRow = new JSONArray();
                JSONArray cellRow = new JSONArray();
                for (int c = 0; c < cols; c++) {
                    mineRow.put(mines[r][c] ? 1 : 0);
                    cellRow.put(cellState[r][c]);
                }
                mineRows.put(mineRow);
                stateRows.put(cellRow);
            }
            state.put("mines", mineRows);
            state.put("cellState", stateRows);
            return state;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 2026-08-23 P2-2：从存档 JSON 恢复局面（中断续玩）。
     *
     * <p>恢复难度、雷区分布与格子状态，并由雷区重算相邻雷数；
     * 已用时间按存档时间戳回推 startTime。</p>
     *
     * @param state 存档状态 JSONObject
     * @return 恢复是否成功（数据缺失/损坏返回 false，调用方应回退新开一局）
     */
    public boolean restoreState(JSONObject state) {
        if (state == null) return false;
        try {
            int savedDifficulty = state.getInt("difficulty");
            int savedRows = state.getInt("rows");
            int savedCols = state.getInt("cols");
            setDifficulty(savedDifficulty);
            // 存档棋盘尺寸与难度不匹配视为损坏数据
            if (rows != savedRows || cols != savedCols) return false;

            mines = new boolean[rows][cols];
            adjacentCount = new int[rows][cols];
            cellState = new int[rows][cols];
            revealed = new boolean[rows][cols];

            JSONArray mineRows = state.getJSONArray("mines");
            JSONArray stateRows = state.getJSONArray("cellState");
            for (int r = 0; r < rows; r++) {
                JSONArray mineRow = mineRows.getJSONArray(r);
                JSONArray cellRow = stateRows.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    mines[r][c] = mineRow.getInt(c) == 1;
                    cellState[r][c] = cellRow.getInt(c);
                    revealed[r][c] = cellState[r][c] == STATE_REVEALED;
                }
            }
            // 由雷区重算相邻雷数（与 placeMines 逻辑一致）
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

            firstClick = state.optBoolean("firstClick", false);
            revealedCount = state.optInt("revealedCount", 0);
            flaggedCount = state.optInt("flaggedCount", 0);
            gameStarted = true;
            gameOver = false;
            gameWon = false;
            long elapsedMs = state.optLong("elapsedMs", 0);
            startTime = elapsedMs > 0 ? System.currentTimeMillis() - elapsedMs : 0;
            invalidate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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
        // 2026-08-23 P2-2：玩家翻开后保存进度
        // （踩雷路径提前 return 不保存——失败局面由 Activity 清除存档；
        //   胜利路径 checkWin 已回调 Activity 置 isGameRunning=false，保存会被跳过）
        if (stateChangeListener != null) stateChangeListener.onStateChanged();
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
        // 2026-08-23 P2-2：玩家标记/取消标记后保存进度
        if (stateChangeListener != null) stateChangeListener.onStateChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }
}
