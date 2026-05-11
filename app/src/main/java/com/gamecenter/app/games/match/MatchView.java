package com.gamecenter.app.games.match;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Random;

/**
 * 连连看游戏绘制 View
 *
 * 玩法：
 * - 8x8 彩色方块网格
 * - 点击选中一个方块，再点击相邻方块交换位置
 * - 交换后如果横向或纵向有3个以上相同颜色则消除
 * - 消除后上方方块下落补位，继续消除连锁
 * - 得分机制：每次消除 +10 分
 *
 * 颜色约定：6种不同颜色的圆角方块
 */
public class MatchView extends View {

    private static final int ROWS = 8;
    private static final int COLS = 8;
    private static final int[] COLORS = {
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
        0xFF2196F3, 0xFF4CAF50, 0xFFFFEB3B
    };

    private Paint paint;
    private int[][] grid;
    private int selectedX = -1, selectedY = -1;
    private boolean isSwapping = false;
    private boolean canSelect = true;
    private int score = 0;
    private Random random;
    private OnGameStateListener listener;

    /** 游戏状态回调接口 */
    public interface OnGameStateListener {
        void onScoreChanged(int score);
        void onNoMoreMoves();
    }

    public MatchView(Context context) {
        super(context);
        init();
    }

    public MatchView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();
        grid = new int[ROWS][COLS];
        generateGrid();
    }

    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 生成网格，确保初始时没有3连相同
     */
    private void generateGrid() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int color;
                do {
                    color = COLORS[random.nextInt(COLORS.length)];
                } while (wouldMatch(r, c, color));
                grid[r][c] = color;
            }
        }
    }

    /** 检查在 (row, col) 放置 color 是否会导致3连消除 */
    private boolean wouldMatch(int row, int col, int color) {
        if (col >= 2 && grid[row][col - 1] == color && grid[row][col - 2] == color) {
            return true;
        }
        if (row >= 2 && grid[row - 1][col] == color && grid[row - 2][col] == color) {
            return true;
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (grid == null) {
            grid = new int[ROWS][COLS];
            generateGrid();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cellW = w / (float) COLS;
        float cellH = h / (float) ROWS;
        float padding = 4;

        // 绘制所有方块，选中的用白边框高亮
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = c * cellW + padding;
                float top = r * cellH + padding;
                float right = (c + 1) * cellW - padding;
                float bottom = (r + 1) * cellH - padding;

                paint.setColor(grid[r][c]);
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 12, 12, paint);

                if (r == selectedY && c == selectedX) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFFFFFFFF);
                    paint.setStrokeWidth(4);
                    canvas.drawRoundRect(new RectF(left, top, right, bottom), 12, 12, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && canSelect && !isSwapping) {
            float cellW = getWidth() / (float) COLS;
            float cellH = getHeight() / (float) ROWS;

            int x = (int) (event.getX() / cellW);
            int y = (int) (event.getY() / cellH);

            if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
                if (selectedX >= 0 && selectedY >= 0) {
                    // 点击相邻格子则交换
                    int dx = Math.abs(x - selectedX);
                    int dy = Math.abs(y - selectedY);

                    if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
                        swapTiles(selectedX, selectedY, x, y);
                    }
                    selectedX = -1;
                    selectedY = -1;
                } else {
                    // 第一次点击选中
                    selectedX = x;
                    selectedY = y;
                }
                invalidate();
            }
        }
        return true;
    }

    /**
     * 交换两个格子
     * 如果没有产生匹配则换回，否则触发下落和消除
     */
    private void swapTiles(int x1, int y1, int x2, int y2) {
        isSwapping = true;
        canSelect = false;

        int temp = grid[y1][x1];
        grid[y1][x1] = grid[y2][x2];
        grid[y2][x2] = temp;

        boolean matched = findAndRemoveMatches();

        if (!matched) {
            // 没有匹配，换回来
            int temp2 = grid[y1][x1];
            grid[y1][x1] = grid[y2][x2];
            grid[y2][x2] = temp2;
            invalidate();
            isSwapping = false;
            canSelect = true;
        } else {
            invalidate();
            postDelayed(this::dropTiles, 200);
        }
    }

    /**
     * 查找并消除3连以上
     * @return 是否有消除发生
     */
    private boolean findAndRemoveMatches() {
        boolean[][] toRemove = new boolean[ROWS][COLS];

        // 横向检测
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS - 2; c++) {
                if (grid[r][c] != 0 && grid[r][c] == grid[r][c + 1] && grid[r][c] == grid[r][c + 2]) {
                    toRemove[r][c] = toRemove[r][c + 1] = toRemove[r][c + 2] = true;
                }
            }
        }

        // 纵向检测
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS - 2; r++) {
                if (grid[r][c] != 0 && grid[r][c] == grid[r + 1][c] && grid[r][c] == grid[r + 2][c]) {
                    toRemove[r][c] = toRemove[r + 1][c] = toRemove[r + 2][c] = true;
                }
            }
        }

        boolean found = false;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (toRemove[r][c]) {
                    found = true;
                    grid[r][c] = 0;
                    score += 10;
                }
            }
        }

        if (found && listener != null) {
            listener.onScoreChanged(score);
        }

        return found;
    }

    /**
     * 方块下落补位
     * 1. 每列从上到下压缩，非空方块向下移动
     * 2. 顶部补充新的随机方块
     * 3. 补充后继续检测消除（连锁反应）
     */
    private void dropTiles() {
        for (int c = 0; c < COLS; c++) {
            int writeRow = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--) {
                if (grid[r][c] != 0) {
                    grid[writeRow][c] = grid[r][c];
                    if (writeRow != r) {
                        grid[r][c] = 0;
                    }
                    writeRow--;
                }
            }

            for (int r = writeRow; r >= 0; r--) {
                grid[r][c] = COLORS[random.nextInt(COLORS.length)];
            }
        }

        invalidate();

        if (findAndRemoveMatches()) {
            postDelayed(this::dropTiles, 200);
        } else {
            isSwapping = false;
            canSelect = true;
        }
    }

    public void reset() {
        score = 0;
        selectedX = selectedY = -1;
        isSwapping = false;
        canSelect = true;
        generateGrid();
        if (listener != null) listener.onScoreChanged(score);
        invalidate();
    }

    public int getScore() {
        return score;
    }
}