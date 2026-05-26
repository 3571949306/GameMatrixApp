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
 * 连连看（三消）游戏绘制 View
 *
 * <p>玩法：</p>
 * <ul>
 *   <li>8x8 彩色方块网格</li>
 *   <li>点击选中一个方块，再点击相邻方块交换位置</li>
 *   <li>交换后如果横向或纵向有3个以上相同颜色则消除</li>
 *   <li>消除后上方方块下落补位，继续消除连锁</li>
 *   <li>得分机制：每次消除 +10 分</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>游戏逻辑和绘制集中在View中，简化架构</li>
 *   <li>初始生成网格时确保没有3连相同，避免开局就消除</li>
 *   <li>交换后无匹配则自动换回，保证游戏状态一致性</li>
 *   <li>消除后延迟200ms再下落，给玩家视觉反馈时间</li>
 *   <li>连锁消除通过递归postDelayed实现</li>
 * </ul>
 *
 * <p>颜色约定：6种不同颜色的圆角方块</p>
 */
public class MatchView extends View {

    /** 网格行数 */
    private static final int ROWS = 8;
    /** 网格列数 */
    private static final int COLS = 8;
    /** 6种方块颜色：红、粉、紫、蓝、绿、黄 */
    private static final int[] COLORS = {
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0,
        0xFF2196F3, 0xFF4CAF50, 0xFFFFEB3B
    };

    /** 通用画笔，绘制时动态设置颜色 */
    private Paint paint;
    /** 方块颜色网格，存储颜色int值 */
    private int[][] grid;
    /** 当前选中方块的列坐标，-1表示未选中 */
    private int selectedX = -1, selectedY = -1;
    /** 是否正在交换中（防止重复操作） */
    private boolean isSwapping = false;
    /** 是否允许选择方块（消除动画期间禁止） */
    private boolean canSelect = true;
    /** 当前得分 */
    private int score = 0;
    /** 随机数生成器 */
    private Random random;
    /** 游戏状态回调监听器 */
    private OnGameStateListener listener;

    /**
     * 游戏状态回调接口
     */
    public interface OnGameStateListener {
        /** 分数变化时回调 */
        void onScoreChanged(int score);
        /** 没有更多可用移动时回调 */
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

    /**
     * 初始化画笔和网格
     */
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();
        grid = new int[ROWS][COLS];
        generateGrid();
    }

    /**
     * 设置游戏状态监听器
     *
     * @param listener 监听器实例
     */
    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    /**
     * 生成网格，确保初始时没有3连相同
     *
     * <p>对每个格子随机选择颜色，如果该颜色会导致横向或纵向3连，
     * 则重新选择，直到不会产生3连为止。</p>
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

    /**
     * 检查在 (row, col) 放置 color 是否会导致3连消除
     *
     * <p>只检查左方两个和上方两个同色方块，因为生成顺序是从左到右、从上到下。</p>
     *
     * @param row 行坐标
     * @param col 列坐标
     * @param color 待放置的颜色
     * @return true表示会导致3连
     */
    private boolean wouldMatch(int row, int col, int color) {
        if (col >= 2 && grid[row][col - 1] == color && grid[row][col - 2] == color) {
            return true;
        }
        if (row >= 2 && grid[row - 1][col] == color && grid[row - 2][col] == color) {
            return true;
        }
        return false;
    }

    /**
     * 视图尺寸变化时初始化网格（如果尚未初始化）
     *
     * @param w 新宽度
     * @param h 新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (grid == null) {
            grid = new int[ROWS][COLS];
            generateGrid();
        }
    }

    /**
     * 绘制游戏界面
     *
     * <p>绘制流程：</p>
     * <ol>
     *   <li>遍历8x8网格</li>
     *   <li>绘制每个方块（圆角矩形），已消除的格子值为0不绘制</li>
     *   <li>选中的方块用白色描边高亮</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cellW = w / (float) COLS;
        float cellH = h / (float) ROWS;
        float padding = 4;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = c * cellW + padding;
                float top = r * cellH + padding;
                float right = (c + 1) * cellW - padding;
                float bottom = (r + 1) * cellH - padding;

                paint.setColor(grid[r][c]);
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 12, 12, paint);

                // 选中的方块用白色描边高亮
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

    /**
     * 处理触摸事件
     *
     * <p>交互逻辑：</p>
     * <ol>
     *   <li>将触摸坐标转换为网格坐标</li>
     *   <li>如果没有选中方块，选中当前方块</li>
     *   <li>如果已有选中方块，且点击的是相邻格子（上下左右），则交换</li>
     *   <li>非相邻格子则重新选中</li>
     * </ol>
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费了事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && canSelect && !isSwapping) {
            float cellW = getWidth() / (float) COLS;
            float cellH = getHeight() / (float) ROWS;

            int x = (int) (event.getX() / cellW);
            int y = (int) (event.getY() / cellH);

            if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
                if (selectedX >= 0 && selectedY >= 0) {
                    // 已有选中方块：判断是否相邻
                    int dx = Math.abs(x - selectedX);
                    int dy = Math.abs(y - selectedY);

                    if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
                        swapTiles(selectedX, selectedY, x, y);
                    }
                    // 无论是否交换，都清除选中状态
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
     *
     * <p>交换后检测是否有3连消除：</p>
     * <ul>
     *   <li>没有匹配：换回原位，恢复可操作状态</li>
     *   <li>有匹配：延迟200ms后执行下落补位</li>
     * </ul>
     *
     * @param x1 第一个格子的列坐标
     * @param y1 第一个格子的行坐标
     * @param x2 第二个格子的列坐标
     * @param y2 第二个格子的行坐标
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
     * 查找并消除3连以上同色方块
     *
     * <p>检测逻辑：</p>
     * <ol>
     *   <li>横向扫描：每行中连续3个以上同色标记为待消除</li>
     *   <li>纵向扫描：每列中连续3个以上同色标记为待消除</li>
     *   <li>将标记的方块设为0（空），每消除一个得10分</li>
     * </ol>
     *
     * @return 是否有消除发生
     */
    private boolean findAndRemoveMatches() {
        boolean[][] toRemove = new boolean[ROWS][COLS];

        // 横向检测：检查每行中连续3个同色方块
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS - 2; c++) {
                if (grid[r][c] != 0 && grid[r][c] == grid[r][c + 1] && grid[r][c] == grid[r][c + 2]) {
                    toRemove[r][c] = toRemove[r][c + 1] = toRemove[r][c + 2] = true;
                }
            }
        }

        // 纵向检测：检查每列中连续3个同色方块
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
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>每列从底部向上扫描，将非空方块压缩到底部</li>
     *   <li>顶部空出的位置填充随机颜色的新方块</li>
     *   <li>补充后继续检测消除（连锁反应）</li>
     *   <li>连锁消除通过递归postDelayed(200ms)实现</li>
     * </ol>
     */
    private void dropTiles() {
        for (int c = 0; c < COLS; c++) {
            // 从底部向上扫描，将非空方块压缩到底部
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

            // 顶部空出的位置填充随机颜色
            for (int r = writeRow; r >= 0; r--) {
                grid[r][c] = COLORS[random.nextInt(COLORS.length)];
            }
        }

        invalidate();

        // 连锁消除：下落后继续检测是否有新的3连
        if (findAndRemoveMatches()) {
            postDelayed(this::dropTiles, 200);
        } else {
            // 无更多消除，恢复可操作状态
            isSwapping = false;
            canSelect = true;
        }
    }

    /**
     * 重置游戏状态
     *
     * <p>清除分数、选中状态，重新生成网格。</p>
     */
    public void reset() {
        score = 0;
        selectedX = selectedY = -1;
        isSwapping = false;
        canSelect = true;
        generateGrid();
        if (listener != null) listener.onScoreChanged(score);
        invalidate();
    }

    /**
     * 获取当前得分
     *
     * @return 得分
     */
    public int getScore() {
        return score;
    }
}
